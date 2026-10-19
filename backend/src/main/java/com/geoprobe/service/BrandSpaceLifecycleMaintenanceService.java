package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.entity.v1.BrandScope;
import com.geoprobe.mapper.v1.BrandScopeMapper;
import com.geoprobe.service.shared.BrandSpaceLifecyclePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandSpaceLifecycleMaintenanceService {

    private final BrandScopeMapper brandScopeMapper;
    private final BrandSpaceConfigService brandSpaceConfigService;

    @Scheduled(cron = "${app.brand-space.lifecycle.cron:0 15 3 * * *}", zone = "${app.brand-space.lifecycle.zone:Asia/Shanghai}")
    public void runScheduledSweep() {
        LifecycleSweepResult result = runSweep(LocalDateTime.now());
        if (result.normalizedScopes() > 0 || result.purgedScopes() > 0) {
            log.info("Brand space lifecycle sweep finished, normalizedScopes={}, purgedScopes={}",
                    result.normalizedScopes(),
                    result.purgedScopes());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public LifecycleSweepResult runSweep(LocalDateTime now) {
        List<BrandScope> archivedScopes = brandScopeMapper.selectList(new LambdaQueryWrapper<BrandScope>()
                .eq(BrandScope::getScopeStatusCode, BrandSpaceLifecyclePolicy.SCOPE_STATUS_ARCHIVED)
                .orderByAsc(BrandScope::getArchivedAt)
                .orderByAsc(BrandScope::getUpdatedAt));
        if (archivedScopes.isEmpty()) {
            return new LifecycleSweepResult(0, 0);
        }

        int normalizedScopes = 0;
        int purgedScopes = 0;
        for (BrandScope scope : archivedScopes) {
            boolean changed = false;
            boolean normalized = false;
            LocalDateTime anchor = resolveArchiveAnchor(scope, now);
            if (scope.getRetentionUntil() == null) {
                scope.setRetentionUntil(BrandSpaceLifecyclePolicy.resolveRetentionUntil(anchor));
                changed = true;
                normalized = true;
            }
            if (scope.getInternalRestoreUntil() == null) {
                scope.setInternalRestoreUntil(BrandSpaceLifecyclePolicy.resolveInternalRestoreUntil(anchor));
                changed = true;
                normalized = true;
            }
            if (scope.getPurgedAt() == null
                    && !BrandSpaceLifecyclePolicy.isWindowOpen(scope.getInternalRestoreUntil(), now)) {
                brandSpaceConfigService.purgeRecoverableConfig(scope.getId());
                scope.setPurgedAt(now);
                changed = true;
                purgedScopes++;
            }
            if (normalized) {
                normalizedScopes++;
            }
            if (changed) {
                brandScopeMapper.updateById(scope);
            }
        }
        return new LifecycleSweepResult(normalizedScopes, purgedScopes);
    }

    private LocalDateTime resolveArchiveAnchor(BrandScope scope, LocalDateTime fallbackNow) {
        if (scope == null) {
            return fallbackNow;
        }
        if (scope.getArchivedAt() != null) {
            return scope.getArchivedAt();
        }
        if (scope.getUpdatedAt() != null) {
            return scope.getUpdatedAt();
        }
        if (scope.getCreatedAt() != null) {
            return scope.getCreatedAt();
        }
        return fallbackNow;
    }

    public record LifecycleSweepResult(int normalizedScopes, int purgedScopes) {
    }
}

package com.geoprobe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserTenancyBootstrapService {

    private final DiagnosisTenantContextService diagnosisTenantContextService;

    public void ensureUserTenancy(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        diagnosisTenantContextService.ensureUserTenancy(userId);
    }
}

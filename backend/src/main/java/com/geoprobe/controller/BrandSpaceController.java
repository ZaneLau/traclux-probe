package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.brandspace.BrandSpaceListResponse;
import com.geoprobe.dto.brandspace.BrandSpaceArchiveListResponse;
import com.geoprobe.dto.brandspace.BrandSpaceBasicProfileView;
import com.geoprobe.dto.brandspace.BrandSpacePreviewResponse;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpaceSystemRefreshView;
import com.geoprobe.dto.brandspace.BrandSpaceArchiveView;
import com.geoprobe.dto.brandspace.BrandSpaceListItemView;
import com.geoprobe.dto.brandspace.BrandSpaceRunDetailView;
import com.geoprobe.dto.brandspace.BrandSpaceRunHistoryView;
import com.geoprobe.dto.brandspace.BrandSpaceScheduleExplanationView;
import com.geoprobe.dto.brandspace.BrandSpaceProbePlanExecuteOnceResponse;
import com.geoprobe.dto.brandspace.BrandSpaceProbePlanView;
import com.geoprobe.dto.brandspace.CreateBrandSpaceResponse;
import com.geoprobe.dto.request.BrandSpaceProbePlanConfirmRequest;
import com.geoprobe.dto.request.BrandSpaceBasicProfileUpdateRequest;
import com.geoprobe.dto.request.BrandSpaceConfigUpdateRequest;
import com.geoprobe.dto.request.BrandSpaceCreateRequest;
import com.geoprobe.dto.request.BrandSpacePreviewRequest;
import com.geoprobe.dto.request.BrandSpaceTrackingModeUpdateRequest;
import com.geoprobe.service.BrandSpaceService;
import com.geoprobe.service.BrandSpaceRunReadService;
import com.geoprobe.service.BrandSpaceProbePlanReadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/brand-spaces")
@RequiredArgsConstructor
public class BrandSpaceController {

    private final BrandSpaceService brandSpaceService;
    private final BrandSpaceRunReadService brandSpaceRunReadService;
    private final BrandSpaceProbePlanReadService brandSpaceProbePlanReadService;

    @PostMapping("/preview")
    public Result<BrandSpacePreviewResponse> preview(@Valid @RequestBody BrandSpacePreviewRequest request,
                                                     Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.preview(userId, request));
    }

    @PostMapping
    public Result<CreateBrandSpaceResponse> create(@Valid @RequestBody BrandSpaceCreateRequest request,
                                                   Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.create(userId, request));
    }

    @GetMapping
    public Result<BrandSpaceListResponse> list(Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.list(userId));
    }

    @GetMapping("/archived")
    public Result<BrandSpaceArchiveListResponse> listArchived(Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.listArchived(userId));
    }

    @GetMapping("/{brandSpaceId}/basic-profile")
    public Result<BrandSpaceBasicProfileView> getBasicProfile(@PathVariable("brandSpaceId") String brandSpaceId,
                                                              Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.getBasicProfile(userId, brandSpaceId));
    }

    @PutMapping("/{brandSpaceId}/basic-profile")
    public Result<BrandSpaceBasicProfileView> updateBasicProfile(@PathVariable("brandSpaceId") String brandSpaceId,
                                                                 @Valid @RequestBody BrandSpaceBasicProfileUpdateRequest request,
                                                                 Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.updateBasicProfile(userId, brandSpaceId, request));
    }

    @GetMapping("/{brandSpaceId}/config")
    public Result<BrandSpaceConfigView> getConfig(@PathVariable("brandSpaceId") String brandSpaceId,
                                                  Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.getConfig(userId, brandSpaceId));
    }

    @PutMapping("/{brandSpaceId}/config")
    public Result<BrandSpaceConfigView> updateConfig(@PathVariable("brandSpaceId") String brandSpaceId,
                                                     @Valid @RequestBody BrandSpaceConfigUpdateRequest request,
                                                     Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.updateConfig(userId, brandSpaceId, request));
    }

    @PostMapping("/{brandSpaceId}/config/regenerate-system-generated")
    public Result<BrandSpaceConfigView> regenerateSystemGeneratedConfig(@PathVariable("brandSpaceId") String brandSpaceId,
                                                                        Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.regenerateSystemGeneratedConfig(userId, brandSpaceId));
    }

    @PostMapping("/{brandSpaceId}/system-refresh")
    public Result<BrandSpaceSystemRefreshView> refreshSystemProfileAndConfig(@PathVariable("brandSpaceId") String brandSpaceId,
                                                                             Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.refreshSystemProfileAndConfig(userId, brandSpaceId));
    }

    @GetMapping("/{brandSpaceId}/schedule")
    public Result<BrandSpaceScheduleExplanationView> getScheduleExplanation(@PathVariable("brandSpaceId") String brandSpaceId,
                                                                            Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.getScheduleExplanation(userId, brandSpaceId));
    }

    @GetMapping("/{brandSpaceId}/probe-plan")
    public Result<BrandSpaceProbePlanView> getProbePlan(@PathVariable("brandSpaceId") String brandSpaceId,
                                                        Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceProbePlanReadService.getProbePlan(userId, brandSpaceId));
    }

    @PostMapping("/{brandSpaceId}/probe-plan/confirm")
    public Result<BrandSpaceProbePlanView> confirmProbePlan(@PathVariable("brandSpaceId") String brandSpaceId,
                                                            @Valid @RequestBody BrandSpaceProbePlanConfirmRequest request,
                                                            Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceProbePlanReadService.confirmCurrentPlan(userId, brandSpaceId, request));
    }

    @PostMapping("/{brandSpaceId}/probe-plan/execute-once")
    public Result<BrandSpaceProbePlanExecuteOnceResponse> executeProbePlanOnce(@PathVariable("brandSpaceId") String brandSpaceId,
                                                                               Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceProbePlanReadService.executeCurrentPlanOnce(userId, brandSpaceId));
    }

    @GetMapping("/{brandSpaceId}/runs")
    public Result<BrandSpaceRunHistoryView> listRuns(@PathVariable("brandSpaceId") String brandSpaceId,
                                                     @RequestParam(name = "dateFrom", required = false) String dateFrom,
                                                     @RequestParam(name = "dateTo", required = false) String dateTo,
                                                     Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceRunReadService.listRuns(userId, brandSpaceId, dateFrom, dateTo));
    }

    @GetMapping("/{brandSpaceId}/runs/{runId}")
    public Result<BrandSpaceRunDetailView> getRunDetail(@PathVariable("brandSpaceId") String brandSpaceId,
                                                        @PathVariable("runId") String runId,
                                                        Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceRunReadService.getRunDetail(userId, brandSpaceId, runId));
    }

    @PatchMapping("/{brandSpaceId}/tracking-mode")
    public Result<BrandSpaceListItemView> updateTrackingMode(@PathVariable("brandSpaceId") String brandSpaceId,
                                                             @Valid @RequestBody BrandSpaceTrackingModeUpdateRequest request,
                                                             Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.updateTrackingMode(userId, brandSpaceId, request));
    }

    @PatchMapping("/{brandSpaceId}/archive")
    public Result<BrandSpaceArchiveView> archive(@PathVariable("brandSpaceId") String brandSpaceId,
                                                 Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.archive(userId, brandSpaceId));
    }

    @PatchMapping("/{brandSpaceId}/restore")
    public Result<BrandSpaceListItemView> restore(@PathVariable("brandSpaceId") String brandSpaceId,
                                                  Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.restore(userId, brandSpaceId));
    }

    @DeleteMapping("/{brandSpaceId}/archived")
    public Result<BrandSpaceArchiveView> deleteArchived(@PathVariable("brandSpaceId") String brandSpaceId,
                                                        Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(brandSpaceService.deleteArchived(userId, brandSpaceId));
    }
}

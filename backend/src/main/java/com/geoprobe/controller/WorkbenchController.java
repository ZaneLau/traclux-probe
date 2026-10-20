package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.workbench.WorkbenchBrandPerformanceView;
import com.geoprobe.dto.workbench.WorkbenchCitationSourcesView;
import com.geoprobe.dto.workbench.WorkbenchDataProvenanceCardView;
import com.geoprobe.dto.workbench.WorkbenchDataProvenanceQuery;
import com.geoprobe.dto.workbench.WorkbenchOverviewView;
import com.geoprobe.dto.workbench.WorkbenchProblemAnalysisView;
import com.geoprobe.dto.workbench.WorkbenchAnalysisContextRequest;
import com.geoprobe.service.WorkbenchDataProvenanceService;
import com.geoprobe.service.WorkbenchProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workbench")
@RequiredArgsConstructor
public class WorkbenchController {

    private final WorkbenchProjectionService workbenchProjectionService;
    private final WorkbenchDataProvenanceService workbenchDataProvenanceService;

    @GetMapping("/overview")
    public Result<WorkbenchOverviewView> getOverview(@RequestParam("brandSpaceId") String brandSpaceId,
                                                     @RequestParam(value = "timePreset", required = false) String timePreset,
                                                     @RequestParam(value = "dateFrom", required = false) String dateFrom,
                                                     @RequestParam(value = "dateTo", required = false) String dateTo,
                                                     @RequestParam(value = "platforms", required = false) List<String> platforms,
                                                     @RequestParam(value = "comparison", required = false) String comparison,
                                                     @RequestParam(value = "frequency", required = false) String frequency,
                                                     Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(workbenchProjectionService.getOverview(userId, brandSpaceId,
                analysisContext(timePreset, dateFrom, dateTo, platforms, comparison, frequency)));
    }

    @GetMapping("/brand-performance")
    public Result<WorkbenchBrandPerformanceView> getBrandPerformance(@RequestParam("brandSpaceId") String brandSpaceId,
                                                                     @RequestParam(value = "timePreset", required = false) String timePreset,
                                                                     @RequestParam(value = "dateFrom", required = false) String dateFrom,
                                                                     @RequestParam(value = "dateTo", required = false) String dateTo,
                                                                     @RequestParam(value = "platforms", required = false) List<String> platforms,
                                                                     @RequestParam(value = "comparison", required = false) String comparison,
                                                                     @RequestParam(value = "frequency", required = false) String frequency,
                                                                     Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(workbenchProjectionService.getBrandPerformance(userId, brandSpaceId,
                analysisContext(timePreset, dateFrom, dateTo, platforms, comparison, frequency)));
    }

    @GetMapping("/citation-sources")
    public Result<WorkbenchCitationSourcesView> getCitationSources(@RequestParam("brandSpaceId") String brandSpaceId,
                                                                   @RequestParam(value = "timePreset", required = false) String timePreset,
                                                                   @RequestParam(value = "dateFrom", required = false) String dateFrom,
                                                                   @RequestParam(value = "dateTo", required = false) String dateTo,
                                                                   @RequestParam(value = "platforms", required = false) List<String> platforms,
                                                                   @RequestParam(value = "comparison", required = false) String comparison,
                                                                   @RequestParam(value = "frequency", required = false) String frequency,
                                                                   Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(workbenchProjectionService.getCitationSources(userId, brandSpaceId,
                analysisContext(timePreset, dateFrom, dateTo, platforms, comparison, frequency)));
    }

    @GetMapping("/problem-analysis")
    public Result<WorkbenchProblemAnalysisView> getProblemAnalysis(@RequestParam("brandSpaceId") String brandSpaceId,
                                                                   @RequestParam(value = "topicNames", required = false) List<String> topicNames,
                                                                   @RequestParam(value = "problemSpaceBuckets", required = false) List<String> problemSpaceBuckets,
                                                                   @RequestParam(value = "timePreset", required = false) String timePreset,
                                                                   @RequestParam(value = "dateFrom", required = false) String dateFrom,
                                                                   @RequestParam(value = "dateTo", required = false) String dateTo,
                                                                   @RequestParam(value = "platforms", required = false) List<String> platforms,
                                                                   @RequestParam(value = "comparison", required = false) String comparison,
                                                                   @RequestParam(value = "frequency", required = false) String frequency,
                                                                   Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(workbenchProjectionService.getProblemAnalysis(userId, brandSpaceId, topicNames, problemSpaceBuckets,
                analysisContext(timePreset, dateFrom, dateTo, platforms, comparison, frequency)));
    }

    @GetMapping("/data-provenance-card")
    public Result<WorkbenchDataProvenanceCardView> getDataProvenanceCard(
            @RequestParam("brandSpaceId") String brandSpaceId,
            @RequestParam(value = "runId", required = false) String runId,
            @RequestParam(value = "topicId", required = false) String topicId,
            @RequestParam(value = "probeId", required = false) String probeId,
            @RequestParam(value = "anchorType", required = false) String anchorType,
            @RequestParam(value = "anchorKey", required = false) String anchorKey,
            @RequestParam(value = "metricKey", required = false) String metricKey,
            @RequestParam(value = "topicName", required = false) String topicName,
            @RequestParam(value = "metricLabel", required = false) String metricLabel,
            @RequestParam(value = "metricValue", required = false) String metricValue,
            @RequestParam(value = "timePreset", required = false) String timePreset,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "resultPointKey", required = false) String resultPointKey,
            @RequestParam(value = "windowKey", required = false) String windowKey,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "platforms", required = false) List<String> platforms,
            @RequestParam(value = "contextCitationCount", required = false) Integer contextCitationCount,
            @RequestParam(value = "contextCitationDomainCount", required = false) Integer contextCitationDomainCount,
            @RequestParam(value = "contextTotalCitationCount", required = false) Integer contextTotalCitationCount,
            @RequestParam(value = "contextCitationShare", required = false) Double contextCitationShare,
            @RequestParam(value = "contextSampleSize", required = false) Integer contextSampleSize,
            @RequestParam(value = "contextMentionCount", required = false) Integer contextMentionCount,
            @RequestParam(value = "contextMentionRate", required = false) Double contextMentionRate,
            @RequestParam(value = "contextRecommendRate", required = false) Double contextRecommendRate,
            @RequestParam(value = "contextTop3Rate", required = false) Double contextTop3Rate,
            @RequestParam(value = "contextDemandVolume", required = false) Double contextDemandVolume,
            @RequestParam(value = "contextDemandTotal", required = false) Double contextDemandTotal,
            @RequestParam(value = "contextActiveTopicCount", required = false) Integer contextActiveTopicCount,
            @RequestParam(value = "contextActivePlatformCount", required = false) Integer contextActivePlatformCount,
            @RequestParam(value = "contextPromptCount", required = false) Integer contextPromptCount,
            @RequestParam(value = "contextTotalProjection", required = false) Double contextTotalProjection,
            @RequestParam(value = "contextHighValuePromptCount", required = false) Integer contextHighValuePromptCount,
            @RequestParam(value = "contextPromptLoopTotalCount", required = false) Integer contextPromptLoopTotalCount,
            @RequestParam(value = "contextPromptLoopCoveredCount", required = false) Integer contextPromptLoopCoveredCount,
            @RequestParam(value = "contextPromptLoopGapCount", required = false) Integer contextPromptLoopGapCount,
            @RequestParam(value = "contextPromptLoopHighOpportunityCount", required = false) Integer contextPromptLoopHighOpportunityCount,
            @RequestParam(value = "samplePage", required = false) Integer samplePage,
            @RequestParam(value = "samplePageSize", required = false) Integer samplePageSize,
            Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(workbenchDataProvenanceService.getCard(userId,
                new WorkbenchDataProvenanceQuery(brandSpaceId, runId, topicId, topicName, probeId, anchorType, anchorKey,
                        metricKey, metricLabel, metricValue, timePreset, dateFrom, dateTo, resultPointKey, windowKey,
                        platform, domain, platforms == null ? List.of() : platforms, contextCitationCount,
                        contextCitationDomainCount, contextTotalCitationCount, contextCitationShare, contextSampleSize,
                        contextMentionCount, contextMentionRate, contextRecommendRate, contextTop3Rate, contextDemandVolume,
                        contextDemandTotal, contextActiveTopicCount, contextActivePlatformCount, contextPromptCount,
                        contextTotalProjection, contextHighValuePromptCount, contextPromptLoopTotalCount,
                        contextPromptLoopCoveredCount, contextPromptLoopGapCount, contextPromptLoopHighOpportunityCount,
                        samplePage, samplePageSize)));
    }

    private WorkbenchAnalysisContextRequest analysisContext(String timePreset,
                                                            String dateFrom,
                                                            String dateTo,
                                                            List<String> platforms,
                                                            String comparison,
                                                            String frequency) {
        return new WorkbenchAnalysisContextRequest(timePreset, dateFrom, dateTo,
                platforms == null ? List.of() : platforms,
                comparison,
                frequency);
    }
}

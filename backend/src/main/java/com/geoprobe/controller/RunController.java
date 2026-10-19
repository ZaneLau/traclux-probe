package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.brandspace.RunEventPageView;
import com.geoprobe.dto.brandspace.RunView;
import com.geoprobe.service.RunReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunReadService runReadService;

    @GetMapping("/{runId}")
    public Result<RunView> getRun(@PathVariable String runId, Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(runReadService.getRun(userId, runId));
    }

    @GetMapping("/{runId}/events")
    public Result<RunEventPageView> getRunEvents(@PathVariable String runId,
                                                 @RequestParam(name = "afterSeq", required = false) Long afterSeq,
                                                 @RequestParam(name = "limit", required = false) Integer limit,
                                                 Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(runReadService.getRunEvents(userId, runId, afterSeq, limit));
    }
}

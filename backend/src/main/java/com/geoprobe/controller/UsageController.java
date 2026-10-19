package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.brandspace.UsageDailyView;
import com.geoprobe.service.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/usage")
@RequiredArgsConstructor
public class UsageController {

    private final UsageService usageService;

    @GetMapping("/daily")
    public Result<UsageDailyView> getDailyUsage(Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(usageService.getDailyUsage(userId));
    }
}

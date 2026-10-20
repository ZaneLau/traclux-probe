package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.service.ServiceAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service-access")
@RequiredArgsConstructor
public class ServiceAccessController {

    private final ServiceAccessService serviceAccessService;

    @GetMapping
    public Result<ServiceAccessView> getServiceAccess(Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(serviceAccessService.getServiceAccess(userId));
    }
}

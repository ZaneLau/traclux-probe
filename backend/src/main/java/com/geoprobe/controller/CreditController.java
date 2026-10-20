package com.geoprobe.controller;

import com.geoprobe.common.Result;
import com.geoprobe.dto.request.PurchaseCreditsRequest;
import com.geoprobe.dto.response.CreditBalanceResponse;
import com.geoprobe.dto.response.PurchaseCreditsResponse;
import com.geoprobe.service.CreditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditService creditService;

    @GetMapping("/balance")
    public Result<CreditBalanceResponse> getBalance(Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(creditService.getBalance(userId));
    }

    @PostMapping("/purchase")
    public Result<PurchaseCreditsResponse> purchaseCredits(@Valid @RequestBody PurchaseCreditsRequest request,
                                                           Authentication authentication) {
        String userId = String.valueOf(authentication.getPrincipal());
        return Result.ok(creditService.purchaseCredits(userId, request.getAmount(), request.getPaymentMethod()));
    }
}

package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditBalanceResponse {

    private Integer creditsRemaining;
    private Integer totalPurchased;
    private Integer totalConsumed;
}

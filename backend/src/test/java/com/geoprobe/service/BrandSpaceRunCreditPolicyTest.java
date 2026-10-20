package com.geoprobe.service;

import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.response.CreditBalanceResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandSpaceRunCreditPolicyTest {

    @Mock
    private CreditService creditService;

    @InjectMocks
    private BrandSpaceRunCreditPolicy brandSpaceRunCreditPolicy;

    @Test
    void shouldQuoteCreditsFromProbeCountAndConfiguredUnitPrice() {
        when(creditService.getBalance("u1")).thenReturn(CreditBalanceResponse.builder()
                .creditsRemaining(80)
                .build());

        BrandSpaceRunCreditPolicy.CreditQuote quote = brandSpaceRunCreditPolicy.quote(
                "u1",
                16,
                ServiceAccessView.builder()
                        .limits(ServiceAccessView.Limits.builder()
                                .creditsPerProbe(2)
                                .build())
                        .build()
        );

        assertEquals(16, quote.targetProbeCount());
        assertEquals(2, quote.creditsPerProbe());
        assertEquals(32, quote.estimatedCredits());
        assertEquals(80, quote.creditsRemaining());
        assertEquals(48, quote.balanceAfterRun());
        assertTrue(quote.sufficient());
    }

    @Test
    void shouldFallbackToOneCreditPerProbeWhenUnitPriceMissing() {
        when(creditService.getBalance("u2")).thenReturn(CreditBalanceResponse.builder()
                .creditsRemaining(6)
                .build());

        BrandSpaceRunCreditPolicy.CreditQuote quote = brandSpaceRunCreditPolicy.quote("u2", 8, null);

        assertEquals(1, quote.creditsPerProbe());
        assertEquals(8, quote.estimatedCredits());
        assertFalse(quote.sufficient());
    }
}

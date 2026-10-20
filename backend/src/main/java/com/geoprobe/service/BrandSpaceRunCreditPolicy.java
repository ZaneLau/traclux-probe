package com.geoprobe.service;

import com.geoprobe.dto.brandspace.ServiceAccessView;
import com.geoprobe.dto.response.CreditBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BrandSpaceRunCreditPolicy {

    private static final int DEFAULT_CREDITS_PER_PROBE = 1;

    private final CreditService creditService;

    public CreditQuote quote(String userId,
                             Integer targetProbeCount,
                             ServiceAccessView serviceAccess) {
        int normalizedTargetProbeCount = normalizeTargetProbeCount(targetProbeCount);
        int creditsPerProbe = resolveCreditsPerProbe(serviceAccess);
        int estimatedCredits = Math.max(1, normalizedTargetProbeCount * creditsPerProbe);
        CreditBalanceResponse balance = creditService.getBalance(userId);
        int creditsRemaining = balance == null || balance.getCreditsRemaining() == null
                ? 0
                : Math.max(0, balance.getCreditsRemaining());
        int balanceAfterRun = Math.max(0, creditsRemaining - estimatedCredits);
        return new CreditQuote(
                normalizedTargetProbeCount,
                creditsPerProbe,
                estimatedCredits,
                creditsRemaining,
                balanceAfterRun,
                creditsRemaining >= estimatedCredits
        );
    }

    public boolean deductQuotedCredits(String userId, String runId, CreditQuote quote) {
        return quote != null
                && quote.estimatedCredits() > 0
                && creditService.deductCredits(userId, runId, quote.estimatedCredits());
    }

    public int resolveCreditsPerProbe(ServiceAccessView serviceAccess) {
        ServiceAccessView.Limits limits = serviceAccess == null ? null : serviceAccess.getLimits();
        Integer value = limits == null ? null : limits.getCreditsPerProbe();
        if (value == null || value <= 0) {
            return DEFAULT_CREDITS_PER_PROBE;
        }
        return value;
    }

    private int normalizeTargetProbeCount(Integer targetProbeCount) {
        if (targetProbeCount == null || targetProbeCount <= 0) {
            return 1;
        }
        return targetProbeCount;
    }

    public record CreditQuote(int targetProbeCount,
                              int creditsPerProbe,
                              int estimatedCredits,
                              int creditsRemaining,
                              int balanceAfterRun,
                              boolean sufficient) {
    }
}

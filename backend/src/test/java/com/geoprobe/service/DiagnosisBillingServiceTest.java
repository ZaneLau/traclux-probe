package com.geoprobe.service;

import com.geoprobe.mapper.DiagnosisMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosisBillingServiceTest {

    @Mock
    private DiagnosisMapper diagnosisMapper;

    @Mock
    private DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DiagnosisBillingService diagnosisBillingService;

    @Test
    void shouldReadChargedCreditsFromRuntimeBillingMetadata() {
        when(diagnosisTaskNativeRuntimeService.readTaskBillingMetadata("d1"))
                .thenReturn(Map.of("chargedCredits", 24));

        int chargedCredits = diagnosisBillingService.readChargedCredits("d1", null);

        assertEquals(24, chargedCredits);
    }
}

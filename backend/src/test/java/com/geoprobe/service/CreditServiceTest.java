package com.geoprobe.service;

import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.User;
import com.geoprobe.mapper.CreditTransactionMapper;
import com.geoprobe.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private CreditTransactionMapper creditTransactionMapper;
    @Mock
    private LegacyCreditMirrorService legacyCreditMirrorService;

    @InjectMocks
    private CreditService creditService;

    @Test
    void shouldDeductCreditsAndCreateConsumeTransaction() {
        when(userMapper.deductCreditsIfEnough(anyString(), anyInt())).thenReturn(1);

        boolean deducted = creditService.deductCredits("u1", "d1", 1);

        assertTrue(deducted);
        verify(userMapper).deductCreditsIfEnough("u1", 1);

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionMapper).insert(txCaptor.capture());
        assertEquals(2, txCaptor.getValue().getType());
        assertEquals(-1, txCaptor.getValue().getAmount());
        assertEquals("d1", txCaptor.getValue().getDiagnosisId());
    }

    @Test
    void shouldReturnFalseWhenBalanceInsufficient() {
        when(userMapper.deductCreditsIfEnough(anyString(), anyInt())).thenReturn(0);

        boolean deducted = creditService.deductCredits("u1", "d2", 1);

        assertFalse(deducted);
        verify(userMapper).deductCreditsIfEnough("u1", 1);
        verify(creditTransactionMapper, never()).insert(org.mockito.ArgumentMatchers.any(CreditTransaction.class));
    }

    @Test
    void shouldRefundCreditsAndCreateRefundTransaction() {
        User user = User.builder().id("u1").creditsRemaining(2).build();
        when(userMapper.selectById("u1")).thenReturn(user);
        when(userMapper.addCredits(anyString(), anyInt())).thenReturn(1);
        when(creditTransactionMapper.selectCount(any())).thenReturn(0L, 1L);

        creditService.refundCredits("u1", "d3", 1);

        verify(userMapper).addCredits("u1", 1);

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionMapper).insert(txCaptor.capture());
        assertEquals(3, txCaptor.getValue().getType());
        assertEquals(1, txCaptor.getValue().getAmount());
        assertEquals("d3", txCaptor.getValue().getDiagnosisId());
    }

    @Test
    void shouldPurchaseCreditsAndCreatePurchaseTransaction() {
        User user = User.builder().id("u1").creditsRemaining(2).build();
        when(userMapper.selectById("u1")).thenReturn(user);
        when(userMapper.addCredits(anyString(), anyInt())).thenReturn(1);

        var response = creditService.purchaseCredits("u1", 5, "MANUAL");

        assertEquals(7, response.getCreditsRemaining());
        assertEquals(5, response.getAmount());
        verify(userMapper).addCredits("u1", 5);

        ArgumentCaptor<CreditTransaction> txCaptor = ArgumentCaptor.forClass(CreditTransaction.class);
        verify(creditTransactionMapper).insert(txCaptor.capture());
        assertEquals(1, txCaptor.getValue().getType());
        assertEquals(5, txCaptor.getValue().getAmount());
    }

    @Test
    void shouldSkipLegacyCreditMirrorWhenFeatureDisabled() {
        when(userMapper.deductCreditsIfEnough(anyString(), anyInt())).thenReturn(1);

        boolean deducted = creditService.deductCredits("u1", "d9", 1);

        assertTrue(deducted);
        verify(legacyCreditMirrorService, never()).mirrorCreditTransaction(anyString(), any(CreditTransaction.class));
    }
}

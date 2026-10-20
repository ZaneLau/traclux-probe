package com.geoprobe.service;

import com.geoprobe.entity.CreditTransaction;
import com.geoprobe.entity.User;
import com.geoprobe.entity.v1.DiagnosisTask;
import com.geoprobe.entity.v1.Organization;
import com.geoprobe.entity.v1.OrganizationMembership;
import com.geoprobe.entity.v1.QuotaAccount;
import com.geoprobe.entity.v1.QuotaLedger;
import com.geoprobe.mapper.v1.DiagnosisTaskMapper;
import com.geoprobe.mapper.v1.QuotaAccountMapper;
import com.geoprobe.mapper.v1.QuotaGrantMapper;
import com.geoprobe.mapper.v1.QuotaLedgerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LegacyCreditMirrorServiceTest {

    @Mock
    private DiagnosisTenantContextService diagnosisTenantContextService;
    @Mock
    private QuotaAccountMapper quotaAccountMapper;
    @Mock
    private QuotaGrantMapper quotaGrantMapper;
    @Mock
    private QuotaLedgerMapper quotaLedgerMapper;
    @Mock
    private DiagnosisTaskMapper diagnosisTaskMapper;

    private LegacyCreditMirrorService legacyCreditMirrorService;

    @BeforeEach
    void setUp() {
        legacyCreditMirrorService = new LegacyCreditMirrorService(
                diagnosisTenantContextService,
                quotaAccountMapper,
                quotaGrantMapper,
                quotaLedgerMapper,
                diagnosisTaskMapper
        );
        ReflectionTestUtils.setField(legacyCreditMirrorService, "enabled", true);
        ReflectionTestUtils.setField(legacyCreditMirrorService, "legacyCreditWriteEnabled", true);
    }

    @Test
    void shouldMirrorConsumeLedgerWithProbePlanAttribution() {
        User user = User.builder().id("user-1").creditsRemaining(9).build();
        QuotaAccount account = QuotaAccount.builder()
                .id("quota-account-1")
                .consumedProbeQuota(0L)
                .build();
        when(diagnosisTenantContextService.ensureUserTenancy("user-1"))
                .thenReturn(new DiagnosisTenantContextService.TenantContext(
                        user,
                        Organization.builder().id("org-1").build(),
                        OrganizationMembership.builder().id("membership-1").build(),
                        null,
                        null,
                        account
                ));
        when(quotaLedgerMapper.selectCount(any())).thenReturn(0L);
        when(diagnosisTaskMapper.selectById("task-1")).thenReturn(DiagnosisTask.builder()
                .id("task-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .taskMetadataJson(Map.of(
                        "planVersionId", "plan-version-1",
                        "planRecordId", "plan-record-1",
                        "probePlanVersionNo", 2,
                        "probePlanRecordNo", 3,
                        "runTypeCode", "INITIAL",
                        "triggerSource", "brand_space_create"
                ))
                .taskOrderJson(Map.of(
                        "businessHints", Map.of(
                                "probePlanBinding", Map.of("enabledProbeCount", 8)
                        )
                ))
                .build());

        legacyCreditMirrorService.mirrorCreditTransaction("user-1", CreditTransaction.builder()
                .id("tx-1")
                .userId("user-1")
                .type(2)
                .amount(-1)
                .diagnosisId("task-1")
                .build());

        ArgumentCaptor<QuotaLedger> ledgerCaptor = ArgumentCaptor.forClass(QuotaLedger.class);
        verify(quotaLedgerMapper).insert(ledgerCaptor.capture());
        QuotaLedger ledger = ledgerCaptor.getValue();
        assertEquals("CONSUME", ledger.getLedgerTypeCode());
        assertEquals("task-1", ledger.getDiagnosisTaskId());
        assertEquals("org-1", ledger.getOrganizationId());
        assertEquals("scope-1", ledger.getBrandScopeId());
        assertEquals("plan-version-1", ledger.getPlanVersionId());
        assertEquals("plan-record-1", ledger.getPlanRecordId());
        assertEquals(1, ledger.getPricingRuleSnapshotJson().get("creditsPerProbe"));
        assertEquals("RUN_BATCH", ledger.getPricingRuleSnapshotJson().get("attributionLevel"));
        assertEquals("INITIAL", ledger.getLedgerMetadataJson().get("runTypeCode"));
        assertEquals(8, ledger.getLedgerMetadataJson().get("enabledProbeCount"));
    }

    @Test
    void shouldBackfillExistingLedgerAfterProbePlanSnapshotIsAttached() {
        QuotaLedger existingLedger = QuotaLedger.builder()
                .id("ledger-1")
                .diagnosisTaskId("task-1")
                .ledgerTypeCode("CONSUME")
                .probeDelta(-1L)
                .balanceAfter(9L)
                .build();
        when(quotaLedgerMapper.selectList(any())).thenReturn(List.of(existingLedger));

        legacyCreditMirrorService.attachDiagnosisTaskAttribution(DiagnosisTask.builder()
                .id("task-1")
                .organizationId("org-1")
                .brandScopeId("scope-1")
                .taskMetadataJson(Map.of(
                        "planVersionId", "plan-version-1",
                        "planRecordId", "plan-record-1",
                        "runTypeCode", "INITIAL"
                ))
                .build());

        ArgumentCaptor<QuotaLedger> ledgerCaptor = ArgumentCaptor.forClass(QuotaLedger.class);
        verify(quotaLedgerMapper).updateById(ledgerCaptor.capture());
        QuotaLedger updatedLedger = ledgerCaptor.getValue();
        assertEquals("ledger-1", updatedLedger.getId());
        assertEquals("org-1", updatedLedger.getOrganizationId());
        assertEquals("scope-1", updatedLedger.getBrandScopeId());
        assertEquals("plan-version-1", updatedLedger.getPlanVersionId());
        assertEquals("plan-record-1", updatedLedger.getPlanRecordId());
        assertNotNull(updatedLedger.getPricingRuleSnapshotJson());
        assertEquals("probe_plan_b2", updatedLedger.getLedgerMetadataJson().get("attributionVersion"));
    }
}

package com.geoprobe.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LegacyDiagnosisMirrorService {

    @Value("${app.database.v1.enabled:false}")
    private boolean databaseV1Enabled;

    @Value("${app.database.v1.legacy-diagnosis-write-enabled:true}")
    private boolean legacyDiagnosisWriteEnabled;

    @Value("${app.database.v1.legacy-diagnosis-write-required:false}")
    private boolean legacyDiagnosisWriteRequired;

    public boolean shouldWriteLegacyDiagnosis() {
        if (!databaseV1Enabled) {
            return true;
        }
        return legacyDiagnosisWriteEnabled;
    }

    public boolean isLegacyDiagnosisWriteRequired() {
        if (!databaseV1Enabled) {
            return true;
        }
        return legacyDiagnosisWriteEnabled && legacyDiagnosisWriteRequired;
    }
}

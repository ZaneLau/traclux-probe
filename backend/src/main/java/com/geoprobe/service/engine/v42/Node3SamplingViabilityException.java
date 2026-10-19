package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.V42DiagnosisResult;

public class Node3SamplingViabilityException extends IllegalStateException {

    private final V42DiagnosisResult.Task taskSnapshot;

    public Node3SamplingViabilityException(String message, V42DiagnosisResult.Task taskSnapshot) {
        super(message);
        this.taskSnapshot = taskSnapshot;
    }

    public V42DiagnosisResult.Task taskSnapshot() {
        return taskSnapshot;
    }
}

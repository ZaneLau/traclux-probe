package com.geoprobe.service.shared;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

@Component
public class DiagnosisCancellationRegistry {

    private final Set<String> cancellationRequests = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    public void registerRunningTask(String diagnosisId, Future<?> future) {
        if (!StringUtils.hasText(diagnosisId) || future == null) {
            return;
        }
        runningTasks.put(diagnosisId, future);
        if (isCancellationRequested(diagnosisId)) {
            future.cancel(true);
        }
    }

    public void requestCancellation(String diagnosisId) {
        if (!StringUtils.hasText(diagnosisId)) {
            return;
        }
        cancellationRequests.add(diagnosisId);
        Future<?> future = runningTasks.get(diagnosisId);
        if (future != null) {
            future.cancel(true);
        }
    }

    public boolean isCancellationRequested(String diagnosisId) {
        return StringUtils.hasText(diagnosisId) && cancellationRequests.contains(diagnosisId);
    }

    public void clear(String diagnosisId) {
        if (!StringUtils.hasText(diagnosisId)) {
            return;
        }
        cancellationRequests.remove(diagnosisId);
        runningTasks.remove(diagnosisId);
    }

    public void throwIfCancellationRequested(String diagnosisId) {
        if (isCancellationRequested(diagnosisId)) {
            throw new CancellationException("诊断任务已取消");
        }
    }
}

package com.geoprobe.service.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.geoprobe.entity.Diagnosis;
import com.geoprobe.mapper.DiagnosisMapper;
import com.geoprobe.service.DiagnosisTaskNativeRuntimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisAliasConfigService {

    private static final String META_KEY = "meta";

    private final DiagnosisMapper diagnosisMapper;
    private final DiagnosisTaskNativeRuntimeService diagnosisTaskNativeRuntimeService;
    private final ObjectMapper objectMapper;

    public DiagnosisAliasConfigData read(Diagnosis diagnosis) {
        DiagnosisAliasConfigData taskNative = readTaskNative(diagnosis == null ? null : diagnosis.getId());
        if (taskNative != null) {
            return taskNative;
        }
        return readLegacy(diagnosis);
    }

    private DiagnosisAliasConfigData readLegacy(Diagnosis diagnosis) {
        if (diagnosis == null || diagnosis.getReportData() == null) {
            return new DiagnosisAliasConfigData(List.of(), Map.of());
        }
        Map<String, Object> reportData = objectMapper.convertValue(diagnosis.getReportData(), new TypeReference<>() {
        });
        Map<String, Object> meta = objectMapper.convertValue(reportData.get(META_KEY), new TypeReference<>() {
        });
        if (meta == null) {
            return new DiagnosisAliasConfigData(List.of(), Map.of());
        }
        return parseAliasConfig(meta);
    }

    private DiagnosisAliasConfigData readTaskNative(String diagnosisId) {
        if (!StringUtils.hasText(diagnosisId)) {
            return null;
        }
        Map<String, Object> metadata = diagnosisTaskNativeRuntimeService.readTaskAliasConfigMetadata(diagnosisId);
        if (metadata.isEmpty()) {
            return null;
        }
        return parseAliasConfig(metadata);
    }

    public void persist(String diagnosisId, DiagnosisAliasConfigData aliasConfig) {
        if (!StringUtils.hasText(diagnosisId) || aliasConfig == null) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            Map<String, Object> aliasMetadata = buildAliasMetadata(aliasConfig, now);
            diagnosisTaskNativeRuntimeService.upsertTaskAliasConfigMetadata(diagnosisId, aliasMetadata);

            Diagnosis current = diagnosisMapper.selectById(diagnosisId);
            if (current == null || current.getReportData() == null) {
                return;
            }

            Map<String, Object> reportData = objectMapper.convertValue(current.getReportData(), new TypeReference<>() {
            });
            Map<String, Object> meta = objectMapper.convertValue(reportData.get(META_KEY), new TypeReference<>() {
            });
            if (meta == null) {
                meta = new LinkedHashMap<>();
            }
            meta.putAll(aliasMetadata);

            Object snapshotObject = meta.get("diagnosisConfigSnapshot");
            Map<String, Object> snapshot = snapshotObject == null
                    ? new LinkedHashMap<>()
                    : objectMapper.convertValue(snapshotObject, new TypeReference<Map<String, Object>>() {
            });
            snapshot.put("brandAliases", aliasConfig.brandAliases());
            snapshot.put("competitorAliases", aliasConfig.competitorAliases());
            meta.put("diagnosisConfigSnapshot", snapshot);

            reportData.put(META_KEY, meta);
            diagnosisMapper.updateById(Diagnosis.builder()
                    .id(diagnosisId)
                    .reportData(reportData)
                    .build());
        } catch (Exception ex) {
            log.warn("Persist alias config failed, diagnosisId={}, reason={}", diagnosisId, ex.getMessage());
        }
    }

    public DiagnosisAliasConfigData mergeLearnedAliases(Diagnosis diagnosis, Map<String, List<String>> learnedAliasesByBrand) {
        DiagnosisAliasConfigData current = read(diagnosis);
        if (learnedAliasesByBrand == null || learnedAliasesByBrand.isEmpty()) {
            return current;
        }
        List<String> brandAliases = new ArrayList<>(current.brandAliases());
        Map<String, List<String>> competitorAliases = new LinkedHashMap<>(current.competitorAliases());

        for (Map.Entry<String, List<String>> entry : learnedAliasesByBrand.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (diagnosis != null && entry.getKey().trim().equals(diagnosis.getBrandName())) {
                brandAliases = mergeAliasList(brandAliases, entry.getValue());
                continue;
            }
            List<String> existing = competitorAliases.getOrDefault(entry.getKey(), List.of());
            competitorAliases.put(entry.getKey(), mergeAliasList(existing, entry.getValue()));
        }

        return new DiagnosisAliasConfigData(brandAliases, competitorAliases);
    }

    private List<String> mergeAliasList(List<String> existing, List<String> learned) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (existing != null) {
            merged.addAll(existing);
        }
        if (learned != null) {
            for (String alias : learned) {
                if (StringUtils.hasText(alias)) {
                    merged.add(alias.trim());
                }
            }
        }
        return List.copyOf(merged);
    }

    private DiagnosisAliasConfigData parseAliasConfig(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return new DiagnosisAliasConfigData(List.of(), Map.of());
        }
        List<String> brandAliases = new ArrayList<>();
        Object brandAliasValue = source.get("brandAliases");
        if (brandAliasValue instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String text && StringUtils.hasText(text)) {
                    brandAliases.add(text.trim());
                }
            }
        }

        Map<String, List<String>> competitorAliases = new LinkedHashMap<>();
        Object competitorAliasValue = source.get("competitorAliases");
        if (competitorAliasValue instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !StringUtils.hasText(key)) {
                    continue;
                }
                List<String> aliases = new ArrayList<>();
                if (entry.getValue() instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof String text && StringUtils.hasText(text)) {
                            aliases.add(text.trim());
                        }
                    }
                }
                competitorAliases.put(key, List.copyOf(new LinkedHashSet<>(aliases)));
            }
        }

        return new DiagnosisAliasConfigData(
                List.copyOf(new LinkedHashSet<>(brandAliases)),
                Map.copyOf(competitorAliases)
        );
    }

    private Map<String, Object> buildAliasMetadata(DiagnosisAliasConfigData aliasConfig, LocalDateTime updatedAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("brandAliases", aliasConfig.brandAliases() == null ? List.of() : aliasConfig.brandAliases());
        metadata.put("competitorAliases", aliasConfig.competitorAliases() == null ? Map.of() : aliasConfig.competitorAliases());
        metadata.put("aliasConfigVersion", "1");
        metadata.put("aliasUpdatedAt", (updatedAt == null ? LocalDateTime.now() : updatedAt).toString());
        return metadata;
    }
}

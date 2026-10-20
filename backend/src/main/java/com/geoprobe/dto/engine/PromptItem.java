package com.geoprobe.dto.engine;

public record PromptItem(
        String id,
        String clusterId,
        String type,
        String text,
        double temperature,
        String angle,
        double clusterWeight,
        String voiceTypeId,
        String voiceTypeName,
        boolean brandAnchored,
        String targetBrandName,
        java.util.List<String> competitors
) {

    public PromptItem {
        competitors = competitors == null ? java.util.List.of() : java.util.List.copyOf(competitors);
    }

    /**
     * 兼容旧构造，默认 angle=type，clusterWeight=0。
     */
    public PromptItem(String id,
                      String clusterId,
                      String type,
                      String text,
                      double temperature) {
        this(id, clusterId, type, text, temperature, type, 0.0d, null, null, false, null, null);
    }

    public PromptItem(String id,
                      String clusterId,
                      String type,
                      String text,
                      double temperature,
                      String angle,
                      double clusterWeight) {
        this(id, clusterId, type, text, temperature, angle, clusterWeight, null, null, false, null, null);
    }

    public PromptItem(String id,
                      String clusterId,
                      String type,
                      String text,
                      double temperature,
                      String angle,
                      double clusterWeight,
                      String voiceTypeId,
                      String voiceTypeName,
                      boolean brandAnchored) {
        this(id, clusterId, type, text, temperature, angle, clusterWeight, voiceTypeId, voiceTypeName, brandAnchored, null, null);
    }
}

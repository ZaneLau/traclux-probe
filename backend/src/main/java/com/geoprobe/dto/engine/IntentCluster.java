package com.geoprobe.dto.engine;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record IntentCluster(
        @JsonAlias({"id"}) String clusterId,
        @JsonAlias({"name"}) String clusterName,
        String description,
        @JsonAlias({"sampleQueries"}) List<String> questions,
        String eeatDimension,
        String involvementLevel,
        double weight,
        int corePromptCount
) {

    /**
     * 兼容旧构造（id/name/sampleQueries）。
     */
    public IntentCluster(String id,
                         String name,
                         String description,
                         double weight,
                         List<String> sampleQueries) {
        this(id, name, description, sampleQueries, null, null, weight, 0);
    }

    /**
     * 兼容旧字段访问：id。
     */
    @JsonIgnore
    public String id() {
        return clusterId;
    }

    /**
     * 兼容旧字段访问：name。
     */
    @JsonIgnore
    public String name() {
        return clusterName;
    }

    /**
     * 兼容旧字段访问：sampleQueries。
     */
    @JsonIgnore
    public List<String> sampleQueries() {
        return questions;
    }
}

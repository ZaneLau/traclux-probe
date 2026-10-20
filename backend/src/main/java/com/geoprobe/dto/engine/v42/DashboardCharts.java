package com.geoprobe.dto.engine.v42;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record DashboardCharts(
        RadarChartData radar,
        ScatterChartData scatter,
        SankeyChartData sankey,
        @JsonProperty("stacked_bar") StackedBarChartData stackedBar
) {

    public DashboardCharts {
        radar = Objects.requireNonNull(radar, "radar 不能为空");
        scatter = Objects.requireNonNull(scatter, "scatter 不能为空");
        sankey = Objects.requireNonNull(sankey, "sankey 不能为空");
        stackedBar = Objects.requireNonNull(stackedBar, "stackedBar 不能为空");
    }

    public record RadarChartData(
            String chartType,
            List<String> dimensions,
            String targetBrand,
            List<Double> brand,
            Map<String, List<Double>> competitors
    ) {

        public RadarChartData {
            requireChartType(chartType, "radar");
            dimensions = immutableStringList(dimensions, "dimensions 不能为空");
            targetBrand = normalizeRequired(targetBrand, "targetBrand 不能为空");
            brand = immutableRateList(brand, "brand 不能为空");
            if (dimensions.size() != brand.size()) {
                throw new IllegalArgumentException("dimensions 与 brand 长度必须一致");
            }
            competitors = immutableSeriesMap(competitors, dimensions.size(), "competitors 不能为空");
        }
    }

    public record ScatterChartData(
            String chartType,
            String xMetric,
            String yMetric,
            List<ScatterPoint> points
    ) {

        public ScatterChartData {
            requireChartType(chartType, "scatter");
            xMetric = normalizeRequired(xMetric, "xMetric 不能为空");
            yMetric = normalizeRequired(yMetric, "yMetric 不能为空");
            points = immutableObjectList(points, "points 不能为空");
        }
    }

    public record ScatterPoint(
            String brandName,
            double brandDistance,
            double recommendRate,
            boolean targetBrand,
            double candidateShare,
            int mentionCount
    ) {

        public ScatterPoint {
            brandName = normalizeRequired(brandName, "brandName 不能为空");
            validateRate(brandDistance, "brandDistance");
            validateRate(recommendRate, "recommendRate");
            validateRate(candidateShare, "candidateShare");
            if (mentionCount < 0) {
                throw new IllegalArgumentException("mentionCount 不能为负数");
            }
        }
    }

    public record SankeyChartData(
            String chartType,
            List<SankeyNode> nodes,
            List<SankeyLink> links
    ) {

        public SankeyChartData {
            requireChartType(chartType, "sankey");
            nodes = immutableObjectList(nodes, "nodes 不能为空");
            links = immutableObjectList(links, "links 不能为空");
        }
    }

    public record SankeyNode(
            String id,
            String label,
            String stage
    ) {

        private static final Set<String> ALLOWED_STAGES = Set.of("intent", "candidate", "recommend");

        public SankeyNode {
            id = normalizeRequired(id, "id 不能为空");
            label = normalizeRequired(label, "label 不能为空");
            stage = normalizeRequired(stage, "stage 不能为空");
            if (!ALLOWED_STAGES.contains(stage)) {
                throw new IllegalArgumentException("不支持的 stage: " + stage);
            }
        }
    }

    public record SankeyLink(
            String source,
            String target,
            int value
    ) {

        public SankeyLink {
            source = normalizeRequired(source, "source 不能为空");
            target = normalizeRequired(target, "target 不能为空");
            if (value <= 0) {
                throw new IllegalArgumentException("value 必须大于 0");
            }
        }
    }

    public record StackedBarChartData(
            String chartType,
            List<String> categories,
            List<MetricSeries> series
    ) {

        public StackedBarChartData {
            requireChartType(chartType, "stacked_bar");
            categories = immutableStringList(categories, "categories 不能为空");
            series = immutableObjectList(series, "series 不能为空");
            for (MetricSeries item : series) {
                if (item.values().size() != categories.size()) {
                    throw new IllegalArgumentException("series values 长度必须与 categories 一致");
                }
            }
        }
    }

    public record MetricSeries(
            String bucketKey,
            String bucketLabel,
            List<Double> values,
            int effectiveSampleSize
    ) {

        public MetricSeries {
            bucketKey = normalizeRequired(bucketKey, "bucketKey 不能为空");
            bucketLabel = normalizeRequired(bucketLabel, "bucketLabel 不能为空");
            values = immutableRateList(values, "values 不能为空");
            if (effectiveSampleSize < 0) {
                throw new IllegalArgumentException("effectiveSampleSize 不能为负数");
            }
        }
    }

    private static void requireChartType(String value, String expected) {
        String normalized = normalizeRequired(value, "chartType 不能为空");
        if (!expected.equals(normalized)) {
            throw new IllegalArgumentException("chartType 必须为 " + expected);
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = Objects.requireNonNull(value, message).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static void validateRate(double value, String fieldName) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(fieldName + " 必须在 0.0 到 1.0 之间");
        }
    }

    private static List<String> immutableStringList(List<String> source, String message) {
        Objects.requireNonNull(source, message);
        List<String> sanitized = new ArrayList<>(source.size());
        for (String item : source) {
            sanitized.add(normalizeRequired(item, message));
        }
        return List.copyOf(sanitized);
    }

    private static List<Double> immutableRateList(List<Double> source, String message) {
        Objects.requireNonNull(source, message);
        List<Double> sanitized = new ArrayList<>(source.size());
        for (Double item : source) {
            Double normalized = Objects.requireNonNull(item, message);
            validateRate(normalized, message);
            sanitized.add(normalized);
        }
        return List.copyOf(sanitized);
    }

    private static <T> List<T> immutableObjectList(List<T> source, String message) {
        Objects.requireNonNull(source, message);
        List<T> sanitized = new ArrayList<>(source.size());
        for (T item : source) {
            sanitized.add(Objects.requireNonNull(item, message));
        }
        return List.copyOf(sanitized);
    }

    private static Map<String, List<Double>> immutableSeriesMap(Map<String, List<Double>> source, int expectedSize, String message) {
        Objects.requireNonNull(source, message);
        LinkedHashMap<String, List<Double>> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> entry : source.entrySet()) {
            String key = normalizeRequired(entry.getKey(), message);
            List<Double> values = immutableRateList(entry.getValue(), message);
            if (values.size() != expectedSize) {
                throw new IllegalArgumentException("competitors series 长度必须与 dimensions 一致");
            }
            sanitized.put(key, values);
        }
        return Collections.unmodifiableMap(sanitized);
    }
}

package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.BucketType;
import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.dto.engine.v42.RenderedProbe;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("v42ProbeQualityGateService")
public class ProbeQualityGateService {

    private static final Map<BucketType, Integer> MIN_FAMILY_COVERAGE = Map.of(
            BucketType.PARETO, 6,
            BucketType.ADVERSARIAL, 5
    );

    private static final List<String> DISALLOWED_PROMPT_FRAGMENTS = List.of(
            "买到合适的",
            "安全放心",
            "更有个性",
            "图个开心",
            "搞懂原理",
            "问问别人再决定",
            "按自己要求找",
            "急着用",
            "马上要喝",
            "想跟你聊清楚",
            "按我的要求",
            "先让我看",
            "少踩坑点",
            "别容易翻车",
            "值不值得先认",
            "顺着大众路子挑",
            "也想搞清区别",
            "也想看懂差别",
            "你们这",
            "差别先看哪点",
            "值不值，会不会太普通",
            "值不值会不会太普通",
            "省不省心",
            "值不值得优先选，才没那么普通",
            "放在现在到底行不行",
            "现在定前还要不要再确认哪点更稳",
            "现在直接上稳不稳",
            "现在就定先看哪点更稳"
    );

    public ProbeQualityGateResult evaluate(List<RenderedProbe> renderedProbes) {
        Objects.requireNonNull(renderedProbes, "renderedProbes 不能为空");
        if (renderedProbes.isEmpty()) {
            throw new IllegalArgumentException("renderedProbes 不能为空");
        }

        Map<BucketType, List<RenderedProbe>> byBucket = renderedProbes.stream()
                .collect(Collectors.groupingBy(RenderedProbe::bucketType, () -> new EnumMap<>(BucketType.class), Collectors.toList()));

        EnumMap<BucketType, BucketQualityStats> bucketStats = new EnumMap<>(BucketType.class);
        List<String> violations = new ArrayList<>();
        for (Map.Entry<BucketType, List<RenderedProbe>> entry : byBucket.entrySet()) {
            BucketType bucketType = entry.getKey();
            List<RenderedProbe> probes = List.copyOf(entry.getValue());
            BucketQualityStats stats = inspectBucket(bucketType, probes, violations);
            bucketStats.put(bucketType, stats);
        }

        return new ProbeQualityGateResult(violations.isEmpty(), List.copyOf(violations), Map.copyOf(bucketStats));
    }

    public List<SurfaceRepairTarget> identifyRepairTargets(List<RenderedProbe> renderedProbes) {
        Objects.requireNonNull(renderedProbes, "renderedProbes 不能为空");
        if (renderedProbes.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<String, SurfaceRepairTargetBuilder> byProbeId = new LinkedHashMap<>();
        renderedProbes.stream()
                .filter(Objects::nonNull)
                .filter(probe -> StringUtils.hasText(probe.probeId()))
                .forEach(probe -> byProbeId.putIfAbsent(
                        probe.probeId(),
                        new SurfaceRepairTargetBuilder(probe.probeId(), probe.bucketType(), probe.renderedPrompt())
                ));

        Map<BucketType, List<RenderedProbe>> byBucket = renderedProbes.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(RenderedProbe::bucketType, () -> new EnumMap<>(BucketType.class), Collectors.toList()));

        for (Map.Entry<BucketType, List<RenderedProbe>> entry : byBucket.entrySet()) {
            BucketType bucketType = entry.getKey();
            List<RenderedProbe> probes = List.copyOf(entry.getValue());
            markDuplicateTargets(probes, byProbeId, false);
            if (bucketType == BucketType.ORTHOGONAL) {
                markDuplicateTargetsByStructural(probes, byProbeId);
            }
            for (RenderedProbe probe : probes) {
                if (probe == null || !StringUtils.hasText(probe.probeId())) {
                    continue;
                }
                if (looksMechanical(probe.renderedPrompt())) {
                    byProbeId.get(probe.probeId()).reasons.add(SurfaceRepairReason.MECHANICAL);
                }
                if (containsDisallowedFragment(probe.renderedPrompt())) {
                    byProbeId.get(probe.probeId()).reasons.add(SurfaceRepairReason.BLACKLISTED);
                }
            }
        }

        return byProbeId.values().stream()
                .filter(builder -> !builder.reasons.isEmpty())
                .map(SurfaceRepairTargetBuilder::build)
                .toList();
    }

    public void assertPass(List<RenderedProbe> renderedProbes) {
        ProbeQualityGateResult result = evaluate(renderedProbes);
        if (!result.passed()) {
            throw new IllegalStateException("V4.2 probe quality gate failed: " + String.join(" | ", result.violations()));
        }
    }

    private BucketQualityStats inspectBucket(BucketType bucketType,
                                             List<RenderedProbe> probes,
                                             List<String> violations) {
        LinkedHashMap<String, List<RenderedProbe>> byCanonicalPrompt = new LinkedHashMap<>();
        LinkedHashSet<String> families = new LinkedHashSet<>();
        int mechanicalCount = 0;
        int blacklistedCount = 0;

        for (RenderedProbe probe : probes) {
            String canonicalPrompt = canonicalPrompt(probe.renderedPrompt());
            byCanonicalPrompt.computeIfAbsent(canonicalPrompt, ignored -> new ArrayList<>()).add(probe);

            String family = resolveFamily(probe.realizationPlan(), probe.renderFamily());
            if (StringUtils.hasText(family)) {
                families.add(family);
            }

            if (looksMechanical(probe.renderedPrompt())) {
                mechanicalCount++;
            }
            if (containsDisallowedFragment(probe.renderedPrompt())) {
                blacklistedCount++;
            }
        }

        List<String> duplicateSamples = byCanonicalPrompt.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getValue().getFirst().renderedPrompt())
                .toList();
        List<String> nearDuplicateSamples = bucketType == BucketType.ORTHOGONAL
                ? structuralNearDuplicates(probes)
                : List.of();

        if (!duplicateSamples.isEmpty()) {
            violations.add(bucketType.name() + " bucket 出现重复 probe: " + duplicateSamples.getFirst());
        }
        if (!nearDuplicateSamples.isEmpty()) {
            violations.add(bucketType.name() + " bucket 出现近重复 probe: " + nearDuplicateSamples.getFirst());
        }
        if (mechanicalCount > 0) {
            violations.add(bucketType.name() + " bucket 出现机械化问句 " + mechanicalCount + " 条");
        }
        if (blacklistedCount > 0) {
            violations.add(bucketType.name() + " bucket 命中伪人话黑名单 " + blacklistedCount + " 条");
        }

        Integer minimumFamilyCount = resolveMinimumFamilyCount(bucketType, probes.size());
        if (minimumFamilyCount != null && families.size() < minimumFamilyCount) {
            violations.add(bucketType.name() + " bucket family 展开不足: " + families.size());
        }

        return new BucketQualityStats(
                bucketType,
                probes.size(),
                byCanonicalPrompt.size(),
                families.size(),
                mechanicalCount,
                blacklistedCount
        );
    }

    private void markDuplicateTargets(List<RenderedProbe> probes,
                                      Map<String, SurfaceRepairTargetBuilder> byProbeId,
                                      boolean structural) {
        LinkedHashMap<String, List<RenderedProbe>> grouped = new LinkedHashMap<>();
        for (RenderedProbe probe : probes) {
            if (probe == null) {
                continue;
            }
            String fingerprint = structural
                    ? structuralPrompt(probe.renderedPrompt())
                    : canonicalPrompt(probe.renderedPrompt());
            grouped.computeIfAbsent(fingerprint, ignored -> new ArrayList<>()).add(probe);
        }
        SurfaceRepairReason reason = structural ? SurfaceRepairReason.NEAR_DUPLICATE : SurfaceRepairReason.DUPLICATE;
        for (List<RenderedProbe> group : grouped.values()) {
            if (group.size() <= 1) {
                continue;
            }
            for (int index = 1; index < group.size(); index++) {
                RenderedProbe probe = group.get(index);
                if (probe != null && StringUtils.hasText(probe.probeId()) && byProbeId.containsKey(probe.probeId())) {
                    byProbeId.get(probe.probeId()).reasons.add(reason);
                }
            }
        }
    }

    private void markDuplicateTargetsByStructural(List<RenderedProbe> probes,
                                                  Map<String, SurfaceRepairTargetBuilder> byProbeId) {
        markDuplicateTargets(probes, byProbeId, true);
    }

    private boolean containsDisallowedFragment(String renderedPrompt) {
        if (!StringUtils.hasText(renderedPrompt)) {
            return true;
        }
        return DISALLOWED_PROMPT_FRAGMENTS.stream().anyMatch(renderedPrompt::contains)
                || renderedPrompt.contains("“")
                || renderedPrompt.contains("”")
                || renderedPrompt.contains("\"")
                || renderedPrompt.matches(".*我选一个(直接买|就买|直接定)(.{0,1}了)?[？?]$")
                || renderedPrompt.matches(".*确认一下这个方向就准备(买|下单|定).*[？?]$")
                || renderedPrompt.matches(".*(差别先看哪点|值不值，会不会太普通|值不值会不会太普通|省不省心).*[？?]$")
                || renderedPrompt.matches(".*(确认一下|确认好|确认完|比较完|确认清楚|确认明白|问清楚|弄清楚|确认没问题|确认过).*(直接买|直接下单|直接定)(.{0,1}了)?[？?]$")
                || renderedPrompt.matches(".*(准备|想|确认一下|确认清楚|确认明白|问清楚|弄清楚|确认没问题|确认过).*(直接买|直接下单|直接定)(.{0,1}了).*[？?]$")
                || renderedPrompt.matches(".*我(直接买|直接下单|直接定)(.{0,1}了).*[？?]$")
                || renderedPrompt.matches(".*(就|好)(直接买|直接下单|直接定)(.{0,1}了)?[？?]$");
    }

    private boolean looksMechanical(String renderedPrompt) {
        if (!StringUtils.hasText(renderedPrompt)) {
            return true;
        }
        String trimmed = renderedPrompt.trim().replaceAll("[？?！!。]+$", "");
        String[] fragments = trimmed.split("[，,、/]");
        if (fragments.length >= 3) {
            int shortFragmentCount = 0;
            for (String fragment : fragments) {
                String normalized = fragment.trim();
                if (normalized.length() <= 8) {
                    shortFragmentCount++;
                }
            }
            if (shortFragmentCount == fragments.length) {
                return true;
            }
        }
        return trimmed.contains("，问问别人再决定，")
                || trimmed.contains("，按自己要求找")
                || trimmed.contains("，买到合适的，")
                || trimmed.contains("，安全放心")
                || trimmed.startsWith("急着用，")
                || trimmed.startsWith("马上要")
                || trimmed.startsWith("想跟你聊清楚，")
                || trimmed.startsWith("如果按我的要求，");
    }

    private String resolveFamily(RealizationPlan realizationPlan, String renderFamily) {
        if (realizationPlan != null && realizationPlan.realizationConstraints() != null
                && StringUtils.hasText(realizationPlan.realizationConstraints().fallbackFamily())) {
            return realizationPlan.realizationConstraints().fallbackFamily();
        }
        return renderFamily;
    }

    private String canonicalPrompt(String renderedPrompt) {
        return OrthogonalPromptFingerprint.canonical(renderedPrompt);
    }

    private List<String> structuralNearDuplicates(List<RenderedProbe> probes) {
        LinkedHashMap<String, List<RenderedProbe>> byStructuralPrompt = new LinkedHashMap<>();
        for (RenderedProbe probe : probes) {
            byStructuralPrompt.computeIfAbsent(structuralPrompt(probe.renderedPrompt()), ignored -> new ArrayList<>())
                    .add(probe);
        }
        return byStructuralPrompt.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getValue().getFirst().renderedPrompt())
                .toList();
    }

    private String structuralPrompt(String renderedPrompt) {
        return OrthogonalPromptFingerprint.structural(renderedPrompt);
    }

    private Integer resolveMinimumFamilyCount(BucketType bucketType, int probeCount) {
        Integer baseline = MIN_FAMILY_COVERAGE.get(bucketType);
        if (baseline == null || probeCount <= 0) {
            return baseline;
        }
        return Math.min(baseline, probeCount);
    }

    public record ProbeQualityGateResult(
            boolean passed,
            List<String> violations,
            Map<BucketType, BucketQualityStats> bucketStats
    ) {
    }

    public enum SurfaceRepairReason {
        DUPLICATE,
        NEAR_DUPLICATE,
        MECHANICAL,
        BLACKLISTED
    }

    public record SurfaceRepairTarget(
            String probeId,
            BucketType bucketType,
            String renderedPrompt,
            List<SurfaceRepairReason> reasons
    ) {
        public SurfaceRepairTarget {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }
    }

    public record BucketQualityStats(
            BucketType bucketType,
            int probeCount,
            int uniquePromptCount,
            int familyCount,
            int mechanicalPromptCount,
            int blacklistedPromptCount
    ) {
    }

    private static final class SurfaceRepairTargetBuilder {
        private final String probeId;
        private final BucketType bucketType;
        private final String renderedPrompt;
        private final LinkedHashSet<SurfaceRepairReason> reasons = new LinkedHashSet<>();

        private SurfaceRepairTargetBuilder(String probeId, BucketType bucketType, String renderedPrompt) {
            this.probeId = probeId;
            this.bucketType = bucketType;
            this.renderedPrompt = renderedPrompt;
        }

        private SurfaceRepairTarget build() {
            return new SurfaceRepairTarget(probeId, bucketType, renderedPrompt, List.copyOf(reasons));
        }
    }
}

package com.geoprobe.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.geoprobe.common.BusinessException;
import com.geoprobe.common.ErrorCode;
import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpaceConfigView;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import com.geoprobe.entity.v1.BrandSpacePrompt;
import com.geoprobe.entity.v1.BrandSpaceTopic;
import com.geoprobe.mapper.v1.BrandSpacePromptMapper;
import com.geoprobe.mapper.v1.BrandSpaceTopicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandSpaceConfigService {

    private static final int SOURCE_SYSTEM_GENERATED = 0;
    private static final int SOURCE_USER_EDITED = 1;
    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final int GENERATED_TOPIC_TARGET = 10;

    private final BrandSpaceTopicMapper brandSpaceTopicMapper;
    private final BrandSpacePromptMapper brandSpacePromptMapper;
    private final BrandSpaceRunConfigService brandSpaceRunConfigService;

    public BrandSpaceConfigView loadConfigView(String brandScopeId, String brandId, String brandName) {
        return loadConfigView(brandScopeId, brandId, brandName, null);
    }

    public BrandSpaceConfigView loadConfigView(String brandScopeId,
                                               String brandId,
                                               String brandName,
                                               Integer configGeneration) {
        if (!StringUtils.hasText(brandScopeId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceId 不能为空");
        }
        List<BrandSpaceTopic> topics = brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandScopeId.trim())
                .orderByAsc(BrandSpaceTopic::getSeqNo)
                .orderByAsc(BrandSpaceTopic::getCreatedAt));
        List<BrandSpacePrompt> prompts = brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandScopeId.trim())
                .orderByAsc(BrandSpacePrompt::getSeqNo)
                .orderByAsc(BrandSpacePrompt::getCreatedAt));

        Map<String, List<BrandSpacePrompt>> promptsByTopicId = new LinkedHashMap<>();
        for (BrandSpacePrompt prompt : prompts) {
            String topicId = normalizeText(prompt.getTopicId());
            if (StringUtils.hasText(topicId)) {
                promptsByTopicId.computeIfAbsent(topicId, ignored -> new ArrayList<>()).add(prompt);
            }
        }

        Map<String, BrandSpaceTopic> topicById = new LinkedHashMap<>();
        for (BrandSpaceTopic topic : topics) {
            topicById.put(topic.getId(), topic);
        }

        List<BrandSpaceConfigView.TopicView> topicViews = topics.stream()
                .map(topic -> {
                    List<BrandSpacePrompt> topicPrompts = promptsByTopicId.getOrDefault(topic.getId(), List.of());
                    int enabledPromptCount = (int) topicPrompts.stream()
                            .filter(prompt -> intEquals(prompt.getStatusCode(), STATUS_ENABLED))
                            .count();
                    return BrandSpaceConfigView.TopicView.builder()
                            .topicId(topic.getId())
                            .name(topic.getName())
                            .description(topic.getDescription())
                            .seqNo(topic.getSeqNo())
                            .enabled(intEquals(topic.getStatusCode(), STATUS_ENABLED))
                            .source(resolveSourceLabel(topic.getSourceCode()))
                            .promptCount(topicPrompts.size())
                            .enabledPromptCount(enabledPromptCount)
                            .createdAt(formatDateTime(topic.getCreatedAt()))
                            .updatedAt(formatDateTime(topic.getUpdatedAt()))
                            .build();
                })
                .toList();

        List<BrandSpaceConfigView.PromptView> promptViews = prompts.stream()
                .sorted(Comparator.comparing(BrandSpacePrompt::getSeqNo, Comparator.nullsLast(Integer::compareTo)))
                .map(prompt -> {
                    BrandSpaceTopic topic = topicById.get(prompt.getTopicId());
                    return BrandSpaceConfigView.PromptView.builder()
                            .promptId(prompt.getId())
                            .topicId(prompt.getTopicId())
                            .topicName(topic == null ? null : topic.getName())
                            .seqNo(prompt.getSeqNo())
                            .text(prompt.getPromptText())
                            .enabled(intEquals(prompt.getStatusCode(), STATUS_ENABLED))
                            .source(resolveSourceLabel(prompt.getSourceCode()))
                            .regionCode(prompt.getRegionCode())
                            .locale(prompt.getLocale())
                            .language(prompt.getLanguage())
                            .createdAt(formatDateTime(prompt.getCreatedAt()))
                            .updatedAt(formatDateTime(prompt.getUpdatedAt()))
                            .build();
                })
                .toList();

        int enabledTopicCount = (int) topics.stream()
                .filter(topic -> intEquals(topic.getStatusCode(), STATUS_ENABLED))
                .count();
        int enabledPromptCount = (int) prompts.stream()
                .filter(prompt -> intEquals(prompt.getStatusCode(), STATUS_ENABLED))
                .count();
        BrandSpaceRunConfigService.CurrentRunConfig currentRunConfig =
                brandSpaceRunConfigService.loadCurrentRunConfig(brandScopeId.trim(), enabledPromptCount);
        boolean canRegenerateSystemConfig = canRegenerateSystemConfig(topics, prompts);
        boolean regenerationRecommended = canRegenerateSystemConfig
                && isLegacyDirectBrandPromptMix(promptViews, brandName);

        return BrandSpaceConfigView.builder()
                .brandSpaceId(brandScopeId.trim())
                .brandId(brandId)
                .brandName(brandName)
                .configGeneration(configGeneration)
                .topics(topicViews)
                .prompts(promptViews)
                .runConfig(BrandSpaceConfigView.RunConfigView.builder()
                        .targetProbeCount(currentRunConfig.targetProbeCount())
                        .targetModelProduct(currentRunConfig.targetModelProduct())
                        .autoRunEnabled(currentRunConfig.autoRunEnabled())
                        .build())
                .summary(BrandSpaceConfigView.SummaryView.builder()
                        .topicCount(topicViews.size())
                        .enabledTopicCount(enabledTopicCount)
                        .promptCount(promptViews.size())
                        .enabledPromptCount(enabledPromptCount)
                        .build())
                .capabilities(BrandSpaceConfigView.ConfigCapabilitiesView.builder()
                        .canRead(true)
                        .canCreateTopic(false)
                        .canUpdateTopic(false)
                        .canReorderTopic(false)
                        .canCreatePrompt(false)
                        .canUpdatePrompt(false)
                        .canReorderPrompt(false)
                        .canRegenerateSystemConfig(canRegenerateSystemConfig)
                        .regenerationRecommended(regenerationRecommended)
                        .tagTruthObjectSupported(false)
                        .build())
                .build();
    }

    public Map<String, ConfigSummarySnapshot> summarizeConfigs(List<String> brandScopeIds) {
        if (brandScopeIds == null || brandScopeIds.isEmpty()) {
            return Map.of();
        }

        List<String> normalizedScopeIds = brandScopeIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (normalizedScopeIds.isEmpty()) {
            return Map.of();
        }

        Map<String, MutableSummaryCounter> counters = new LinkedHashMap<>();
        for (BrandSpaceTopic topic : brandSpaceTopicMapper.selectList(new LambdaQueryWrapper<BrandSpaceTopic>()
                .in(BrandSpaceTopic::getBrandScopeId, normalizedScopeIds))) {
            if (topic == null || !StringUtils.hasText(topic.getBrandScopeId())) {
                continue;
            }
            MutableSummaryCounter counter = counters.computeIfAbsent(topic.getBrandScopeId().trim(), ignored -> new MutableSummaryCounter());
            counter.topicCount++;
            if (intEquals(topic.getStatusCode(), STATUS_ENABLED)) {
                counter.enabledTopicCount++;
            }
        }

        for (BrandSpacePrompt prompt : brandSpacePromptMapper.selectList(new LambdaQueryWrapper<BrandSpacePrompt>()
                .in(BrandSpacePrompt::getBrandScopeId, normalizedScopeIds))) {
            if (prompt == null || !StringUtils.hasText(prompt.getBrandScopeId())) {
                continue;
            }
            MutableSummaryCounter counter = counters.computeIfAbsent(prompt.getBrandScopeId().trim(), ignored -> new MutableSummaryCounter());
            counter.promptCount++;
            if (intEquals(prompt.getStatusCode(), STATUS_ENABLED)) {
                counter.enabledPromptCount++;
            }
        }

        Map<String, ConfigSummarySnapshot> result = new LinkedHashMap<>();
        counters.forEach((scopeId, counter) -> result.put(scopeId, new ConfigSummarySnapshot(
                counter.topicCount,
                counter.enabledTopicCount,
                counter.promptCount,
                counter.enabledPromptCount
        )));
        return result;
    }

    public void purgeRecoverableConfig(String brandScopeId) {
        if (!StringUtils.hasText(brandScopeId)) {
            return;
        }
        String normalizedScopeId = brandScopeId.trim();
        brandSpacePromptMapper.delete(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, normalizedScopeId));
        brandSpaceTopicMapper.delete(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, normalizedScopeId));
    }

    public List<BrandSpaceTopicPayload> generateTopics(BrandSpaceDraftPayload draft) {
        BrandSpaceDraftPayload normalized = normalizeDraft(draft);
        List<GeneratedTopicBlueprint> blueprints = buildGeneratedTopicBlueprints(normalized);
        List<BrandSpaceTopicPayload> topics = new ArrayList<>();
        for (int index = 0; index < blueprints.size(); index++) {
            GeneratedTopicBlueprint blueprint = blueprints.get(index);
            topics.add(BrandSpaceTopicPayload.builder()
                    .tempId("topic_tmp_" + (index + 1))
                    .name(blueprint.name())
                    .description(blueprint.description())
                    .enabled(true)
                    .source("system_generated")
                    .build());
        }
        return topics;
    }

    public List<BrandSpacePromptPayload> generatePrompts(BrandSpaceDraftPayload draft,
                                                         List<BrandSpaceTopicPayload> topics) {
        BrandSpaceDraftPayload normalized = normalizeDraft(draft);
        List<BrandSpaceTopicPayload> safeTopics = topics == null ? List.of() : topics;
        List<GeneratedTopicBlueprint> blueprints = buildGeneratedTopicBlueprints(normalized);
        List<BrandSpacePromptPayload> prompts = new ArrayList<>();
        for (int index = 0; index < safeTopics.size(); index++) {
            BrandSpaceTopicPayload topic = safeTopics.get(index);
            GeneratedTopicBlueprint blueprint = index < blueprints.size() ? blueprints.get(index) : null;
            prompts.add(BrandSpacePromptPayload.builder()
                    .tempId("prompt_tmp_" + (index + 1))
                    .topicTempId(defaultIfBlank(topic.getTempId(), "topic_tmp_" + (index + 1)))
                    .text(resolvePromptText(normalized, topic, blueprint))
                    .enabled(Boolean.TRUE.equals(topic.getEnabled()))
                    .source("system_generated")
                    .build());
        }
        return prompts;
    }

    public List<BrandSpaceTopicPayload> resolvePreviewTopics(BrandSpaceDraftPayload draft,
                                                             List<BrandSpaceTopicPayload> requestedTopics) {
        if (requestedTopics == null || requestedTopics.isEmpty()) {
            return generateTopics(draft);
        }
        return normalizeTopics(requestedTopics);
    }

    private List<GeneratedTopicBlueprint> buildGeneratedTopicBlueprints(BrandSpaceDraftPayload draft) {
        String brandName = draft.getBrandName();
        String category = draft.getCategory();
        List<String> competitors = draft.getCompetitors();
        String firstCompetitor = competitors.isEmpty() ? "同类头部品牌" : competitors.get(0);
        String competitorSummary = summarizeCompetitors(competitors);
        String officialDomain = extractDomain(draft.getOfficialWebsite());

        List<GeneratedTopicBlueprint> blueprints = new ArrayList<>();
        blueprints.add(new GeneratedTopicBlueprint(
                category + "选购场景",
                "围绕" + brandName + "在" + category + "基础决策中的真实问法。",
                category + "怎么选，哪些品牌更值得先加入候选？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "竞品对比",
                "聚焦" + brandName + "与" + competitorSummary + "的差异化比较。",
                brandName + "和" + firstCompetitor + "相比，谁更适合大多数" + category + "用户？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "官网与信任线索",
                buildTrustDescription(brandName, officialDomain),
                "判断一个" + category + "品牌是否靠谱，应该优先看官网、资质还是第三方口碑？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "真实体验口碑",
                "聚焦消费者真实体验、常见优缺点和被反复提到的风险点。",
                "用户讨论" + category + "品牌时，最常提到的优缺点和风险点是什么？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "价格与性价比",
                "关注" + brandName + "在同品类价格带中的位置，以及被推荐时的性价比理由。",
                "同价位" + category + "里，哪些品牌更容易被认为性价比高？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "人群与使用场景",
                "看模型是否能把" + brandName + "放进合适的人群、场景和需求语境里。",
                "什么人、什么使用场景更需要优先看" + category + "品牌的适配度？"
        ));
        blueprints.addAll(resolveCategoryAngles(draft, firstCompetitor));
        blueprints.add(new GeneratedTopicBlueprint(
                "推荐与替代品牌",
                "观察模型在不点名时，会不会主动把" + brandName + "放进推荐名单。",
                "如果不指定品牌，AI 会推荐" + brandName + "还是其他" + category + "品牌？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                "购买决策阻力",
                "识别用户在下单前最可能追问的顾虑、误解和决策阻力。",
                "用户在考虑" + category + "时，最常见的顾虑和劝退点是什么？"
        ));
        blueprints.add(new GeneratedTopicBlueprint(
                competitors.size() > 1 ? "多品牌排序" : "渠道与信息来源",
                competitors.size() > 1
                        ? "聚焦多品牌同场比较时，" + brandName + "能否稳定进入前列。"
                        : "关注模型会引用哪些官网、媒体和社区来源来认识" + brandName + "。",
                competitors.size() > 1
                        ? buildMultiBrandRankingPrompt(brandName, competitors)
                        : "关于" + brandName + "，AI 主要参考哪些来源，它们的可信度怎么样？"
        ));
        return deduplicateBlueprints(blueprints);
    }

    private List<GeneratedTopicBlueprint> resolveCategoryAngles(BrandSpaceDraftPayload draft, String firstCompetitor) {
        String category = draft.getCategory();
        String brandName = draft.getBrandName();
        String lowered = category.toLowerCase(Locale.ROOT);

        if (containsAny(lowered, "辣条", "零食", "食品", "饮料", "餐饮", "咖啡", "奶茶", "烘焙")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "口味与配料",
                            "观察模型是否会把" + brandName + "的口味、原料和成分表达清楚。",
                            "买" + category + "时，口味、配料和成分信息会怎样影响选择？"
                    ),
                    new GeneratedTopicBlueprint(
                            "囤货与送礼场景",
                            "围绕复购、囤货、送礼和分享场景，看品牌是否会被优先推荐。",
                            "囤货、送礼或分享场景下，哪些" + category + "品牌更容易被优先推荐？"
                    )
            );
        }

        if (containsAny(lowered, "厨房", "收纳", "置物", "厨具", "锅具", "餐具", "保鲜")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "容量与空间适配",
                            "聚焦台面、橱柜、租房厨房等真实收纳场景，看" + brandName + "是否能说清楚容量和空间匹配。",
                            "小户型、出租屋或台面空间有限时，" + category + "应该优先看哪些容量和空间适配点？"
                    ),
                    new GeneratedTopicBlueprint(
                            "材质与耐用度",
                            "关注材质安全、承重、耐脏和长期使用后的稳定性。",
                            "挑" + category + "时，材质安全、承重和耐用度应该怎么比较，哪些品牌更稳妥？"
                    )
            );
        }

        if (containsAny(lowered, "花洒", "卫浴", "五金", "家居", "厨卫", "家电", "家具", "净水", "门锁")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "安装与耐用度",
                            "聚焦安装门槛、耐用性和长期使用成本这些高频决策因素。",
                            "安装门槛、做工和长期耐用度，通常会怎样影响" + category + "的购买决策？"
                    ),
                    new GeneratedTopicBlueprint(
                            "售后与维护",
                            "看模型是否会主动提到售后、配件、维护和长期使用体验。",
                            "买" + category + "时，售后、维护和配件支持该怎么判断？"
                    )
            );
        }

        if (containsAny(lowered, "护肤", "彩妆", "洗发", "护发", "面膜", "精华", "美妆", "个护")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "成分与适配人群",
                            "聚焦成分表达、肤质/发质适配和敏感人群顾虑。",
                            "选" + category + "时，成分表达和适配人群信息应该怎么看？"
                    ),
                    new GeneratedTopicBlueprint(
                            "功效与体感",
                            "围绕功效兑现、使用体感和长期复购理由来判断品牌表达力。",
                            "判断" + category + "是否值得长期回购，应该重点看哪些功效和体感反馈？"
                    )
            );
        }

        if (containsAny(lowered, "汽车", "汽车品牌", "新能源", "suv", "轿车", "mpv", "电车")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "配置与智能化",
                            "观察模型是否会主动提到决定购买的关键配置、智能座舱和辅助驾驶能力。",
                            "买" + category + "时，核心配置、智能化和空间通常怎么比较？"
                    ),
                    new GeneratedTopicBlueprint(
                            "续航与保值",
                            "聚焦续航、补能、空间和长期保值这些高关注决策点。",
                            "续航、补能和长期保值，通常会怎样影响" + category + "的购买决策？"
                    )
            );
        }

        if (containsAny(lowered, "saas", "软件", "系统", "平台", "crm", "erp", "协作", "数据", "ai")) {
            return List.of(
                    new GeneratedTopicBlueprint(
                            "上手与集成",
                            "关注部署周期、上手难度、迁移成本和现有系统集成复杂度。",
                            "选" + category + "时，上手门槛、迁移成本和系统集成一般怎么评估？"
                    ),
                    new GeneratedTopicBlueprint(
                            "价格与 ROI",
                            "看模型是否会把价格、团队协作收益和投入产出比说清楚。",
                            "团队采购" + category + "时，价格和 ROI 通常怎么比较？"
                    )
            );
        }

        return List.of(
                new GeneratedTopicBlueprint(
                        category + "核心卖点",
                        "拆解" + brandName + "最值得被提到的卖点，以及它和" + firstCompetitor + "的差异。",
                        "在" + category + "里，哪些卖点最容易真正影响选择，而不只是宣传口号？"
                ),
                new GeneratedTopicBlueprint(
                        "决策顾虑",
                        "识别这个品类里最容易让用户犹豫的风险点，看品牌是否能被有效解释。",
                        "用户在买" + category + "时最怕踩哪些坑，" + brandName + "通常会不会被拿来对比？"
                )
        );
    }

    private List<GeneratedTopicBlueprint> deduplicateBlueprints(List<GeneratedTopicBlueprint> input) {
        LinkedHashMap<String, GeneratedTopicBlueprint> deduplicated = new LinkedHashMap<>();
        for (GeneratedTopicBlueprint blueprint : input) {
            if (blueprint == null || !StringUtils.hasText(blueprint.name()) || !StringUtils.hasText(blueprint.promptText())) {
                continue;
            }
            deduplicated.putIfAbsent(blueprint.name().trim(), blueprint);
        }
        return deduplicated.values().stream()
                .limit(GENERATED_TOPIC_TARGET)
                .toList();
    }

    private String resolvePromptText(BrandSpaceDraftPayload draft,
                                     BrandSpaceTopicPayload topic,
                                     GeneratedTopicBlueprint blueprint) {
        String topicName = StringUtils.hasText(topic.getName()) ? topic.getName().trim() : draft.getCategory();
        if (blueprint != null
                && StringUtils.hasText(blueprint.promptText())
                && topicName.equals(normalizeText(blueprint.name()))) {
            return blueprint.promptText();
        }
        return topicName + "里，" + draft.getBrandName() + "表现怎么样？";
    }

    private String summarizeCompetitors(List<String> competitors) {
        if (competitors == null || competitors.isEmpty()) {
            return "同类头部品牌";
        }
        return competitors.stream().limit(3).collect(java.util.stream.Collectors.joining("、"));
    }

    private String buildTrustDescription(String brandName, String officialDomain) {
        if (StringUtils.hasText(officialDomain)) {
            return "关注官网 " + officialDomain + "、可信来源和品牌自身表达是否被模型吸收。";
        }
        return "关注官方站点、可信来源和品牌自身表达是否被模型吸收。";
    }

    private String buildMultiBrandRankingPrompt(String brandName, List<String> competitors) {
        List<String> names = new ArrayList<>();
        names.add(brandName);
        names.addAll(competitors.stream().limit(2).toList());
        return String.join("、", names) + "放在一起比较，谁更值得优先推荐？";
    }

    private String extractDomain(String website) {
        if (!StringUtils.hasText(website)) {
            return null;
        }
        try {
            String host = URI.create(website.trim()).getHost();
            if (!StringUtils.hasText(host)) {
                return null;
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void replaceConfig(String organizationId,
                              String brandId,
                              String brandScopeId,
                              BrandSpaceDraftPayload draft,
                              List<BrandSpaceTopicPayload> topics,
                              List<BrandSpacePromptPayload> prompts) {
        if (!StringUtils.hasText(organizationId) || !StringUtils.hasText(brandId) || !StringUtils.hasText(brandScopeId)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "品牌空间配置缺少持久化主键");
        }
        BrandSpaceDraftPayload normalizedDraft = normalizeDraft(draft);
        List<BrandSpaceTopicPayload> normalizedTopics = normalizeTopics(topics);
        List<BrandSpacePromptPayload> normalizedPrompts = normalizePrompts(prompts);
        validatePromptTopicReferences(normalizedTopics, normalizedPrompts);

        brandSpacePromptMapper.delete(new LambdaQueryWrapper<BrandSpacePrompt>()
                .eq(BrandSpacePrompt::getBrandScopeId, brandScopeId));
        brandSpaceTopicMapper.delete(new LambdaQueryWrapper<BrandSpaceTopic>()
                .eq(BrandSpaceTopic::getBrandScopeId, brandScopeId));

        Map<String, String> topicIdByTempId = new LinkedHashMap<>();
        for (int index = 0; index < normalizedTopics.size(); index++) {
            BrandSpaceTopicPayload topic = normalizedTopics.get(index);
            String topicId = UUID.randomUUID().toString();
            topicIdByTempId.put(topic.getTempId(), topicId);
            brandSpaceTopicMapper.insert(BrandSpaceTopic.builder()
                    .id(topicId)
                    .organizationId(organizationId)
                    .brandId(brandId)
                    .brandScopeId(brandScopeId)
                    .seqNo(index + 1)
                    .name(topic.getName().trim())
                    .description(StringUtils.hasText(topic.getDescription()) ? topic.getDescription().trim() : null)
                    .sourceCode(resolveSourceCode(topic.getSource()))
                    .statusCode(Boolean.TRUE.equals(topic.getEnabled()) ? STATUS_ENABLED : STATUS_DISABLED)
                    .build());
        }

        for (int index = 0; index < normalizedPrompts.size(); index++) {
            BrandSpacePromptPayload prompt = normalizedPrompts.get(index);
            brandSpacePromptMapper.insert(BrandSpacePrompt.builder()
                    .id(UUID.randomUUID().toString())
                    .organizationId(organizationId)
                    .brandId(brandId)
                    .brandScopeId(brandScopeId)
                    .topicId(resolvePromptTopicId(topicIdByTempId, prompt.getTopicTempId()))
                    .seqNo(index + 1)
                    .promptText(prompt.getText().trim())
                    .regionCode(defaultIfBlank(normalizedDraft.getRegionCode(), "CN"))
                    .locale(defaultIfBlank(normalizedDraft.getLocale(), "zh-CN"))
                    .language(defaultIfBlank(normalizedDraft.getLanguage(), "zh-CN"))
                    .sourceCode(resolveSourceCode(prompt.getSource()))
                    .statusCode(Boolean.TRUE.equals(prompt.getEnabled()) ? STATUS_ENABLED : STATUS_DISABLED)
                    .build());
        }
    }

    public List<String> resolveEnabledPromptTexts(List<BrandSpacePromptPayload> prompts) {
        return normalizePrompts(prompts).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(BrandSpacePromptPayload::getText)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    public boolean canRegenerateSystemConfig(List<BrandSpaceTopic> topics,
                                             List<BrandSpacePrompt> prompts) {
        if (topics == null || topics.isEmpty() || prompts == null || prompts.isEmpty()) {
            return false;
        }
        return topics.stream().allMatch(topic -> intEquals(topic.getSourceCode(), SOURCE_SYSTEM_GENERATED))
                && prompts.stream().allMatch(prompt -> intEquals(prompt.getSourceCode(), SOURCE_SYSTEM_GENERATED));
    }

    public boolean isLegacyDirectBrandPromptMix(List<BrandSpaceConfigView.PromptView> prompts,
                                                String brandName) {
        if (!StringUtils.hasText(brandName) || prompts == null || prompts.isEmpty()) {
            return false;
        }
        List<BrandSpaceConfigView.PromptView> enabledPrompts = prompts.stream()
                .filter(Objects::nonNull)
                .filter(prompt -> Boolean.TRUE.equals(prompt.getEnabled()))
                .toList();
        List<BrandSpaceConfigView.PromptView> target = enabledPrompts.isEmpty() ? prompts : enabledPrompts;
        if (target.size() < 3) {
            return false;
        }
        String normalizedBrand = brandName.trim().toLowerCase(Locale.ROOT);
        long brandMentionCount = target.stream()
                .map(BrandSpaceConfigView.PromptView::getText)
                .filter(StringUtils::hasText)
                .map(text -> text.trim().toLowerCase(Locale.ROOT))
                .filter(text -> text.contains(normalizedBrand))
                .count();
        long nonBrandCount = target.size() - brandMentionCount;
        long directMentionThreshold = Math.max(6L, Math.round(Math.ceil(target.size() * 0.7d)));
        return brandMentionCount >= directMentionThreshold && nonBrandCount <= 2;
    }

    public List<String> resolveEnabledTopicNames(List<BrandSpaceTopicPayload> topics) {
        return normalizeTopics(topics).stream()
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .map(BrandSpaceTopicPayload::getName)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private void validatePromptTopicReferences(List<BrandSpaceTopicPayload> topics,
                                               List<BrandSpacePromptPayload> prompts) {
        LinkedHashSet<String> topicTempIds = topics.stream()
                .map(BrandSpaceTopicPayload::getTempId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        List<String> invalidTopicTempIds = prompts.stream()
                .map(BrandSpacePromptPayload::getTopicTempId)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(tempId -> !topicTempIds.contains(tempId))
                .distinct()
                .toList();
        if (!invalidTopicTempIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "prompts 存在未声明的 topicTempId: " + String.join(", ", invalidTopicTempIds)
            );
        }
    }

    private String resolvePromptTopicId(Map<String, String> topicIdByTempId, String topicTempId) {
        if (!StringUtils.hasText(topicTempId)) {
            return null;
        }
        return topicIdByTempId.get(topicTempId.trim());
    }

    private int resolveSourceCode(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_SYSTEM_GENERATED;
        }
        String normalized = source.trim().toLowerCase(Locale.ROOT);
        if ("user_generated".equals(normalized) || "user_edited".equals(normalized) || "manual".equals(normalized)) {
            return SOURCE_USER_EDITED;
        }
        return SOURCE_SYSTEM_GENERATED;
    }

    private String resolveSourceLabel(Integer sourceCode) {
        if (intEquals(sourceCode, SOURCE_USER_EDITED)) {
            return "user_edited";
        }
        return "system_generated";
    }

    private boolean intEquals(Integer value, int expected) {
        return value != null && value == expected;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? null : java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    private List<BrandSpaceTopicPayload> normalizeTopics(List<BrandSpaceTopicPayload> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "topics 不能为空");
        }
        List<BrandSpaceTopicPayload> normalized = new ArrayList<>();
        LinkedHashSet<String> tempIds = new LinkedHashSet<>();
        for (BrandSpaceTopicPayload topic : topics) {
            if (topic == null || !StringUtils.hasText(topic.getName())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "topics 存在空 name");
            }
            String tempId = defaultIfBlank(topic.getTempId(), "topic_tmp_" + (normalized.size() + 1));
            if (!tempIds.add(tempId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "topics.tempId 不能重复");
            }
            normalized.add(BrandSpaceTopicPayload.builder()
                    .tempId(tempId)
                    .name(topic.getName().trim())
                    .description(StringUtils.hasText(topic.getDescription()) ? topic.getDescription().trim() : null)
                    .enabled(defaultBoolean(topic.getEnabled(), true))
                    .source(topic.getSource())
                    .build());
        }
        return List.copyOf(normalized);
    }

    private List<BrandSpacePromptPayload> normalizePrompts(List<BrandSpacePromptPayload> prompts) {
        if (prompts == null || prompts.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "prompts 不能为空");
        }
        List<BrandSpacePromptPayload> normalized = new ArrayList<>();
        LinkedHashSet<String> tempIds = new LinkedHashSet<>();
        for (BrandSpacePromptPayload prompt : prompts) {
            if (prompt == null || !StringUtils.hasText(prompt.getText())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "prompts 存在空 text");
            }
            String tempId = defaultIfBlank(prompt.getTempId(), "prompt_tmp_" + (normalized.size() + 1));
            if (!tempIds.add(tempId)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "prompts.tempId 不能重复");
            }
            normalized.add(BrandSpacePromptPayload.builder()
                    .tempId(tempId)
                    .topicTempId(defaultIfBlank(prompt.getTopicTempId(), null))
                    .text(prompt.getText().trim())
                    .enabled(defaultBoolean(prompt.getEnabled(), true))
                    .source(prompt.getSource())
                    .build());
        }
        return List.copyOf(normalized);
    }

    private BrandSpaceDraftPayload normalizeDraft(BrandSpaceDraftPayload draft) {
        if (draft == null || !StringUtils.hasText(draft.getBrandName()) || !StringUtils.hasText(draft.getCategory())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "brandSpaceDraft 不完整");
        }
        List<String> competitors = draft.getCompetitors() == null
                ? List.of()
                : draft.getCompetitors().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .distinct()
                .toList();
        return BrandSpaceDraftPayload.builder()
                .brandName(draft.getBrandName().trim())
                .category(draft.getCategory().trim())
                .officialWebsite(defaultIfBlank(draft.getOfficialWebsite(), null))
                .regionCode(defaultIfBlank(draft.getRegionCode(), "CN"))
                .locale(defaultIfBlank(draft.getLocale(), "zh-CN"))
                .language(defaultIfBlank(draft.getLanguage(), "zh-CN"))
                .competitors(competitors)
                .supplementalBrief(defaultIfBlank(draft.getSupplementalBrief(), null))
                .build();
    }

    private Boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value.trim();
    }

    public record ConfigSummarySnapshot(int topicCount,
                                        int enabledTopicCount,
                                        int promptCount,
                                        int enabledPromptCount) {
    }

    private static final class MutableSummaryCounter {
        private int topicCount;
        private int enabledTopicCount;
        private int promptCount;
        private int enabledPromptCount;
    }

    private record GeneratedTopicBlueprint(String name, String description, String promptText) {
    }
}

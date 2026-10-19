package com.geoprobe.service.engine.v42;

import com.geoprobe.dto.engine.v42.RealizationPlan;
import com.geoprobe.service.engine.v42.ThreeLayerMappingEvaluator.MappingResult;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

class Node2BehaviorRuleComposer {

    String simulationProfile(RealizationPlan realizationPlan) {
        if (realizationPlan == null) {
            return joinLines(List.of("像真人在手机上随手问一句，别像提示词。"));
        }
        return joinLines(List.of(
                "表层意图：" + Node2PromptHintHumanizer.humanizeSurfaceIntent(realizationPlan.simulationIntentProfile().surfaceIntent()),
                "交流姿态：" + Node2PromptHintHumanizer.humanizeInteractionPosture(realizationPlan.simulationIntentProfile().interactionPosture()),
                "互动模式：" + Node2PromptHintHumanizer.humanizeConversationMode(realizationPlan.simulationIntentProfile().conversationMode()),
                "品牌处理：" + Node2PromptHintHumanizer.humanizeBrandFraming(realizationPlan.simulationIntentProfile().brandFraming()),
                "语言松紧：" + Node2PromptHintHumanizer.humanizeDegradation(realizationPlan.simulationIntentProfile().languageDegradationLevel())
        ));
    }

    String joinSemanticConstraints(RealizationPlan realizationPlan) {
        if (realizationPlan == null) {
            return joinLines(List.of("问题要表达出真实需求，不要空泛。"));
        }
        return joinLines(realizationPlan.realizationConstraints().mustExpressSemantics().stream()
                .map(Node2PromptHintHumanizer::humanizeMustSemantic)
                .toList());
    }

    List<String> softHints(RealizationPlan realizationPlan) {
        if (realizationPlan == null) {
            return List.of("允许自然发挥，但不要模板腔。");
        }
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .map(Node2PromptHintHumanizer::humanizeSoftHint)
                .toList();
    }

    String joinBehaviorRules(MappingResult mappingResult,
                             RealizationPlan realizationPlan,
                             List<String> failedChecks) {
        List<String> promptInstructions = mappingResult.promptInstructions();
        List<String> retryRepairRules = retryRepairRules(failedChecks, realizationPlan);
        if (promptInstructions.isEmpty()) {
            List<String> defaultRules = new ArrayList<>(List.of(
                    "- 保持自然口语，不要把内部状态词直接拼进句子里",
                    "- 用句子的直接程度、铺垫多少、是否偏向某个选项来体现状态，不要自我说明"
            ));
            defaultRules.addAll(intentSpecificBehaviorRules(realizationPlan));
            defaultRules.addAll(contextSensitiveBehaviorRules(realizationPlan));
            defaultRules.addAll(retryRepairRules);
            return String.join("\n", defaultRules);
        }
        List<String> lines = new ArrayList<>(promptInstructions.stream()
                .map(instruction -> "- " + instruction)
                .toList());
        lines.add("- 不要把时间压力、负荷状态、确认阶段直接说出来");
        lines.add("- 用句子的直接程度、铺垫多少、是否偏向某个选项来体现状态");
        if (realizationPlan != null && "high".equals(realizationPlan.simulationIntentProfile().languageDegradationLevel())) {
            lines.add("- 可以更短、更直接，但不要写成搜索关键词或状态说明");
        }
        lines.addAll(intentSpecificBehaviorRules(realizationPlan));
        lines.addAll(contextSensitiveBehaviorRules(realizationPlan));
        lines.addAll(retryRepairRules);
        return String.join("\n", lines);
    }

    private List<String> retryRepairRules(List<String> failedChecks,
                                          RealizationPlan realizationPlan) {
        if (failedChecks == null || failedChecks.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        if (failedChecks.contains("semantic")) {
            String semanticReminder = joinSemanticConstraints(realizationPlan)
                    .replace("- ", "")
                    .replace("\n", "；");
            lines.add("- 上一版没有把必须语义表达够，这次要更清楚地带出：" + semanticReminder);
            if (realizationPlan != null && realizationPlan.simulationIntentProfile() != null) {
                switch (realizationPlan.simulationIntentProfile().surfaceIntent()) {
                    case "browse_for_options" ->
                            lines.add("- 如果是浏览型开口，要像先摸一圈方向，不要直接收成要结论或最终比较");
                    case "confirm_and_buy" ->
                            lines.add("- 如果是临近购买确认，要自然带出买前确认信号，不要退成普通比较题");
                    case "ask_for_actionable_steps" ->
                            lines.add("- 如果是动作导向，要问清先看哪几块或按什么思路挑，不要只问哪个好");
                    case "seek_clear_answer" -> {
                        lines.add("- 如果是想尽快得到判断，要把“想尽快定一个方向”说出来，但不要写成硬模板");
                        if (hasRequiredSemantic(realizationPlan, "D2:informational_directed")) {
                            lines.add("- 这轮必须写成直接要判断/要推荐的问法，不要再退成“怎么选 / 先从哪几步看 / 按什么思路挑”这类过程型问题");
                            lines.add("- 优先落成“哪个更靠谱 / 哪个更稳 / 我该先看哪个”这类一句话判断题");
                            lines.add("- 不要写成“先看哪个更...”这类把判断题前面硬挂“先看哪个”的句子");
                            if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                                lines.add("- 如果既想快点有判断，又想顺手把差别看懂，优先写成“哪个更稳，差别主要在哪 / 哪个更靠谱，主要差在哪”");
                                lines.add("- 不要写成“先看哪个会更稳 / 值得先看再看区别”这种半浏览半判断的拼接句");
                            }
                        }
                        if (hasRequiredSemantic(realizationPlan, "D3:identity_freedom")) {
                            lines.add("- 如果还要带“不太大众 / 不那么普通”，把它嵌进判断题里，不要写成“怎么选才能……还不会太普通”");
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        if (failedChecks.contains("anchor")) {
            String sourceCategoryAnchor = sourceCategoryAnchorPhrase(realizationPlan);
            if (StringUtils.hasText(sourceCategoryAnchor)) {
                lines.add("- 原题已经明确点出“" + sourceCategoryAnchor + "”，这次不要把这个品类锚点藏掉，句子里至少自然出现一次");
            }
            lines.add("- 上一版缺少明确品类锚点，这次句子里至少直接出现一个真实品类词，不要只说“这个 / 哪个 / 哪款 / 这几个”");
            lines.add("- 就算保持短句，也要让用户一眼知道你问的是哪一类东西，别把品类藏掉");
        }
        if (failedChecks.contains("brand_visible_missing")) {
            lines.add("- 上一版把品牌藏掉了；这轮既然允许提品牌，品牌名必须自然出现一次，不能再只剩品类泛问");
            lines.add("- 优先把品牌落进主问题里，比如“买某品牌前 / 某品牌这个品类靠不靠谱 / 某品牌和谁比更稳”，不要像广告口播");
        }
        if (failedChecks.contains("mechanical")) {
            lines.add("- 上一版太像模板句，不要写成“先看哪 / 大家一般会选哪个 / 更省心 / 直接定”这类硬拼开口");
            lines.add("- 保持短句可以，但句子必须像真人会发出去的话，不能像规则改写题");
            if (realizationPlan != null
                    && realizationPlan.simulationIntentProfile() != null
                    && "seek_clear_answer".equals(realizationPlan.simulationIntentProfile().surfaceIntent())
                    && hasRequiredSemantic(realizationPlan, "D3:identity_freedom")) {
                lines.add("- 不要把两个短条件硬并列成“怎么选能又快又准，又不会太普通”这类句子；改成一个更顺的完整问句，例如“手机里有没有哪款更靠谱，又不会太普通？”");
            }
            if (realizationPlan != null
                    && realizationPlan.simulationIntentProfile() != null
                    && "seek_clear_answer".equals(realizationPlan.simulationIntentProfile().surfaceIntent())
                    && hasRequiredSemantic(realizationPlan, "D2:informational_directed")) {
                lines.add("- 判断题不要写成“先看哪个更...”这种半搜索模板，直接写“哪个更靠谱 / 哪个更稳 / 我该先看哪个”会更自然");
            }
            if (realizationPlan != null
                    && realizationPlan.simulationIntentProfile() != null
                    && "seek_clear_answer".equals(realizationPlan.simulationIntentProfile().surfaceIntent())
                    && hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
                lines.add("- 如果还要带“大家常选 / 大家常买”，优先写成“哪个更靠谱，也更像大家常选的”这类自然判断题");
            }
            if (realizationPlan != null
                    && realizationPlan.simulationIntentProfile() != null
                    && "confirm_and_buy".equals(realizationPlan.simulationIntentProfile().surfaceIntent())
                    && hasRequiredSemantic(realizationPlan, "D2:resource_acquisition")
                    && hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 临近购买又担心踩坑时，要把“准备买 / 买前确认”和“更稳 / 不容易出问题”收进一个主问题里，不要拆成三四个短分句串起来");
                lines.add("- 不要写成“准备定了，买前确认下，跟谁比，它稳吗，会不会有啥坑”这种一截一截往上挂的句子");
                lines.add("- 优先写成“准备买小米了，跟华为苹果比哪个更稳妥？”这类临门一脚确认句");
            }
        }
        if (failedChecks.contains("official_cue")) {
            String exactOfficialObject = sourceOfficialObjectPhrase(realizationPlan);
            if (StringUtils.hasText(exactOfficialObject)) {
                lines.add("- 原题已经明确写了“" + exactOfficialObject + "”，这次优先原词保留，不要只缩成“官网 / 官方 / 来源”");
            }
            lines.add("- 上一版把官网 / 官方 / 可信来源这类对象词写丢了，这次句子里必须真的出现官网 / 官方 / 来源，不要只剩“哪个更靠谱”");
            lines.add("- 如果这轮同时在比较对象和官网/来源，优先写成“谁家官网 / 哪边官方信息 / 哪些来源”这类直接问法");
        }
        if (failedChecks.contains("awkward")) {
            lines.add("- 避免倒装和并列硬拼，不要出现“值不值会不会太普通”这种生硬结构");
        }
        return lines;
    }

    private List<String> intentSpecificBehaviorRules(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.simulationIntentProfile() == null) {
            return List.of();
        }
        String surfaceIntent = realizationPlan.simulationIntentProfile().surfaceIntent();
        String conversationMode = realizationPlan.simulationIntentProfile().conversationMode();
        List<String> lines = new ArrayList<>();
        if ("browse_for_options".equals(surfaceIntent)) {
            lines.add("- 这句要像先摸一圈候选，不是立刻要明确结论或最终比较");
            lines.add("- 最好保留“有哪些可以先看看 / 我该先看哪些 / 先从哪几块看起”这种浏览开口");
            lines.add("- 不要直接收成“哪个好 / 主要区别在哪 / 该选哪个”");
            if ("single_turn_search_like".equals(conversationMode)) {
                lines.add("- 就算写短句，也要保留“先看看哪些 / 我该先看哪些”这层浏览开口，不要直接变成比较题");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                lines.add("- 如果顺带想看懂差别，优先写成“先从哪个方向看会更容易分清 / 先看哪几块差别更容易搞懂”，不要直接问“主要区别在哪”");
                lines.add("- 这里不要写“值得先看 / 值得先留意”这类评测腔，宁可问“先看哪几块 / 先从哪个方向看”");
                lines.add("- 如果是一边先摸方向一边顺手看懂差别，优先写成“有哪些可以先看看，差别先从哪块看最容易分清 / 我该先看哪些，哪块差别最容易看懂”");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 可以带不容易踩坑，但仍然是在先看哪些更不容易踩坑的选项");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:creation_idleness")) {
                lines.add("- 可以带省心，但不要退成只问“哪些牌子比较省心”");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
                lines.add("- 可以借大家常选来开口，但不要写成“大家一般先看什么 / 大家一般怎么选”");
                lines.add("- 真要带大家常买这层参考，优先写成“大家一般都会先看看哪些 / 我该先看哪些”，不要写“大家通常会先拿哪种”");
            }
        }
        if ("confirm_and_buy".equals(surfaceIntent)) {
            lines.add("- 这句要像临近购买前的最后确认，不是普通做功课或开放比较");
            lines.add("- 最好自然带一个购买前信号：现在买 / 买前 / 下单前 / 准备定 / 拍板前 / 直接拿");
            lines.add("- 如果提到比较或差别，目的是帮助马上定一个，不要只停在“差别在哪 / 大家选哪个 / 哪个更好”");
            if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 优先让句子落在更稳 / 不容易出问题 / 不容易踩坑，而不是泛泛地问哪个好");
            }
            if (isBrandVisible(realizationPlan)
                    && hasRequiredSemantic(realizationPlan, "D2:resource_acquisition")
                    && hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 就算顺手落到更具体的单品、部件或使用场景，句子里也要保留一次上层品类词，不要只剩具体件名");
                lines.add("- 优先写成“上层品类词 + 具体件名/场景 + 买前确认”的一句话，比如先点出厨房收纳，再落到水槽沥水架");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                lines.add("- 如果要问差别，最好问“哪块差别最关键 / 看清哪点就能定”，不要只泛问区别");
            }
        }
        if ("ask_for_actionable_steps".equals(surfaceIntent)) {
            lines.add("- 这句要问清下一步具体怎么挑 / 怎么判断，不是泛泛问哪个好或差别在哪");
            lines.add("- 优先写成“从哪几步看起 / 先抓哪几块 / 按什么思路挑”这种动作导向句");
            lines.add("- 就算写得短，也要保留一个明确的品类锚点，比如饮用水 / 包装饮用水 / 天然水，不要泛成“水 / 这个牌子 / 这种东西”");
            lines.add("- 尽量写成“从哪几步看起 / 从哪几块入手 / 按什么思路挑”，不要写成“先看哪 / 先看哪几块 / 先看哪几点”这种硬模板");
            if (realizationPlan.realizationConstraints().brandExposurePolicy() == com.geoprobe.dto.engine.v42.ProbeExposurePolicy.BRAND_VISIBLE) {
                lines.add("- 如果这轮允许提品牌，品牌要自然落进动作问题里，不要退成只有品类的泛问");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 最好自然带上不容易买错 / 不容易出问题这类结果约束");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                lines.add("- 如果提到差别，要落成“从哪几块差别入手 / 哪几块差别最该先看清”，不要只问区别");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:creation_idleness")) {
                lines.add("- 可以带省事，但不要写成“省心直接拿”这种硬拼");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
                lines.add("- 如果想参考大家常选的路数，也要落成自己的挑选顺序，不要写成“大家一般先看哪几点 / 大家一般怎么挑”");
            }
        }
        if ("seek_clear_answer".equals(surfaceIntent)) {
            lines.add("- 这句要像在要一个直接判断/推荐，不是在问完整挑选流程");
            lines.add("- 优先写成“哪个更靠谱 / 我该先看哪个 / 哪个更稳”这类一句话判断题");
            lines.add("- 不要滑成“怎么选 / 从哪几步看 / 按什么思路挑”这种动作导向问法");
            if ("single_turn_search_like".equals(conversationMode)) {
                lines.add("- 如果是随手搜一句的开口，尽量更短更直给，但仍然要保留“要判断”的感觉");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:identity_freedom")) {
                lines.add("- 如果要带“不太大众 / 不那么普通”，也要落在判断题里，比如“哪个更靠谱，又不会太普通”，不要写成过程型条件句");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                lines.add("- 如果想问差别，也要收成帮助判断的差别，不要退成完整教程");
            }
            if (isBrandVisible(realizationPlan) && hasRequiredSemantic(realizationPlan, "D3:understanding_competence")) {
                lines.add("- 这轮既然允许提品牌，又要尽快判断，句子里要同时保留品牌名和上层品类词，比如“某品牌这个厨房收纳，跟另外几家比差别主要在哪，哪个更稳”");
            }
            if (hasRequiredSemantic(realizationPlan, "D3:subsistence_protection")) {
                lines.add("- 如果担心踩坑，优先收在更稳 / 不容易出问题这种结论里");
            }
        }
        if (hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation")) {
            lines.add("- 可以带“大家常选 / 大家常买”这层参考，但不要直写“大家一般会选哪个 / 大家一般会先看哪个”");
            if ("seek_clear_answer".equals(surfaceIntent)) {
                lines.add("- 如果是直接判断题，优先写成“哪个更靠谱，也更像大家常选的 / 按大家常买的来挑，我该先看哪个”");
            }
        }
        return lines;
    }

    private List<String> contextSensitiveBehaviorRules(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.realizationConstraints() == null) {
            return List.of();
        }
        List<String> hints = realizationPlan.realizationConstraints().softSemanticHints();
        boolean hasCompareTarget = hints.stream().anyMatch(value -> hasHintPrefix(value, "COMPARE:"));
        boolean hasOfficialCue = hints.stream().anyMatch(this::containsOfficialCue);
        String exactOfficialObject = sourceOfficialObjectPhrase(realizationPlan);
        String sourceCategoryAnchor = sourceCategoryAnchorPhrase(realizationPlan);
        boolean hasAffectionSemantic = hasRequiredSemantic(realizationPlan, "D3:affection_belonging_participation");
        List<String> lines = new ArrayList<>();
        if (hasCompareTarget) {
            lines.add("- 如果这轮已经有明确对比对象，句子里要自然保留 1 到 2 个真实对比对象，不要偷换成“这几家 / 这几个牌子”");
        } else {
            lines.add("- 如果这轮不是显式比较题，不要因为输入里给了竞品池就主动点名竞品，也不要凭空写“这几家 / 这几个牌子”");
        }
        if (StringUtils.hasText(sourceCategoryAnchor)) {
            lines.add("- 如果原题已经明确点出“" + sourceCategoryAnchor + "”，这轮不要把这个品类锚点藏掉，句子里至少自然出现一次");
        }
        if (hasOfficialCue) {
            if (StringUtils.hasText(exactOfficialObject)) {
                lines.add("- 如果原题里已经明确写了“" + exactOfficialObject + "”，这轮优先原词保留，不要只缩成“官网 / 官方 / 来源”");
            }
            lines.add("- 如果这轮在问官网 / 官方 / 可信来源 / 来源，句子里必须真的保留这些对象词，不要改写成“哪个更靠谱”这类泛判断句");
            if (hasCompareTarget) {
                lines.add("- 如果这轮同时要比较对象和官网/来源，优先写成“谁家官网 / 哪边官方信息 / 哪些来源”这类直接问法");
            }
        }
        if (hasCompareTarget && hasAffectionSemantic && isBrandVisible(realizationPlan)) {
            lines.add("- 如果这轮既要带品牌比较，又要带“大家常选”参照，优先把品牌、竞品和“大家常选”放进同一句判断题里，不要拆成两层泛问");
        }
        return lines;
    }

    private boolean hasHintPrefix(String value, String prefix) {
        return StringUtils.hasText(value)
                && StringUtils.hasText(prefix)
                && value.trim().startsWith(prefix);
    }

    private String sourceOfficialObjectPhrase(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.realizationConstraints() == null) {
            return null;
        }
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .filter(value -> hasHintPrefix(value, "SOURCE_OFFICIAL_OBJECT:"))
                .map(value -> value.substring("SOURCE_OFFICIAL_OBJECT:".length()).trim())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String sourceCategoryAnchorPhrase(RealizationPlan realizationPlan) {
        if (realizationPlan == null || realizationPlan.realizationConstraints() == null) {
            return null;
        }
        return realizationPlan.realizationConstraints().softSemanticHints().stream()
                .filter(value -> hasHintPrefix(value, "SOURCE_CATEGORY_ANCHOR:"))
                .map(value -> value.substring("SOURCE_CATEGORY_ANCHOR:".length()).trim())
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private boolean containsOfficialCue(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.contains("官网")
                || trimmed.contains("官方")
                || trimmed.contains("可信来源")
                || trimmed.contains("来源");
    }

    private boolean hasRequiredSemantic(RealizationPlan realizationPlan, String semantic) {
        if (realizationPlan == null || !StringUtils.hasText(semantic)) {
            return false;
        }
        return realizationPlan.realizationConstraints().mustExpressSemantics().stream()
                .anyMatch(value -> semantic.equalsIgnoreCase(value));
    }

    private boolean isBrandVisible(RealizationPlan realizationPlan) {
        return realizationPlan != null
                && realizationPlan.realizationConstraints() != null
                && realizationPlan.realizationConstraints().brandExposurePolicy()
                == com.geoprobe.dto.engine.v42.ProbeExposurePolicy.BRAND_VISIBLE;
    }

    private String joinLines(List<String> lines) {
        return lines.stream()
                .filter(StringUtils::hasText)
                .map(line -> "- " + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 无");
    }
}

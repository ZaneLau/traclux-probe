package com.geoprobe.service.engine.v42;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SemanticKeywordMatcher {

    private static final Map<String, List<String>> KEYWORDS_BY_DIMENSION_AND_CODE = Map.ofEntries(
            Map.entry("D2:informational_directed", List.of(
                    "到底怎么选", "哪个更靠谱", "直接告诉我", "到底哪个好", "值不值得选", "该选哪个",
                    "给个明确答案", "给个准话", "想要个明确判断", "值不值得优先选",
                    "先看哪个", "先选哪个", "该看哪个", "值得先看吗", "先考虑哪个", "更好分辨",
                    "差别在哪", "差别主要在哪", "区别在哪", "主要差在哪", "主要区别在哪", "好在哪", "优势在哪", "强在哪", "哪个好一点", "哪个更好",
                    "大家一般会怎么选", "别人一般怎么选",
                    "哪个更值得看", "更值得先看", "先看什么牌子", "哪种好点", "有没有什么牌子",
                    "到底怎么样", "算不算", "看哪几点", "值得选", "怎么才能",
                    "哪个更不容易出问题", "哪个更省心",
                    "香不香", "值不值", "哪家性价比高", "哪家更香", "哪家更值", "哪家更划算",
                    "香气好不好", "香一点哪个好", "性价比高的怎么选"
            )),
            Map.entry("D2:informational_undirected", List.of(
                    "先看看", "先了解下", "先做做功课", "先从哪看起", "有哪些值得看", "先摸个底",
                    "先了解一下", "先看看方向", "先大概看看"
            )),
            Map.entry("D2:instructional_active", List.of(
                    "怎么做", "怎么选", "先看什么", "该怎么挑", "具体怎么看", "重点看什么",
                    "该从哪看起", "先看哪几点", "具体该怎么挑", "按什么挑", "先确认哪几点", "先确认哪几个点"
            )),
            Map.entry("D2:resource_acquisition", List.of(
                    "现在买哪个更稳", "值不值得买", "现在买", "能不能直接买", "直接入手", "买哪个更稳", "下单",
                    "想直接买", "确认了就买", "该不该下单", "现在入手", "值不值得直接选",
                    "准备买前", "买前先确认", "买前先看", "买前先核对", "现在就定",
                    "确认一下再买", "再确认一下", "确认下再买", "确认下", "定之前", "决定前", "准备定前", "先看清再定",
                    "下单前", "临下单前", "拍板前", "直接拿", "先拿哪个", "最后看哪点",
                    "最后比哪点", "最后确认哪点", "再核对哪点", "下手前",
                    "一般选哪个", "买的话选哪个", "买哪个牌子", "有什么要注意", "没问题吧",
                    "会不会踩坑", "不容易出问题", "不容易踩坑", "最省心", "更省心", "准备买了",
                    "准备买", "想买", "买的话", "哪个更适合直接买", "哪个更值得买",
                    "哪个最值得买", "哪个比较稳一点", "哪个更稳妥", "哪个最稳妥",
                    "哪个比较放心", "哪个更放心"
            )),
            Map.entry("D3:subsistence_protection", List.of(
                    "不想踩坑", "怕踩坑", "安全点", "放心点", "别出问题", "稳一点", "少出错",
                    "安全放心", "不容易踩坑", "更稳妥", "别踩雷", "不想踩坑", "不容易出错"
                    , "怕买错", "怕选错", "选到不好的", "不容易出问题", "更不容易出问题", "买错"
            )),
            Map.entry("D3:affection_belonging_participation", List.of(
                    "大家都买", "送人", "跟别人差不多", "圈子里怎么选", "拿出去不尴尬", "别人一般怎么选",
                    "大家一般怎么选", "别人都怎么挑", "跟大多数人差不多", "大家常买"
            )),
            Map.entry("D3:understanding_competence", List.of(
                    "差别在哪", "怎么看区别", "参数怎么看", "想弄明白", "原理上有什么区别", "判断依据",
                    "搞懂原理", "看懂差别", "想看明白", "分清差别", "看清差别", "主要差在哪",
                    "先看清", "先分清", "怎么区分", "搞明白哪几个差别", "比哪两点", "比哪一点", "先比哪点",
                    "更好分辨", "更容易分清", "更好判断"
            )),
            Map.entry("D3:identity_freedom", List.of(
                    "不想太普通", "有点特别", "别撞款", "更有风格", "跟别人不太一样", "有点自己的风格",
                    "别太大众", "想选得特别一点", "不想跟大家都一样", "没那么普通", "不那么普通",
                    "没那么大众", "不那么大众", "有特色", "比较有特色", "特别的选择",
                    "更有个性", "有个性", "个性一点",
                    "有特色的选择", "不至于太普通"
            )),
            Map.entry("D3:creation_idleness", List.of(
                    "省点心", "轻松点", "舒服点", "图个省事", "别太折腾", "用着舒服",
                    "省事一点", "轻松省心", "省点事", "费事"
            ))
    );

    private SemanticKeywordMatcher() {
    }

    public static boolean matches(String rendered, String dimId, String abstractCode, String translatedValue) {
        String normalizedRendered = normalize(rendered);
        if (!StringUtils.hasText(normalizedRendered)) {
            return false;
        }
        List<String> keywords = keywordsFor(dimId, abstractCode);
        boolean keywordHit = keywords.stream()
                .map(SemanticKeywordMatcher::normalize)
                .anyMatch(normalizedRendered::contains);
        if (keywordHit) {
            return true;
        }
        if (matchesSemanticCue(normalizedRendered, buildKey(dimId, abstractCode))) {
            return true;
        }
        return StringUtils.hasText(translatedValue)
                && normalizedRendered.contains(normalize(translatedValue));
    }

    public static String primaryKeyword(String dimId, String abstractCode, String translatedValue) {
        List<String> keywords = keywordsFor(dimId, abstractCode);
        if (!keywords.isEmpty()) {
            return keywords.getFirst();
        }
        return translatedValue == null ? "" : translatedValue.trim();
    }

    private static List<String> keywordsFor(String dimId, String abstractCode) {
        return KEYWORDS_BY_DIMENSION_AND_CODE.getOrDefault(buildKey(dimId, abstractCode), List.of());
    }

    private static String buildKey(String dimId, String abstractCode) {
        return normalizeDimension(dimId) + ":" + normalizeCode(abstractCode);
    }

    private static String normalizeDimension(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String normalizeCode(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？、；：“”‘’（）()《》【】]+", "");
    }

    private static boolean matchesSemanticCue(String normalizedRendered, String key) {
        return switch (key) {
            case "D2:informational_directed" -> containsAny(normalizedRendered,
                    "到底", "明确", "准话", "直接说", "说清楚", "先看哪个", "先选哪个", "该看哪个", "值得先看", "先考虑哪个",
                    "差别在哪", "区别在哪", "主要差在哪", "主要区别在哪", "差在哪", "算不算", "有没有", "哪几点", "到底怎么样", "怎么才能",
                    "哪个", "哪种", "哪款", "哪家", "香不香", "值不值")
                    && containsAny(normalizedRendered,
                    "哪个好", "更靠谱", "更值", "该选哪个", "行不行", "值不值", "优先选", "先看哪个", "先选哪个", "更好分辨",
                    "在哪", "更好", "更值得", "区别", "差别", "普通", "特色", "省心", "踩坑", "怎么样", "值得选", "出问题",
                    "性价比", "香气", "更香", "香一点", "划算");
            case "D2:informational_undirected" -> containsAny(normalizedRendered,
                    "先看看", "先了解", "先摸", "有哪些", "先从", "先看", "大概看看", "先扫一眼");
            case "D2:instructional_active" -> containsAny(normalizedRendered,
                    "看哪", "怎么看", "怎么挑", "怎么选", "看什么", "按什么", "从哪", "盯哪", "主要看", "确认哪");
            case "D2:resource_acquisition" -> (containsAny(normalizedRendered,
                    "买", "下单", "入手", "定", "定下来", "直接上", "直接定", "直接选", "买前", "现在就定", "定之前", "决定前", "准备定前",
                    "直接拿", "拍板前", "下单前", "临下单前", "下手前", "准备买", "准备买了", "想买", "买的话")
                    && containsAny(normalizedRendered,
                    "值不值", "更值", "行不行", "靠谱不", "稳不稳", "稳吗", "合不合适", "更合适", "更稳", "先确认", "先看", "先核对", "最该先看", "再确认", "确认一下", "确认下", "先看清",
                    "最后看", "最后比", "最后确认", "再核对", "再比", "再看", "先拿哪个",
                    "一般选哪个", "哪个牌子", "买哪个", "要注意", "没问题", "会不会踩坑", "不容易出问题", "不容易踩坑", "最省心", "更省心"))
                    || containsAny(normalizedRendered,
                    "直接买", "直接下单", "值不值得买", "适不适合直接入手", "值不值得直接选",
                    "准备买前", "买前先确认", "买前先看", "买前先核对", "现在就定", "确认一下再买", "再确认一下",
                    "定之前", "决定前", "准备定前", "先看清再定", "下单前", "临下单前", "拍板前",
                    "值不值得直接拿", "直接拿行不行", "直接拿哪个", "先拿哪个", "最后看哪点", "最后比哪点",
                    "最后确认哪点", "再核对哪点", "下手前", "一般选哪个", "买的话选哪个", "买哪个牌子",
                    "有什么要注意", "没问题吧", "会不会踩坑", "不容易出问题", "不容易踩坑", "最省心", "更省心",
                    "准备买了", "准备买", "想买", "买的话", "哪个更适合直接买", "哪个更值得买",
                    "哪个最值得买", "哪个比较稳一点", "哪个更稳妥", "哪个最稳妥",
                    "哪个比较放心", "哪个更放心");
            case "D3:subsistence_protection" -> containsAny(normalizedRendered,
                    "踩坑", "翻车", "稳", "稳妥", "靠谱", "放心", "出问题", "别雷", "不出岔子", "不出错", "出错",
                    "怕买错", "怕选错", "选到不好", "买错");
            case "D3:affection_belonging_participation" -> containsAny(normalizedRendered,
                    "大家", "别人", "大多数", "一般都", "周围", "常见", "都怎么", "跟着选", "常买");
            case "D3:understanding_competence" -> containsAny(normalizedRendered,
                    "差别", "区别", "搞清", "看懂", "弄明白", "判断", "门道", "区别点", "分清", "看清", "区分", "主要差在", "先看清");
            case "D3:identity_freedom" -> containsAny(normalizedRendered,
                    "太普通", "大众", "特别", "不一样", "撞", "自己的偏好", "自己的风格", "千篇一律", "跟大家都一样",
                    "没那么普通", "不那么普通", "没那么大众", "不那么大众", "有特色", "不至于太普通",
                    "个性", "有个性", "更有个性");
            case "D3:creation_idleness" -> containsAny(normalizedRendered,
                    "省心", "省事", "轻松", "别折腾", "不费脑", "费脑子", "简单点", "少费劲", "省力", "费事", "省点事");
            default -> false;
        };
    }

    private static boolean containsAny(String normalizedRendered, String... cues) {
        for (String cue : cues) {
            if (normalizedRendered.contains(normalize(cue))) {
                return true;
            }
        }
        return false;
    }
}

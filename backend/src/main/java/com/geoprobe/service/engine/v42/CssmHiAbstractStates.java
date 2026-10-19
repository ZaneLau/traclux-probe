package com.geoprobe.service.engine.v42;

import java.util.List;
import java.util.Map;

/**
 * CSSM-HI six-dimensional abstract state table for V4.2 Node 0.
 * Stage 1 is fully deterministic and category-agnostic. LLM is not allowed
 * to invent, delete, merge, or modify these abstract codes.
 */
public final class CssmHiAbstractStates {

    // D1 认知异常与情感状态 (4)
    public static final List<String> D1_COGNITIVE_ANOMALY = List.of(
            "initiation",
            "exploration",
            "formulation",
            "verification"
    );

    // D2 目的论微观目标 (4)
    public static final List<String> D2_TELEOLOGICAL_GOAL = List.of(
            "informational_directed",
            "informational_undirected",
            "instructional_active",
            "resource_acquisition"
    );

    // D3 价值论驱动力 (5)
    public static final List<String> D3_AXIOLOGICAL_DRIVE = List.of(
            "subsistence_protection",
            "affection_belonging_participation",
            "understanding_competence",
            "identity_freedom",
            "creation_idleness"
    );

    // D4 认知负荷容量 (3)
    public static final List<String> D4_COGNITIVE_LOAD = List.of(
            "severely_depleted",
            "moderate_strain",
            "optimal"
    );

    // D5 时间约束 (3)
    public static final List<String> D5_TIME_CONSTRAINT = List.of(
            "acute_emergency",
            "anticipatory_constraint",
            "reflective"
    );

    // D6 交互模态 (3)
    public static final List<String> D6_INTERACTION_MODALITY = List.of(
            "specification",
            "recognition",
            "dialogic"
    );

    public static final List<String> DIMENSION_IDS = List.of("D1", "D2", "D3", "D4", "D5", "D6");

    public static final Map<String, String> DIMENSION_NAMES = Map.of(
            "D1", "认知异常与情感状态",
            "D2", "目的论微观目标",
            "D3", "价值论驱动力",
            "D4", "认知负荷容量",
            "D5", "时间约束",
            "D6", "交互模态"
    );

    public static final Map<String, List<String>> ABSTRACT_CODE_MAP = Map.of(
            "D1", D1_COGNITIVE_ANOMALY,
            "D2", D2_TELEOLOGICAL_GOAL,
            "D3", D3_AXIOLOGICAL_DRIVE,
            "D4", D4_COGNITIVE_LOAD,
            "D5", D5_TIME_CONSTRAINT,
            "D6", D6_INTERACTION_MODALITY
    );

    public static final Map<String, List<String>> DETERMINISTIC_FALLBACK_VARIABLES = Map.of(
            "D1", List.of("完全迷茫不知从何问起", "越查越困惑", "大概知道要什么了", "就想确认一下"),
            "D2", List.of("想知道一个确切答案", "随便了解了解", "想找个具体方法", "想买/想找到"),
            "D3", List.of("为了健康安全", "想融入圈子", "想搞清楚原理", "就要与众不同", "图个开心"),
            "D4", List.of("脑子已经转不动了", "有点累但还行", "头脑清醒能分析"),
            "D5", List.of("马上就要用", "还有几天时间", "不急慢慢看"),
            "D6", List.of("直接搜答案", "随便刷刷看", "跟AI聊聊")
    );

    private CssmHiAbstractStates() {
    }
}

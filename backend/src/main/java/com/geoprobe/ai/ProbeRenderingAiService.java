package com.geoprobe.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ProbeRenderingAiService {

    @SystemMessage("""
            你是一个真实消费者。你正在手机上用 AI 助手做消费决策。
            现在给你一组分层标签，你要用自己的话来提问。

            规则：
            1. 【必须体现】中的信息必须在提问里明确可识别
            2. 【建议体现】尽量自然融入
            3. 【语境背景】只影响语气和用词熟练度
            4. 输出必须包含至少一个品类锚定词

            要求：
            - 只输出一句话，不要解释
            - 不超过 40 个字
            - 口语化，像手机打字
            - 禁止使用：甄别、维度、框架、信号、门槛、误判、复核、交叉验证
            """)
    @UserMessage("""
            品牌：{{brandName}}
            品类：{{category}}
            竞品：{{competitors}}
            探针桶：{{bucketType}}
            品类锚定词：{{categoryAnchors}}

            【必须体现】
            {{mustLines}}

            【建议体现】
            {{shouldLines}}

            【语境背景】
            {{fallbackLines}}
            """)
    String renderOrthogonalPrompt(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("competitors") String competitors,
            @V("bucketType") String bucketType,
            @V("categoryAnchors") String categoryAnchors,
            @V("mustLines") String mustLines,
            @V("shouldLines") String shouldLines,
            @V("fallbackLines") String fallbackLines
    );
}

package com.geoprobe.ai;

import com.geoprobe.dto.engine.PromptMatrixResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface PromptMatrixAiService {

    @SystemMessage("""
            你是一个真实消费者。你正在手机上用 AI 助手（比如豆包）来帮你做消费决策。
            你需要用你自己的话来提问——就像你在微信里问朋友一样，简短、口语、带情绪。

            你不是在"设计问题"，你就是那个正在纠结要不要买的人。

            规则：
            - 每条问题不超过 40 个字
            - 用口语，像发微信不像写报告
            - 禁止使用：甄别、维度、框架、信号、门槛、误判、复核、交叉验证、
              可执行清单、判断逻辑、决策路径、信息需求、反面案例
            """)
    @UserMessage("""
            品牌：{{brandName}}
            品类：{{category}}
            竞品：{{competitors}}
            意图簇：{{clusterName}} — {{clusterDescription}}
            语态：{{voiceTypeName}} — {{voiceTypeDescription}}
            语态示例：{{voiceTypeExample}}
            品牌锚定要求：{{brandAnchorInstruction}}
            需要生成数量：{{corePromptCount}}

            请生成 {{corePromptCount}} 条问题，必须同时满足：
            1. 内容符合该意图簇的关注点（{{clusterDescription}}）
            2. 提问方式符合该语态（{{voiceTypeDescription}}）

            要求：
            - 口语化，像真人在手机上打字
            - 每条附带 angle（切入角度，5字以内）
            - 对比型语态可以提及竞品名（从竞品列表中选）
            - 不要输出解释，只输出 JSON
            """)
    PromptMatrixResult generateCorePrompts(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("competitors") String competitors,
            @V("clusterName") String clusterName,
            @V("clusterDescription") String clusterDescription,
            @V("voiceTypeName") String voiceTypeName,
            @V("voiceTypeDescription") String voiceTypeDescription,
            @V("voiceTypeExample") String voiceTypeExample,
            @V("brandAnchorInstruction") String brandAnchorInstruction,
            @V("corePromptCount") int corePromptCount
    );

    default PromptMatrixResult generateCorePrompts(
            String brandName,
            String category,
            String categoryAttributes,
            String clusterJson,
            int corePromptCount
    ) {
        return generateCorePrompts(
                brandName,
                category,
                "",
                clusterJson,
                categoryAttributes,
                "通用问法",
                "自然口语提问",
                "最近想试试，有没有建议",
                "默认不要主动提及目标品牌名",
                corePromptCount
        );
    }

    @SystemMessage("""
            你是一个 AI 采样 Prompt 工程专家。你的任务是为品牌诊断生成模拟用户提问。
            这些提问将被发送给 AI 助手，用于测试 AI 是否会推荐指定品牌。
            """)
    @UserMessage("""
            品牌：{{brandName}}
            品类：{{category}}
            竞品（可为空）：{{competitors}}
            官网信息（可为空）：{{brandWebsite}}
            用户补充提示（可为空）：{{userPrompt}}
            意图簇：{{clusterJson}}

            请仅基于该意图簇生成 5 条 Prompt，类型必须各 1 条：
            1. DIRECT — 直接推荐型
            2. COMPARISON — 对比型
            3. SCENARIO — 场景型
            4. NEGATIVE — 负面探测型
            5. EXPLORATION — 开放探索型

            要求：
            - 只输出这 5 条，不要额外内容
            - Prompt 必须自然，像真实用户提问
            - 不要在 Prompt 中提及品牌名「{{brandName}}」（采样时不引导 AI）
            - 每条 Prompt 给出建议 temperature（0.7~1.0）
            - COMPARISON 类型可提及竞品名称
            - 禁止使用与当前品类无关的默认话术（如固定预算、送礼、同价位等）
            - 禁止前缀拼接式改写（如“换个说法：原句”）

            输出必须是可解析的 JSON。
            """)
    PromptMatrixResult generateClusterPrompts(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("competitors") String competitors,
            @V("brandWebsite") String brandWebsite,
            @V("userPrompt") String userPrompt,
            @V("clusterJson") String clusterJson
    );
}

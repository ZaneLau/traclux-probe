package com.geoprobe.ai;

import com.geoprobe.service.shared.FcbClassification;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface IntentClusterAiService {

    @SystemMessage("""
            你是品类分析专家，擅长分析各类产品与服务的消费者决策特征。
            你的任务是基于品牌词做 FCB-GEO 分类判定，输出稳定可解析 JSON。
            """)
    @UserMessage("""
            品牌：{{brandName}}
            官网信息（可为空）：{{brandWebsite}}
            用户补充提示（可为空）：{{userPrompt}}

            请分析该品牌所属品类的消费者决策特征，并严格按 JSON 返回：
            1) category: 品类名称
            2) involvement: HIGH 或 LOW（决策权重与信息需求强度）
            3) parameterValid: true/false（消费者是否会用参数/规格决策）
            4) experienceValid: true/false（消费者是否会看真实体验/评价决策）
            5) trustValid: true/false（消费者是否会看权威背书/认证决策）
            6) categoryAttributes: 5~8 个品类筛选维度
            7) competitors: 3~5 个主要竞品（不含目标品牌）
            8) nonDiscriminatingKeywords: 8~20 个该品类常见“非品牌区分型”问题关键词（例如流程科普、通用知识，不依赖具体品牌）

            要求：
            - 只输出 JSON，不要额外解释
            - involvement 只能是 HIGH/LOW
            - categoryAttributes、competitors、nonDiscriminatingKeywords 不要为空
            - 必须按输入品牌所属品类语境推理，不要默认电商购物语境
            """)
    FcbClassification inferClassification(@V("brandName") String brandName,
                                          @V("brandWebsite") String brandWebsite,
                                          @V("userPrompt") String userPrompt);
}

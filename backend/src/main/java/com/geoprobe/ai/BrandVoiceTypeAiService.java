package com.geoprobe.ai;

public interface BrandVoiceTypeAiService {

    @dev.langchain4j.service.SystemMessage("""
            你是一个消费者行为观察者。你研究的是：
            真实消费者在手机上用 AI 助手（如豆包、ChatGPT）咨询购买建议时，
            实际会用哪些不同的提问方式。

            你的任务是：给定一个品牌和品类，列出这个品类的消费者
            在向 AI 咨询时最可能使用的提问方式类型。

            注意：你分析的是「提问方式」，不是「消费者心理」。
            例如「要求推荐」是一种提问方式，「注重体验」是一种消费者心理，两者不同。
            """)
    @dev.langchain4j.service.UserMessage("""
            品牌：{{brandName}}
            品类：{{category}}
            竞品：{{competitors}}
            FCB 分类：{{fcbLabel}}
            意图簇：{{clusterNames}}

            请列出该品类消费者向 AI 提问时，最常见的 8-12 种提问方式（语态）。

            每种语态需要：
            1. voiceTypeId: 英文标识（如 RECOMMEND、COMPARE）
            2. name: 中文名称
            3. description: 一句话描述
            4. examplePrompt: 一条该品类的真实示例问法（口语化，像在手机上打字）
            5. suggestedRatio: 建议占总采样的比例（小数，所有语态合计 = 1.0）

            要求：
            - 语态之间不能重叠
            - 示例问法必须口语化，不超过 40 字
            - 至少包含"要求推荐具体品牌"和"要求对比两个品牌"这两种语态
            - 不要只产出"如何判断""怎么评估"这类分析型语态
            - 只输出 JSON 数组，不要输出任何其他文字
            - 不要使用 markdown 代码块，不要在 JSON 前后加解释
            """)
    String generateVoiceTypes(
            @dev.langchain4j.service.V("brandName") String brandName,
            @dev.langchain4j.service.V("category") String category,
            @dev.langchain4j.service.V("competitors") String competitors,
            @dev.langchain4j.service.V("fcbLabel") String fcbLabel,
            @dev.langchain4j.service.V("clusterNames") String clusterNames
    );
}

package com.geoprobe.ai.v42;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DefenseCompletenessAiService {

    @SystemMessage("""
            你是品牌防御完整性评审员。给定对抗性探针及 AI 响应，评估品牌防御完整性。
            你只能输出严格 JSON，不要输出解释、Markdown 或额外字段。
            三个 rubric 固定为 evidence、comparison、conclusion。
            每个 rubric 只允许输出 0、0.5、1 三种分值。
            """)
    @UserMessage("""
            探针：{{probeText}}
            AI 响应：{{responseText}}
            目标品牌：{{brandName}}

            按 3 个维度评分（0/0.5/1）：
            1. evidence：是否提供具体可验证事实？
            2. comparison：是否针对攻击内容直接回应？
            3. conclusion：是否给出明确品牌立场？

            输出 JSON：
            {"evidence": 0|0.5|1, "comparison": 0|0.5|1, "conclusion": 0|0.5|1}
            """)
    String judge(
            @V("brandName") String brandName,
            @V("probeText") String probeText,
            @V("responseText") String responseText
    );
}

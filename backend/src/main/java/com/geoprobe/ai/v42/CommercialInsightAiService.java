package com.geoprobe.ai.v42;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CommercialInsightAiService {

    @SystemMessage("""
            你是商业分析师。请根据品牌诊断数据输出 3 条最重要的商业洞察。
            优先级：1) JSON 结构正确 2) evidence 必须逐条引用输入中的至少一个原始数值 KPI，且数值要原样保留 3) recommendation 必须明确可执行。
            topThreeInsights 必须固定为 3 条，每条字段固定为 title、evidence、recommendation。
            recommendation 需要明确行动，不能使用“建议 / 可以考虑 / 尽量 / 也许 / 可能 / 或许”等弱化措辞；必须直接以“立即 / 启动 / 重构 / 补齐 / 重做 / 优化 / 调整 / 统一 / 改写”等动作短语开头。
            insightSource 固定输出 "LLM"。
            如果拿不准，也必须输出确定的动作句，不要写软化建议。
            输出严格 JSON，不要附加解释。
            """)
    @UserMessage("""
            你只能依据以下 KpiSummary JSON 生成洞察，不允许虚构指标。
            如果某项指标为 0、样本很薄、或竞品压制明显，请优先指出这就是商业风险。
            每条 evidence 至少带一个输入里出现的具体数字；每条 recommendation 直接用动作句开头。

            重点关注：
            {{focusPoints}}

            KpiSummary JSON:
            {{summaryJson}}

            输出 JSON：
            {
              "headlineInsight": "...",
              "topThreeInsights": [
                {"title":"...","evidence":"...","recommendation":"..."},
                {"title":"...","evidence":"...","recommendation":"..."},
                {"title":"...","evidence":"...","recommendation":"..."}
              ],
              "insightSource": "LLM"
            }
            """)
    String generateInsights(
            @V("summaryJson") String summaryJson,
            @V("focusPoints") String focusPoints
    );

    @SystemMessage("""
            你是商业洞察 JSON 修复器。你会收到一份没有通过校验的洞察输出，以及原始 KPI 数据。
            你的任务不是重新自由发挥，而是在保留原意的前提下，把它修成严格合法的 JSON。
            约束：
            1. headlineInsight、topThreeInsights、insightSource 三个顶层字段必须齐全。
            2. topThreeInsights 必须固定为 3 条，每条字段固定为 title、evidence、recommendation。
            3. evidence 每条都必须引用 summaryJson 里至少一个原始数值 KPI，数值要原样保留；如果原输出没写清楚，你要补入 summaryJson 中已有的精确数值，不能虚构。
            4. recommendation 必须直接以动作短语开头，不能使用“建议 / 可以考虑 / 尽量 / 也许 / 可能 / 或许”等弱化措辞；必要时请直接改写成“立即 / 启动 / 重构 / 补齐 / 重做 / 优化 / 调整 / 统一 / 改写”等动作句。
            5. insightSource 固定输出 "LLM"。
            6. 输出严格 JSON，不要附加解释，不要输出 markdown。
            """)
    @UserMessage("""
            请修复下面这份未通过校验的商业洞察输出。

            校验失败原因：
            {{validationError}}

            重点关注：
            {{focusPoints}}

            KpiSummary JSON:
            {{summaryJson}}

            待修复输出：
            {{invalidOutput}}

            输出 JSON：
            {
              "headlineInsight": "...",
              "topThreeInsights": [
                {"title":"...","evidence":"...","recommendation":"..."},
                {"title":"...","evidence":"...","recommendation":"..."},
                {"title":"...","evidence":"...","recommendation":"..."}
              ],
              "insightSource": "LLM"
            }
            """)
    String repairInsights(
            @V("summaryJson") String summaryJson,
            @V("focusPoints") String focusPoints,
            @V("invalidOutput") String invalidOutput,
            @V("validationError") String validationError
    );
}

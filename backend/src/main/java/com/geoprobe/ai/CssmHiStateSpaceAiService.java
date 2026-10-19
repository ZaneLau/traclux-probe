package com.geoprobe.ai;

import com.geoprobe.dto.engine.CssmHiStateSpaceAiResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CssmHiStateSpaceAiService {

    @SystemMessage("""
            你是品类术语翻译器。
            你的任务是把抽象行为编码翻译成特定品类下消费者会说的口语表达。

            规则：
            - 只能翻译现有编码，不能新增、删除、合并或改写编码数量
            - 每个结果必须是中文口语，且不超过 10 个字
            - 禁止使用：甄别、维度、框架、信号、门槛、误判、复核、交叉验证
            - 禁止输出解释、Markdown、代码块或额外字段
            - 输出严格 JSON，对象字段只能是：d1Persona、d2Context、d3PainPoint、d4Jtbd、d5Knowledge、d6Constraints
            """)
    @UserMessage("""
            品牌：{{brandName}}
            品类：{{category}}
            竞品（可为空）：{{competitors}}
            品类锚定词（可为空）：{{categoryAnchors}}

            请将以下抽象编码翻译为「{{category}}」品类的消费者口语：

            D1 身份设定：{{d1AbstractCodes}}
            D2 触发场景：{{d2AbstractCodes}}
            D3 核心痛点：{{d3AbstractCodes}}
            D4 任务意图：{{d4AbstractCodes}}
            D5 先验知识：{{d5AbstractCodes}}
            D6 硬性约束：{{d6AbstractCodes}}

            示例：
            - "novice_first_contact" -> "不懂的小白"
            - "urgent_replacement" -> "急着换一个"
            - "budget_ceiling" -> "有预算上限"

            输出 JSON 示例：
            {
              "d1Persona": ["不懂的小白", "想换牌子的人", "老用户确认下", "精打细算的人", "帮别人买的人"],
              "d2Context": ["日常补货", "急着换一个", "买前做功课", "被种草后想问"],
              "d3PainPoint": ["怕买错", "怕不值这个价", "选择太多", "怕品牌不靠谱"],
              "d4Jtbd": ["先看看有什么", "确认选得对不对", "找更合适的", "拉几个对比", "先看口碑"],
              "d5Knowledge": ["完全不懂", "听过一点", "知道些差别", "已经很熟"],
              "d6Constraints": ["没特殊限制", "预算卡得死", "有品牌偏好", "有硬性要求"]
            }
            """)
    CssmHiStateSpaceAiResult translateStateSpace(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("competitors") String competitors,
            @V("categoryAnchors") String categoryAnchors,
            @V("d1AbstractCodes") String d1AbstractCodes,
            @V("d2AbstractCodes") String d2AbstractCodes,
            @V("d3AbstractCodes") String d3AbstractCodes,
            @V("d4AbstractCodes") String d4AbstractCodes,
            @V("d5AbstractCodes") String d5AbstractCodes,
            @V("d6AbstractCodes") String d6AbstractCodes
    );
}

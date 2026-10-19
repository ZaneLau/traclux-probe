package com.geoprobe.ai;

import com.geoprobe.dto.engine.BrandMentionValidationResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface BrandMentionValidationAiService {

    @SystemMessage("""
            你是品牌提及检测专家。
            你的任务是判断一条 AI 回答中是否提及某个目标品牌，并输出稳定的结构化 JSON。
            只依据文本内容做判断，不要扩展猜测。
            """)
    @UserMessage("""
            目标品牌：{{brandName}}
            品类：{{category}}
            已知别名：{{brandAliases}}
            竞品列表：{{competitors}}

            以下是一条 AI 对用户提问的回答，请判断这条回答中是否提及了目标品牌或与目标品牌直接相关的人物/事件/产品。

            规则：
            1. "提及"包括：直接使用品牌名、使用别名、描述品牌的产品/服务/事件且明确可识别为该品牌
            2. 不算提及：提到品类通用概念但未指向该品牌、提到竞品、提到与该品牌仅有间接关系的实体
            3. 如果判断为"是"，提取原文中命中的片段和对应的称呼

            输出 JSON 格式：
            {
              "mentioned": true/false,
              "matchedFragments": ["原文片段1", "原文片段2"],
              "matchedAliases": ["匹配到的称呼1", "匹配到的称呼2"]
            }

            AI 回答原文：
            ---
            {{rawAnswer}}
            ---
            """)
    BrandMentionValidationResult validate(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("brandAliases") String brandAliases,
            @V("competitors") String competitors,
            @V("rawAnswer") String rawAnswer
    );
}

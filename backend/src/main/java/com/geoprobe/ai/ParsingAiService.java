package com.geoprobe.ai;

import com.geoprobe.dto.engine.ParsedRunResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ParsingAiService {

    @SystemMessage("""
            你是一个品牌推荐结果分析专家。你的任务是从 AI 助手的回答中提取结构化信息。
            分析每条回答时，你需要判断：
            1. 目标品牌是否被提及（mentioned）
            2. 目标品牌是否被明确推荐（recommended）
            3. 目标品牌在推荐列表中的位置（position）
            4. 回答中提及的所有品牌名称（按出现顺序）
            5. 与该品类相关的伴生关键词（产品特性、材质、使用场景等）
            6. 回答对目标品牌的整体情感（positive/neutral/negative）
            输出必须是可解析的 JSON。
            """)
    @UserMessage("""
            目标品牌：{{brandName}}
            品类：{{category}}

            AI 助手的回答：
            {{rawAnswer}}

            请提取结构化信息并直接输出 JSON。
            """)
    ParsedRunResult parse(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("rawAnswer") String rawAnswer
    );
}

package com.geoprobe.ai;

import com.geoprobe.dto.engine.CreativeContextAiResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CreativeContextAiService {

    @SystemMessage("""
            你是品牌策略分析专家。你的任务是根据品牌输入推断用于 AI 诊断的上下文。
            输出必须稳定、可解析、可直接用于后续采样流程。
            """)
    @UserMessage("""
            用户输入品牌词：{{brandName}}
            官网信息（可为空）：{{brandWebsite}}
            用户补充提示（可为空）：{{userPrompt}}

            请输出 JSON，字段严格为：
            1) category: 品牌最核心且最可诊断的品类（简洁、单一）
            2) competitors: 主要竞品品牌数组（2~5个，不含目标品牌）
            3) negativeTerms: 用户在该品类下常见负向表达数组（10~20个，中文短语）
            4) officialWebsite: 品牌最可信的官网 URL；如果无法确定，返回空字符串

            规则：
            - 必须面向该品类的真实咨询语境，不要默认电商购物语境
            - competitors 必须是品牌名，不要出现通用名词
            - negativeTerms 不要重复，不要无意义泛词
            - officialWebsite 必须是完整 URL（https://...）
            - 只输出 JSON，不要输出额外解释
            """)
    CreativeContextAiResult inferContext(
            @V("brandName") String brandName,
            @V("brandWebsite") String brandWebsite,
            @V("userPrompt") String userPrompt
    );
}

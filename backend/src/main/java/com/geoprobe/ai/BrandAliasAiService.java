package com.geoprobe.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface BrandAliasAiService {

    @SystemMessage("""
            你是品牌信息抽取专家。
            你的任务是基于品牌名、品类和竞品列表，提取该品牌的常见别名与识别称呼。
            输出必须稳定、保守、结构化，避免幻觉。
            """)
    @UserMessage("""
            品牌名：{{brandName}}
            品类：{{category}}
            竞品列表：{{competitors}}

            请列出该品牌的所有常见称呼，包括但不限于：
            1. 官方简称和缩写
            2. 民间俗称和昵称
            3. 核心关联人物名（仅限提到此人时几乎等同于提到该品牌）
            4. 子品牌名和系列名
            5. 英文名和拼音名

            规则：
            - 只输出 JSON 数组，不解释
            - 每个别名是一个字符串
            - 不要包含品牌名本身
            - 不要包含竞品名
            - 不要包含品类通用词（如"直播""培训""电商"）
            - 关联人物只收录"提到这个人 ≈ 提到这个品牌"的情况，如果某人同时关联多个品牌则不收录
            """)
    String generateAliases(
            @V("brandName") String brandName,
            @V("category") String category,
            @V("competitors") String competitors
    );
}

package com.geoprobe.service;

import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpacePromptPayload;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandSpaceConfigServiceTest {

    private final BrandSpaceConfigService service = new BrandSpaceConfigService(null, null, null);

    @Test
    void shouldGenerateTenSuggestedTopicsAndPromptsForBrandCreation() {
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("卫龙")
                .category("辣条")
                .officialWebsite("https://www.weilongshipin.com")
                .competitors(List.of("麻辣王子", "良品铺子", "三只松鼠"))
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .build();

        List<BrandSpaceTopicPayload> topics = service.generateTopics(draft);
        List<BrandSpacePromptPayload> prompts = service.generatePrompts(draft, topics);

        assertEquals(10, topics.size());
        assertEquals(10, prompts.size());
        assertEquals("辣条选购场景", topics.get(0).getName());
        assertEquals("竞品对比", topics.get(1).getName());
        assertTrue(topics.stream().anyMatch(item -> "口味与配料".equals(item.getName())));
        assertTrue(topics.stream().anyMatch(item -> "囤货与送礼场景".equals(item.getName())));
        assertEquals("辣条怎么选，哪些品牌更值得先加入候选？", prompts.get(0).getText());
        assertTrue(prompts.stream().anyMatch(item -> item.getText().contains("卫龙和麻辣王子相比")));
        assertTrue(prompts.stream().anyMatch(item -> item.getText().contains("官网")));
        assertTrue(prompts.stream().filter(item -> item.getText().contains("卫龙")).count() < prompts.size());
        assertTrue(prompts.stream().anyMatch(item -> !item.getText().contains("卫龙")));
    }

    @Test
    void shouldCompilePromptFromTopicNameWhenTopicWasChangedByUser() {
        BrandSpaceDraftPayload draft = BrandSpaceDraftPayload.builder()
                .brandName("稳沐")
                .category("淋浴花洒")
                .regionCode("CN")
                .locale("zh-CN")
                .language("zh-CN")
                .build();

        List<BrandSpacePromptPayload> prompts = service.generatePrompts(draft, List.of(
                BrandSpaceTopicPayload.builder()
                        .tempId("topic-1")
                        .name("出租屋花洒选购")
                        .enabled(true)
                        .source("user_edited")
                        .build(),
                BrandSpaceTopicPayload.builder()
                        .tempId("topic-2")
                        .name("暂不关注")
                        .enabled(false)
                        .source("manual")
                        .build()
        ));

        assertEquals(2, prompts.size());
        assertEquals("出租屋花洒选购里，稳沐表现怎么样？", prompts.get(0).getText());
        assertTrue(prompts.get(0).getEnabled());
        assertEquals("暂不关注里，稳沐表现怎么样？", prompts.get(1).getText());
        assertTrue(Boolean.FALSE.equals(prompts.get(1).getEnabled()));
    }
}

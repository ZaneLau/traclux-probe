package com.geoprobe.config;

import com.geoprobe.ai.BrandAliasAiService;
import com.geoprobe.ai.BrandMentionValidationAiService;
import com.geoprobe.ai.CreativeContextAiService;
import com.geoprobe.ai.CssmHiStateSpaceAiService;
import com.geoprobe.ai.IntentClusterAiService;
import com.geoprobe.ai.ParsingAiService;
import com.geoprobe.ai.ProbeRenderingAiService;
import com.geoprobe.ai.PromptMatrixAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class AiServiceConfigTest {

    @Autowired
    private IntentClusterAiService intentClusterAiService;

    @Autowired
    private CreativeContextAiService creativeContextAiService;

    @Autowired
    private CssmHiStateSpaceAiService cssmHiStateSpaceAiService;

    @Autowired
    private PromptMatrixAiService promptMatrixAiService;

    @Autowired
    private ProbeRenderingAiService probeRenderingAiService;

    @Autowired
    private ParsingAiService parsingAiService;

    @Autowired
    private BrandAliasAiService brandAliasAiService;

    @Autowired
    private BrandMentionValidationAiService brandMentionValidationAiService;

    @Test
    void shouldCreateAiServiceBeans() {
        assertNotNull(intentClusterAiService);
        assertNotNull(creativeContextAiService);
        assertNotNull(cssmHiStateSpaceAiService);
        assertNotNull(promptMatrixAiService);
        assertNotNull(probeRenderingAiService);
        assertNotNull(parsingAiService);
        assertNotNull(brandAliasAiService);
        assertNotNull(brandMentionValidationAiService);
    }
}

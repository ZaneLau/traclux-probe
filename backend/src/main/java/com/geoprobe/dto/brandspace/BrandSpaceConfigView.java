package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceConfigView {

    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private Integer configGeneration;
    private List<TopicView> topics;
    private List<PromptView> prompts;
    private RunConfigView runConfig;
    private SummaryView summary;
    private ConfigCapabilitiesView capabilities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicView {
        private String topicId;
        private String name;
        private String description;
        private Integer seqNo;
        private Boolean enabled;
        private String source;
        private Integer promptCount;
        private Integer enabledPromptCount;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromptView {
        private String promptId;
        private String topicId;
        private String topicName;
        private Integer seqNo;
        private String text;
        private Boolean enabled;
        private String source;
        private String regionCode;
        private String locale;
        private String language;
        private String createdAt;
        private String updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryView {
        private Integer topicCount;
        private Integer enabledTopicCount;
        private Integer promptCount;
        private Integer enabledPromptCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunConfigView {
        private Integer targetProbeCount;
        private String targetModelProduct;
        private Boolean autoRunEnabled;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigCapabilitiesView {
        private Boolean canRead;
        private Boolean canCreateTopic;
        private Boolean canUpdateTopic;
        private Boolean canReorderTopic;
        private Boolean canCreatePrompt;
        private Boolean canUpdatePrompt;
        private Boolean canReorderPrompt;
        private Boolean canRegenerateSystemConfig;
        private Boolean regenerationRecommended;
        private Boolean tagTruthObjectSupported;
    }
}

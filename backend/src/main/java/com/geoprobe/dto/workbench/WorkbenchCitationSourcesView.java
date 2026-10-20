package com.geoprobe.dto.workbench;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchCitationSourcesView {

    private WorkbenchBrandIdentityView identity;
    private String brandSpaceId;
    private String brandId;
    private String brandName;
    private String category;
    private String officialWebsite;
    private String primaryDomain;
    private String brandSpaceStatus;
    private String latestRunId;
    private String latestReportId;
    private String lastGeneratedAt;
    private WorkbenchAnalysisContextView analysisContext;
    private WorkbenchDataAvailabilityView dataAvailability;
    private SourceSummaryView summary;
    private List<CategorySliceView> categorySlices;
    private List<DomainRowView> domainRows;
    private List<UrlRowView> urlRows;
    private List<TrendPointView> trendPoints;
    private OwnershipView ownership;
    private List<DomainDetailRowView> domainDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceSummaryView {
        private Integer totalCitations;
        private Double ownedShare;
        private Double thirdPartyShare;
        private Double competitorShare;
        private String dominantLabel;
        private Double dominantShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySliceView {
        private String key;
        private String label;
        private Double share;
        private Integer used;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainRowView {
        private String id;
        private String domain;
        private String type;
        private Integer used;
        private Double avgCitations;
        private Double share;
        private Double delta;
        private String url;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrlRowView {
        private String id;
        private String title;
        private String url;
        private String domain;
        private String type;
        private Integer used;
        private Double avgCitations;
        private Double share;
        private Double delta;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPointView {
        private String sliceKey;
        private String label;
        private String generatedAt;
        private Integer totalCitations;
        private Double ownedShare;
        private Double thirdPartyShare;
        private Double competitorShare;
        private Boolean isLatest;
        private List<TrendDomainSliceView> topDomains;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDomainSliceView {
        private String domain;
        private Integer used;
        private Double share;
        private String type;
        private String ownershipKey;
        private String ownershipLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnershipView {
        private OwnershipSummaryView summary;
        private List<OwnershipSliceView> slices;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnershipSummaryView {
        private Double ownedShare;
        private Double thirdPartyShare;
        private Double competitorShare;
        private Double otherShare;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnershipSliceView {
        private String key;
        private String label;
        private Double share;
        private Integer used;
        private Integer domainCount;
        private Integer urlCount;
        private List<OwnershipDomainRowView> topDomains;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OwnershipDomainRowView {
        private String domain;
        private Integer used;
        private Double share;
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DomainDetailRowView {
        private String domainId;
        private String domain;
        private String type;
        private String ownershipKey;
        private String ownershipLabel;
        private Integer used;
        private Double avgCitations;
        private Double share;
        private Double delta;
        private Integer urlCount;
        private List<UrlDetailRowView> urls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UrlDetailRowView {
        private String urlId;
        private String url;
        private String title;
        private Integer used;
        private Double share;
        private Double delta;
    }
}

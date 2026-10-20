package com.geoprobe.dto.request;

import com.geoprobe.dto.brandspace.BrandSpaceDraftPayload;
import com.geoprobe.dto.brandspace.BrandSpaceRecognitionView;
import com.geoprobe.dto.brandspace.BrandSpaceTopicPayload;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BrandSpacePreviewRequest {

    @Size(max = 200, message = "brandName 长度不能超过200")
    private String brandName;

    @Size(max = 200, message = "category 长度不能超过200")
    private String category;

    @Size(max = 500, message = "officialWebsite 长度不能超过500")
    private String officialWebsite;

    private List<String> competitors;

    @Size(max = 1000, message = "supplementalBrief 长度不能超过1000")
    private String supplementalBrief;

    @Size(max = 16, message = "regionCode 长度不能超过16")
    private String regionCode;

    @Size(max = 32, message = "locale 长度不能超过32")
    private String locale;

    @Size(max = 32, message = "language 长度不能超过32")
    private String language;

    @Size(max = 64, message = "idempotencyKey 长度不能超过64")
    private String idempotencyKey;

    private BrandSpaceRecognitionView recognition;

    private List<BrandSpaceTopicPayload> topics;

    public BrandSpaceDraftPayload toDraft() {
        return BrandSpaceDraftPayload.builder()
                .brandName(brandName)
                .category(category)
                .officialWebsite(officialWebsite)
                .regionCode(regionCode)
                .locale(locale)
                .language(language)
                .competitors(competitors)
                .supplementalBrief(supplementalBrief)
                .build();
    }
}

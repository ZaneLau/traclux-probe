package com.geoprobe.dto.brandspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandSpaceDraftPayload {

    @NotBlank(message = "brandName 不能为空")
    @Size(max = 200, message = "brandName 长度不能超过200")
    private String brandName;

    @NotBlank(message = "category 不能为空")
    @Size(max = 200, message = "category 长度不能超过200")
    private String category;

    @Size(max = 500, message = "officialWebsite 长度不能超过500")
    private String officialWebsite;

    @Size(max = 16, message = "regionCode 长度不能超过16")
    private String regionCode;

    @Size(max = 32, message = "locale 长度不能超过32")
    private String locale;

    @Size(max = 32, message = "language 长度不能超过32")
    private String language;

    private List<String> competitors;

    @Size(max = 1000, message = "supplementalBrief 长度不能超过1000")
    private String supplementalBrief;
}

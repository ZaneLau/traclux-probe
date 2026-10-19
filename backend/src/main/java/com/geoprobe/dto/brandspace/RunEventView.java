package com.geoprobe.dto.brandspace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunEventView {

    private Long seq;
    private String type;
    private Boolean provisional;
    private String createdAt;
    private Map<String, Object> payload;
}

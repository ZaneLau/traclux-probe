package com.geoprobe.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackingResponse {

    private String trackingId;
    private String brandName;
    private String edition;
    private String interval;
    private LocalDateTime nextRunAt;
    private String status;
}

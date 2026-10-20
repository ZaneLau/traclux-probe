package com.geoprobe.dto.request;

import lombok.Data;

@Data
public class UpdateTrackingRequest {

    private String interval;
    private String status;
}

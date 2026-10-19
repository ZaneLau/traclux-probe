package com.geoprobe.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.bootstrap.seed-user")
public class SeedUserProperties {

    private boolean enabled = false;
    private String username;
    private String password;
    private String displayName;
    private String companyName = "AeoProbe Alpha";
    private Integer creditsRemaining = 20;
    private Integer role = 0;
}

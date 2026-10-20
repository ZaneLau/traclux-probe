package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("model_price_catalog")
public class ModelPriceCatalog {

    @TableId
    private String id;

    private String provider;
    private String modelId;
    private String endpointRef;
    private BigDecimal inputYuanPer1kToken;
    private BigDecimal outputYuanPer1kToken;
    private BigDecimal pluginYuanPerCall;
    private String priceVersion;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private LocalDateTime createdAt;
}

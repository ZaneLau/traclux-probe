package com.geoprobe.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PurchaseCreditsRequest {

    @NotNull(message = "amount 不能为空")
    @Min(value = 1, message = "amount 必须大于0")
    private Integer amount;

    @NotBlank(message = "paymentMethod 不能为空")
    private String paymentMethod;
}

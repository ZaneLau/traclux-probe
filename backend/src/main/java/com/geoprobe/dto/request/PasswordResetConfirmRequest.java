package com.geoprobe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {

    @NotBlank(message = "重置令牌不能为空")
    @Size(max = 200, message = "重置令牌长度不能超过200")
    private String token;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度必须在8到100位之间")
    private String password;
}

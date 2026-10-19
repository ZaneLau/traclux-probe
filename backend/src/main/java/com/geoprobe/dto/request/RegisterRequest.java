package com.geoprobe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "用户名长度不能超过100")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 100, message = "密码长度必须在8到100位之间")
    private String password;

    @Size(max = 100, message = "显示名长度不能超过100")
    private String displayName;

    @Size(max = 200, message = "公司名长度不能超过200")
    private String companyName;

    @Size(max = 2048, message = "验证令牌长度不能超过2048")
    private String turnstileToken;
}

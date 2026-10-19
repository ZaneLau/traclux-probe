package com.geoprobe.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class User {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String username;
    private String password;
    private String displayName;
    private String companyName;
    private String defaultOrganizationId;
    private Integer creditsRemaining;
    // 0=USER, 1=ADMIN
    private Integer role;
    // 0=DISABLED, 1=ENABLED
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

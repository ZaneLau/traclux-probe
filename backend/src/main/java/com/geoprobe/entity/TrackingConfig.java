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
@TableName("tracking_configs")
public class TrackingConfig {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;
    private String brandName;
    // 0=EXPERIENCE, 1=STANDARD, 2=DEEP
    private Integer edition;
    // 1=WEEKLY, 2=BIWEEKLY, 3=MONTHLY
    private Integer intervalType;
    // 1=ACTIVE, 2=PAUSED, 3=CANCELLED
    private Integer status;
    private LocalDateTime nextRunAt;
    private LocalDateTime lastRunAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

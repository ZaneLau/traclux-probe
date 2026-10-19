package com.geoprobe.entity.v1;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "migration_legacy_keymap", autoResultMap = true)
public class MigrationLegacyKeymap {

    @TableId
    private String id;

    private String legacyTableName;
    private String legacyId;
    private String newTableName;
    private String newId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadataJson;

    private LocalDateTime createdAt;
}

package com.geoprobe.mapper.v1;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.geoprobe.entity.v1.DiagnosisTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DiagnosisTaskMapper extends BaseMapper<DiagnosisTask> {

    @Select("""
            <script>
            SELECT id,
                   organization_id,
                   brand_id,
                   brand_scope_id,
                   status_code,
                   brand_name_snapshot,
                   category_snapshot,
                   official_website_snapshot,
                   task_order_json,
                   created_at,
                   updated_at
            FROM (
                SELECT id,
                       organization_id,
                       brand_id,
                       brand_scope_id,
                       status_code,
                       brand_name_snapshot,
                       category_snapshot,
                       official_website_snapshot,
                       task_order_json,
                       created_at,
                       updated_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY brand_scope_id
                           ORDER BY created_at DESC, updated_at DESC, id DESC
                       ) AS rn
                FROM diagnosis_tasks
                WHERE brand_scope_id IN
                <foreach collection='brandScopeIds' item='brandScopeId' open='(' separator=',' close=')'>
                    #{brandScopeId}
                </foreach>
            ) latest_tasks
            WHERE rn = 1
            </script>
            """)
    @Results({
            @Result(property = "taskOrderJson", column = "task_order_json", typeHandler = JacksonTypeHandler.class)
    })
    List<DiagnosisTask> selectLatestByBrandScopeIds(@Param("brandScopeIds") List<String> brandScopeIds);
}

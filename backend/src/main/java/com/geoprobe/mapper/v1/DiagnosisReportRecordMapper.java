package com.geoprobe.mapper.v1;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.geoprobe.entity.v1.DiagnosisReportRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DiagnosisReportRecordMapper extends BaseMapper<DiagnosisReportRecord> {

    @Results(id = "diagnosisReportHeadMap", value = {
            @Result(property = "summaryJson", column = "summary_json", typeHandler = JacksonTypeHandler.class)
    })
    @Select("""
            SELECT id,
                   diagnosis_task_id,
                   brand_scope_id,
                   brand_id,
                   category_snapshot,
                   target_model_product,
                   report_status_code,
                   sample_size,
                   spontaneous_mention_rate,
                   confirmation_response_rate,
                   defense_completeness,
                   citation_rate,
                   abv_index,
                   abv_grade,
                   summary_json,
                   generated_at,
                   created_at
            FROM diagnosis_reports
            WHERE organization_id = #{organizationId}
              AND brand_scope_id = #{brandScopeId}
            ORDER BY generated_at DESC, created_at DESC, id DESC
            LIMIT 1
            """)
    DiagnosisReportRecord selectLatestHeadByBrandScopeId(@Param("organizationId") String organizationId,
                                                         @Param("brandScopeId") String brandScopeId);

    @Results(id = "diagnosisReportPayloadMap", value = {
            @Result(property = "dimensionPenetrationJson", column = "dimension_penetration_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "brandDriftDetailsJson", column = "brand_drift_details_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "pathologyLabelsJson", column = "pathology_labels_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "topInsightsJson", column = "top_insights_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "summaryJson", column = "summary_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "chartsJson", column = "charts_json", typeHandler = JacksonTypeHandler.class),
            @Result(property = "reportProjectionJson", column = "report_projection_json", typeHandler = JacksonTypeHandler.class)
    })
    @Select("""
            SELECT id,
                   diagnosis_task_id,
                   organization_id,
                   brand_scope_id,
                   brand_id,
                   category_snapshot,
                   target_model_product,
                   report_status_code,
                   sample_size,
                   spontaneous_mention_rate,
                   confirmation_response_rate,
                   defense_completeness,
                   citation_rate,
                   abv_index,
                   abv_grade,
                   dimension_penetration_json,
                   brand_drift_score,
                   brand_drift_details_json,
                   pathology_labels_json,
                   top_insights_json,
                   summary_json,
                   charts_json,
                   report_projection_json,
                   generated_at,
                   created_at
            FROM diagnosis_reports
            WHERE id = #{id}
            LIMIT 1
            """)
    DiagnosisReportRecord selectPayloadById(@Param("id") String id);

    @ResultMap("diagnosisReportHeadMap")
    @Select("""
            <script>
            SELECT id,
                   diagnosis_task_id,
                   brand_scope_id,
                   brand_id,
                   category_snapshot,
                   target_model_product,
                   report_status_code,
                   sample_size,
                   spontaneous_mention_rate,
                   confirmation_response_rate,
                   defense_completeness,
                   citation_rate,
                   abv_index,
                   abv_grade,
                   summary_json,
                   generated_at,
                   created_at
            FROM (
                SELECT id,
                       diagnosis_task_id,
                       brand_scope_id,
                       brand_id,
                       category_snapshot,
                       target_model_product,
                       report_status_code,
                       sample_size,
                       spontaneous_mention_rate,
                       confirmation_response_rate,
                       defense_completeness,
                       citation_rate,
                       abv_index,
                       abv_grade,
                       summary_json,
                       generated_at,
                       created_at,
                       ROW_NUMBER() OVER (
                           PARTITION BY brand_scope_id
                           ORDER BY generated_at DESC, created_at DESC, id DESC
                       ) AS rn
                FROM diagnosis_reports
                WHERE organization_id = #{organizationId}
                  AND brand_scope_id IN
                <foreach collection='brandScopeIds' item='brandScopeId' open='(' separator=',' close=')'>
                    #{brandScopeId}
                </foreach>
            ) latest_reports
            WHERE rn = 1
            </script>
            """)
    List<DiagnosisReportRecord> selectLatestHeadsByBrandScopeIds(@Param("organizationId") String organizationId,
                                                                 @Param("brandScopeIds") List<String> brandScopeIds);

    @ResultMap("diagnosisReportHeadMap")
    @Select("""
            SELECT id,
                   diagnosis_task_id,
                   brand_scope_id,
                   brand_id,
                   category_snapshot,
                   target_model_product,
                   report_status_code,
                   sample_size,
                   spontaneous_mention_rate,
                   confirmation_response_rate,
                   defense_completeness,
                   citation_rate,
                   abv_index,
                   abv_grade,
                   summary_json,
                   generated_at,
                   created_at
            FROM diagnosis_reports
            WHERE brand_scope_id = #{brandScopeId}
            ORDER BY generated_at DESC, created_at DESC, id DESC
            LIMIT 1
            """)
    DiagnosisReportRecord selectLatestWorkbenchHeadByBrandScopeId(@Param("brandScopeId") String brandScopeId);

    @ResultMap("diagnosisReportHeadMap")
    @Select("""
            SELECT id,
                   diagnosis_task_id,
                   brand_scope_id,
                   brand_id,
                   category_snapshot,
                   target_model_product,
                   report_status_code,
                   sample_size,
                   spontaneous_mention_rate,
                   confirmation_response_rate,
                   defense_completeness,
                   citation_rate,
                   abv_index,
                   abv_grade,
                   summary_json,
                   generated_at,
                   created_at
            FROM diagnosis_reports
            WHERE brand_scope_id = #{brandScopeId}
            ORDER BY generated_at DESC, created_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<DiagnosisReportRecord> selectRecentWorkbenchHeadsByBrandScopeId(@Param("brandScopeId") String brandScopeId,
                                                                         @Param("limit") int limit);

    @Select("""
            SELECT id,
                   diagnosis_task_id,
                   brand_scope_id,
                   generated_at,
                   created_at
            FROM diagnosis_reports
            WHERE brand_scope_id = #{brandScopeId}
            ORDER BY COALESCE(generated_at, created_at) ASC, id ASC
            """)
    List<DiagnosisReportRecord> selectHistoryRefsByBrandScopeId(@Param("brandScopeId") String brandScopeId);

    @Select("""
            SELECT DISTINCT brand_scope_id
            FROM diagnosis_reports
            WHERE brand_scope_id IS NOT NULL
              AND brand_scope_id <> ''
            ORDER BY brand_scope_id ASC
            """)
    List<String> selectDistinctBrandScopeIdsWithReports();
}

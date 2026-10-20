package com.geoprobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geoprobe.entity.LlmCallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface LlmCallLogMapper extends BaseMapper<LlmCallLog> {

    @Select("""
            SELECT COALESCE(SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)), 0)
            FROM llm_call_logs
            WHERE model = #{modelId}
              AND created_at >= #{startAt}
              AND created_at < #{endAt}
              AND module <> #{excludedModule}
            """)
    Long sumTokensByModelExcludingModuleBetween(@Param("modelId") String modelId,
                                                @Param("excludedModule") int excludedModule,
                                                @Param("startAt") LocalDateTime startAt,
                                                @Param("endAt") LocalDateTime endAt);

    @Select("""
            SELECT COALESCE(SUM(COALESCE(input_tokens, 0) + COALESCE(output_tokens, 0)), 0)
            FROM llm_call_logs
            WHERE model = #{modelId}
              AND created_at >= #{startAt}
              AND created_at < #{endAt}
              AND module = #{module}
            """)
    Long sumTokensByModelAndModuleBetween(@Param("modelId") String modelId,
                                          @Param("module") int module,
                                          @Param("startAt") LocalDateTime startAt,
                                          @Param("endAt") LocalDateTime endAt);
}

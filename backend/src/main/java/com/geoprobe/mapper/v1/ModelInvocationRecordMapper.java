package com.geoprobe.mapper.v1;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geoprobe.entity.v1.ModelInvocationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

@Mapper
public interface ModelInvocationRecordMapper extends BaseMapper<ModelInvocationRecord> {

    @Select("""
            SELECT COALESCE(SUM(COALESCE(total_tokens, 0)), 0)
            FROM model_invocations
            WHERE model_id = #{modelId}
              AND node_code = #{nodeCode}
              AND created_at >= #{startAt}
              AND created_at < #{endAt}
            """)
    Long sumTotalTokensByModelAndNodeBetween(@Param("modelId") String modelId,
                                             @Param("nodeCode") String nodeCode,
                                             @Param("startAt") LocalDateTime startAt,
                                             @Param("endAt") LocalDateTime endAt);
}

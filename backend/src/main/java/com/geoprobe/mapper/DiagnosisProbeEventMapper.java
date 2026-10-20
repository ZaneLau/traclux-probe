package com.geoprobe.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geoprobe.entity.DiagnosisProbeEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DiagnosisProbeEventMapper extends BaseMapper<DiagnosisProbeEvent> {

    int insertBatch(@Param("events") List<DiagnosisProbeEvent> events);
}

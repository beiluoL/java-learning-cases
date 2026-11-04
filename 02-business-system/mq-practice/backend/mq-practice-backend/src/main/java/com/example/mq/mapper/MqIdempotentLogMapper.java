package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mq.entity.MqIdempotentLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 幂等日志 Mapper */
@Mapper
public interface MqIdempotentLogMapper extends BaseMapper<MqIdempotentLog> {

    @Select("SELECT * FROM mq_idempotent_log WHERE biz_key = #{bizKey}")
    MqIdempotentLog selectByBizKey(@Param("bizKey") String bizKey);

    @Select("SELECT COUNT(1) FROM mq_idempotent_log WHERE create_time >= #{from}")
    long countSince(@Param("from") LocalDateTime from);
}

package com.example.mq.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.mq.entity.MqMessageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 消息发送日志 Mapper */
@Mapper
public interface MqMessageLogMapper extends BaseMapper<MqMessageLog> {

    @Select("SELECT COUNT(1) FROM mq_message_log WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    @Select("SELECT COUNT(1) FROM mq_message_log WHERE create_time >= #{from}")
    long countSince(@Param("from") LocalDateTime from);
}

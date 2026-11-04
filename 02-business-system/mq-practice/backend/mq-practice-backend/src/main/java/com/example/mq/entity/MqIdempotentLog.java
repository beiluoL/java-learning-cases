package com.example.mq.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 幂等日志：biz_key 上有唯一索引，是「消费幂等」的最后一道防线。
 * 数据库唯一键的价值在于——它不受 Redis 是否可用、是否被 flush、消息是否跨机房重投的影响。
 */
@Data
@TableName("mq_idempotent_log")
public class MqIdempotentLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int STATUS_PROCESSING = 1;
    public static final int STATUS_SUCCESS = 2;
    public static final int STATUS_FAILED = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String bizKey;

    private String scene;

    private String messageId;

    private Integer status;

    private String payload;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

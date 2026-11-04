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
 * 消息发送日志（本地消息表雏形）。
 * 与 Publisher Confirm / Return 回调配合，把"消息到底有没有进 broker"这件事变成可查的数据。
 */
@Data
@TableName("mq_message_log")
public class MqMessageLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STATUS_SENDING = "SENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String messageId;

    private String exchangeName;

    private String routingKey;

    private String scene;

    private String bizKey;

    private String payload;

    private String status;

    private Integer retryCount;

    private String errorMsg;

    private Long costMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

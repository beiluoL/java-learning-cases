package com.finpay.business.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道配置表。对应 {@code ChannelConfigProvider} 的数据源。
 * 私钥/证书等敏感信息应加密存储（本表示例以 ciphertext 字段示意，生产接 KMS）。
 */
@Data
@TableName("t_channel_config")
public class ChannelConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 渠道编码 */
    private String channel;
    private String merchantNo;
    private String appId;
    /** 是否启用 */
    private Boolean enabled;
    /** 配置参数 JSON（私钥、公钥、证书路径、APIv3Key 等，加密存储） */
    private String configJson;
    private Boolean sandbox;
    private LocalDateTime createTime;
}

package com.finpay.payment.core.model.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 渠道运行配置。由 {@code ChannelConfigProvider} 按「商户 + 渠道」提供，
 * 包含 appId、商户号与渠道私有参数（私钥、公钥证书路径、网关地址等）。
 * 采用 params 透传，便于扩展新渠道而无需改动接口定义。
 */
@Data
public class ChannelConfig {

    private String channel;
    private String merchantNo;
    private String appId;
    private Map<String, String> params = new HashMap<>();

    public String get(String key) {
        return params.get(key);
    }

    public String getOrDefault(String key, String def) {
        return params.getOrDefault(key, def);
    }

    /** 是否沙箱环境 */
    public boolean isSandbox() {
        return Boolean.parseBoolean(params.getOrDefault("sandbox", "false"));
    }
}

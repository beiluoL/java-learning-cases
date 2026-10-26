package com.finpay.business.infrastructure.impl;

import com.alibaba.fastjson2.JSON;
import com.finpay.business.infrastructure.entity.ChannelConfigEntity;
import com.finpay.business.infrastructure.mapper.ChannelConfigMapper;
import com.finpay.payment.core.api.ChannelConfigProvider;
import com.finpay.payment.core.model.dto.ChannelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 渠道配置提供方实现。从数据库 {@code t_channel_config} 加载运行参数。
 * 生产环境中 configJson 应加密存储，此处读取后直接反序列化为 params（演示用）。
 */
@Slf4j
@Component
public class DbChannelConfigProvider implements ChannelConfigProvider {

    private final ChannelConfigMapper mapper;

    public DbChannelConfigProvider(ChannelConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ChannelConfig getConfig(String channel, String merchantNo) {
        ChannelConfigEntity e = mapper.selectOne(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ChannelConfigEntity>lambdaQuery()
                        .eq(ChannelConfigEntity::getChannel, channel)
                        .eq(ChannelConfigEntity::getMerchantNo, merchantNo)
                        .eq(ChannelConfigEntity::getEnabled, true));
        if (e == null) return null;
        ChannelConfig cfg = new ChannelConfig();
        cfg.setChannel(e.getChannel());
        cfg.setMerchantNo(e.getMerchantNo());
        cfg.setAppId(e.getAppId());
        Map<String, String> params = JSON.parseObject(e.getConfigJson(), Map.class);
        cfg.setParams(params);
        if (e.getSandbox() != null) cfg.getParams().put("sandbox", String.valueOf(e.getSandbox()));
        return cfg;
    }

    @Override
    public List<String> listChannels() {
        List<String> list = new ArrayList<>();
        // 简化：返回已配置渠道的去重集合
        mapper.selectList(null).forEach(e -> { if (!list.contains(e.getChannel())) list.add(e.getChannel()); });
        return list;
    }
}

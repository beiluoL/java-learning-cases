package com.example.videostream.config;

import com.example.videostream.properties.TranscodeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Yaml 中的转码档位配置暴露为易查询的结构：
 * 1) 按档位名索引的 Map（快速命中 1080p/720p...）
 * 2) 按 tier 升序的 List（ABR 自适应由高到低选择）
 */
@Configuration
public class TranscodeProfileConfig {

    private final TranscodeProperties transcodeProperties;

    public TranscodeProfileConfig(TranscodeProperties transcodeProperties) {
        this.transcodeProperties = transcodeProperties;
    }

    @Bean
    public Map<String, TranscodeProperties.TranscodeProfile> transcodeProfileMap() {
        Map<String, TranscodeProperties.TranscodeProfile> map = new LinkedHashMap<>();
        for (TranscodeProperties.TranscodeProfile p : transcodeProperties.getProfiles()) {
            map.put(p.getName(), p);
        }
        return map;
    }

    @Bean
    public List<TranscodeProperties.TranscodeProfile> transcodeProfileOrdered() {
        // 按 tier 升序（1 最高清晰度在前），播放端优先尝试高清晰度
        List<TranscodeProperties.TranscodeProfile> list = new java.util.ArrayList<>(
                transcodeProperties.getProfiles());
        list.sort(java.util.Comparator.comparingInt(TranscodeProperties.TranscodeProfile::getTier));
        return list;
    }
}

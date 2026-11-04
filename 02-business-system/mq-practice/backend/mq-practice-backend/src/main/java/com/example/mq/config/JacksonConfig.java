package com.example.mq.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局 Jackson 配置。
 * <p>
 * 统一 {@code LocalDateTime} 的序列化格式为 {@code yyyy-MM-dd HH:mm:ss}：
 * 前端 el-table 直接展示，不需要在每个字段上写 @JsonFormat；
 * 同时保证 MQ 消息体（同样走 Jackson）的时间格式与 HTTP 接口一致。
 */
@Configuration
public class JacksonConfig {

    public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        return builder -> {
            builder.serializerByType(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
            builder.deserializerByType(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
            // 不要输出时间戳数组（默认会把 LocalDateTime 写成 [2026,11,4,...]）
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            // 空值不输出，前端可选链判断更简单
            builder.failOnUnknownProperties(false);
        };
    }
}

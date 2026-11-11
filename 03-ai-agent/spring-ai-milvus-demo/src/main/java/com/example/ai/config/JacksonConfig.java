package com.example.ai.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 前端按 ISO 8601 UTC（带 Z）提交时间，后端实体用 LocalDateTime 存 UTC。
 * 这里做兼容反序列化，避免「带 Z 就 400」的联调问题；序列化仍走标准 ISO。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule flexibleLocalDateTimeModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                String value = p.getValueAsString();
                if (value == null || value.isBlank()) {
                    return null;
                }
                try {
                    return Instant.parse(value).atZone(ZoneOffset.UTC).toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
                }
            }
        });
        return module;
    }
}

package com.hic.config;

import com.hic.util.AppTimeZone;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class JacksonTimeZoneConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonTimeZoneCustomizer() {
        return builder -> {
            builder.timeZone(TimeZone.getTimeZone(AppTimeZone.ZONE));
            builder.modules(new JavaTimeModule());
        };
    }
}

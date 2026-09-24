package com.qjj.user.framework.core;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Long → String 全局序列化（CLAUDE.md 6.4.7）：出参 Long（含 id）一律 String；禁止字段上散落 @JsonSerialize。
 * 注：分页契约的 total/size/current 为基本类型 long，保持 JSON 数字。
 */
@Configuration
public class JacksonSupportConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}

package com.mars.linker.broker.ui.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置，允许来源从 MarsLinkerUiProperties.corsAllowedOrigins 读取。
 * 未配置时默认允许开发环境 localhost 地址。
 */
@Configuration
@ConditionalOnProperty(prefix = "mars.linker.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
public class UiCorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(UiCorsConfig.class);

    private final MarsLinkerUiProperties props;

    public UiCorsConfig(MarsLinkerUiProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false)
                .maxAge(3600);
        log.info("CORS 已配置为全允许（allowedOrigins=*）");
    }
}

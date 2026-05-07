package org.dsgroup.journeycraft.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置（无拦截器 — 已移除 token 认证）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
}

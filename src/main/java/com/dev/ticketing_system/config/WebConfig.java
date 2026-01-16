package com.dev.ticketing_system.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // static 폴더 안의 리소스들을 허용
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // RateLimitInterceptor 등록
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**", "/concerts/**") // 보호할 경로 (API + 화면)
                .excludePathPatterns("/css/**", "/js/**", "/images/**", "/favicon.ico", "/error"); // 제외 경로
    }
}
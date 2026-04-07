package com.example.demo.studentbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 🔧 GLOBAL CORS Configuration
 * 
 * Mục đích:
 *  - Cho phép frontend từ bất kỳ domain nào gọi tới backend API
 *  - Apply cho tất cả endpoints (bao gồm /api/students/* và con đường con)
 * 
 * Cấu hình:
 *  - allowedOrigins: "*" → cho phép tất cả domain
 *  - allowedMethods: GET, POST, PUT, DELETE, OPTIONS → các HTTP method
 *  - allowedHeaders: "*" → cho phép tất cả headers
 *  - allowCredentials: true → cho phép gửi credentials (cookies, auth headers)
 *  - maxAge: 3600 → cache CORS preflight 1 giờ
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // Apply cho tất cả paths
                .allowedOrigins("*")  // Allow from any origin
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .maxAge(3600);  // Cache preflight requests for 1 hour
    }
}

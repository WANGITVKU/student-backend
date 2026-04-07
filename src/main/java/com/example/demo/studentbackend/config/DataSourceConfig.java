package com.example.demo.studentbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
@EnableScheduling
public class DataSourceConfig {

    // ============================================
    // ✅ SECONDARY DATASOURCE (Railway)
    // ============================================

    @Value("${secondary.datasource.url}")
    private String secondaryUrl;

    @Value("${secondary.datasource.username}")
    private String secondaryUsername;

    @Value("${secondary.datasource.password}")
    private String secondaryPassword;

    @Value("${secondary.datasource.driver-class-name:org.postgresql.Driver}")
    private String secondaryDriver;

    @Bean(name = "secondaryDataSource")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create()
                .url(secondaryUrl)
                .username(secondaryUsername)
                .password(secondaryPassword)
                .driverClassName(secondaryDriver)
                .build();
    }

    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate() {
        return new JdbcTemplate(secondaryDataSource());
    }

    // ============================================
    // ✅ ASYNC EXECUTOR
    // ============================================

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("student-service-");
        executor.initialize();
        return executor;
    }
}
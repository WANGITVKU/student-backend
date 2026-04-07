package com.example.demo.studentbackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.util.concurrent.Executor;

/**
 * ⚙️ CONFIG: DataSourceConfig
 * 
 * Mục đích:
 *  - Cấu hình kết nối database (Primary + Secondary)
 *  - Tạo các Bean cho Dependency Injection
 *  - Kích hoạt @Async và @Scheduled
 * 
 * Kiến trúc:
 *  ┌──────────────────┐
 *  │ application.properties │ (Chứa URL credentials)
 *      │
 *  ┌───▼─────────────┐
 *  │ DataSourceConfig │ (Đọc config và tạo Bean)
 *      │
 *  ├──┬─────────────────────┐
 *  │  │                      │
 *  ▼  ▼                      ▼
 * Render PostgreSQL      Railway PostgreSQL
 * (Primary)              (Secondary)
 * 
 * Annotation:
 *  - @Configuration: Đánh dấu đây là Spring config class
 *  - @EnableAsync: Cho phép dùng @Async (chạy background)
 *  - @EnableScheduling: Cho phép dùng @Scheduled (chạy định kỳ)
 *  - @Primary: Ưu tiên bean này khi có nhiều cùng loại
 *  - @Bean: Đăng ký thành Bean để Spring quản lý
 *  - @Value: Đọc giá trị từ application.properties
 */
@Configuration
@EnableAsync        // Kích hoạt @Async trong StudentService
@EnableScheduling   // Kích hoạt @Scheduled trong StudentService
public class DataSourceConfig {

    // ============================================
    // ✅ PRIMARY DATASOURCE (Render PostgreSQL)
    // ============================================

    // 📖 Đọc từ application.properties
    @Value("${spring.datasource.url}")
    private String primaryUrl;
    @Value("${spring.datasource.username}")
    private String primaryUsername;
    @Value("${spring.datasource.password}")
    private String primaryPassword;
    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String primaryDriver;

    /**
     * 🏢 Tạo DataSource Bean cho Primary DB (Render)
     * @Primary: Khi Spring cần DataSource và không chỉ định, lấy cái này
     * Dùng bởi: Spring Data JPA (studentRepository)
     */
    @Bean(name = "primaryDatasource")
    @Primary
    public DataSource primaryDatasource() {
        return DataSourceBuilder.create()
                .url(primaryUrl)
                .username(primaryUsername)
                .password(primaryPassword)
                .driverClassName(primaryDriver)
                .build();
    }

    /**
     * 🔨 Tạo JdbcTemplate Bean cho Primary DB
     * Dùng bởi: 
     *  - StudentService (direct SQL queries)
     *  - DiagnosticService (table inspection)
     */
    @Bean(name = "primaryJdbc")
    @Primary
    public JdbcTemplate primaryJdbcTemplate() {
        return new JdbcTemplate(primaryDatasource());
    }

    // ============================================
    // ✅ SECONDARY DATASOURCE (Railway PostgreSQL)
    // ============================================

    // 📖 Đọc từ application.properties
    @Value("${secondary.datasource.url}")
    private String secondaryUrl;
    @Value("${secondary.datasource.username}")
    private String secondaryUsername;
    @Value("${secondary.datasource.password}")
    private String secondaryPassword;
    @Value("${secondary.datasource.driver-class-name:org.postgresql.Driver}")
    private String secondaryDriver;

    /**
     * 🏢 Tạo DataSource Bean cho Secondary DB (Railway)
     * Không dùng @Primary vì đó là DB phụ
     */
    @Bean(name = "secondaryDataSource")
    public DataSource secondaryDataSource() {
        return DataSourceBuilder.create()
                .url(secondaryUrl)
                .username(secondaryUsername)
                .password(secondaryPassword)
                .driverClassName(secondaryDriver)
                .build();
    }

    /**
     * 🔨 Tạo JdbcTemplate Bean cho Secondary DB
     * Dùng bởi:
     *  - StudentService (dual-write + fallover)
     *  - DiagnosticService (data consistency check)
     */
    @Bean(name = "secondaryJdbcTemplate")
    public JdbcTemplate secondaryJdbcTemplate() {
        return new JdbcTemplate(secondaryDataSource());
    }

    // ============================================
    // ✅ ASYNC EXECUTOR (ThreadPool cho @Async)
    // ============================================

    /**
     * 🔄 Tạo ThreadPool cho chạy @Async tasks
     * 
     * Cấu hình:
     *  - corePoolSize = 2: Số thread chờ sẵn
     *  - maxPoolSize = 5: Tối đa 5 thread đồng thời
     *  - queueCapacity = 100: Chờ tối đa 100 tasks
     * 
     * Dùng bởi:
     *  - StudentService.createStudent() → async secondary write
     *  - StudentService.syncToSecondary() → async data sync
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // Luôn có 2 thread chạy
        executor.setMaxPoolSize(5);        // Tối đa tạo 5 thread
        executor.setQueueCapacity(100);    // Queue chờ 100 task
        executor.setThreadNamePrefix("student-service-");
        executor.initialize();
        return executor;
    }
}
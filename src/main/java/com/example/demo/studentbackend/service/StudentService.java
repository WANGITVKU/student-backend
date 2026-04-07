package com.example.demo.studentbackend.service;

import com.example.demo.studentbackend.model.Student;
import com.example.demo.studentbackend.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🧠 SERVICE: StudentService
 * 
 * Mục đích:
 *  - Chứa logic nghiệp vụ (business logic) chính
 *  - Xử lý CRUD operations cho sinh viên
 *  - Quản lý dual-database (Render primary + Railway secondary)
 *  - Tự động sync dữ liệu giữa 2 database
 * 
 * Kiến trúc Dual-Database:
 *  ┌─────────────────────┐
 *  │   Controller        │ (API Endpoints)
 *  └──────────┬──────────┘
 *             │
 *  ┌──────────▼──────────┐
 *  │  StudentService     │ (Logic + Failover)
 *  └──┬────────────────┬─┘
 *     │                │
 *  ┌──▼────┐      ┌───▼──┐
 *  │Render │      │Railway│ (Primary) (Secondary)
 *  │(Render)      │(Rail) │
 *  └─────────┘      └──────┘
 * 
 * Luồng dữ liệu:
 *  - Read: Render → (nếu down) → Railway
 *  - Write: Render + Railway (dual-write)
 *  - Sync: Periodic schedule every 5 seconds
 * 
 * Các componet chính:
 *  - studentRepository: JPA repository cho Render DB
 *  - primaryJdbc: Direct JDBC cho Render
 *  - secondaryJdbc: Direct JDBC cho Railway
 *  - Health tracking: primaryDbHealthy, secondaryDbHealthy
 *  - Sync status: syncInProgress
 */
@Service
public class StudentService {
    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    // 🏦 Repository - giao tiếp với Primary DB (Render)
    @Autowired
    private StudentRepository studentRepository;

    // 📋 JDBC template cho Secondary DB (Railway)
    private final JdbcTemplate secondaryJdbc;
    
    // ✅ JDBC template cho Primary DB (Render)
    private JdbcTemplate primaryJdbc;
    
    // Inject primary datasource từ DataSourceConfig
    @Autowired
    public void setPrimaryDataSource(DataSource primaryDataSource) {
        this.primaryJdbc = new JdbcTemplate(primaryDataSource);
    }
    
    // 🏥 Theo dõi sức khỏe của Render Database
    // true = healthy (kết nối tốt), false = down (kết nối mất)
    private final AtomicBoolean primaryDbHealthy = new AtomicBoolean(true);
    
    // 🏥 Theo dõi sức khỏe của Railway Database
    private final AtomicBoolean secondaryDbHealthy = new AtomicBoolean(true);
    
    // 🔄 Theo dõi trạng thái đồng bộ dữ liệu
    // true = đang sync (chờ xong), false = idle (sẵn sàng)
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    // Cấu hình có bật secondary DB hay không
    @Value("${app.secondary.enabled:true}")
    private boolean secondaryEnabled;
    
    // ⚙️ Retry configuration cho async writes
    private static final int MAX_RETRIES = 3;               // Thử lại 3 lần
    private static final int INITIAL_RETRY_DELAY_MS = 500;  // Chờ 500ms trước lần thử 1

    public StudentService(@Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        this.secondaryJdbc = new JdbcTemplate(secondaryDataSource);
    }

    // ============================================
    // ✅ KHỞI TẠO HỆ THỐNG (Initialization)
    // ============================================

    /**
     * 🚀 @PostConstruct: Chạy sau khi cấu hình xong, trước khi app sử dụng
     * Mục đích: Chuẩn bị Secondary DB (tạo table + sync dữ liệu)
     */
    @PostConstruct
    public void initializeSecondaryDb() {
        log.info("🔄 Initializing secondary database...");
        initializeSecondaryDbAsync();  // Chạy async để không block startup
    }

    /**
     * 🔄 @Async: Chạy trong background thread
     * Mục đích: Tạo bảng students trong Railway nếu chưa có
     * Sau đó tự động sync dữ liệu từ Render
     */
    @Async
    private void initializeSecondaryDbAsync() {
        // Step 1: Create table (bắt buộc trước sync)
        try {
            log.debug("📝 Creating/verifying secondary table...");
            secondaryJdbc.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    phone VARCHAR(255),
                    age INTEGER
                )
            """);
            log.info("✅ Secondary table created/verified");
            
            // Verify table exists
            Integer count = secondaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            log.info("✅ Secondary table verified - current record count: {}", count);
            secondaryDbHealthy.set(true);
        } catch (Exception e) {
            log.error("❌ Failed to create secondary table: {}", e.getMessage(), e);
            secondaryDbHealthy.set(false);
            return;  // ⚠️ Dừng nếu tạo table thất bại
        }

        // Step 2: Sync data từ Primary → Secondary (chỉ sau khi table ready)
        syncToSecondaryAsync();
    }
    // ============================================

    @PostConstruct
    public void initializeSecondaryDb() {
        log.info("🔄 Initializing secondary database...");
        initializeSecondaryDbAsync();  // Single async method that does both
    }

    @Async
    private void initializeSecondaryDbAsync() {
        // Step 1: Create table (required before sync)
        try {
            log.debug("📝 Creating/verifying secondary table...");
            secondaryJdbc.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    phone VARCHAR(255),
                    age INTEGER
                )
            """);
            log.info("✅ Secondary table created/verified");
            
            // Verify table exists
            Integer count = secondaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            log.info("✅ Secondary table verified - current record count: {}", count);
            secondaryDbHealthy.set(true);
        } catch (Exception e) {
            log.error("❌ Failed to create secondary table: {}", e.getMessage(), e);
            secondaryDbHealthy.set(false);
            return;  // ⚠️ Stop sync if table creation failed
        }

        // Step 2: Sync data (only after table is ready)
        syncToSecondaryAsync();
    }

    // ============================================
    // ✅ HEALTH CHECK with Timeout Fix
    // ============================================

    @Scheduled(fixedDelay = 5000)
    @Transactional(timeout = 2)  // 2 second timeout
    public void checkPrimaryDbHealth() {
        try {
            // Use simple query (faster than count on large tables)
            Integer result = primaryJdbc.queryForObject("SELECT 1", Integer.class);
            if (result != null) {
                primaryDbHealthy.set(true);
                log.debug("✅ Primary DB is healthy");
            }
        } catch (Exception e) {
            primaryDbHealthy.set(false);
            log.warn("❌ Primary DB health check failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional(timeout = 2)
    public void checkSecondaryDbHealth() {
        if (!secondaryEnabled) return;

        try {
            Integer result = secondaryJdbc.queryForObject("SELECT 1", Integer.class);
            if (result != null) {
                secondaryDbHealthy.set(true);
                log.debug("✅ Secondary DB is healthy");
            }
        } catch (Exception e) {
            secondaryDbHealthy.set(false);
            log.warn("❌ Secondary DB health check failed: {}", e.getClass().getSimpleName());
        }
    }

    // ============================================
    // ✅ READ OPERATIONS - Smart Routing
    // ============================================

    /**
     * ✅ Đọc với smart fallover
     * - Nếu Render healthy → đọc từ Render
     * - Nếu Render down → tự động fallover Railway
     * - Nếu cả 2 down → return empty list
     */
    public List<Student> getAllStudents() {
        if (primaryDbHealthy.get()) {
            try {
                log.debug("📖 Reading from primary DB...");
                List<Student> result = studentRepository.findAll();
                log.info("✅ Read from primary DB: {} records found", result.size());
                for (Student s : result) {
                    log.debug("   - ID: {}, Name: {}, Email: {}", s.getId(), s.getName(), s.getEmail());
                }
                return result;
            } catch (Exception e) {
                log.error("❌ ERROR reading from primary DB: {}", e.getMessage(), e);
                log.warn("⚠️ Primary DB read failed, falling back to secondary");
                primaryDbHealthy.set(false);
                return fallbackToSecondary("SELECT * FROM students ORDER BY id");
            }
        } else {
            log.info("⚠️ Primary DB is marked unhealthy, using secondary");
            return fallbackToSecondary("SELECT * FROM students ORDER BY id");
        }
    }

    /**
     * ✅ Fallback logic - query Railway DB
     */
    private List<Student> fallbackToSecondary(String sql) {
        if (!secondaryEnabled) {
            log.error("❌ Secondary DB is disabled");
            return List.of();
        }

        try {
            return secondaryJdbc.query(
                sql,
                (rs, rowNum) -> new Student(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getInt("age")
                )
            );
        } catch (Exception e) {
            log.error("❌ Secondary DB fallback also failed: {}", e.getMessage());
            return List.of();
        }
    }

    public Student getStudentById(Long id) {
        if (primaryDbHealthy.get()) {
            try {
                return studentRepository.findById(id).orElse(null);
            } catch (Exception e) {
                primaryDbHealthy.set(false);
                return fallbackGetStudentById(id);
            }
        } else {
            return fallbackGetStudentById(id);
        }
    }

    private Student fallbackGetStudentById(Long id) {
        if (!secondaryEnabled) return null;

        try {
            return secondaryJdbc.queryForObject(
                "SELECT * FROM students WHERE id = ?",
                new Object[]{id},
                (rs, rowNum) -> new Student(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getInt("age")
                )
            );
        } catch (Exception e) {
            log.error("❌ Failed to get student {} from secondary: {}", id, e.getMessage());
            return null;
        }
    }

    // ============================================
    // ✅ FIX 3: WRITE with Failover Support
    // ============================================

    @Transactional
    public Student createStudent(Student student) {
        if (primaryDbHealthy.get()) {
            try {
                // ✅ Primary write (main path)
                log.debug("📝 Saving student: name={}, email={}", student.getName(), student.getEmail());
                Student saved = studentRepository.save(student);
                log.info("✅ Student {} INSERTED into primary DB: {}", saved.getId(), saved.getName());
                
                // ✅ Verify data was actually saved by reading back
                try {
                    Student verify = studentRepository.findById(saved.getId()).orElse(null);
                    if (verify != null) {
                        log.info("✅ VERIFIED: Student {} exists in primary DB after save", verify.getId());
                    } else {
                        log.error("❌ WARNING: Student {} not found after save! Data commit might have failed", saved.getId());
                    }
                } catch (Exception verifyEx) {
                    log.error("❌ Failed to verify saved student: {}", verifyEx.getMessage());
                }

                // ✅ Async secondary write (with retry)
                writeToSecondaryAsync(
                    "INSERT INTO students (id, name, email, phone, age) VALUES (?, ?, ?, ?, ?)",
                    saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAge()
                );

                return saved;

            } catch (Exception e) {
                log.error("❌ PRIMARY DB WRITE FAILED - Exception: {}", e.getMessage(), e);
                log.warn("❌ Primary DB write failed, attempting secondary failover: {}", e.getMessage());
                primaryDbHealthy.set(false);
                
                // ✅ Failover path: write to secondary
                return createStudentInSecondary(student);
            }
        } else {
            log.info("⚠️ Primary DB already marked unhealthy, writing to secondary");
            return createStudentInSecondary(student);
        }
    }

    private Student createStudentInSecondary(Student student) {
        if (!secondaryEnabled || !secondaryDbHealthy.get()) {
            String msg = "❌ Both primary and secondary DBs are unavailable";
            log.error(msg);
            throw new RuntimeException(msg);
        }

        try {
            // Use RETURNING clause to get generated ID
            Long id = secondaryJdbc.queryForObject(
                "INSERT INTO students (name, email, phone, age) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                student.getName(), student.getEmail(), student.getPhone(), student.getAge()
            );
            
            student.setId(id);
            log.info("✅ Failover: Student {} created in secondary DB (Railway)", id);
            return student;

        } catch (Exception e) {
            log.error("❌ Secondary failover also failed: {}", e.getMessage());
            secondaryDbHealthy.set(false);
            throw new RuntimeException("❌ Both primary and secondary DB are down: " + e.getMessage());
        }
    }

    @Transactional
    public Student updateStudent(Long id, Student student) {
        if (primaryDbHealthy.get()) {
            try {
                student.setId(id);
                Student updated = studentRepository.save(student);
                log.info("✅ Student {} updated in primary DB", id);

                writeToSecondaryAsync(
                    "UPDATE students SET name=?, email=?, phone=?, age=? WHERE id=?",
                    updated.getName(), updated.getEmail(), updated.getPhone(), updated.getAge(), id
                );

                return updated;

            } catch (Exception e) {
                log.warn("❌ Primary update failed, attempting secondary failover: {}", e.getMessage());
                primaryDbHealthy.set(false);
                return updateStudentInSecondary(id, student);
            }
        } else {
            return updateStudentInSecondary(id, student);
        }
    }

    private Student updateStudentInSecondary(Long id, Student student) {
        if (!secondaryEnabled || !secondaryDbHealthy.get()) {
            throw new RuntimeException("❌ Secondary DB is unavailable for failover update");
        }

        try {
            student.setId(id);
            secondaryJdbc.update(
                "UPDATE students SET name=?, email=?, phone=?, age=? WHERE id=?",
                student.getName(), student.getEmail(), student.getPhone(), student.getAge(), id
            );
            
            log.info("✅ Failover: Student {} updated in secondary DB", id);
            return student;

        } catch (Exception e) {
            log.error("❌ Secondary update failed: {}", e.getMessage());
            secondaryDbHealthy.set(false);
            throw new RuntimeException("❌ Update failed in both DBs: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteStudent(Long id) {
        if (primaryDbHealthy.get()) {
            try {
                studentRepository.deleteById(id);
                log.info("✅ Student {} deleted from primary DB", id);

                writeToSecondaryAsync("DELETE FROM students WHERE id=?", id);

            } catch (Exception e) {
                log.warn("❌ Primary delete failed, attempting secondary failover: {}", e.getMessage());
                primaryDbHealthy.set(false);
                deleteStudentInSecondary(id);
            }
        } else {
            deleteStudentInSecondary(id);
        }
    }

    private void deleteStudentInSecondary(Long id) {
        if (!secondaryEnabled || !secondaryDbHealthy.get()) {
            throw new RuntimeException("❌ Secondary DB is unavailable for failover delete");
        }

        try {
            secondaryJdbc.update("DELETE FROM students WHERE id=?", id);
            log.info("✅ Failover: Student {} deleted from secondary DB", id);

        } catch (Exception e) {
            log.error("❌ Secondary delete failed: {}", e.getMessage());
            secondaryDbHealthy.set(false);
            throw new RuntimeException("❌ Delete failed in both DBs: " + e.getMessage());
        }
    }

    // ============================================
    // ✅ FIX 2: Retry Logic with Exponential Backoff
    // ============================================

    @Async
    private void writeToSecondaryAsync(String sql, Object... args) {
        if (!secondaryEnabled) {
            return;
        }

        int attempt = 0;
        long delayMs = INITIAL_RETRY_DELAY_MS;

        while (attempt < MAX_RETRIES) {
            try {
                secondaryJdbc.update(sql, args);
                log.debug("✅ Secondary DB write successful (attempt {}/{})", attempt + 1, MAX_RETRIES);
                return;  // Success - exit
            } catch (Exception e) {
                attempt++;

                if (attempt < MAX_RETRIES) {
                    log.warn("⚠️ Secondary write failed (attempt {}/{}), retrying in {}ms... Error: {}",
                            attempt, MAX_RETRIES, delayMs, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                        delayMs *= 2;  // Exponential backoff: 500ms → 1s → 2s
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("⚠️ Retry sleep interrupted");
                        return;
                    }
                } else {
                    log.error("❌ Secondary DB write FAILED after {} attempts. Data will be synced later. Error: {}",
                            MAX_RETRIES, e.getMessage());
                    // Data inconsistency detected - next manual sync will fix it
                }
            }
        }
    }

    // ============================================
    // ✅ SYNC OPERATIONS
    // ============================================

    public String resetSecondaryDb() {
        try {
            log.info("🚨 Clearing secondary database...");
            secondaryJdbc.execute("TRUNCATE TABLE students CASCADE");
            log.info("✅ Secondary DB cleared successfully");
            syncToSecondaryAsync();
            return "✅ Secondary DB reset and sync initiated";
        } catch (Exception e) {
            log.error("❌ Failed to reset secondary DB: {}", e.getMessage(), e);
            return "❌ Reset failed: " + e.getMessage();
        }
    }

    @Async
    private void syncToSecondaryAsync() {
        if (!secondaryEnabled || syncInProgress.getAndSet(true)) {
            return;
        }

        try {
            log.info("🔄 Starting data sync from primary to secondary DB...");

            List<Student> allStudents = studentRepository.findAll();
            log.info("📊 Found {} students in primary DB", allStudents.size());

            if (allStudents.isEmpty()) {
                log.info("ℹ️ Primary DB is empty, skipping sync");
                return;
            }

            Integer secondaryCount = secondaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            log.info("📊 Secondary DB currently has {} records", secondaryCount);

            // ✅ UPSERT pattern: handles duplicates gracefully
            for (Student student : allStudents) {
                try {
                    secondaryJdbc.update(
                        "INSERT INTO students (id, name, email, phone, age) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, email = EXCLUDED.email, phone = EXCLUDED.phone, age = EXCLUDED.age",
                        student.getId(), student.getName(), student.getEmail(),
                        student.getPhone(), student.getAge()
                    );
                } catch (Exception e) {
                    log.warn("⚠️ Failed to sync student {}: {}", student.getId(), e.getMessage());
                }
            }

            log.info("✅ Sync completed successfully");

        } catch (Exception e) {
            log.error("❌ Sync failed: {}", e.getMessage(), e);
        } finally {
            syncInProgress.set(false);
        }
    }

    public String manualSync() {
        if (syncInProgress.get()) {
            return "Sync already in progress...";
        }

        syncToSecondaryAsync();
        return "Sync started (async, check logs)";
    }

    // ============================================
    // ✅ FIX 4: DATA CONSISTENCY CHECK
    // ============================================

    public Map<String, Object> checkDataConsistency() {
        long primaryCount = -1;
        long secondaryCount = -1;
        List<String> inconsistencies = new ArrayList<>();
        boolean isConsistent = false;

        try {
            primaryCount = studentRepository.count();
        } catch (Exception e) {
            log.error("❌ Cannot count primary: {}", e.getMessage());
            inconsistencies.add("Cannot access primary DB: " + e.getMessage());
        }

        if (secondaryEnabled) {
            try {
                secondaryCount = secondaryJdbc.queryForObject(
                    "SELECT COUNT(*) FROM students",
                    Long.class
                );
            } catch (Exception e) {
                log.error("❌ Cannot count secondary: {}", e.getMessage());
                inconsistencies.add("Cannot access secondary DB: " + e.getMessage());
                secondaryCount = -1;
            }
        } else {
            inconsistencies.add("Secondary DB is disabled");
        }

        // Check consistency
        if (primaryCount >= 0 && secondaryCount >= 0) {
            if (primaryCount == secondaryCount) {
                isConsistent = true;
                log.info("✅ Data is consistent: {} records in both DBs", primaryCount);
            } else {
                inconsistencies.add(String.format(
                    "Record count mismatch: Primary has %d records, Secondary has %d records",
                    primaryCount, secondaryCount
                ));
                log.warn("⚠️ Data inconsistency detected: Primary=%d, Secondary=%d", primaryCount, secondaryCount);
            }
        }

        return Map.of(
            "primary_count", primaryCount,
            "secondary_count", secondaryCount,
            "consistent", isConsistent,
            "primary_healthy", primaryDbHealthy.get(),
            "secondary_healthy", secondaryDbHealthy.get(),
            "inconsistencies", inconsistencies
        );
    }

    // ============================================
    // ✅ HEALTH STATUS
    // ============================================

    public String getHealthStatus() {
        return String.format(
            "Primary: %s | Secondary: %s | Sync: %s | Consistent: %b",
            primaryDbHealthy.get() ? "✅ UP" : "❌ DOWN",
            secondaryEnabled ? (secondaryDbHealthy.get() ? "✅ UP" : "❌ DOWN") : "⚠️ DISABLED",
            syncInProgress.get() ? "🔄 IN_PROGRESS" : "✅ IDLE",
            checkDataConsistency().get("consistent")
        );
    }

    // ============================================
    // ✅ DEBUG HELPERS
    // ============================================

    public List<Student> queryPrimaryDirect() {
        try {
            return primaryJdbc.query(
                "SELECT * FROM students ORDER BY id",
                (rs, rowNum) -> new Student(
                    rs.getLong("id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getInt("age")
                )
            );
        } catch (Exception e) {
            log.error("❌ Direct query to primary failed: {}", e.getMessage());
            return List.of();
        }
    }

    public int getPrimaryCountDirect() {
        try {
            return primaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
        } catch (Exception e) {
            log.error("❌ Count query to primary failed: {}", e.getMessage());
            return -1;
        }
    }
}

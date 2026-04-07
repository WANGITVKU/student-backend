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

@Service
public class StudentService {
    private static final Logger log = LoggerFactory.getLogger(StudentService.class);

    @Autowired
    private StudentRepository studentRepository;

    private final JdbcTemplate secondaryJdbc;
    
    // ✅ Add primary JdbcTemplate for direct SQL queries
    private JdbcTemplate primaryJdbc;
    
    @Autowired
    public void setPrimaryDataSource(DataSource primaryDataSource) {
        this.primaryJdbc = new JdbcTemplate(primaryDataSource);
    }
    
    // ✅ Track health của Render DB
    private final AtomicBoolean primaryDbHealthy = new AtomicBoolean(true);
    // ✅ NEW: Track secondary health separately
    private final AtomicBoolean secondaryDbHealthy = new AtomicBoolean(true);
    
    // ✅ Track sync status
    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);

    @Value("${app.secondary.enabled:true}")
    private boolean secondaryEnabled;
    
    // ✅ NEW: Retry configuration
    private static final int MAX_RETRIES = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 500;

    public StudentService(@Qualifier("secondaryDataSource") DataSource secondaryDataSource) {
        this.secondaryJdbc = new JdbcTemplate(secondaryDataSource);
    }

    // ============================================
    // ✅ FIX 1: Sequential Initialization (Race condition fix)
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
            secondaryDbHealthy.set(true);
        } catch (Exception e) {
            log.error("❌ Failed to create secondary table: {}", e.getMessage());
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
                List<Student> result = studentRepository.findAll();
                log.info("✅ Read from primary DB: {} records", result.size());
                return result;
            } catch (Exception e) {
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
                Student saved = studentRepository.save(student);
                log.info("✅ Student {} created in primary DB", saved.getId());

                // ✅ Async secondary write (with retry)
                writeToSecondaryAsync(
                    "INSERT INTO students (id, name, email, phone, age) VALUES (?, ?, ?, ?, ?)",
                    saved.getId(), saved.getName(), saved.getEmail(), saved.getPhone(), saved.getAge()
                );

                return saved;

            } catch (Exception e) {
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

            // Sync missing records
            for (Student student : allStudents) {
                try {
                    Integer exists = secondaryJdbc.queryForObject(
                        "SELECT COUNT(*) FROM students WHERE id = ?",
                        Integer.class,
                        student.getId()
                    );

                    if (exists == 0) {
                        secondaryJdbc.update(
                            "INSERT INTO students (id, name, email, phone, age) VALUES (?, ?, ?, ?, ?)",
                            student.getId(), student.getName(), student.getEmail(),
                            student.getPhone(), student.getAge()
                        );
                    }
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

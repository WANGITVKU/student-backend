package com.example.demo.studentbackend.controller;

import com.example.demo.studentbackend.model.Student;
import com.example.demo.studentbackend.service.StudentService;
import com.example.demo.studentbackend.util.DiagnosticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 🎛️ CONTROLLER: StudentController
 * 
 * Mục đích:
 *  - Xử lý HTTP requests từ client (Frontend/API)
 *  - Định nghĩa các REST API endpoints
 *  - Chuyển request → service → response
 * 
 * Annotation:
 *  - @RestController: Cho phép viết JSON response trực tiếp
 *  - @RequestMapping("/api/students"): Tiền tố URL cho tất cả endpoint
 *  - @GetMapping, @PostMapping, @PutMapping, @DeleteMapping: HTTP methods
 *  - @CrossOrigin: Cho phép requests từ frontend ở domain khác (CORS)
 * 
 * Các Endpoint (REST API):
 *  GET    /api/students              → Lấy danh sách tất cả sinh viên
 *  GET    /api/students/{id}         → Lấy 1 sinh viên theo ID
 *  POST   /api/students              → Thêm sinh viên mới
 *  PUT    /api/students/{id}         → Cập nhật sinh viên
 *  DELETE /api/students/{id}         → Xóa sinh viên
 *  
 * Các Endpoint Khác (Advanced):
 *  GET    /api/students/health/status    → Kiểm tra sức khỏe hệ thống
 *  POST   /api/students/admin/sync       → Đồng bộ dữ liệu thủ công
 *  GET    /api/students/debug/diagnostics → Chẩn đoán chi tiết
 */
@RestController
@RequestMapping("/api/students")
@CrossOrigin(origins = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
public class StudentController {

    // 🔗 Inject StudentService (chứa business logic)
    @Autowired
    private StudentService studentService;

    // 🔗 Inject DiagnosticService (chứa diagnostic logic)
    @Autowired
    private DiagnosticService diagnosticService;

    // ========================================
    // ✅ CRUD OPERATIONS
    // ========================================

    @GetMapping
    public List<Student> getAllStudents() {
        return studentService.getAllStudents();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        Student student = studentService.getStudentById(id);
        if (student == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(student);
    }

    @PostMapping
    public ResponseEntity<Student> createStudent(@RequestBody Student student) {
        Student created = studentService.createStudent(student);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student student) {
        Student updated = studentService.updateStudent(id, student);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        studentService.deleteStudent(id);
        return ResponseEntity.ok().build();
    }

    // ========================================
    // ✅ HEALTH & ADMIN ENDPOINTS
    // ========================================

    /**
     * ✅ Health check endpoint
     * - Dùng để monitor từ docker, k8s, hay monitoring tools
     */
    @GetMapping("/health/status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        String status = studentService.getHealthStatus();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("timestamp", System.currentTimeMillis());
        response.put("service", "StudentService");
        
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ NEW: Extended health check with data consistency
     */
    @GetMapping("/health/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedHealth() {
        String status = studentService.getHealthStatus();
        Map<String, Object> consistency = studentService.checkDataConsistency();
        
        Map<String, Object> response = new HashMap<>();
        response.put("status", status);
        response.put("consistency", consistency);
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ NEW: Data consistency check endpoint
     * - Verifies record count between primary and secondary DB
     * - Returns 200 if consistent, 503 if inconsistent
     */
    @GetMapping("/admin/consistency-check")
    public ResponseEntity<Map<String, Object>> checkConsistency() {
        Map<String, Object> result = studentService.checkDataConsistency();
        
        // Return 200 if consistent, 503 if inconsistent
        int statusCode = (boolean) result.get("consistent") ? 200 : 503;
        return ResponseEntity.status(statusCode).body(result);
    }

    /**
     * ✅ Manual sync endpoint
     * - Dùng khi cần đồng bộ dữ liệu emergency
     */
    @PostMapping("/admin/sync")
    public ResponseEntity<Map<String, String>> manualSync() {
        String result = studentService.manualSync();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ Reset secondary database endpoint
     * - Clears Railway (truncates students table)
     * - Triggers fresh sync from Render
     * - Use when Railway has duplicate or stale data
     */
    @PostMapping("/admin/reset-secondary")
    public ResponseEntity<Map<String, String>> resetSecondaryDb() {
        String result = studentService.resetSecondaryDb();
        
        Map<String, String> response = new HashMap<>();
        response.put("message", result);
        response.put("timestamp", String.valueOf(System.currentTimeMillis()));
        response.put("action", "Railway DB truncated and fresh sync initiated");
        
        return ResponseEntity.ok(response);
    }

    /**
     * ✅ Simple health check (for container orchestration)
     */
    @GetMapping("/health/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("✅ Service is running");
    }

    /**
     * ✅ Debug endpoint - Check database connection and data
     */
    @GetMapping("/debug/db-info")
    public ResponseEntity<Map<String, Object>> getDbInfo() {
        Map<String, Object> info = new HashMap<>();
        
        try {
            // Get count via JPA repository
            long jpaCount = studentService.getPrimaryCountDirect();
            info.put("jpa_count", jpaCount);
            
            // Get all students via JPA
            List<Student> jpaStudents = studentService.getAllStudents();
            info.put("jpa_students_count", jpaStudents.size());
            
            // Get all students via direct JDBC query
            List<Student> directStudents = studentService.queryPrimaryDirect();
            info.put("direct_jdbc_count", directStudents.size());
            info.put("direct_jdbc_students", directStudents);
            
            info.put("primary_db_status", "connected");
            
        } catch (Exception e) {
            info.put("error", e.getMessage());
            info.put("primary_db_status", "error");
        }
        
        return ResponseEntity.ok(info);
    }

    // ========================================
    // ✅ NEW: DIAGNOSTIC ENDPOINTS
    // ========================================

    /**
     * 🔍 Full diagnostic report - Check all systems
     * Endpoint: GET /api/students/debug/diagnostics
     * Returns: Comprehensive health check of primary DB, secondary DB, tables, data
     */
    @GetMapping("/debug/diagnostics")
    public ResponseEntity<Map<String, Object>> runDiagnostics() {
        Map<String, Object> diagnostics = diagnosticService.runFullDiagnostics();
        return ResponseEntity.ok(diagnostics);
    }

    /**
     * 🔧 Initialize databases if tables don't exist
     * Endpoint: POST /api/students/debug/init-db
     * Returns: Status of table creation in both databases
     */
    @PostMapping("/debug/init-db")
    public ResponseEntity<Map<String, Object>> initializeDatabase() {
        Map<String, String> initResult = diagnosticService.initializeDatabase();
        Map<String, Object> response = new HashMap<>(initResult);
        response.put("timestamp", System.currentTimeMillis());
        response.put("action", "Initialize students table in both primary and secondary databases");
        return ResponseEntity.ok(response);
    }

    /**
     * 📊 Quick health check with data counts
     * Endpoint: GET /api/students/debug/quick-check
     * Returns: Connection status and record counts
     */
    @GetMapping("/debug/quick-check")
    public ResponseEntity<Map<String, Object>> quickHealthCheck() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get consistency check
            Map<String, Object> consistency = studentService.checkDataConsistency();
            response.putAll(consistency);
            response.put("timestamp", System.currentTimeMillis());
            response.put("status", "✅ OK");
        } catch (Exception e) {
            response.put("status", "❌ ERROR");
            response.put("error", e.getMessage());
            response.put("timestamp", System.currentTimeMillis());
        }
        
        return ResponseEntity.ok(response);
    }
}

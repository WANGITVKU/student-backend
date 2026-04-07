package com.example.demo.studentbackend.controller;

import com.example.demo.studentbackend.model.Student;
import com.example.demo.studentbackend.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

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
}

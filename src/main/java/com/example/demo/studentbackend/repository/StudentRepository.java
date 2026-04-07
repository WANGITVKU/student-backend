package com.example.demo.studentbackend.repository;

import com.example.demo.studentbackend.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 🏦 REPOSITORY: StudentRepository
 * 
 * Mục đích:
 *  - Giao tiếp với database (tầng Data Access)
 *  - Cung cấp các phương thức CRUD tự động
 *  - Không cần viết SQL raw queries thông thường
 * 
 * Extends JpaRepository<Student, Long>:
 *  - Student: Entity class
 *  - Long: Kiểu dữ liệu của Primary Key
 * 
 * Các method tự động có sẵn:
 *  - findAll() → Lấy tất cả sinh viên
 *  - findById(id) → Lấy sinh viên theo ID
 *  - save(student) → Thêm/cập nhật sinh viên
 *  - deleteById(id) → Xóa sinh viên
 *  - count() → Đếm số lượng sinh viên
 * 
 * Cách mở rộng:
 *  - Thêm method tùy chỉnh với @Query nếu cần
 *  - VD: findByEmail(String email) - Spring tự hiện thực
 */
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    // Spring Data JPA tự động implement tất cả hàm CRUD
    // Không cần viết implementation, chỉ cần khai báo interface
}
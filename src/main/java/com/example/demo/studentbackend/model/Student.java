package com.example.demo.studentbackend.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * 📊 ENTITY: Student (Sinh Viên)
 * 
 * Mục đích:
 *  - Đại diện cho bảng "students" trong database
 *  - Chứa thông tin cơ bản của một sinh viên
 *  - Ánh xạ từ Object (Java) → Table (SQL)
 * 
 * Cấu trúc:
 *  - id: Mã sinh viên (tự động tăng)
 *  - name: Họ tên
 *  - email: Email (duy nhất, không duplicate)
 *  - phone: Số điện thoại
 *  - age: Tuổi
 * 
 * Annotation:
 *  - @Entity: Đánh dấu đây là JPA entity
 *  - @Table: Tên bảng trong database
 *  - @Data: Lombok - tự động sinh getter/setter/toString/equals
 *  - @NoArgsConstructor: Constructor không tham số
 *  - @AllArgsConstructor: Constructor có tất cả tham số
 */
@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    // 🔑 Mã sinh viên (Primary Key)
    // @GeneratedValue(IDENTITY): Tự động tăng
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 📝 Họ tên sinh viên
    private String name;

    // 📧 Email sinh viên (duy nhất, không trùng)
    private String email;

    // 📞 Số điện thoại
    private String phone;

    // 🎂 Tuổi (phải 13-100)
    private int age;
}
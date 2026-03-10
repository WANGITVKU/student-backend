package com.example.demo.studentbackend;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "students")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;      // Họ tên
    private String email;     // Email
    private String phone;     // Số điện thoại
    private int age;          // Tuổi
}
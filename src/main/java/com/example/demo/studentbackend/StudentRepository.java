package com.example.demo.studentbackend;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository 
    extends JpaRepository<Student, Long> {
}
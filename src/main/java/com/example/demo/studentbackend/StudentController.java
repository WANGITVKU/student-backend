package com.example.demo.studentbackend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/students")
@CrossOrigin(origins = "*")
public class StudentController {

    @Autowired
    private StudentRepository repo;

    // Lấy tất cả sinh viên
    @GetMapping
    public List<Student> getAll() {
        return repo.findAll();
    }

    // Lấy 1 sinh viên theo id
    @GetMapping("/{id}")
    public Student getById(@PathVariable Long id) {
        return repo.findById(id).orElseThrow();
    }

    // Thêm sinh viên mới
    @PostMapping
    public Student create(@RequestBody Student student) {
        return repo.save(student);
    }

    // Sửa sinh viên
    @PutMapping("/{id}")
    public Student update(@PathVariable Long id, 
                          @RequestBody Student student) {
        student.setId(id);
        return repo.save(student);
    }

    // Xóa sinh viên
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        repo.deleteById(id);
    }
}
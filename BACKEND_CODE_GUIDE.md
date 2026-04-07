# 📚 BACKEND CODE GUIDE - Các File Chính

## 🗺️ Bản Đồ Kiến Trúc Backend

```
┌─────────────────────────────────────────────────────────┐
│         HTTP REQUEST từ Client (Frontend/API)           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│   StudentController.java (🎛️ API Endpoints)             │
│   - @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
│   - Nhận request → gọi service → trả response           │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│   StudentService.java (🧠 Business Logic)               │
│   - CRUD operations (Create, Read, Update, Delete)      │
│   - Dual-database logic (write to both DB)              │
│   - Failover logic (if Primary down → use Secondary)   │
│   - Sync logic (keep data consistent)                   │
└────┬──────────────────────────────────┬─────────────────┘
     │                                  │
┌────▼──────────┐           ┌──────────▼──────┐
│  Student      │           │ StudentRepository
│  Repository   │           │ (JPA Interface)
│  (JPA)        │           │                 │
└────┬──────────┘           └──────────┬──────┘
     │                                 │
┌────▼─────────────────────────────────▼──────────┐
│         DataSourceConfig.java (⚙️ Config)       │
│   - @Bean: primaryDatasource (Render)           │
│   - @Bean: secondaryDataSource (Railway)        │
│   - @Bean: taskExecutor (Thread pool for @Async)
└────┬──────────────────────────┬────────────────┘
     │                          │
┌────▼──────────────────┐  ┌───▼──────────────────┐
│  Render PostgreSQL    │  │  Railway PostgreSQL  │
│  (Primary)            │  │  (Secondary/Backup)  │
│  [Production Data]    │  │  [Sync Data]         │
└──────────────────────┘  └──────────────────────┘
```

---

## 📂 Các File Java Chính

### 1️⃣ **DemoApplication.java** (🚀 Điểm vào)

**Đường dẫn:** `demo/src/main/java/com/example/demo/DemoApplication.java`

**Mục đích:**
- Khởi động ứng dụng Spring Boot
- Quét package tìm @Component, @Service, @Controller
- Tải configuration từ DataSourceConfig
- Khởi động server Tomcat trên port 8080

**Luồng khởi động:**
```
1. main() → SpringApplication.run()
   ↓
2. Spring Boot tạo Application Context
   ↓
3. Quét classpath → tìm @Component
   ↓
4. DataSourceConfig.java load → tạo Primary + Secondary DataSource
   ↓
5. StudentService load → @PostConstruct initializeSecondaryDb()
   ↓
6. Tomcat khởi động (port 8080)
   ↓
7. App ready → chờ request
```

**File:
```java
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

---

### 2️⃣ **DataSourceConfig.java** (⚙️ Configuration)

**Đường dẫn:** `demo/src/main/java/com/example/demo/studentbackend/config/DataSourceConfig.java`

**Mục đích:**
- Cấu hình kết nối 2 database (Render + Railway)
- Tạo Bean cho Dependency Injection
- Kích hoạt @Async (chạy background) và @Scheduled (chạy định kỳ)

**Các Bean tạo ra:**
```java
@Bean primaryDatasource()           // Render DB
@Bean primaryJdbcTemplate()         // Render JDBC
@Bean secondaryDataSource()         // Railway DB
@Bean secondaryJdbcTemplate()       // Railway JDBC
@Bean taskExecutor()                // ThreadPool (5 threads)
```

**Annotation:**
```java
@Configuration                      // Đây là config class
@EnableAsync                        // Cho phép @Async
@EnableScheduling                   // Cho phép @Scheduled
@Primary                            // Ưu tiên khi có nhiều bean
```

**Cách sử dụng:**
```java
@Autowired
@Qualifier("primaryJdbc")
private JdbcTemplate primaryJdbc;   // Lấy bean "primaryJdbc"
```

---

### 3️⃣ **Student.java** (📊 Entity/Model)

**Đường dẫn:** `demo/src/main/java/com/example/demo/studentbackend/model/Student.java`

**Mục đích:**
- Đại diện cho bảng "students" trong database
- Ánh xạ từ Java object → SQL table
- Chứa dữ liệu sinh viên

**Cấu trúc:**
```java
@Entity                 // JPA entity
@Table(name="students") // Tên bảng SQL
public class Student {
    
    @Id
    @GeneratedValue(IDENTITY) // Tự động tăng
    private Long id;
    
    private String name;
    private String email;
    private String phone;
    private int age;
}
```

**Annotation:**
```java
@Data                   // Lombok: auto sinh getter/setter/toString
@NoArgsConstructor      // Constructor ()
@AllArgsConstructor     // Constructor (id, name, email, ...)
```

**Mapping:**
```
Java Object                 SQL Table
────────────────────────────────────
Student {                   students {
  id: 1        ────→           id: 1
  name: "A"    ────→           name: 'A'
  email: "..."  ────→          email: '...'
  age: 20      ────→           age: 20
}                           }
```

---

### 4️⃣ **StudentRepository.java** (🏦 Data Access)

**Đường dẫn:** `demo/src/main/java/com/example/demo/studentbackend/repository/StudentRepository.java`

**Mục đích:**
- Giao tiếp với database (tầng Data Access)
- Cung cấp CRUD methods tự động
- Không cần viết SQL raw queries

**Cú pháp:**
```java
@Repository
public interface StudentRepository extends JpaRepository<Student, Long> {
    // Spring Data JPA tự động implement
}
```

**Methods có sẵn:**
```java
findAll()                   // SELECT * FROM students
findById(id)                // SELECT * FROM students WHERE id = ?
save(student)               // INSERT/UPDATE
deleteById(id)              // DELETE ... WHERE id = ?
count()                     // SELECT COUNT(*)
exists(id)                  // Kiểm tra tồn tại
```

**Cách dùng:**
```java
@Autowired
private StudentRepository repo;

List<Student> all = repo.findAll();      // Lấy tất cả
Student s = repo.findById(1L).orElse(null);  // Lấy 1
repo.save(new Student(...));              // Thêm mới
repo.deleteById(1L);                      // Xóa
```

---

### 5️⃣ **StudentService.java** (🧠 Business Logic)

**Đường dẫn:** `demo/src/main/java/com/example/demo/studentbackend/service/StudentService.java`

**Mục đích:**
- Chứa logic nghiệp vụ (business logic) chính
- Xử lý CRUD operations
- Quản lý dual-database architecture
- Tự động sync dữ liệu giữa Render + Railway

**Cấu trúc:**

```java
@Service
public class StudentService {
    
    @Autowired
    private StudentRepository studentRepository;    // Primary DB
    
    private JdbcTemplate primaryJdbc;               // Render JDBC
    private JdbcTemplate secondaryJdbc;             // Railway JDBC
    
    private AtomicBoolean primaryDbHealthy;         // Track Render health
    private AtomicBoolean secondaryDbHealthy;       // Track Railway health
    private AtomicBoolean syncInProgress;           // Track sync status
}
```

**Các phương thức chính:**

```java
// ===== CRUD OPERATIONS =====

public List<Student> getAllStudents()               // Lấy tất cả
public Student getStudentById(Long id)              // Lấy 1
public Student createStudent(Student student)        // Thêm mới
public Student updateStudent(Long id, Student s)    // Cập nhật
public void deleteStudent(Long id)                  // Xóa

// ===== HEALTH CHECK =====

@Scheduled(fixedDelay=5000)
public void checkPrimaryDbHealth()                  // Kiểm tra Render (mỗi 5s)

@Scheduled(fixedDelay=5000)
public void checkSecondaryDbHealth()                // Kiểm tra Railway (mỗi 5s)

public String getHealthStatus()                     // Trạng thái chi tiết

// ===== SYNC OPERATIONS =====

@PostConstruct
public void initializeSecondaryDb()                 // Khởi tạo Railway

@Async
private void syncToSecondaryAsync()                 // Đồng bộ dữ liệu

public String manualSync()                          // Sync thủ công

// ===== FAILOVER LOGIC =====

private Student createStudentInSecondary()          // Fallback khi Render down
private Student updateStudentInSecondary()          // Fallback update
private void deleteStudentInSecondary()             // Fallback delete
```

**Luồng CRUD với Dual-Database:**

```
CREATE (Thêm mới):
  Request → StudentService.createStudent()
  ├─ Render DB (Primary) → SUCCESS ✅
  ├─ @Async: Railway DB (Secondary) → RETRY (3 lần)
  └─ Return: Sinh viên đã tạo + ID

READ (Lấy dữ liệu):
  Request → StudentService.getStudent()
  ├─ Nếu Render healthy: đọc từ Render ✅
  ├─ Nếu Render down: fallback to Railway ⚠️
  └─ Nếu cả 2 down: return empty ❌

UPDATE (Cập nhật):
  Request → StudentService.updateStudent()
  ├─ Render DB (Primary) → SUCCESS ✅
  ├─ @Async: Railway DB (Secondary) → RETRY
  └─ Return: Sinh viên đã cập nhật

DELETE (Xóa):
  Request → StudentService.deleteStudent()
  ├─ Render DB (Primary) → SUCCESS ✅
  ├─ @Async: Railway DB (Secondary) → RETRY
  └─ Return: OK (đã xóa)
```

**Retry Logic (Exponential Backoff):**
```
Attempt 1: 0ms      → Thử lần 1 (ngay)
  FAIL ❌
Attempt 2: 500ms    → Chờ 500ms, thử lần 2
  FAIL ❌
Attempt 3: 1000ms   → Chờ 1000ms (1s), thử lần 3
  FAIL ❌
Result: Log error, skip (next sync sẽ fix)
```

---

### 6️⃣ **StudentController.java** (🎛️ API Endpoints)

**Đường dẫn:** `demo/src/main/java/com/example/demo/studentbackend/controller/StudentController.java`

**Mục đích:**
- Xử lý HTTP requests từ client
- Định nghĩa REST API endpoints
- Chuyển request → service → response

**Annotation:**
```java
@RestController                     // Return JSON response
@RequestMapping("/api/students")    // Base URL
@GetMapping                         // HTTP GET
@PostMapping                        // HTTP POST
@PutMapping                         // HTTP PUT
@DeleteMapping                      // HTTP DELETE
@PathVariable                       // Lấy {id} từ URL
@RequestBody                        // Lấy dữ liệu từ body
```

**Các Endpoint:**

```java
// ===== CRUD ENDPOINTS =====

@GetMapping
public List<Student> getAllStudents()
// GET /api/students
// Trả: Danh sách tất cả sinh viên (JSON)

@GetMapping("/{id}")
public ResponseEntity<Student> getStudentById(@PathVariable Long id)
// GET /api/students/1
// Trả: Sinh viên ID=1 hoặc 404 Not Found

@PostMapping
public ResponseEntity<Student> createStudent(@RequestBody Student s)
// POST /api/students
// Body: {"name":"A","email":"a@g.com","phone":"0901...","age":20}
// Trả: Sinh viên vừa tạo (có ID)

@PutMapping("/{id}")
public ResponseEntity<Student> updateStudent(@PathVariable Long id, @RequestBody Student s)
// PUT /api/students/1
// Body: {"name":"A2","email":"a2@g.com",...}
// Trả: Sinh viên đã cập nhật

@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteStudent(@PathVariable Long id)
// DELETE /api/students/1
// Trả: 200 OK (đã xóa)

// ===== DIAGNOSTIC ENDPOINTS =====

@GetMapping("/debug/diagnostics")
// GET /api/students/debug/diagnostics
// Trả: Phuờng đầy đủ (Primary/Secondary health + Table status)

@PostMapping("/debug/init-db")
// POST /api/students/debug/init-db
// Trả: Kết quả tạo table

@GetMapping("/debug/quick-check")
// GET /api/students/debug/quick-check
// Trả: Record count + Consistency check
```

**Response Examples:**

```json
// GET /api/students (200 OK)
[
  {"id":1,"name":"A","email":"a@g.com","phone":"0901...","age":20},
  {"id":2,"name":"B","email":"b@g.com","phone":"0901...","age":21}
]

// POST /api/students (200 OK, Created)
{"id":3,"name":"C","email":"c@g.com","phone":"0901...","age":22}

// GET /api/students/1 (200 OK)
{"id":1,"name":"A","email":"a@g.com","phone":"0901...","age":20}

// GET /api/students/999 (404 Not Found)
[empty response]

// GET /api/students/debug/diagnostics
{
  "primary_db": {"connection":"✅ CONNECTED","status":"✅ HEALTHY"},
  "secondary_db": {"connection":"✅ CONNECTED","status":"✅ HEALTHY"},
  "table_status": {
    "primary": {"table_exists":"✅ YES","record_count":3},
    "secondary": {"table_exists":"✅ YES","record_count":3}
  }
}
```

---

## 🔄 Luồng Dữ Liệu (Data Flow)

### **Scenario: Thêm sinh viên mới**

```
1. Frontend/Client gửi HTTP POST
   POST /api/students
   Body: {"name":"John","email":"john@g.com","phone":"0901...","age":20}

2. StudentController nhận request
   @PostMapping
   public ResponseEntity<Student> createStudent(@RequestBody Student student)

3. Gọi StudentService
   Student created = studentService.createStudent(student)

4. StudentService.createStudent() thực hiện:
   
   a) Check nếu Render healthy
      if (primaryDbHealthy.get()) {
   
   b) Save vào Render DB (Primary)
      Student saved = studentRepository.save(student)
      → INSERT INTO students (name, email, phone, age) VALUES (...)
      → Render trả ID (auto-generated)
   
   c) Verify data
      Student verify = studentRepository.findById(saved.getId())
      if (verify != null) ✅ OK
      else ❌ Log warning
   
   d) Async write to Railway (Secondary)
      @Async
      writeToSecondaryAsync(INSERT, ...)
      → Ngay lập tức return, không chờ
      → Chạy background: INSERT INTO railway.students ...
      → Nếu fail, retry 3 lần (500ms, 1s, 2s)
   
   e) Return saved student
      return saved

5. StudentController trả response
   ResponseEntity.ok(created)
   → HTTP 200 OK
   → Body: {"id":3,"name":"John",...}

6. Frontend nhận response
   → Hiển thị "Đã thêm sinh viên"
```

### **Scenario: Render DB Down (Failover)**

```
Request → StudentService.createStudent()

1. Check: if (primaryDbHealthy.get())
   → primaryDbHealthy = false (Render down)

2. Fallback to Secondary (Railway)
   createStudentInSecondary(student)
   → INSERT INTO railway.students ...
   → Railway trả ID
   → return student with ID

3. Log
   ⚠️ PRIMARY DB WRITE FAILED - Exception: Connection refused
   ❌ Primary DB write failed, attempting secondary failover

Result: Data vẫn được lưu (ở Railway), không mất
```

### **Scenario: Scheduled Health Check (mỗi 5 giây)**

```
@Scheduled(fixedDelay = 5000)  // Chạy mỗi 5s
public void checkPrimaryDbHealth()

1. Gửi query: SELECT 1
2. Nếu thành công: primaryDbHealthy = true ✅
3. Nếu thất bại: primaryDbHealthy = false ❌
4. Log: ✅ Primary DB is healthy / ❌ Primary DB health check failed

Cập nhật failover logic:
  - Nếu primaryDbHealthy = true: read/write from Render
  - Nếu primaryDbHealthy = false: fallback to Railway
```

---

## 📋 Luồng Khởi Động (Startup Flow)

```
1. main() → SpringApplication.run(DemoApplication.class)

2. Spring Boot tạo Application Context
   ├─ Scan classpath → tìm @Component, @Service, @Repository, @Configuration
   └─ Load properties từ application.properties

3. Instantiate DataSourceConfig
   ├─ @Bean primaryDatasource() → Kết nối Render
   ├─ @Bean primaryJdbc() → JdbcTemplate cho Render
   ├─ @Bean secondaryDataSource() → Kết nối Railway
   ├─ @Bean secondaryJdbcTemplate() → JdbcTemplate cho Railway
   └─ @Bean taskExecutor() → ThreadPool (2-5 threads)

4. Instantiate StudentService
   ├─ @Autowired StudentRepository
   ├─ @Autowired setPrimaryDataSource (set primaryJdbc)
   └─ @PostConstruct initializeSecondaryDb()
      └─ @Async initializeSecondaryDbAsync()
         ├─ CREATE TABLE IF NOT EXISTS students ON Railway
         └─ @Async syncToSecondaryAsync()
            └─ SELECT * FROM renderDB → INSERT INTO railwayDB

5. Instantiate StudentController
   ├─ @Autowired StudentService
   └─ @Autowired DiagnosticService

6. Instantiate DiagnosticService

7. Tomcat Server khởi động
   └─ Listen on port 8080

8. @Scheduled tasks start
   ├─ checkPrimaryDbHealth() (mỗi 5s)
   └─ checkSecondaryDbHealth() (mỗi 5s)

9. ✅ Application Ready
   └─ Chờ HTTP requests trên http://localhost:8080
```

---

## 📊 Database Schema

```sql
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,                    -- Auto-increment
    name VARCHAR(255) NOT NULL,                  -- Họ tên
    email VARCHAR(255) NOT NULL UNIQUE,         -- Email duy nhất
    phone VARCHAR(20),                           -- Số điện thoại
    age INTEGER NOT NULL,                       -- Tuổi (13-100)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- Thời gian tạo
);
```

---

## 🔍 Các File Config Quan Trọng

### **application.properties**
```properties
# Primary DB (Render)
spring.datasource.url=jdbc:postgresql://...&TimeZone=UTC
spring.datasource.username=...
spring.datasource.password=...

# Secondary DB (Railway)
secondary.datasource.url=jdbc:postgresql://...&TimeZone=UTC
secondary.datasource.username=...
secondary.datasource.password=...

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Logging
logging.level.com.example.demo=DEBUG
```

### **pom.xml** (Dependencies)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
</dependency>
```

---

## ✅ Checklist: Các File Đã Add Comment

- [x] DemoApplication.java - χ Main entry point
- [x] DataSourceConfig.java - ⚙️ Configuration + Beans
- [x] Student.java - 📊 Entity
- [x] StudentRepository.java - 🏦 Data Access Layer
- [x] StudentService.java - 🧠 Business Logic
- [x] StudentController.java - 🎛️ API Endpoints

---

## 📞 Kết Luận

**Backend của bạn sử dụng:**
- **Spring Boot 3.x**: Framework chính
- **PostgreSQL**: 2 database (Render + Railway)
- **JPA/Hibernate**: ORM mapping
- **JDBC Template**: Direct SQL queries
- **Async/Threading**: Background tasks
- **Scheduled Tasks**: Health checks (mỗi 5s)
- **Failover Architecture**: Tự động chuyển sang Railway nếu Render down

**Mục tiêu:**
✅ High Availability (HA) - Luôn sẵn sàng phục vụ
✅ Zero Downtime - Không downtime khi Primary DB down
✅ Data Redundancy - Dữ liệu backup ở 2 chỗ
✅ Automatic Recovery - Tự động sync dữ liệu

---

**Cập nhật:** April 7, 2026
**Trạng thái:** ✅ Hoàn tất (All main files documented)

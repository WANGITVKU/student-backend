# 📚 Student Backend API - Spring Boot

REST API Backend cho ứng dụng **quản lý sinh viên** với kiến trúc **dual-database** trên cloud (Render + Railway) đảm bảo high availability và zero downtime.

**Stack:** Spring Boot 3.5 + PostgreSQL + Docker

---

## 🎯 Phần 1: Công dụng chi tiết Backend

### 🔑 Chức năng chính

Backend cung cấp các chức năng sau để quản lý dữ liệu sinh viên:

#### 1. **CRUD Operations - Quản lý Sinh Viên**
   - **GET /api/students** 
     - Lấy danh sách toàn bộ sinh viên từ database
     - Response: `List<Student>`
   
   - **GET /api/students/{id}**
     - Lấy thông tin chi tiết một sinh viên theo ID
     - Response: `Student` hoặc 404 nếu không tìm thấy
   
   - **POST /api/students**
     - Thêm sinh viên mới
     - Request body: `{ "name": "", "email": "", "phone": "", "age": 0 }`
     - Tự động đồng bộ sang database secondary (Railway)
   
   - **PUT /api/students/{id}**
     - Cập nhật thông tin sinh viên
     - Request body: Dữ liệu cần cập nhật
     - Dual-write: cập nhật cả Render và Railway
   
   - **DELETE /api/students/{id}**
     - Xóa sinh viên khỏi database
     - Xóa ở cả 2 database (Render + Railway)

#### 2. **Health Check & Monitor**
   - **GET /api/students/health/status**
     - Kiểm tra sức khỏe của backend và database connections
     - Response: `{ "status": "UP/DOWN", "timestamp": 123456 }`
     - Dùng để monitor từ Kubernetes, Docker, hoặc monitoring tools
   
   - **GET /api/students/health/ping**
     - Simple health check cho container orchestration (quá nhanh, không kiểm tra DB)
     - Response: `✅ Service is running`

#### 3. **Admin & Maintenance Endpoints**
   - **POST /api/students/admin/sync**
     - Đồng bộ dữ liệu từ Primary DB (Render) sang Secondary DB (Railway)
     - Dùng cho emergency sync hoặc disaster recovery
     - Response: `{ "message": "Sync completed", "timestamp": 123456 }`
   
   - **GET /api/students/debug/db-info**
     - Debug endpoint: kiểm tra kết nối database và số lượng records
     - Hiển thị: JPA count, direct JDBC count, database status
     - Response: `{ "jpa_count": 5, "direct_jdbc_count": 5, "primary_db_status": "connected" }`

### 🔄 Dual-Database Architecture (Render + Railway)

| Thành phần | Vai trò | Mô tả |
|-----------|--------|-------|
| **Render PostgreSQL** | Primary DB | Database chính - tất cả read/write mặc định đi đây |
| **Railway PostgreSQL** | Secondary DB | Database dự phòng - tự động sync & failover |
| **Dual-Write Pattern** | Write Logic | Mỗi write operation ghi vào cả 2 database |
| **Automatic Failover** | Failover | Nếu Render down, tự động fallback sang Railway (read-only) |
| **Auto Sync** | Sync Logic | Mỗi 5 giây kiểm tra sync status, tự động đồng bộ nếu có sự khác biệt |

### ⚙️ Async & Non-Blocking Operations

- **Initialization**: Khởi tạo secondary table + sync dữ liệu chạy **async** → không block app startup
- **Sync Operations**: Tất cả sync operations chạy async → request không phải chờ
- **Health Checks**: Chạy scheduled mỗi 5 giây (background)
- **Lợi ích**: App startup nhanh, request không bị delay

---

## 📂 Phần 2: Cấu trúc File và Công dụng từng File

### Project Structure

```
demo/
├── src/main/java/com/example/demo/
│   ├── DemoApplication.java                          # Spring Boot main class
│   └── studentbackend/
│       ├── config/
│       │   └── DataSourceConfig.java                 # ⚙️ [CẤU HÌNH] Secondary DB + Async Executor
│       ├── controller/
│       │   └── StudentController.java                # 🎛️ [API ENDPOINTS] REST controller
│       ├── model/
│       │   └── Student.java                          # 📊 [ENTITY] Student model
│       ├── repository/
│       │   └── StudentRepository.java                # 🏦 [DATABASE] JPA repository
│       └── service/
│           └── StudentService.java                   # 🧠 [BUSINESS LOGIC] Dual-DB logic
├── src/main/resources/
│   ├── application.properties                        # 🔐 [CONFIG] Database URLs, credentials
│   ├── application.yml                               # 📝 [CONFIG] Alternative YAML config
│   ├── static/                                       # 📁 Static files (CSS, JS)
│   └── templates/                                    # 📁 Thymeleaf templates (nếu có)
├── src/test/java/                                    # 🧪 Unit tests
├── pom.xml                                           # 📦 Maven dependencies
├── Dockerfile                                        # 🐳 Docker image config
├── mvnw (Linux/Mac) & mvnw.cmd (Windows)            # 📜 Maven wrapper scripts
└── README.md                                         # 📖 This file

```

### 📄 Chi tiết File quan trọng

#### **1. `DemoApplication.java`**
- **Công dụng**: Spring Boot main class, entry point của ứng dụng
- **Nội dung**: `@SpringBootApplication` annotation + main method
- **Chạy**: `./mvnw spring-boot:run`

#### **2. `DataSourceConfig.java` (⚙️ CẤU HÌNH)**
- **Công dụng**: Cấu hình Secondary DataSource (Railway) + Async Executor
- **Nội dung chính**:
  ```java
  @Bean(name = "secondaryDataSource")     // ← Railway PostgreSQL
  @Bean(name = "secondaryJdbcTemplate")   // ← JdbcTemplate cho Railway
  @Bean(name = "taskExecutor")            // ← ThreadPool cho async operations
  ```
- **Chi tiết**:
  - Đọc `secondary.datasource.*` từ `application.properties`
  - Tạo DataSource cho Railway
  - ThreadPool 2-5 threads, queue 100 tasks
- **Tại sao**: Spring Boot chỉ tự động create primary DataSource, secondary phải config thủ công

#### **3. `StudentService.java` (🧠 BUSINESS LOGIC)**
- **Công dụng**: Xử lý logic dual-database, async operations, sync/failover
- **Phương thức chính**:
  ```java
  getAllStudents()          // Lấy toàn bộ sinh viên từ Primary DB
  getStudentById(id)        // Lấy chi tiết từ Primary DB
  createStudent(student)    // Thêm student: write → Primary + Secondary async
  updateStudent(id, data)   // Cập nhật: dual-write
  deleteStudent(id)         // Xóa: dual-write
  
  // Admin/Health methods:
  getHealthStatus()         // Kiểm tra sức khỏe DB connections
  manualSync()              // Đồng bộ dữ liệu thủ công
  
  // Initialization:
  @PostConstruct initializeSecondaryDb()  // Chạy khi app startup
  @Scheduled(fixedDelay=5000) syncHealth() // Chạy mỗi 5 giây
  ```
- **Dual-Write Logic** (Quan trọng):
  - Write Primary (Render) → **synchronous** (chính)
  - Write Secondary (Railway) → **async** (background)
  - Nếu Primary fail → fallback sang Secondary (read-only)
- **Async Operations**:
  - `@Async` annotation → chạy trong thread pool
  - Không block request
  - Try-catch + log errors

#### **4. `StudentController.java` (🎛️ REST API)**
- **Công dụng**: Định nghĩa REST endpoints và xử lý HTTP requests
- **Endpoints** (đã liệt kê ở Phần 1):
  ```
  GET    /api/students              → getAllStudents()
  GET    /api/students/{id}         → getStudentById(id)
  POST   /api/students              → createStudent()
  PUT    /api/students/{id}         → updateStudent()
  DELETE /api/students/{id}         → deleteStudent()
  
  Bonus Endpoints:
  GET    /api/students/health/status   → Health check
  GET    /api/students/health/ping     → Simple ping
  POST   /api/students/admin/sync      → Manual sync
  GET    /api/students/debug/db-info   → Debug info
  ```
- **Request/Response**: Sử dụng JSON, managed bởi Spring's `@RestController`

#### **5. `Student.java` (📊 ENTITY)**
- **Công dụng**: JPA Entity model, represents bảng `students` trong database
- **Fields**:
  ```java
  @Id @GeneratedValue
  private Long id;        // Primary key, tự tăng
  
  private String name;    // Họ tên sinh viên
  private String email;   // Email (có thể unique)
  private String phone;   // Số điện thoại
  private int age;        // Tuổi
  ```
- **Annotations**:
  - `@Entity` → ORM mapping
  - `@Table(name="students")` → Tên bảng database
  - `@Data` (Lombok) → Tự generate getter/setter/equals/hashCode/toString
  - `@NoArgsConstructor` → Constructor không tham số
  - `@AllArgsConstructor` → Constructor đầy đủ tham số

#### **6. `StudentRepository.java` (🏦 DATABASE)**
- **Công dụng**: JPA Repository interface, tương tác với Primary Database (Render)
- **Extends**: `JpaRepository<Student, Long>`
- **Phương thức** (tự động generate):
  ```java
  findAll()           // SELECT * FROM students
  findById(id)        // SELECT * FROM students WHERE id = ?
  save(student)       // INSERT hoặc UPDATE
  deleteById(id)      // DELETE FROM students WHERE id = ?
  ```
- **Lưu ý**: Repository chỉ dùng cho Primary DB (Render)
  - Secondary (Railway) dùng `@Qualifier("secondaryJdbcTemplate")` trực tiếp

#### **7. `application.properties` (🔐 CONFIG - QUAN TRỌNG)**
- **Công dụng**: Cấu hình database connections, JPA settings, server port
- **Database URLs**:
  ```properties
  # Primary (Render) - Spring Boot tự động nhận
  spring.datasource.url=jdbc:postgresql://dpg-xxx.render.com/db?sslmode=require
  spring.datasource.username=user
  spring.datasource.password=pass
  
  # Secondary (Railway) - Cấu hình thủ công
  secondary.datasource.url=jdbc:postgresql://xxx.railway.app:5432/db
  secondary.datasource.username=postgres
  secondary.datasource.password=pass
  ```
- **JPA Settings**:
  ```properties
  spring.jpa.hibernate.ddl-auto=update    # Auto create/update tables
  spring.jpa.show-sql=true                # Log SQL queries
  spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
  ```
- **Server**:
  ```properties
  server.port=${PORT:8080}                # Port (8080 default, override với env var)
  ```

#### **8. `pom.xml` (📦 MAVEN DEPENDENCIES)**
- **Công dụng**: Quản lý dependencies và build config
- **Dependencies chính**:
  ```xml
  spring-boot-starter-web              <!-- Spring Web MVC -->
  spring-boot-starter-data-jpa         <!-- JPA/Hibernate -->
  spring-boot-starter-actuator         <!-- Health checks -->
  spring-boot-starter-validation       <!-- Input validation -->
  postgresql                           <!-- PostgreSQL driver -->
  lombok                               <!-- Annotations helper -->
  springdoc-openapi                    <!-- Swagger/OpenAPI (tùy chọn) -->
  ```
- **Build**:
  - `<java.version>17</java.version>` → Java 17
  - Maven plugins cho build/package

#### **9. `Dockerfile` (🐳 CONTAINER)**
- **Công dụng**: Build Docker image để deploy lên cloud (Render, Railway, Vercel, etc.)
- **2 Stages**:
  - **Builder Stage**: Compile code, build JAR
  - **Runtime Stage**: Copy JAR vào runtime image (nhẹ hơn)
- **Output**: JAR file chạy trên port 8080

---

## 🚀 Phần 3: Chạy Backend Locally và Deploy lên Cloud qua GitHub

### 📌 Yêu cầu hệ thống

Để chạy backend locally, bạn cần:
- **Java 17+**: `java -version`
- **Maven** (hoặc dùng `mvnw` - Maven wrapper đã kèm theo project)
- **PostgreSQL** (hoặc dùng Render/Railway connection string) - ✅ Không cần cài local nếu dùng cloud DB

### 🖥️ Chạy Backend Locally

#### **Step 1: Configure Database (Optional - nếu muốn test local)**

Nếu muốn test với local PostgreSQL:
1. Cài PostgreSQL
2. Tạo database: `createdb student_db`
3. Update `application.properties`:
   ```properties
   # Local PostgreSQL
   spring.datasource.url=jdbc:postgresql://localhost:5432/student_db?sslmode=disable
   spring.datasource.username=postgres
   spring.datasource.password=your_local_password
   
   # Secondary (có thể trỏ sang local khác port hoặc comment out)
   secondary.datasource.url=...
   ```

**Hoặc** (Dễ hơn) - Dùng cloud database URLs trực tiếp:
- Bỏ qua bước này, `application.properties` đã có Render + Railway URLs

#### **Step 2: Clone Repository từ GitHub**

```bash
# Clone project
git clone https://github.com/YOUR_USERNAME/DTDM.git
cd DTDM/demo

# Xem branch hiện tại
git status
```

#### **Step 3: Build Project**

```bash
# Option 1: Dùng Maven wrapper (recommended - không cần cài Maven)
./mvnw clean package          # Windows: .\mvnw.cmd clean package

# Option 2: Dùng Maven (cần cài Maven trước)
mvn clean package
```

**Output**: `target/demo-0.0.1-SNAPSHOT.jar` ✅

#### **Step 4: Chạy Backend**

```bash
# Cách 1: Chạy trực tiếp từ Spring Boot
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run

# Cách 2: Chạy từ JAR đã build
java -jar target/demo-0.0.1-SNAPSHOT.jar

# Cách 3: Chạy với environment variables (cho cloud URLs)
SET SPRING_DATASOURCE_URL=jdbc:postgresql://render-url/db  # Windows
export SPRING_DATASOURCE_URL="..."                          # Linux/Mac
```

**Output**:
```
  ╔═════════════════════════════════════╗
  ║   Demo Application Started         ║
  ╚═════════════════════════════════════╝
  
Tomcat started on port: 8080 ✅
```

#### **Step 5: Test Backend Locally**

```bash
# Lấy danh sách sinh viên
curl http://localhost:8080/api/students

# Thêm sinh viên mới
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{"name":"Nguyễn Văn A","email":"a@test.com","phone":"0123456789","age":20}'

# Health check
curl http://localhost:8080/api/students/health/status

# View ở browser
Open: http://localhost:8080/api/students
```

---

### 🌐 Deploy lên Cloud (Render / Railway) qua GitHub

#### **Step 1: Prepare GitHub Repository**

```bash
# 1.1: Initialize git (nếu chưa có)
cd d:\VS\DTDM
git init
git add .
git commit -m "Initial commit: Student Backend with dual-database"

# 1.2: Add remote (thay YOUR_USERNAME, REPO_NAME)
git remote add origin https://github.com/YOUR_USERNAME/DTDM.git

# 1.3: Push lên GitHub
git branch -M main
git push -u origin main
```

✅ **GitHub Repository ready!**

---

#### **Step 2: Deploy lên Render (Primary Database)**

##### **2.1: Tạo PostgreSQL Database trên Render**

1. Đăng nhập [render.com](https://render.com)
2. Dashboard → **New** → **PostgreSQL**
3. Điền thông tin:
   - **Name**: `student-db`
   - **Region**: Singapore (gần hơn)
   - **PostgreSQL Version**: 16
   - Để default khác
4. Click **Create Database**
5. **Copy connection string**:
   - URL format: `postgresql://user:password@host:port/dbname?sslmode=require`
   - **Save lại** cho Step 2.3

##### **2.2: Deploy Spring Boot Backend lên Render**

1. Dashboard → **New** → **Web Service**
2. Kết nối GitHub:
   - **Connect account** → GitHub
   - **Select repository** → `DTDM`
   - **Branch**: `main`
3. Điền thông tin:
   - **Name**: `student-backend`
   - **Environment**: `Docker` (Render sẽ dùng Dockerfile)
   - **Region**: Singapore
   - **Pricing**: Free tier (tạm được)
4. **Advanced**:
   - **Auto-deploy**: ✅ Enable (mỗi push đến main → auto deploy)
5. Click **Create Web Service** → Render tự build & deploy
6. **Wait** ~2-3 phút → Backend link sẵn sàng

##### **2.3: Cấu hình Environment Variables (Quan trọng!)**

Sau khi service được tạo:
1. Vào **Settings** → **Environment**
2. Thêm biến:
   ```
   SPRING_DATASOURCE_URL = postgresql://user:password@host:port/dbname?sslmode=require
   SPRING_DATASOURCE_USERNAME = user
   SPRING_DATASOURCE_PASSWORD = password
   SPRING_DATASOURCE_DRIVER_CLASS_NAME = org.postgresql.Driver
   ```
3. Click **Save** → Auto redeploy với biến mới

⚠️ **HOẶC** Update `application.properties` cục bộ → commit → push:
```properties
spring.datasource.url=${SPRING_DATASOURCE_URL}  # Environment variable
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
```

---

#### **Step 3: Deploy Secondary Database lên Railway**

##### **3.1: Tạo PostgreSQL trên Railway**

1. Đăng nhập [railway.app](https://railway.app)
2. **New Project** → **Deploy from GitHub** → Chọn DTDM repo
3. **Add PostgreSQL plugin**:
   - Railway → **Plugins** → **Add** → **PostgreSQL**
4. **Get connection string**:
   - Plugin → **PostgreSQL** → **Logs** → Copy connection URL
   - Format: `postgresql://user:password@host:port/db`

##### **3.2: Update Secondary Datasource**

Update `application.properties`:
```properties
# Secondary DB (Railway)
secondary.datasource.url=postgresql://user:password@railway.host:port/railway?sslmode=require
secondary.datasource.username=user
secondary.datasource.password=password
```

**Commit & Push**:
```bash
git add application.properties
git commit -m "Configure Railway secondary database"
git push origin main
```

✅ Render sẽ auto-redeploy với config mới!

---

#### **Step 4: Verify Cloud Deployment**

Kiểm tra backend chạy trên cloud:

```bash
# Test API (thay YOUR_RENDER_URL)
curl https://student-backend-xxx.onrender.com/api/students

# Health check
curl https://student-backend-xxx.onrender.com/api/students/health/status

# Debug info
curl https://student-backend-xxx.onrender.com/api/students/debug/db-info
```

**Expected Response**:
```json
{
  "status": "UP",
  "timestamp": 1712500000000,
  "service": "StudentService"
}
```

✅ **Backend running on cloud!**

---

### 📋 Workflow Update Code & Redeploy

Mỗi khi bạn thay đổi code:

```bash
# 1. Edit code locally
# 2. Build & test local
./mvnw clean package
./mvnw spring-boot:run

# 3. Commit & push GitHub
git add .
git commit -m "Update: [describe changes]"
git push origin main

# 4. Auto-deploy (Render)
# ✅ Render detects push → Auto rebuild & deploy (2-3 phút)
# ✅ Railway detects push → Auto rebuild & deploy

# 5. Verify
curl https://student-backend-xxx.onrender.com/api/students
```

---

### 🔧 Troubleshooting

| Vấn đề | Giải pháp |
|--------|----------|
| **Build fail trên Render** | Kiểm tra logs: Render dashboard → Logs tab; Thường là Java version hoặc dependency issue |
| **Database connection fail** | Check `application.properties` → Database URL, username, password phải đúng; Verify SSL mode |
| **Port conflict local (8080)** | `./mvnw spring-boot:run -Dserver.port=8081` |
| **Git push fail** | `git pull origin main` trước, resolve conflicts, rồi push lại |
| **Secondary DB không sync** | Đủ RAM/CPU không? Render/Railway quota hết không? Kiểm tra `POST /api/students/admin/sync` |

---

### 📚 Lệnh hữu dụng

```bash
# Build
./mvnw clean package              # Build JAR
./mvnw clean compile             # Chỉ compile (không build JAR)

# Run local
./mvnw spring-boot:run           # Development mode
java -jar target/demo*.jar       # Production mode

# Test
./mvnw test                      # Run unit tests
curl http://localhost:8080/api/students  # Manual test API

# Docker (tùy chọn - nếu chạy local)
docker build -t student-backend .
docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... student-backend

# Git
git status                        # Check status
git add .                        # Stage changes
git commit -m "..."              # Commit
git push origin main             # Push to GitHub
```

---

## 💾 Database Schema (PostgreSQL)

```sql
CREATE TABLE students (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(20),
    age INTEGER
);
```

**Auto-tạo**: Spring Boot tự động tạo bảng khi `spring.jpa.hibernate.ddl-auto=update` ✅

---

## ✅ Checklist Deployment

- [ ] code pushed to GitHub
- [ ] Render PostgreSQL database created
- [ ] Render Web Service created + configured
- [ ] Environment variables set on Render
- [ ] Railway PostgreSQL created (backup)
- [ ] Secondary datasource configured
- [ ] Health check endpoint responding: `/api/students/health/status`
- [ ] CRUD operations tested
- [ ] Frontend connected to backend URL

---

**Status**: 🟢 Backend Production Ready on Cloud!

Cho bất kỳ câu hỏi, xem logs trên **Render Dashboard** hoặc **Railway Dashboard**.


- **Spring Boot 3.5.11**
- **Spring Data JPA**
- **PostgreSQL** (Render + Railway)
- **Lombok**
- **Maven**

## 📖 Hướng dẫn chi tiết

### Cách hoạt động

1. **Khởi động**:
   - Spring Boot tự động cấu hình Primary DB (Render) từ `spring.datasource.*`
   - `DataSourceConfig` tạo Secondary DB (Railway) từ `secondary.datasource.*`
   - `StudentService` tự động sync dữ liệu từ Render sang Railway (async)

2. **Đọc dữ liệu**:
   - Ưu tiên đọc từ Render (Primary)
   - Nếu Render down → tự động fallback sang Railway
   - Health check mỗi 5 giây để phát hiện sự cố

3. **Ghi dữ liệu**:
   - Luôn ghi vào Render trước (Primary)
   - Async ghi vào Railway (Secondary)
   - Nếu Railway fail → không ảnh hưởng request, data sẽ sync sau

### Lưu ý quan trọng

- **Timezone**: Phải set `TimeZone=UTC` trong connection URL hoặc JVM argument để tránh lỗi với PostgreSQL
- **Không commit** thông tin database lên GitHub
- Luôn sử dụng environment variables khi deploy
- Railway free tier có giới hạn (500 hours/tháng, 1GB storage)

## 🔍 Monitoring

### Logs theo dõi:
- `✅ Secondary table created/verified` - Railway table đã được tạo
- `✅ Found X students in primary DB` - Sync dữ liệu thành công
- `✅ Primary DB is healthy` - Render DB hoạt động tốt
- `⚠️ Primary DB health check failed` - Render DB có vấn đề
- `⚠️ Secondary DB write failed` - Railway write fail (sẽ sync sau)

### Health endpoints:
- `GET /api/students/health/status` - Trạng thái chi tiết
- `GET /api/students/health/ping` - Simple ping check
- `POST /api/students/admin/sync` - Manual sync (emergency)

## 📄 License

MIT License

---

**Developed with ❤️ using Spring Boot**
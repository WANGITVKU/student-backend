# 🔧 Debug Notes - Student Backend

## ❌ Lỗi gặp phải khi chạy ứng dụng

### Thời điểm: 2026-04-01 14:15:10

### Mô tả lỗi:
Khi khởi động ứng dụng Spring Boot, kết nối đến PostgreSQL database trên Render thất bại với lỗi:

```
FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
```

### Chi tiết:
- **Database**: PostgreSQL trên Render (Primary DB)
- **URL**: `jdbc:postgresql://dpg-d6o28s7afjfc73aqo55g-a.singapore-postgres.render.com/dtdm_udfr?sslmode=require`
- **Lỗi**: PostgreSQL server không chấp nhận timezone "Asia/Saigon"

### Nguyên nhân:
- Hệ điều hành Windows đang set timezone là "Asia/Saigon"
- JVM tự động sử dụng timezone của hệ thống khi kết nối PostgreSQL
- PostgreSQL server trên Render không hỗ trợ timezone "Asia/Saigon"

### Giải pháp đề xuất:

#### 1. Thêm TimeZone vào connection URL
```properties
spring.datasource.url=jdbc:postgresql://dpg-d6o28s7afjfc73aqo55g-a.singapore-postgres.render.com/dtdm_udfr?sslmode=require&TimeZone=UTC
```

#### 2. Set JVM argument khi chạy
```bash
java -Duser.timezone=UTC -jar target/demo-0.0.1-SNAPSHOT.jar
```

#### 3. Sử dụng timezone được hỗ trợ
Các timezone thường được hỗ trợ bởi PostgreSQL:
- `UTC`
- `Asia/Ho_Chi_Minh` (có thể thử)
- `GMT+7`

### Các file liên quan:
- `src/main/resources/application.properties` - Cấu hình datasource
- `src/main/java/com/example/demo/studentbackend/config/SecondaryDataSourceConfig.java` - Cấu hình Secondary DB

### Kiến trúc hiện tại:
- **Primary DB (Render)**: Spring Boot tự động cấu hình qua `spring.datasource.*`
- **Secondary DB (Railway)**: Cấu hình thủ công qua `SecondaryDataSourceConfig`
- **Cơ chế**: 
  - Đọc từ Primary, fallback sang Secondary khi Primary down
  - Ghi vào cả 2 DB đồng thời
  - Tự động sync dữ liệu khi khởi động

### Log lỗi đầy đủ:
```
org.postgresql.util.PSQLException: FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"
    at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2875)
    at org.postgresql.core.v3.QueryExecutorImpl.readStartupMessages(QueryExecutorImpl.java:3000)
    ...
Caused by: org.hibernate.exception.DataException: Unable to open JDBC Connection for DDL execution
[FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"]
```

### Trạng thái:
- [x] Đã xác định nguyên nhân
- [ ] Chưa sửa code (theo yêu cầu)
- [ ] Cần thêm TimeZone parameter vào connection URL

### Tài liệu tham khảo:
- [PostgreSQL Timezone Documentation](https://www.postgresql.org/docs/current/datatype-datetime.html)
- [Spring Boot DataSource Configuration](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#data.sql.datasource.configuration)
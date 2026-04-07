# Hướng dẫn Setup Railway PostgreSQL làm Backup Database

## Tổng quan
Project được cấu hình để sử dụng 2 database:
- **Primary DB (Render)**: Database chính, tất cả các thao tác ghi đều phải thành công
- **Secondary DB (Railway)**: Database dự phòng, dùng để backup và fallback khi Primary gặp sự cố

## Bước 1: Tạo Database trên Railway

1. Truy cập [railway.app](https://railway.app)
2. Đăng nhập bằng GitHub account
3. Click **New Project**
4. Chọn **Add a service** → **Database** → **PostgreSQL**
5. Đợi database được tạo (thường mất 1-2 phút)

## Bước 2: Lấy thông tin kết nối

Sau khi tạo xong PostgreSQL service:

1. Vào PostgreSQL service → tab **Variables**
2. Copy các thông tin sau:
   ```
   PGHOST=xxx.railway.app
   PGPORT=5432
   PGDATABASE=railway
   PGUSER=postgres
   PGPASSWORD=xxx
   ```
3. Hoặc copy thẳng **DATABASE_URL** dạng:
   ```
   postgresql://postgres:password@xxx.railway.app:5432/railway
   ```

## Bước 3: Cấu hình Environment Variables trên Render

Vào **Render Dashboard** → **Environment**:

Thêm các biến môi trường sau:

```properties
# Primary Database (Render)
PRIMARY_DB_URL=jdbc:postgresql://dpg-xxx.render.com/db?sslmode=require
PRIMARY_DB_USER=your_user
PRIMARY_DB_PASS=your_password

# Secondary Database (Railway)
SECONDARY_DB_URL=jdbc:postgresql://xxx.railway.app:5432/railway?sslmode=require
SECONDARY_DB_USER=postgres
SECONDARY_DB_PASS=your_railway_password

# Port (optional)
PORT=8080
```

## Bước 4: Kiểm tra kết nối

Sau khi deploy, kiểm tra logs để đảm bảo cả 2 database đều kết nối thành công.

## Cấu trúc thư mục

```
src/main/java/com/example/demo/studentbackend/
├── config/
│   ├── PrimaryDataSourceConfig.java      # Cấu hình Primary DB (Render)
│   └── SecondaryDataSourceConfig.java    # Cấu hình Secondary DB (Railway)
├── model/
│   └── Student.java                      # Entity chung cho cả 2 DB
├── repository/
│   ├── primary/
│   │   └── StudentPrimaryRepository.java    # Repository cho Primary DB
│   └── secondary/
│       └── StudentSecondaryRepository.java  # Repository for Secondary DB
├── service/
│   └── StudentService.java               # Service với logic dual-database
└── controller/
    └── StudentController.java            # REST API Controller
```

## Logic hoạt động

### Khi ghi dữ liệu (Create/Update/Delete):
1. **Bắt buộc** ghi thành công vào Primary DB (Render)
2. Thử ghi vào Secondary DB (Railway) - nếu lỗi sẽ log warning nhưng không fail request

### Khi đọc dữ liệu (Read):
1. Thử đọc từ Primary DB (Render)
2. Nếu Primary gặp sự cố → tự động fallback sang Secondary DB (Railway)

## Lợi ích

✅ **High Availability**: Luôn có dữ liệu để đọc ngay cả khi Primary DB down
✅ **Backup tự động**: Dữ liệu luôn được sao lưu sang Railway
✅ **Fault Tolerance**: Hệ thống tự động chuyển sang backup khi cần
✅ **Zero Downtime**: Không gián đoạn dịch vụ khi Primary DB gặp sự cố

## Lưu ý quan trọng

⚠️ **Không commit thông tin database lên GitHub!**
- Luôn sử dụng Environment Variables
- File `application.properties` chỉ chứa placeholder

⚠️ **Railway free tier có giới hạn:**
- 500 hours/tháng (khoảng 20 ngày)
- 1GB storage
- Nên upgrade lên paid plan cho production

## Xử lý sự cố

### Lỗi kết nối Railway:
- Kiểm tra DATABASE_URL có đúng format không
- Đảm bảo SSL mode: `sslmode=require`
- Kiểm tra firewall/network settings

### Lỗi đồng bộ dữ liệu:
- Kiểm tra logs để xem chi tiết lỗi
- Có thể cần manual sync nếu có sự khác biệt lớn

### Primary DB down:
- Hệ thống tự động fallback sang Railway
- Khi Primary hoạt động lại, dữ liệu mới sẽ được ghi vào cả 2

## Monitoring

Theo dõi logs để đảm bảo:
- ✅ "Saved to Railway backup" - Backup thành công
- ⚠️ "Railway backup failed" - Backup thất bại (cần kiểm tra)
- ⚠️ "Primary down, reading from Railway" - Đang dùng backup

## Cập nhật trong tương lai

Nếu muốn thay đổi:
- **Primary DB**: Cập nhật `PRIMARY_DB_*` environment variables
- **Secondary DB**: Cập nhật `SECONDARY_DB_*` environment variables
- **Logic**: Sửa trong `StudentService.java`

## Hỗ trợ

Nếu gặp vấn đề:
1. Kiểm tra logs trên Render Dashboard
2. Xem Railway dashboard để kiểm tra database status
3. Đảm bảo environment variables được set đúng
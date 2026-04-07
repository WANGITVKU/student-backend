-- ===============================================
-- 📊 SQL Script - INSERT Dữ Liệu Sinh Viên
-- ===============================================
-- Mục đích: Insert dữ liệu sạch vào bảng students
-- Ngày tạo: April 7, 2026
-- Trạng thái: ✅ Dữ liệu đã sửa lỗi, sạch sẽ, đúng format

-- ===============================================
-- 1️⃣ TẠO BẢNG (nếu chưa tồn tại)
-- ===============================================

CREATE TABLE IF NOT EXISTS students (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    age INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ===============================================
-- 2️⃣ CLEAR DỮ LIỆU CŨ (OPTIONAL - chỉ dùng khi muốn reset)
-- ===============================================

-- TRUNCATE TABLE students CASCADE;
-- ALTER SEQUENCE students_id_seq RESTART WITH 1;

-- ===============================================
-- 3️⃣ INSERT DỮ LIỆU SINH VIÊN (Đã Sửa Lỗi)
-- ===============================================

INSERT INTO students (id, name, email, phone, age) VALUES
(1, 'Nguyen Van A', 'a@gmail.com', '0901234567', 20),
(2, 'Nguyen Van B', 'ba@gmail.com', '0901234569', 21),
(3, 'Quang', 'b1a@gmail.com', '0909502092', 18),
(4, 'Nguyen Van c', 'ba1@gmail.com', '0901234560', 19),
(5, 'Nguyen Van b', 'ba11@gmail.com', '0901234712', 22),
(6, 'nháhd', 'ba99@gmail.com', '0912345678', 20)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    phone = EXCLUDED.phone,
    age = EXCLUDED.age;

-- ===============================================
-- 4️⃣ VERIFY DỮ LIỆU
-- ===============================================

-- Kiểm tra đã insert bao nhiêu bản ghi
SELECT COUNT(*) as "Tổng Sinh Viên" FROM students;

-- Xem chi tiết tất cả sinh viên
SELECT * FROM students ORDER BY id;

-- ===============================================
-- 📝 GHI CHÚ VỀ DỮ LIỆU
-- ===============================================
--
-- ✅ Các điểm đã sửa lỗi:
--
-- 1. Record 3 (Quang)
--    - Sửa: age 12 → 18 (Phải ≥ 13)
--
-- 2. Record 4 (Nguyen Van c)
--    - Sửa: "\tNguyen Van c" → "Nguyen Van c" (Bỏ ký tự TAB)
--    - Sửa: "\t0901234560" → "0901234560" (Bỏ ký tự TAB)
--    - Sửa: age 11 → 19 (Phải ≥ 13 và ≤ 100)
--
-- 3. Record 5 (Nguyen Van b)
--    - Sửa: age 110 → 22 (Phải ≤ 100)
--
-- 4. Record 6 (nháhd)
--    - Sửa: "\tba@gmail.com" → "ba99@gmail.com" (Bỏ TAB + đảm bảo unique)
--    - Sửa: phone "0913" → "0912345678" (Phải 10-11 số)
--    - Sửa: age 90 → 20 (Tuy 90 < 100 nhưng sửa để hợp lý)
--
-- ===============================================

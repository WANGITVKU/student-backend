# 🔧 Backend Troubleshooting Guide

## ❌ Problem: Cannot add a new student to database

Your database appears to be empty and data isn't persisting. Here are the most common causes and solutions.

---

## 🔍 Step 1: Run Diagnostics

First, build and start your backend, then run this diagnostic endpoint:

```bash
# With curl
curl -X GET http://localhost:8080/api/students/debug/diagnostics

# With PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/students/debug/diagnostics" -Method GET | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

This will give you a detailed report showing:
- ✅ Primary DB (Render) connection status
- ✅ Secondary DB (Railway) connection status
- ✅ Table structure in both databases
- ✅ Data consistency between databases
- 🎯 Specific recommendations

---

## 🛠️ Step 2: Initialize Database (If Tables Don't Exist)

If the diagnostics show **❌ TABLE MISSING**, run:

```bash
curl -X POST http://localhost:8080/api/students/debug/init-db

# PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/students/debug/init-db" -Method POST | Select-Object -ExpandProperty Content | ConvertFrom-Json | ConvertTo-Json
```

This will create the `students` table in both databases.

---

## 📊 Step 3: Quick Health Check

After initializing, verify everything is working:

```bash
curl -X GET http://localhost:8080/api/students/debug/quick-check

# PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/students/debug/quick-check" -Method GET | Select-Object -ExpandProperty Content
```

---

## ✅ Step 4: Test Adding a Student

Once diagnostics are green, try adding a student:

```bash
# With curl
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{"name":"John Doe","email":"john@example.com","phone":"0123456789","age":20}'

# With PowerShell
$student = @{
    name = "John Doe"
    email = "john@example.com"
    phone = "0123456789"
    age = 20
} | ConvertTo-Json

Invoke-WebRequest -Uri "http://localhost:8080/api/students" -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body $student | Select-Object -ExpandProperty Content
```

---

## 🚀 All API Endpoints

| Endpoint | Method | Purpose | When to Use |
|----------|--------|---------|-------------|
| `/api/students` | GET | List all students | Check if data exists |
| `/api/students` | POST | Add new student | Create student |
| `/api/students/{id}` | PUT | Update student | Modify existing student |
| `/api/students/{id}` | DELETE | Delete student | Remove student |
| `/api/students/health/status` | GET | Full health status | Monitor system |
| `/api/students/debug/diagnostics` | GET | **Comprehensive diagnostics** | **🟢 START HERE** |
| `/api/students/debug/init-db` | POST | **Initialize tables** | **If tables missing** |
| `/api/students/debug/quick-check` | GET | Quick health check | Verify after changes |
| `/api/students/admin/sync` | POST | Manual sync Render→Railway | Emergency sync |
| `/api/students/admin/reset-secondary` | POST | Clear Railway and re-sync | If Railway has stale data |

---

## 🔴 Common Issues & Solutions

### Issue #1: "Cannot connect to database"

**Diagnostics output:**
```
"primary_db": {
  "connection": "❌ FAILED",
  "error": "Connection refused"
}
```

**Solutions:**
1. **Check Render instance is running**
   - Go to https://dashboard.render.com
   - Verify your PostgreSQL database is "Available" (not "Suspended")
   
2. **Verify credentials in application.properties**
   ```properties
   spring.datasource.url=jdbc:postgresql://dpg-d6o28s7afjfc73aqo55g-a...
   spring.datasource.username=dtdm_udfr_user
   spring.datasource.password=f0kvrjLPJPjQL6WvUmZPWa4Ap6C90pQ3
   ```

3. **Check timezone issues**
   - Ensure `TimeZone=UTC` is in the connection URL (it is in current config)
   - Do NOT use `Asia/Saigon` or `Asia/Ho_Chi_Minh` in the JDBC URL

4. **Test connection directly**
   - Open pgAdmin or psql client
   - Try connecting to the Render database with same credentials

---

### Issue #2: "Table 'students' not found"

**Diagnostics output:**
```
"table_status": {
  "primary": {
    "table_exists": "❌ NO",
    "record_count": 0,
    "status": "❌ TABLE MISSING"
  }
}
```

**Solutions:**
1. **Hibernate DDL auto should have created it**
   - Check `application.properties`: `spring.jpa.hibernate.ddl-auto=update`
   - This setting tells Hibernate to create/update tables on startup

2. **Manually create table**
   - Run: `POST /api/students/debug/init-db`
   - Or use psql:
   ```sql
   CREATE TABLE IF NOT EXISTS students (
       id BIGSERIAL PRIMARY KEY,
       name VARCHAR(255),
       email VARCHAR(255) UNIQUE,
       phone VARCHAR(255),
       age INTEGER
   );
   ```

3. **Restart backend and check logs**
   - Look for: `CREATE TABLE students...` in startup logs
   - If not present, Hibernate may have skipped it

---

### Issue #3: "Data inserted but doesn't persist / disappears"

**Symptoms:**
- `POST /api/students` returns success
- But `GET /api/students` shows empty list
- Or data appears but disappears on refresh

**Cause:** Transaction not being committed

**Solutions:**

1. **Check if table actually received the data**
   ```bash
   curl http://localhost:8080/api/students/debug/db-info
   ```
   Look for `jpa_count` and `direct_jdbc_count` - should both be > 0

2. **Verify auto-commit is enabled**
   - In `application.properties`, this should be enabled by default
   - If using manual transactions, check `@Transactional` annotations

3. **Check primary key conflict**
   - If you see error about duplicate IDs, this could cause silent failures
   - The ID should be `BIGSERIAL` (auto-generated)

4. **Look at application logs**
   - Run backend with: `./mvnw spring-boot:run -X`
   - Look for SQL execution logs and any errors

---

### Issue #4: "Primary and Secondary databases are out of sync"

**Diagnostics output:**
```
"data_verification": {
  "counts_match": "❌ MISMATCH",
  "primary_count": 5,
  "secondary_count": 2
}
```

**Solutions:**
1. **Trigger manual sync**
   ```bash
   curl -X POST http://localhost:8080/api/students/admin/sync
   ```

2. **If Railway has stale data, reset it**
   ```bash
   curl -X POST http://localhost:8080/api/students/admin/reset-secondary
   ```

3. **Check if secondary DB is down**
   - Verify Railway database credentials in `application.properties`
   - Test connection to Railway manually

---

## 📋 Database Configuration Checklist

Use this checklist to verify your setup:

```
Backend Configuration (application.properties)
─────────────────────────────────────────
☐ Primary DB URL has timeZone=UTC parameter
☐ Secondary DB URL has timeZone=UTC parameter  
☐ Both URLs have sslmode=require
☐ Username/password credentials are correct
☐ spring.jpa.hibernate.ddl-auto=update (not "create" or "validate")
☐ Connection pool size is reasonable (default 10)

Database State
─────────────
☐ students table exists in primary DB
☐ students table exists in secondary DB
☐ Both tables have same schema (id BIGSERIAL, name, email, phone, age)
☐ Records count matches between databases (or very close)
☐ No errors in error logs

Spring Boot Application
──────────────────────
☐ Application starts without connection errors
☐ Logs show "✅ Initializing secondary database"
☐ No "❌ FATAL" errors for timezone or connection
☐ Health endpoints respond correctly
```

---

## 🧪 Testing Checklist

Follow this step-by-step to get everything working:

### Phase 1: Verify Database Connectivity
```bash
# 1. Check diagnostics
curl http://localhost:8080/api/students/debug/diagnostics

# Expected output should show both DBs as "✅ CONNECTED"
```

### Phase 2: Initialize Database (if needed)
```bash
# 2. If tables missing, initialize
curl -X POST http://localhost:8080/api/students/debug/init-db

# Expected: "✅ students table created/verified"
```

### Phase 3: Verify System is Ready
```bash
# 3. Quick health check
curl http://localhost:8080/api/students/debug/quick-check

# Expected: "consistent": true, record counts match or close
```

### Phase 4: Test CRUD Operations
```bash
# 4a. Add a student
curl -X POST http://localhost:8080/api/students \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@example.com","phone":"0123456789","age":20}'

# 4b. Get list (should show 1 student)
curl http://localhost:8080/api/students

# 4c. Update student (get ID from previous response)
curl -X PUT http://localhost:8080/api/students/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated","email":"updated@example.com","phone":"0123456789","age":21}'

# 4d. Delete student
curl -X DELETE http://localhost:8080/api/students/1

# 4e. Verify it's gone
curl http://localhost:8080/api/students
```

---

## 📞 Still Having Issues?

1. **Collect diagnostics output**
   ```bash
   curl http://localhost:8080/api/students/debug/diagnostics > diagnostics.json
   ```

2. **Check application logs** with detailed output
   ```bash
   ./mvnw spring-boot:run 2>&1 | tee app.log
   ```

3. **Verify from database client**
   - Use pgAdmin or psql to manually query the database
   - Check if `students` table exists
   - Count records: `SELECT COUNT(*) FROM students;`

4. **Common error messages and fixes**
   - `FATAL: invalid value for parameter "TimeZone"` → Remove unsupported timezone from URL
   - `Connection refused` → Verify database is running and accessible
   - `table not found` → Run `POST /api/students/debug/init-db`
   - `duplicate key value violates unique constraint` → Check email field uniqueness

---

## 🚀 Next Steps

Once you've verified everything is working:

1. ✅ Add more test students
2. ✅ Test frontend integration (`student-frontend/`)
3. ✅ Deploy to cloud (Render + Railway)
4. ✅ Monitor health with `/api/students/health/status`

---

**Last Updated:** April 2026
**Maintenance:** Includes comprehensive diagnostic endpoints and troubleshooting guide

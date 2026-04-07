package com.example.demo.studentbackend.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.*;

@Service
public class DiagnosticService {
    private static final Logger log = LoggerFactory.getLogger(DiagnosticService.class);

    @Autowired
    @Qualifier("primaryJdbc")
    private JdbcTemplate primaryJdbc;

    @Autowired
    @Qualifier("secondaryJdbcTemplate")
    private JdbcTemplate secondaryJdbc;

    /**
     * 🔍 Comprehensive diagnostics report
     */
    public Map<String, Object> runFullDiagnostics() {
        Map<String, Object> report = new LinkedHashMap<>();
        
        report.put("timestamp", System.currentTimeMillis());
        report.put("primary_db", testPrimaryDatabase());
        report.put("secondary_db", testSecondaryDatabase());
        report.put("table_status", checkTableStatus());
        report.put("data_verification", verifyData());
        report.put("connection_details", getConnectionDetails());
        report.put("recommendations", generateRecommendations(report));

        return report;
    }

    /**
     * ✅ Test Primary Database Connection
     */
    private Map<String, Object> testPrimaryDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Primary DB (Render)");
        
        try {
            // Test 1: Basic connection
            Integer ping = primaryJdbc.queryForObject("SELECT 1", Integer.class);
            result.put("connection", "✅ CONNECTED");
            
            // Test 2: Version
            String version = primaryJdbc.queryForObject("SELECT version()", String.class);
            result.put("version", version != null ? version.substring(0, Math.min(50, version.length())) : "Unknown");
            
            // Test 3: Current user
            String currentUser = primaryJdbc.queryForObject("SELECT current_user", String.class);
            result.put("current_user", currentUser);
            
            // Test 4: Response time
            long startTime = System.currentTimeMillis();
            primaryJdbc.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            result.put("response_time_ms", responseTime);
            result.put("status", "✅ HEALTHY");
            
        } catch (Exception e) {
            result.put("connection", "❌ FAILED");
            result.put("error", e.getMessage());
            result.put("error_class", e.getClass().getSimpleName());
            result.put("status", "❌ ERROR");
            
            // Try to get more details
            Throwable cause = e.getCause();
            if (cause != null) {
                result.put("root_cause", cause.getMessage());
            }
        }
        
        return result;
    }

    /**
     * ✅ Test Secondary Database Connection
     */
    private Map<String, Object> testSecondaryDatabase() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "Secondary DB (Railway)");
        
        try {
            // Test 1: Basic connection
            Integer ping = secondaryJdbc.queryForObject("SELECT 1", Integer.class);
            result.put("connection", "✅ CONNECTED");
            
            // Test 2: Version
            String version = secondaryJdbc.queryForObject("SELECT version()", String.class);
            result.put("version", version != null ? version.substring(0, Math.min(50, version.length())) : "Unknown");
            
            // Test 3: Current user
            String currentUser = secondaryJdbc.queryForObject("SELECT current_user", String.class);
            result.put("current_user", currentUser);
            
            // Test 4: Response time
            long startTime = System.currentTimeMillis();
            secondaryJdbc.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            result.put("response_time_ms", responseTime);
            result.put("status", "✅ HEALTHY");
            
        } catch (Exception e) {
            result.put("connection", "❌ FAILED");
            result.put("error", e.getMessage());
            result.put("error_class", e.getClass().getSimpleName());
            result.put("status", "❌ ERROR");
        }
        
        return result;
    }

    /**
     * ✅ Check if 'students' table exists and has correct schema
     */
    private Map<String, Object> checkTableStatus() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("primary", checkTableInPrimary());
        result.put("secondary", checkTableInSecondary());
        return result;
    }

    private Map<String, Object> checkTableInPrimary() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            // Check if table exists
            Integer tableCount = primaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'students'",
                Integer.class
            );
            
            if (tableCount == 0) {
                result.put("table_exists", "❌ NO");
                result.put("reason", "Table 'students' not found in primary DB");
                result.put("record_count", 0);
                result.put("status", "❌ TABLE MISSING");
                return result;
            }
            
            result.put("table_exists", "✅ YES");
            
            // Get schema
            List<Map<String, Object>> columns = primaryJdbc.queryForList(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'students' ORDER BY ordinal_position"
            );
            
            List<String> expectedColumns = Arrays.asList("id", "name", "email", "phone", "age");
            List<String> actualColumns = new ArrayList<>();
            for (Map<String, Object> col : columns) {
                actualColumns.add((String) col.get("column_name"));
            }
            
            result.put("columns", columns);
            
            // Check columns
            boolean schemaValid = expectedColumns.stream().allMatch(col -> actualColumns.contains(col));
            result.put("schema_valid", schemaValid ? "✅ YES" : "❌ INCOMPLETE");
            
            // Record count
            Integer count = primaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            result.put("record_count", count != null ? count : 0);
            
            result.put("status", count > 0 ? "✅ OK - Has data" : "⚠️ OK - Empty table");
            
        } catch (Exception e) {
            result.put("status", "❌ ERROR: " + e.getMessage());
        }
        
        return result;
    }

    private Map<String, Object> checkTableInSecondary() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            Integer tableCount = secondaryJdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'students'",
                Integer.class
            );
            
            if (tableCount == 0) {
                result.put("table_exists", "❌ NO");
                result.put("reason", "Table 'students' not found in secondary DB");
                result.put("record_count", 0);
                result.put("status", "❌ TABLE MISSING");
                return result;
            }
            
            result.put("table_exists", "✅ YES");
            
            Integer count = secondaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            result.put("record_count", count != null ? count : 0);
            
            result.put("status", count > 0 ? "✅ OK - Has data" : "⚠️ OK - Empty table");
            
        } catch (Exception e) {
            result.put("status", "❌ ERROR: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * ✅ Verify data integrity between databases
     */
    private Map<String, Object> verifyData() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            Integer primaryCount = primaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            Integer secondaryCount = secondaryJdbc.queryForObject("SELECT COUNT(*) FROM students", Integer.class);
            
            result.put("primary_count", primaryCount);
            result.put("secondary_count", secondaryCount);
            result.put("counts_match", primaryCount.equals(secondaryCount) ? "✅ YES" : "❌ MISMATCH");
            
            // Show first few records
            List<Map<String, Object>> primaryData = primaryJdbc.queryForList("SELECT * FROM students LIMIT 3 ORDER BY id");
            List<Map<String, Object>> secondaryData = secondaryJdbc.queryForList("SELECT * FROM students LIMIT 3 ORDER BY id");
            
            result.put("primary_sample", primaryData);
            result.put("secondary_sample", secondaryData);
            
        } catch (Exception e) {
            result.put("status", "❌ ERROR: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * ✅ Get connection URL details
     */
    private Map<String, Object> getConnectionDetails() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        try {
            // Primary connection metadata
            String primaryUrl = primaryJdbc.queryForObject(
                "SELECT setting FROM pg_settings WHERE name = 'max_connections'",
                String.class
            );
            
            String secondaryUrl = secondaryJdbc.queryForObject(
                "SELECT setting FROM pg_settings WHERE name = 'max_connections'",
                String.class
            );
            
            result.put("primary_max_connections", primaryUrl);
            result.put("secondary_max_connections", secondaryUrl);
            
        } catch (Exception e) {
            log.warn("Could not get connection details: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * 🎯 Generate recommendations based on diagnostics
     */
    private List<String> generateRecommendations(Map<String, Object> report) {
        List<String> recommendations = new ArrayList<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> primaryDb = (Map<String, Object>) report.get("primary_db");
        @SuppressWarnings("unchecked")
        Map<String, Object> secondaryDb = (Map<String, Object>) report.get("secondary_db");
        @SuppressWarnings("unchecked")
        Map<String, Object> tableStatus = (Map<String, Object>) report.get("table_status");
        
        // Check primary DB
        if ("❌ FAILED".equals(primaryDb.get("connection"))) {
            recommendations.add("🔴 PRIMARY DB CONNECTION FAILED!");
            recommendations.add("   - Check Render database credentials in application.properties");
            recommendations.add("   - Verify database URL and port");
            recommendations.add("   - Check if Render instance is running and accessible");
        }
        
        // Check secondary DB
        if ("❌ FAILED".equals(secondaryDb.get("connection"))) {
            recommendations.add("🟡 SECONDARY DB CONNECTION FAILED!");
            recommendations.add("   - Check Railway database credentials");
            recommendations.add("   - Verify the secondary datasource configuration");
        }
        
        // Check table
        @SuppressWarnings("unchecked")
        Map<String, Object> primaryTableStatus = (Map<String, Object>) tableStatus.get("primary");
        if ("❌ TABLE MISSING".equals(primaryTableStatus.get("status"))) {
            recommendations.add("🔴 PRIMARY TABLE NOT FOUND!");
            recommendations.add("   - Ensure spring.jpa.hibernate.ddl-auto=update");
            recommendations.add("   - Try POST /api/students/admin/init-db to create table");
            recommendations.add("   - Check if Hibernate generated the table correctly");
        }
        
        return recommendations;
    }

    /**
     * 🛠️ Initialize database tables if they don't exist
     */
    public Map<String, String> initializeDatabase() {
        Map<String, String> result = new LinkedHashMap<>();
        
        // Create table in primary
        try {
            primaryJdbc.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255) UNIQUE,
                    phone VARCHAR(255),
                    age INTEGER
                )
            """);
            result.put("primary", "✅ students table created/verified");
        } catch (Exception e) {
            result.put("primary", "❌ Failed: " + e.getMessage());
        }
        
        // Create table in secondary
        try {
            secondaryJdbc.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    email VARCHAR(255),
                    phone VARCHAR(255),
                    age INTEGER
                )
            """);
            result.put("secondary", "✅ students table created/verified");
        } catch (Exception e) {
            result.put("secondary", "❌ Failed: " + e.getMessage());
        }
        
        return result;
    }
}

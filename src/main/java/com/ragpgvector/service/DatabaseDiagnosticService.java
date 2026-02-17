 package com.ragpgvector.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Database diagnostic service to help troubleshoot connection and table issues
 */
@Component
@Slf4j
public class DatabaseDiagnosticService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void runDiagnostics() {
        log.info("=== DATABASE DIAGNOSTICS ===");

        try {
            // Test basic connection
            testConnection();

            // Check database info
            checkDatabaseInfo();

            // Check if pgvector extension exists
            checkPgVectorExtension();

            // List all tables
            listAllTables();

            // Check if timesheets table exists
            checkTimesheetsTable();

            log.info("=== DIAGNOSTICS COMPLETE ===");

        } catch (Exception e) {
            log.error("Database diagnostics failed: {}", e.getMessage(), e);
        }
    }

    private void testConnection() {
        try {
            String result = jdbcTemplate.queryForObject("SELECT 'Connection OK' as status", String.class);
            log.info("✓ Database connection: {}", result);
        } catch (Exception e) {
            log.error("✗ Database connection failed: {}", e.getMessage());
            throw e;
        }
    }

    private void checkDatabaseInfo() {
        try {
            String dbName = jdbcTemplate.queryForObject("SELECT current_database()", String.class);
            String dbUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);
            String dbVersion = jdbcTemplate.queryForObject("SELECT version()", String.class);

            log.info("✓ Database: {} | User: {} | Version: {}",
                dbName, dbUser, dbVersion.substring(0, Math.min(50, dbVersion.length())));
        } catch (Exception e) {
            log.error("✗ Could not get database info: {}", e.getMessage());
        }
    }

    private void checkPgVectorExtension() {
        try {
            String extensionSql = "SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'";
            Integer count = jdbcTemplate.queryForObject(extensionSql, Integer.class);

            if (count != null && count > 0) {
                log.info("✓ pgvector extension is installed");
            } else {
                log.warn("✗ pgvector extension is NOT installed");
            }
        } catch (Exception e) {
            log.error("✗ Could not check pgvector extension: {}", e.getMessage());
        }
    }

    private void listAllTables() {
        try {
            String tablesSql = """
                SELECT table_name, table_type 
                FROM information_schema.tables 
                WHERE table_schema = current_schema() 
                ORDER BY table_name
                """;

            List<Map<String, Object>> tables = jdbcTemplate.queryForList(tablesSql);

            log.info("✓ Tables in current schema ({}): ", tables.size());
            for (Map<String, Object> table : tables) {
                log.info("  - {} ({})", table.get("table_name"), table.get("table_type"));
            }

            if (tables.isEmpty()) {
                log.warn("✗ No tables found in current schema");
            }

        } catch (Exception e) {
            log.error("✗ Could not list tables: {}", e.getMessage());
        }
    }

    private void checkTimesheetsTable() {
        try {
            // Check if table exists
            String checkTableSql = """
                SELECT COUNT(*) FROM information_schema.tables 
                WHERE table_name = 'timesheets' AND table_schema = current_schema()
                """;

            Integer tableExists = jdbcTemplate.queryForObject(checkTableSql, Integer.class);

            if (tableExists != null && tableExists > 0) {
                log.info("✓ Timesheets table exists");

                // Get column info
                String columnsSql = """
                    SELECT column_name, data_type, is_nullable 
                    FROM information_schema.columns 
                    WHERE table_name = 'timesheets' AND table_schema = current_schema()
                    ORDER BY ordinal_position
                    """;

                List<Map<String, Object>> columns = jdbcTemplate.queryForList(columnsSql);
                log.info("  Columns ({}):", columns.size());
                for (Map<String, Object> col : columns) {
                    log.info("    - {} ({}) nullable: {}",
                        col.get("column_name"),
                        col.get("data_type"),
                        col.get("is_nullable"));
                }

                // Check record count
                Integer recordCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM timesheets", Integer.class);
                log.info("  Records: {}", recordCount);

            } else {
                log.warn("✗ Timesheets table does NOT exist");
                log.info("  This means init.sql hasn't been executed or database was not properly initialized");
            }

        } catch (Exception e) {
            log.error("✗ Could not check timesheets table: {}", e.getMessage());
        }
    }
}

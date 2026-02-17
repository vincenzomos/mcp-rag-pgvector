package com.ragpgvector.dataIngestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class RelationalDataIngestionService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void ingestTimesheetData() throws IOException {
        log.info("Starting relational timesheet data ingestion");

        // Ensure table exists first
        ensureTableExists();

        // Clear existing data
        clearExistingData();

        // Load and process CSV file
        ClassPathResource csvResource = new ClassPathResource("hoursheets/Hoursheets.csv");

        if (!csvResource.exists()) {
            throw new RuntimeException("CSV resource not found: hoursheets/Hoursheets.csv");
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(csvResource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            boolean isFirstLine = true;
            int recordCount = 0;

            while ((line = reader.readLine()) != null) {
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    log.info("Skipping header line: {}", line);
                    continue;
                }

                // Process data line
                if (!line.trim().isEmpty()) {
                    processTimesheetRecord(line);
                    recordCount++;
                }
            }

            log.info("Successfully ingested {} timesheet records into relational database", recordCount);
        }
    }

    private void ensureTableExists() {
        log.info("Ensuring timesheets table exists");
        try {
            // Create table if it doesn't exist
            String createTableSql = """
                CREATE TABLE IF NOT EXISTS timesheets (
                    id SERIAL PRIMARY KEY,
                    month_year VARCHAR(50) NOT NULL,
                    assignment_name VARCHAR(255) NOT NULL,
                    typecode VARCHAR(10) NOT NULL,
                    hours INTEGER NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;

            jdbcTemplate.execute(createTableSql);
            log.info("Timesheets table creation verified");

            // Create indexes if they don't exist
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_timesheets_month_year ON timesheets(month_year)",
                "CREATE INDEX IF NOT EXISTS idx_timesheets_assignment ON timesheets(assignment_name)",
                "CREATE INDEX IF NOT EXISTS idx_timesheets_typecode ON timesheets(typecode)",
                "CREATE INDEX IF NOT EXISTS idx_timesheets_composite ON timesheets(month_year, assignment_name, typecode)"
            };

            for (String indexSql : indexStatements) {
                try {
                    jdbcTemplate.execute(indexSql);
                } catch (Exception e) {
                    log.warn("Failed to create index: {} - Error: {}", indexSql, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Failed to ensure table exists: {}", e.getMessage(), e);
            throw new RuntimeException("Could not create timesheets table", e);
        }
    }

    private void clearExistingData() {
        log.info("Clearing existing timesheet data");
        try {
            // Check if table exists first
            String checkTableSql = """
                SELECT COUNT(*) FROM information_schema.tables 
                WHERE table_name = 'timesheets' AND table_schema = current_schema()
                """;

            Integer tableCount = jdbcTemplate.queryForObject(checkTableSql, Integer.class);

            if (tableCount != null && tableCount > 0) {
                jdbcTemplate.update("DELETE FROM timesheets");
                log.info("Existing timesheet data cleared");
            } else {
                log.info("Timesheets table doesn't exist yet - skipping clear operation");
            }
        } catch (Exception e) {
            log.warn("Could not clear existing data (table may not exist): {}", e.getMessage());
            // Don't throw exception here - the table will be created by ensureTableExists
        }
    }

    private void processTimesheetRecord(String line) {
        try {
            // Split CSV line (assuming semicolon separator based on the CSV file)
            String[] parts = line.split(";");

            if (parts.length != 4) {
                log.warn("Invalid CSV line format (expected 4 columns): {}", line);
                return;
            }

            String monthYear = parts[0].trim();
            String assignmentName = parts[1].trim();
            String typecode = parts[2].trim();
            int hours = Integer.parseInt(parts[3].trim());

            // Insert into database
            String sql = "INSERT INTO timesheets (month_year, assignment_name, typecode, hours) VALUES (?, ?, ?, ?)";
            jdbcTemplate.update(sql, monthYear, assignmentName, typecode, hours);

            log.debug("Inserted record: {} | {} | {} | {}", monthYear, assignmentName, typecode, hours);

        } catch (NumberFormatException e) {
            log.error("Failed to parse hours as integer in line: {}", line, e);
        } catch (Exception e) {
            log.error("Failed to process CSV line: {}", line, e);
        }
    }

    /**
     * Get count of records in the timesheet table for verification
     */
    public int getTimesheetRecordCount() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM timesheets", Integer.class);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Could not get timesheet record count (table may not exist): {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get summary of data for verification
     */
    public void logDataSummary() {
        try {
            // First check if table exists
            String checkTableSql = """
                SELECT COUNT(*) FROM information_schema.tables 
                WHERE table_name = 'timesheets' AND table_schema = current_schema()
                """;

            Integer tableCount = jdbcTemplate.queryForObject(checkTableSql, Integer.class);

            if (tableCount == null || tableCount == 0) {
                log.warn("Timesheets table does not exist - cannot provide data summary");
                return;
            }

            int totalRecords = getTimesheetRecordCount();
            log.info("Total timesheet records: {}", totalRecords);

            if (totalRecords > 0) {
                // Get distinct years
                String yearsSql = "SELECT DISTINCT SPLIT_PART(month_year, ' ', 2) as year FROM timesheets ORDER BY year";
                var years = jdbcTemplate.queryForList(yearsSql, String.class);
                if (!years.isEmpty()) {
                    log.info("Data spans from {} to {}", years.get(0), years.get(years.size() - 1));
                }

                // Get distinct projects
                String projectsSql = "SELECT DISTINCT assignment_name FROM timesheets ORDER BY assignment_name";
                jdbcTemplate.queryForList(projectsSql, String.class).forEach(project ->
                        log.info("Found project: {}", project)
                );

                // Get count by year
                String countByYearSql = """
                    SELECT SPLIT_PART(month_year, ' ', 2) as year, COUNT(*) as record_count 
                    FROM timesheets 
                    GROUP BY SPLIT_PART(month_year, ' ', 2) 
                    ORDER BY year
                    """;
                jdbcTemplate.queryForList(countByYearSql).forEach(row ->
                        log.info("Year {}: {} records", row.get("year"), row.get("record_count"))
                );
            } else {
                log.info("No timesheet records found in database");
            }

        } catch (Exception e) {
            log.error("Failed to log data summary: {}", e.getMessage(), e);
        }
    }
}

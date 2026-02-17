package com.ragpgvector.repository;

import com.ragpgvector.model.TimesheetRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Repository for accessing timesheet data from the relational database
 */
@Repository
@Slf4j
public class TimesheetRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RowMapper<TimesheetRecord> timesheetRowMapper = new RowMapper<TimesheetRecord>() {
        @Override
        public TimesheetRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            TimesheetRecord record = new TimesheetRecord();
            record.setId(rs.getLong("id"));
            record.setMonthYear(rs.getString("month_year"));
            record.setAssignmentName(rs.getString("assignment_name"));
            record.setTypecode(rs.getString("typecode"));
            record.setHours(rs.getInt("hours"));

            // Handle timestamps that might be null
            if (rs.getTimestamp("created_at") != null) {
                record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            }
            if (rs.getTimestamp("updated_at") != null) {
                record.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            }

            return record;
        }
    };

    /**
     * Search timesheets based on flexible criteria
     */
    public List<TimesheetRecord> searchTimesheets(String monthYear, String assignmentName, String typecode, String year, String month) {
        StringBuilder sql = new StringBuilder("SELECT * FROM timesheets WHERE 1=1");

        if (monthYear != null && !monthYear.trim().isEmpty()) {
            sql.append(" AND LOWER(month_year) LIKE LOWER(?)");
        }
        if (assignmentName != null && !assignmentName.trim().isEmpty()) {
            sql.append(" AND LOWER(assignment_name) LIKE LOWER(?)");
        }
        if (typecode != null && !typecode.trim().isEmpty()) {
            sql.append(" AND LOWER(typecode) = LOWER(?)");
        }
        if (year != null && !year.trim().isEmpty()) {
            sql.append(" AND SPLIT_PART(month_year, ' ', 2) = ?");
        }
        if (month != null && !month.trim().isEmpty()) {
            sql.append(" AND LOWER(SPLIT_PART(month_year, ' ', 1)) = LOWER(?)");
        }

        sql.append(" ORDER BY month_year, assignment_name");

        log.debug("Executing timesheet search query: {}", sql);

        // Build parameters dynamically
        Object[] params = buildParams(monthYear, assignmentName, typecode, year, month);

        return jdbcTemplate.query(sql.toString(), timesheetRowMapper, params);
    }

    /**
     * Search timesheets with text-based query (for natural language queries)
     */
    public List<TimesheetRecord> searchTimesheetsByText(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            return getAllTimesheets();
        }

        String sql = """
            SELECT * FROM timesheets 
            WHERE LOWER(month_year) LIKE LOWER(?) 
               OR LOWER(assignment_name) LIKE LOWER(?) 
               OR LOWER(typecode) LIKE LOWER(?)
            ORDER BY month_year, assignment_name
            """;

        String searchPattern = "%" + searchText.trim() + "%";
        log.debug("Executing text search for: {}", searchText);

        return jdbcTemplate.query(sql, timesheetRowMapper, searchPattern, searchPattern, searchPattern);
    }

    /**
     * Get all timesheets
     */
    public List<TimesheetRecord> getAllTimesheets() {
        String sql = "SELECT * FROM timesheets ORDER BY month_year, assignment_name";
        return jdbcTemplate.query(sql, timesheetRowMapper);
    }

    /**
     * Get timesheets for a specific project and period
     */
    public List<TimesheetRecord> getTimesheetsForProjectAndPeriod(String project, String monthYear) {
        String sql = """
            SELECT * FROM timesheets 
            WHERE LOWER(assignment_name) LIKE LOWER(?) 
            AND LOWER(month_year) LIKE LOWER(?)
            ORDER BY month_year, typecode
            """;

        String projectPattern = "%" + (project != null ? project : "") + "%";
        String periodPattern = "%" + (monthYear != null ? monthYear : "") + "%";

        return jdbcTemplate.query(sql, timesheetRowMapper, projectPattern, periodPattern);
    }

    /**
     * Get summary statistics
     */
    public Map<String, Object> getTimesheetSummary() {
        String sql = """
            SELECT 
                COUNT(*) as total_records,
                COUNT(DISTINCT assignment_name) as unique_projects,
                COUNT(DISTINCT SPLIT_PART(month_year, ' ', 2)) as unique_years,
                COUNT(DISTINCT month_year) as unique_periods,
                MIN(SPLIT_PART(month_year, ' ', 2)) as earliest_year,
                MAX(SPLIT_PART(month_year, ' ', 2)) as latest_year,
                SUM(hours) as total_hours,
                AVG(hours) as average_hours
            FROM timesheets
            """;

        return jdbcTemplate.queryForMap(sql);
    }

    /**
     * Get distinct project names
     */
    public List<String> getDistinctProjects() {
        String sql = "SELECT DISTINCT assignment_name FROM timesheets ORDER BY assignment_name";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Get distinct years
     */
    public List<String> getDistinctYears() {
        String sql = "SELECT DISTINCT SPLIT_PART(month_year, ' ', 2) as year FROM timesheets ORDER BY year";
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * Get total hours for a specific project
     */
    public Integer getTotalHoursForProject(String projectName) {
        String sql = "SELECT COALESCE(SUM(hours), 0) FROM timesheets WHERE LOWER(assignment_name) LIKE LOWER(?)";
        String projectPattern = "%" + (projectName != null ? projectName : "") + "%";
        return jdbcTemplate.queryForObject(sql, Integer.class, projectPattern);
    }

    private Object[] buildParams(String monthYear, String assignmentName, String typecode, String year, String month) {
        int paramCount = 0;
        if (monthYear != null && !monthYear.trim().isEmpty()) paramCount++;
        if (assignmentName != null && !assignmentName.trim().isEmpty()) paramCount++;
        if (typecode != null && !typecode.trim().isEmpty()) paramCount++;
        if (year != null && !year.trim().isEmpty()) paramCount++;
        if (month != null && !month.trim().isEmpty()) paramCount++;

        Object[] params = new Object[paramCount];
        int index = 0;

        if (monthYear != null && !monthYear.trim().isEmpty()) {
            params[index++] = "%" + monthYear.trim() + "%";
        }
        if (assignmentName != null && !assignmentName.trim().isEmpty()) {
            params[index++] = "%" + assignmentName.trim() + "%";
        }
        if (typecode != null && !typecode.trim().isEmpty()) {
            params[index++] = typecode.trim();
        }
        if (year != null && !year.trim().isEmpty()) {
            params[index++] = year.trim();
        }
        if (month != null && !month.trim().isEmpty()) {
            params[index++] = month.trim();
        }

        return params;
    }
}

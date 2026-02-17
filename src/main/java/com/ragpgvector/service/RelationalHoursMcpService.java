package com.ragpgvector.service;

import com.ragpgvector.model.TimesheetRecord;
import com.ragpgvector.repository.TimesheetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * New MCP service that uses relational database for timesheet queries
 */
@Component
@Slf4j
public class RelationalHoursMcpService {

    @Autowired
    private TimesheetRepository timesheetRepository;

    /**
     * Search timesheets using the relational database - replacement for searchUren
     */
    @McpTool(
            name = "searchTimesheetsDB",
            description = "Search the relational timesheet database for worked hours, assignment name, typecode and periods. " +
                    " - The columns in the database are: Month Year, Assignment Name, Type Code, Hours" +
                    " - If asked when hours were worked, answer with the full month name (e.g. \"In January 2024\") rather than numeric notation." +
                    " - If asked how many hours were worked for a specific code, sum the hours and answer like: \"There are in total 24 hours for project code Devops Client Reporting\"." +
                    " - Can search by project names like 'Devops ClientReporting' or 'Standby ClientReporting'" +
                    " - Can search by specific months like 'October 2021' or years like '2021'" +
                    " - Provides accurate data directly from the relational database"
    )
    public Map<String, Object> searchTimesheetsDB(String query) {
        log.info("MCP tool searchTimesheetsDB called with query: {}", query);

        try {
            // Parse the query to extract search criteria
            SearchCriteria criteria = parseQuery(query);
            log.debug("Parsed search criteria: {}", criteria);

            List<TimesheetRecord> results = performSearch(criteria, query);
            log.info("Found {} timesheet records matching query", results.size());

            if (results.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "No timesheet records found for query: " + query);
                errorResponse.put("suggestion", "Try searching for 'Devops', 'Standby', specific years like '2021', or months like 'October'");
                errorResponse.put("available_projects", timesheetRepository.getDistinctProjects());
                errorResponse.put("available_years", timesheetRepository.getDistinctYears());
                errorResponse.put("results", List.of());
                return errorResponse;
            }

            // Format results with summary information
            Map<String, Object> summary = generateSummary(results, query);
            List<Map<String, Object>> formattedResults = formatResults(results);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("query", query);
            response.put("total_records", results.size());
            response.put("summary", summary);
            response.put("results", formattedResults);
            response.put("raw_data", results.stream()
                    .map(this::convertToMap)
                    .collect(Collectors.toList()));
            return response;

        } catch (Exception e) {
            log.error("Error in searchTimesheetsDB: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("query", query);
            return errorResponse;
        }
    }

    /**
     * Get comprehensive timesheet statistics
     */
    @McpTool(
            name = "getTimesheetStatistics",
            description = "Get comprehensive statistics about all timesheet data including total hours, projects, years covered, and summaries by project"
    )
    public Map<String, Object> getTimesheetStatistics() {
        log.info("Getting comprehensive timesheet statistics");

        try {
            Map<String, Object> dbSummary = timesheetRepository.getTimesheetSummary();
            List<String> projects = timesheetRepository.getDistinctProjects();
            List<String> years = timesheetRepository.getDistinctYears();

            // Get hours by project
            Map<String, Integer> hoursByProject = new HashMap<>();
            for (String project : projects) {
                Integer totalHours = timesheetRepository.getTotalHoursForProject(project);
                hoursByProject.put(project, totalHours);
            }

            // Get sample records for each year
            Map<String, Integer> recordsByYear = new HashMap<>();
            for (String year : years) {
                List<TimesheetRecord> yearRecords = timesheetRepository.searchTimesheets(null, null, null, year, null);
                recordsByYear.put(year, yearRecords.size());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("database_summary", dbSummary);
            response.put("available_projects", projects);
            response.put("available_years", years);
            response.put("hours_by_project", hoursByProject);
            response.put("records_by_year", recordsByYear);
            response.put("success", true);
            return response;

        } catch (Exception e) {
            log.error("Error getting timesheet statistics: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Get project information including valid project codes
     */
    @McpTool(
            name = "getProjectInformation",
            description = "Retrieve detailed information about all projects including valid project codes, typecodes, and total hours"
    )
    public Map<String, Object> getProjectInformation() {
        log.info("Getting detailed project information");

        try {
            List<String> projects = timesheetRepository.getDistinctProjects();
            Map<String, Object> projectDetails = new HashMap<>();

            for (String project : projects) {
                List<TimesheetRecord> projectRecords = timesheetRepository.searchTimesheets(null, project, null, null, null);

                Set<String> typecodes = projectRecords.stream()
                    .map(TimesheetRecord::getTypecode)
                    .collect(Collectors.toSet());

                Integer totalHours = projectRecords.stream()
                    .mapToInt(TimesheetRecord::getHours)
                    .sum();

                Set<String> years = projectRecords.stream()
                    .map(TimesheetRecord::getYear)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

                Map<String, Object> projectInfo = new HashMap<>();
                projectInfo.put("total_hours", totalHours);
                projectInfo.put("typecodes", typecodes);
                projectInfo.put("years_active", years.stream().sorted().collect(Collectors.toList()));
                projectInfo.put("total_records", projectRecords.size());

                projectDetails.put(project, projectInfo);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("project_details", projectDetails);
            response.put("standard_projects", List.of("Devops ClientReporting", "Standby ClientReporting"));
            response.put("total_projects", projects.size());
            return response;

        } catch (Exception e) {
            log.error("Error getting project information: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    // MCP Prompts - reused from original with updates for relational data

    @McpPrompt(
            name = "analyze-timesheet-hours-db",
            description = "Generate a detailed analysis prompt for timesheet hours by project and period using relational database"
    )
    public String analyzeTimesheetHours(String projectCode, String monthYear) {
        log.info("MCP prompt analyzeTimesheetHours called with projectCode={}, monthYear={}", projectCode, monthYear);

        String prompt = String.format("""
                Please analyze the timesheet data from the relational database for the following criteria:
                
                Project Code: %s
                Period: %s
                
                Provide a comprehensive analysis including:
                1. Total hours worked on this project during this period
                2. Distribution of hours by type code (if available)
                3. Comparison with other periods for the same project
                4. Any notable patterns or anomalies in the time registration
                5. Breakdown by month if a full year is specified
                
                Use the searchTimesheetsDB tool to retrieve the relevant data and provide specific numbers from the database.
                """,
                projectCode != null ? projectCode : "[any project]",
                monthYear != null ? monthYear : "[any period]");

        log.debug("Generated prompt template for timesheet analysis");
        return prompt;
    }

    @McpPrompt(
            name = "compare-project-hours-db",
            description = "Generate a prompt to compare hours worked across different projects in a given period using relational data"
    )
    public String compareProjectHours(String period) {
        log.info("MCP prompt compareProjectHours called with period={}", period);

        String prompt = String.format("""
                Please compare the hours worked across all available projects for the period: %s
                
                For each project found:
                1. List the project code and assignment name
                2. Total hours worked
                3. Breakdown by type code
                4. Number of records/entries
                
                Then provide:
                - Which project had the most hours
                - Which project had the least hours
                - Total hours across all projects
                - Average hours per project
                
                Use the searchTimesheetsDB and getProjectInformation tools to gather the data.
                """,
                period != null ? period : "the most recent period available");

        log.debug("Generated prompt template for project comparison");
        return prompt;
    }

    @McpPrompt(
            name = "validate-timesheet-quality-db",
            description = "Generate a prompt to validate data quality and completeness of timesheet records from relational database"
    )
    public String validateTimesheetQuality() {
        log.info("MCP prompt validateTimesheetQuality called");

        String prompt = """
                Please perform a comprehensive data quality check on all available timesheet records from the relational database:
                
                1. DATA COMPLETENESS
                   - Check for missing months or gaps in the timeline
                   - Identify any projects with sparse data
                   - Calculate coverage by year and project
                
                2. DATA CONSISTENCY
                   - Check for any unusual hour values (e.g., >200 hours/month, 0 hours entries)
                   - Verify all project codes match expected patterns
                   - Look for typecode consistency within projects
                
                3. SUMMARY STATISTICS
                   - Total records analyzed
                   - Date range covered (earliest to latest)
                   - Average hours per month by project
                   - Projects with most/least activity
                
                Use the getTimesheetStatistics and searchTimesheetsDB tools to gather comprehensive data.
                """;

        log.debug("Generated prompt template for data quality validation");
        return prompt;
    }

    // Helper methods

    private SearchCriteria parseQuery(String query) {
        SearchCriteria criteria = new SearchCriteria();

        if (query == null) return criteria;

        String lowerQuery = query.toLowerCase();

        // Extract year patterns
        Pattern yearPattern = Pattern.compile("\\b(20[0-2][0-9])\\b");
        Matcher yearMatcher = yearPattern.matcher(query);
        if (yearMatcher.find()) {
            criteria.year = yearMatcher.group(1);
        }

        // Extract month patterns
        String[] months = {"january", "february", "march", "april", "may", "june",
                          "july", "august", "september", "october", "november", "december"};
        for (String month : months) {
            if (lowerQuery.contains(month)) {
                criteria.month = month;
                break;
            }
        }

        // Extract project patterns
        if (lowerQuery.contains("devops")) {
            criteria.projectName = "Devops ClientReporting";
        } else if (lowerQuery.contains("standby")) {
            criteria.projectName = "Standby ClientReporting";
        }

        // Extract typecode patterns
        if (lowerQuery.contains("dev")) {
            criteria.typecode = "DEV";
        } else if (lowerQuery.contains("stbl") || lowerQuery.contains("standby low")) {
            criteria.typecode = "STBL";
        } else if (lowerQuery.contains("stbh") || lowerQuery.contains("standby high")) {
            criteria.typecode = "STBH";
        }

        return criteria;
    }

    private List<TimesheetRecord> performSearch(SearchCriteria criteria, String originalQuery) {
        List<TimesheetRecord> results;

        // Try structured search first if we have specific criteria
        if (criteria.hasSpecificCriteria()) {
            String monthYear = null;
            if (criteria.month != null && criteria.year != null) {
                monthYear = criteria.month + " " + criteria.year;
            }

            results = timesheetRepository.searchTimesheets(
                monthYear,
                criteria.projectName,
                criteria.typecode,
                criteria.year,
                criteria.month
            );
        } else {
            // Fall back to text search
            results = timesheetRepository.searchTimesheetsByText(originalQuery);
        }

        return results;
    }

    private Map<String, Object> generateSummary(List<TimesheetRecord> results, String query) {
        int totalHours = results.stream().mapToInt(TimesheetRecord::getHours).sum();

        Map<String, Integer> hoursByProject = results.stream()
            .collect(Collectors.groupingBy(
                TimesheetRecord::getAssignmentName,
                Collectors.summingInt(TimesheetRecord::getHours)
            ));

        Map<String, Integer> hoursByYear = results.stream()
            .filter(r -> r.getYear() != null)
            .collect(Collectors.groupingBy(
                TimesheetRecord::getYear,
                Collectors.summingInt(TimesheetRecord::getHours)
            ));

        Map<String, Long> recordsByMonth = results.stream()
            .collect(Collectors.groupingBy(
                TimesheetRecord::getMonthYear,
                Collectors.counting()
            ));

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_hours", totalHours);
        summary.put("total_records", results.size());
        summary.put("hours_by_project", hoursByProject);
        summary.put("hours_by_year", hoursByYear);
        summary.put("records_by_month", recordsByMonth);
        return summary;
    }

    private List<Map<String, Object>> formatResults(List<TimesheetRecord> results) {
        return results.stream()
            .map(record -> {
                Map<String, Object> map = new HashMap<>();
                map.put("period", record.getMonthYear());
                map.put("project", record.getAssignmentName());
                map.put("typecode", record.getTypecode());
                map.put("hours", record.getHours());
                map.put("formatted", String.format("%s: %d hours for %s (%s)",
                    record.getMonthYear(),
                    record.getHours(),
                    record.getAssignmentName(),
                    record.getTypecode()));
                return map;
            })
            .collect(Collectors.toList());
    }

    private Map<String, Object> convertToMap(TimesheetRecord record) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", record.getId());
        map.put("monthYear", record.getMonthYear());
        map.put("assignmentName", record.getAssignmentName());
        map.put("typecode", record.getTypecode());
        map.put("hours", record.getHours());
        map.put("year", record.getYear() != null ? record.getYear() : "");
        map.put("month", record.getMonth() != null ? record.getMonth() : "");
        return map;
    }

    // Helper class for search criteria
    private static class SearchCriteria {
        String year;
        String month;
        String projectName;
        String typecode;

        boolean hasSpecificCriteria() {
            return year != null || month != null || projectName != null || typecode != null;
        }

        @Override
        public String toString() {
            return String.format("SearchCriteria{year='%s', month='%s', projectName='%s', typecode='%s'}",
                year, month, projectName, typecode);
        }
    }
}

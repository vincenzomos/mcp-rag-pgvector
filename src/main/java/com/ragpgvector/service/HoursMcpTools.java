package com.ragpgvector.service;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class HoursMcpTools {

    @Autowired
    private VectorStore vectorStore;

    /**
     * This tool is invoked by Gemini via the MCP protocol.
     * It searches the PGVector database for relevant time registration entries.
     */
    @McpTool(
            name = "searchUren",
            description = "Search the timesheets and PDF documents for worked hours, assignment name, typecode and periods. " +
                    " - The columns in the loaded input are: Month Year, Assignment Name, Type Code, Hours, Completed" +
                    " - If asked when hours were worked, answer with the full month name (e.g. \"In January 2024\") rather than numeric notation." +
                    " - If asked how many hours were worked for a specific code, sum the hours and answer like: \"There are in total 24 hours for project code Devops Client Reporting\". "
    )
    public String searchUren(String query) {
        log.info("MCP tool searchUren called with query: {}", query);

        try {
            // 1. Execute a similarity search in the vector database
            log.debug("Performing similarity search with topK=5 and threshold=0.7");
            List<Document> resultaten = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(5)
                            .similarityThreshold(0.7)
                            .build()
            );

            log.info("Number of documents found: {}", resultaten.size());

            if (resultaten.isEmpty()) {
                log.warn("No relevant information found for query: {}", query);
                return "I could not find any relevant information in the timesheets for the query: " + query;
            }

            // 2. Format the results including the source
            String response = resultaten.stream()
                    .map(doc -> {
                        String bron = (String) doc.getMetadata().getOrDefault("source", "Unknown file");
                        log.debug("Document found from source: {}", bron);
                        return String.format("--- Source: %s ---\nContent: %s", bron, doc.getFormattedContent());
                    })
                    .collect(Collectors.joining("\n\n"));

            log.info("Successfully generated response for query: {}", query);
            return response;

        } catch (Exception e) {
            log.error("Error occurred while executing searchUren: {}", e.getMessage(), e);
            return "An error occurred while searching the timesheets: " + e.getMessage();
        }
    }

    /**
     * Returns a fixed list of project codes.
     * Useful for the LLM to know the exact terms to use when searching.
     */
    @McpTool(name = "getProjectCodes",
            description = "Retrieve the list of all valid project codes for time registration.")
    public List<String> getProjectCodes() {
        // We return the list directly. Spring AI will translate this to a JSON array for the MCP client.
        return List.of("Devops Client Reporting", "Standby Client Reporting");
    }

    /**
     * MCP Prompt: Provides template prompts for common timesheet queries.
     * This helps users formulate effective questions about their time registrations.
     */
    @McpPrompt(
            name = "analyze-timesheet-hours",
            description = "Generate a detailed analysis prompt for timesheet hours by project and period"
    )
    public String analyzeTimesheetHours(String projectCode, String monthYear) {
        log.info("MCP prompt analyzeTimesheetHours called with projectCode={}, monthYear={}", projectCode, monthYear);

        String prompt = String.format("""
                Please analyze the timesheet data for the following criteria:
                
                Project Code: %s
                Period: %s
                
                Provide a comprehensive analysis including:
                1. Total hours worked on this project during this period
                2. Distribution of hours by type code (if available)
                3. Any incomplete or uncertain records (where Completed='N')
                4. Comparison with typical monthly hours for this project type
                5. Any notable patterns or anomalies in the time registration
                
                Use the searchUren tool to retrieve the relevant data and provide specific numbers from the source documents.
                """,
                projectCode != null ? projectCode : "[any project]",
                monthYear != null ? monthYear : "[any period]");

        log.debug("Generated prompt template for timesheet analysis");
        return prompt;
    }

    /**
     * MCP Prompt: Provides a template for comparing hours across multiple projects.
     */
    @McpPrompt(
            name = "compare-project-hours",
            description = "Generate a prompt to compare hours worked across different projects in a given period"
    )
    public String compareProjectHours(String period) {
        log.info("MCP prompt compareProjectHours called with period={}", period);

        String prompt = String.format("""
                Please compare the hours worked across all available projects for the period: %s
                
                For each project found:
                1. List the project code and assignment name
                2. Total hours worked
                3. Breakdown by type code
                4. Data quality status (percentage of complete vs incomplete records)
                
                Then provide:
                - Which project had the most hours
                - Which project had the least hours
                - Total hours across all projects
                - Any projects with data quality concerns
                
                Use the searchUren tool to gather the data and getProjectCodes to see available projects.
                """,
                period != null ? period : "the most recent period available");

        log.debug("Generated prompt template for project comparison");
        return prompt;
    }

    /**
     * MCP Prompt: Provides a template for validating timesheet data quality.
     */
    @McpPrompt(
            name = "validate-timesheet-quality",
            description = "Generate a prompt to validate data quality and completeness of timesheet records"
    )
    public String validateTimesheetQuality() {
        log.info("MCP prompt validateTimesheetQuality called");

        String prompt = """
                Please perform a comprehensive data quality check on all available timesheet records:
                
                1. COMPLETENESS CHECK
                   - Identify all records where Completed='N'
                   - List the affected projects and periods
                   - Calculate percentage of incomplete records
                
                2. DATA CONSISTENCY
                   - Check for any unusual hour values (e.g., >24 hours in a day, 0 hours entries)
                   - Verify all project codes match known valid codes
                   - Identify any missing months or gaps in the timeline
                
                3. SUMMARY STATISTICS
                   - Total records analyzed
                   - Date range covered
                   - Average hours per entry
                   - Projects with most/least records
                
                Use the searchUren and getProjectCodes tools to gather comprehensive data.
                If incomplete records are found, ask whether to include them in the analysis.
                """;

        log.debug("Generated prompt template for data quality validation");
        return prompt;
    }

//    /**
//     * If one or more records used to generate an answer have the 'complete' field set to 'N',
//     * this tool asks the user whether uncertain data should be used.
//     * Input: a list of records (Map with keys like projectCode, month, year, hours, complete)
//     * Output: an English confirmation question or a status message when all data is complete.
//     */
//    @McpTool(name = "confirmUseOfUncertainData",
//            description = "Check if used records contain incomplete data (complete='N') and ask the user whether to use that uncertain data in the answer.")
//    public String confirmUseOfUncertainData(List<java.util.Map<String, Object>> usedRecords) {
//        log.info("confirmUseOfUncertainData called with {} records", usedRecords == null ? 0 : usedRecords.size());
//
//        if (usedRecords == null || usedRecords.isEmpty()) {
//            log.debug("No records provided to confirmUseOfUncertainData");
//            return "No records were provided to check. No confirmation needed.";
//        }
//
//        var incomplete = usedRecords.stream()
//                .filter(r -> {
//                    Object c = r.get("complete");
//                    if (c == null) return false;
//                    String s = String.valueOf(c).trim();
//                    return s.equalsIgnoreCase("N") || s.equalsIgnoreCase("false");
//                })
//                .toList();
//
//        if (incomplete.isEmpty()) {
//            log.debug("All used records are marked complete (complete='Y' or not specified)");
//            return "All used records are marked as complete ('Y'). No additional confirmation is required.";
//        }
//
//        String sampleSummary = incomplete.stream()
//                .limit(5)
//                .map(r -> {
//                    Object proj = r.getOrDefault("projectCode", r.getOrDefault("project", "Unknown project"));
//                    Object month = r.getOrDefault("month", r.getOrDefault("maand", "Unknown month"));
//                    Object year = r.getOrDefault("year", r.getOrDefault("jaar", "Unknown year"));
//                    Object hours = r.getOrDefault("hours", r.getOrDefault("uren", "Unknown"));
//                    return String.format("%s %s %s (%s hours)", proj, month, year, hours);
//                })
//                .collect(Collectors.joining("; "));
//
//        String question = "Notice: one or more records used contain uncertain data (complete='N').\n" +
//                "Example incomplete records: " + sampleSummary + "\n" +
//                "Would you like me to use these uncertain records when forming an answer? Reply 'Yes' to proceed or 'No' to exclude them.";
//
//        log.warn("Confirmation requested for {} uncertain records", incomplete.size());
//        return question;
//    }

}

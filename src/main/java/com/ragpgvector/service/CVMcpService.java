package com.ragpgvector.service;

import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MCP service for CV analysis using RAG (Retrieval Augmented Generation)
 * Provides intelligent CV search and analysis capabilities using vector embeddings
 */
@Component
@Slf4j
public class CVMcpService {

    @Autowired
    private VectorStore vectorStore;

    /**
     * Search CV information using vector similarity search
     * Best for finding specific information, skills, experience, or qualifications in Berend Botje's CV
     */
    @McpTool(
            name = "searchCVInformation",
            description = "Search Berend Botje's CV for specific information including skills, experience, education, projects, qualifications, hobbies, and personal interests. " +
                    "Use this for targeted queries like 'Java experience', 'Spring Boot skills', 'university education', 'work experience', 'hobbies', 'interests', etc. " +
                    "Returns relevant CV sections with context about the candidate's background."
    )
    public Map<String, Object> searchCVInformation(String query) {
        log.info("MCP tool searchCVInformation called with query: {}", query);

        try {
            // Filter specifically for CV documents and perform similarity search
            List<Document> results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(6) // Get more results for better coverage
                            .similarityThreshold(0.5) // Lower threshold for better recall
                            .filterExpression("document_category == 'cv'") // Only search CV documents
                            .build()
            );

            log.info("Found {} CV document chunks matching query", results.size());

            if (results.isEmpty()) {
                return createErrorResponse("No relevant information found in Berend Botje's CV for: " + query,
                        "Try broader terms like 'experience', 'skills', 'education', 'projects', or specific technologies");
            }

            // Extract candidate info (should be consistent across all chunks)
            String candidateName = results.stream()
                    .map(doc -> (String) doc.getMetadata().get("candidate_name"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Berend Botje");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("candidate_name", candidateName);
            response.put("query", query);
            response.put("sections_found", results.size());

            // Format the CV content sections
            List<Map<String, Object>> contentSections = formatCVSections(results);
            response.put("relevant_sections", contentSections);

            // Extract and consolidate skills mentioned in the results
            Set<String> skillsFound = extractSkillsFromResults(results);
            response.put("skills_mentioned", skillsFound);

            // Extract and consolidate hobbies mentioned in the results
            Set<String> hobbiesFound = extractHobbiesFromResults(results);
            response.put("hobbies_mentioned", hobbiesFound);

            // Categorize the content types found
            Set<String> contentTypes = extractChunkTypes(results);
            response.put("content_types_covered", contentTypes);

            // Calculate relevance summary
            response.put("relevance_summary", generateRelevanceSummary(results, query));

            return response;

        } catch (Exception e) {
            log.error("Error in searchCVInformation: {}", e.getMessage(), e);
            return createErrorResponse("Error searching CV information: " + e.getMessage(),
                    "Please try again with a different query");
        }
    }

    /**
     * Generate a comprehensive summary of Berend Botje's CV
     * Best for getting a complete overview of the candidate's profile
     */
    @McpTool(
            name = "getCVSummary",
            description = "Generate a comprehensive summary of Berend Botje's CV including complete profile, skills, experience, education, hobbies, interests, and key highlights. " +
                    "Use this for general overview questions or when you want to understand the candidate's complete background including personal interests. " +
                    "Returns a structured profile with all key information from the CV."
    )
    public Map<String, Object> getCVSummary() {
        log.info("MCP tool getCVSummary called");

        try {
            // Get comprehensive CV content using a broad query
            List<Document> allCvContent = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("experience education skills projects qualifications background") // Broad query for complete coverage
                            .topK(20) // Get many chunks to ensure completeness
                            .similarityThreshold(0.3) // Lower threshold for comprehensive coverage
                            .filterExpression("document_category == 'cv'")
                            .build()
            );

            log.info("Found {} CV content sections for comprehensive summary", allCvContent.size());

            if (allCvContent.isEmpty()) {
                return createErrorResponse("No CV content found in the system",
                        "Please ensure Berend Botje's CV has been properly processed");
            }

            // Extract candidate name
            String candidateName = allCvContent.stream()
                    .map(doc -> (String) doc.getMetadata().get("candidate_name"))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("Berend Botje");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("candidate_name", candidateName);
            response.put("total_cv_sections", allCvContent.size());

            // Generate comprehensive profile
            Map<String, Object> profile = generateCompleteProfile(allCvContent);
            response.put("profile", profile);

            // Organize content by type
            Map<String, List<Document>> contentByType = allCvContent.stream()
                    .collect(Collectors.groupingBy(doc ->
                        (String) doc.getMetadata().getOrDefault("chunk_type", "general")));

            Map<String, Object> organizedContent = new HashMap<>();
            for (Map.Entry<String, List<Document>> entry : contentByType.entrySet()) {
                String contentType = entry.getKey();
                List<Document> docs = entry.getValue();

                String consolidatedContent = docs.stream()
                        .map(Document::getFormattedContent)
                        .collect(Collectors.joining("\n\n"));

                organizedContent.put(contentType, Map.of(
                        "section_count", docs.size(),
                        "content", consolidatedContent
                ));
            }
            response.put("cv_sections", organizedContent);

            return response;

        } catch (Exception e) {
            log.error("Error in getCVSummary: {}", e.getMessage(), e);
            return createErrorResponse("Error generating CV summary: " + e.getMessage(),
                    "Please try again or check system logs for details");
        }
    }

    // MCP Prompts for CV analysis

    @McpPrompt(
            name = "analyze-cv-for-role",
            description = "Generate a detailed prompt to analyze how well Berend Botje's background fits specific job requirements"
    )
    public String analyzeCVForRole(String jobTitle, String requiredSkills, String experienceLevel) {
        log.info("MCP prompt analyzeCVForRole called for job: {}", jobTitle);

        return String.format("""
                Please analyze Berend Botje's CV to determine how well his background fits the following position:
                
                **Job Title:** %s
                **Required Skills:** %s  
                **Experience Level:** %s
                
                Please provide a comprehensive analysis including:
                
                1. **SKILL MATCH ANALYSIS**
                   - Which required skills are present in his CV
                   - Skill proficiency level indicators found
                   - Missing critical skills from the requirements
                   - Additional valuable skills he possesses beyond requirements
                
                2. **EXPERIENCE EVALUATION**  
                   - Years of relevant experience mentioned
                   - Leadership or senior role indicators
                   - Industry experience relevance
                   - Project complexity and scope from his background
                
                3. **OVERALL ASSESSMENT**
                   - Fit percentage estimation (0-100%%)
                   - Key strengths for this specific role
                   - Potential concerns or gaps identified
                   - Recommendation (Strong Fit / Good Fit / Partial Fit / Poor Fit)
                
                4. **INTERVIEW FOCUS AREAS**
                   - Specific technical areas to explore with him
                   - Experience validation questions to ask
                   - Skill demonstration opportunities to request
                
                Use searchCVInformation to gather specific details about his background and skills.
                Use getCVSummary to get a complete overview of his profile first.
                """,
                jobTitle != null ? jobTitle : "[Job Title Not Specified]",
                requiredSkills != null ? requiredSkills : "[Skills Not Specified]",
                experienceLevel != null ? experienceLevel : "[Experience Level Not Specified]");
    }

    @McpPrompt(
            name = "extract-cv-highlights",
            description = "Generate a prompt to extract key highlights and achievements from Berend Botje's CV"
    )
    public String extractCVHighlights(String focusArea) {
        log.info("MCP prompt extractCVHighlights called with focus: {}", focusArea);

        return String.format("""
                Please extract and highlight the most impressive and relevant information from Berend Botje's CV:
                
                **Focus Area:** %s
                
                Please organize the highlights into these categories:
                
                1. **TECHNICAL EXPERTISE**
                   - Core programming languages and frameworks
                   - Architecture and design experience  
                   - DevOps and deployment capabilities
                   - Database and data management skills
                
                2. **PROFESSIONAL ACHIEVEMENTS**
                   - Notable projects and their impact
                   - Leadership roles and team management
                   - Problem-solving examples
                   - Innovation and improvement initiatives
                
                3. **EDUCATION & CERTIFICATIONS**
                   - Formal education background and institutions
                   - Professional certifications earned
                   - Continuous learning evidence
                   - Specialized training or courses
                
                4. **CAREER PROGRESSION**
                   - Career growth trajectory
                   - Increasing responsibility over time
                   - Industry reputation indicators
                   - Recognition and awards
                
                5. **UNIQUE DIFFERENTIATORS**
                   - What makes Berend stand out
                   - Unique skill combinations
                   - Special accomplishments
                   - Thought leadership or innovation examples
                
                **Format the response as a professional summary** suitable for executive review or client presentation.
                
                Use searchCVInformation for targeted searches on specific areas.
                Use getCVSummary for comprehensive background information.
                """,
                focusArea != null ? focusArea : "overall professional profile");
    }

    // Helper methods

    private Map<String, Object> createErrorResponse(String message, String suggestion) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        response.put("suggestion", suggestion);
        return response;
    }

    private List<Map<String, Object>> formatCVSections(List<Document> docs) {
        return docs.stream()
                .map(doc -> {
                    Map<String, Object> section = new HashMap<>();
                    section.put("content", doc.getFormattedContent());
                    section.put("content_type", doc.getMetadata().getOrDefault("chunk_type", "general"));
                    section.put("source_file", doc.getMetadata().getOrDefault("source_file", "unknown"));
                    section.put("relevance_score", doc.getMetadata().getOrDefault("distance", 0.8));
                    return section;
                })
                .collect(Collectors.toList());
    }

    private Set<String> extractSkillsFromResults(List<Document> docs) {
        Set<String> allSkills = new HashSet<>();
        for (Document doc : docs) {
            Object skills = doc.getMetadata().get("technical_skills");
            if (skills instanceof List) {
                allSkills.addAll((List<String>) skills);
            }
        }
        return allSkills;
    }

    private Set<String> extractHobbiesFromResults(List<Document> docs) {
        Set<String> allHobbies = new HashSet<>();
        for (Document doc : docs) {
            Object hobbies = doc.getMetadata().get("hobbies_interests");
            if (hobbies instanceof List) {
                allHobbies.addAll((List<String>) hobbies);
            }
        }
        return allHobbies;
    }

    private Set<String> extractChunkTypes(List<Document> docs) {
        return docs.stream()
                .map(doc -> (String) doc.getMetadata().getOrDefault("chunk_type", "general"))
                .collect(Collectors.toSet());
    }

    private Map<String, Object> generateRelevanceSummary(List<Document> results, String query) {
        Map<String, Object> summary = new HashMap<>();

        // Calculate average relevance score
        double avgRelevance = results.stream()
                .mapToDouble(doc -> (Double) doc.getMetadata().getOrDefault("distance", 0.8))
                .average()
                .orElse(0.7);

        summary.put("average_relevance", 1.0 - avgRelevance); // Convert distance to relevance
        summary.put("total_sections", results.size());
        summary.put("query_focus", query);

        // Get the most relevant section
        Document mostRelevant = results.stream()
                .min(Comparator.comparing(doc -> (Double) doc.getMetadata().getOrDefault("distance", 1.0)))
                .orElse(null);

        if (mostRelevant != null) {
            summary.put("most_relevant_section", Map.of(
                    "content_type", mostRelevant.getMetadata().getOrDefault("chunk_type", "general"),
                    "content_preview", mostRelevant.getFormattedContent().length() > 200 ?
                            mostRelevant.getFormattedContent().substring(0, 200) + "..." :
                            mostRelevant.getFormattedContent()
            ));
        }

        return summary;
    }

    private Map<String, Object> generateCompleteProfile(List<Document> allContent) {
        Map<String, Object> profile = new HashMap<>();

        // Extract consolidated information from all chunks
        Set<String> allSkills = extractSkillsFromResults(allContent);
        profile.put("technical_skills", allSkills);

        // Extract hobbies and interests
        Set<String> allHobbies = extractHobbiesFromResults(allContent);
        profile.put("hobbies_interests", allHobbies);

        // Determine experience level from metadata
        String experienceLevel = allContent.stream()
                .map(doc -> (String) doc.getMetadata().get("experience_level"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("not specified");
        profile.put("experience_level", experienceLevel);

        // Check education background
        boolean hasHigherEducation = allContent.stream()
                .anyMatch(doc -> Boolean.TRUE.equals(doc.getMetadata().get("has_higher_education")));
        profile.put("has_higher_education", hasHigherEducation);

        // Check hobbies section availability
        boolean hasHobbiesSection = allContent.stream()
                .anyMatch(doc -> Boolean.TRUE.equals(doc.getMetadata().get("has_hobbies_section")));
        profile.put("has_hobbies_section", hasHobbiesSection);

        // Check contact information availability
        boolean hasEmail = allContent.stream()
                .anyMatch(doc -> Boolean.TRUE.equals(doc.getMetadata().get("has_email")));
        boolean hasSocialProfiles = allContent.stream()
                .anyMatch(doc -> Boolean.TRUE.equals(doc.getMetadata().get("has_social_profiles")));
        profile.put("contact_info_available", hasEmail || hasSocialProfiles);

        // Get available content types
        Set<String> contentTypes = extractChunkTypes(allContent);
        profile.put("cv_sections_available", contentTypes);

        // Create a comprehensive content overview
        String fullContent = allContent.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n"));

        profile.put("content_length", fullContent.length());
        profile.put("total_sections", allContent.size());

        // Provide content summary
        profile.put("content_summary", fullContent.length() > 2000 ?
                fullContent.substring(0, 2000) + "... [truncated]" : fullContent);

        return profile;
    }
}

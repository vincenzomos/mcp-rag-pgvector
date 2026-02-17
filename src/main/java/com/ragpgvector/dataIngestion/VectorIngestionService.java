package com.ragpgvector.dataIngestion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VectorIngestionService {

    private final VectorStore vectorStore;

    public VectorIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestCvFiles() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/cv/*.pdf");

        log.info("Start loading {} CV PDF files...", resources.length);

        for (Resource pdfResource : resources) {
            try {
                // Enhanced PDF config for CV documents
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .build();

                PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);
                var documents = reader.get();

                // Enhance CV documents with specific metadata
                var enhancedDocs = documents.stream().map(doc -> {
                    String content = doc.getFormattedContent();
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());

                    // Add CV-specific metadata
                    metadata.put("source_file", pdfResource.getFilename());
                    metadata.put("file_type", "cv_pdf");
                    metadata.put("document_category", "cv");
                    metadata.put("candidate_name", extractCandidateName(pdfResource.getFilename()));

                    // Extract CV-specific information
                    extractCvMetadata(content, metadata);

                    return new Document(content, metadata);
                }).collect(Collectors.toList());

                // Use larger chunks for CV content to maintain context
                TokenTextSplitter splitter = new TokenTextSplitter(800, 150, 5, 2000, true);
                var processedDocs = splitter.apply(enhancedDocs);

                // Add chunk-specific metadata for CV
                for (int i = 0; i < processedDocs.size(); i++) {
                    var doc = processedDocs.get(i);
                    doc.getMetadata().put("chunk_index", i);
                    doc.getMetadata().put("total_chunks", processedDocs.size());

                    String chunkType = determineCvChunkType(doc.getFormattedContent());
                    doc.getMetadata().put("chunk_type", chunkType);

                    // Log when hobbies section is found
                    if ("hobbies".equals(chunkType)) {
                        log.info("Detected hobbies section in chunk {} of {}", i + 1, processedDocs.size());
                        log.debug("Hobbies content preview: {}",
                            doc.getFormattedContent().length() > 200 ?
                            doc.getFormattedContent().substring(0, 200) + "..." :
                            doc.getFormattedContent());
                    }
                }

                vectorStore.accept(processedDocs);
                log.info("Successfully ingested {} CV chunks from {}", processedDocs.size(), pdfResource.getFilename());

            } catch (Exception e) {
                log.error("Failed to ingest CV PDF: {}", pdfResource.getFilename(), e);
                throw new RuntimeException("CV PDF ingestion failed", e);
            }
        }
    }


    private String extractCandidateName(String filename) {
        // Extract candidate name from filename (e.g., "BEREND-BOTJE-CV.pdf" -> "Berend Botje")
        String name = filename.replace("-CV.pdf", "").replace(".pdf", "");
        return Arrays.stream(name.split("-"))
                .map(part -> part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }

    private void extractCvMetadata(String content, Map<String, Object> metadata) {
        String lowerContent = content.toLowerCase();

        // Extract skills
        Set<String> skills = new HashSet<>();
        String[] techSkills = {"java", "spring", "python", "javascript", "react", "docker", "kubernetes",
                              "microservices", "rest", "api", "sql", "postgresql", "mysql", "mongodb",
                              "git", "jenkins", "maven", "gradle", "junit", "mockito", "hibernate",
                              "angular", "vue", "node.js", "typescript", "aws", "azure", "gcp"};

        for (String skill : techSkills) {
            if (lowerContent.contains(skill.toLowerCase())) {
                skills.add(skill);
            }
        }
        if (!skills.isEmpty()) {
            metadata.put("technical_skills", new ArrayList<>(skills));
        }

        // Extract hobbies and interests
        Set<String> hobbies = new HashSet<>();
        String[] hobbyKeywords = {"reading", "writing", "music", "sports", "running", "cycling", "swimming",
                                 "photography", "travel", "cooking", "gaming", "hiking", "painting", "drawing",
                                 "dancing", "singing", "yoga", "meditation", "gardening", "fishing", "chess",
                                 "basketball", "football", "tennis", "soccer", "volleyball", "climbing"};

        for (String hobby : hobbyKeywords) {
            if (lowerContent.contains(hobby.toLowerCase())) {
                hobbies.add(hobby);
            }
        }
        if (!hobbies.isEmpty()) {
            metadata.put("hobbies_interests", new ArrayList<>(hobbies));
        }

        // Check if hobbies section exists
        if (lowerContent.contains("hobbies") || lowerContent.contains("interests") ||
            lowerContent.contains("personal interests") || lowerContent.contains("leisure")) {
            metadata.put("has_hobbies_section", true);
        }

        // Extract experience level indicators
        if (lowerContent.contains("senior") || lowerContent.contains("lead") ||
            lowerContent.contains("architect") || lowerContent.contains("years experience")) {
            metadata.put("experience_level", "senior");
        } else if (lowerContent.contains("junior") || lowerContent.contains("graduate")) {
            metadata.put("experience_level", "junior");
        } else {
            metadata.put("experience_level", "mid");
        }

        // Extract education keywords
        if (lowerContent.contains("university") || lowerContent.contains("bachelor") ||
            lowerContent.contains("master") || lowerContent.contains("phd") || lowerContent.contains("degree")) {
            metadata.put("has_higher_education", true);
        }

        // Extract contact information patterns
        if (lowerContent.contains("@") && lowerContent.contains(".")) {
            metadata.put("has_email", true);
        }
        if (lowerContent.contains("linkedin") || lowerContent.contains("github")) {
            metadata.put("has_social_profiles", true);
        }
    }

    private String determineCvChunkType(String content) {
        String lowerContent = content.toLowerCase();

        if (lowerContent.contains("experience") || lowerContent.contains("work") ||
            lowerContent.contains("employment") || lowerContent.contains("career")) {
            return "experience";
        } else if (lowerContent.contains("education") || lowerContent.contains("university") ||
                  lowerContent.contains("degree") || lowerContent.contains("qualification")) {
            return "education";
        } else if (lowerContent.contains("skill") || lowerContent.contains("technical") ||
                  lowerContent.contains("programming") || lowerContent.contains("technology")) {
            return "skills";
        } else if (lowerContent.contains("project") || lowerContent.contains("achievement") ||
                  lowerContent.contains("accomplishment")) {
            return "projects";
        } else if (lowerContent.contains("contact") || lowerContent.contains("email") ||
                  lowerContent.contains("phone") || lowerContent.contains("address")) {
            return "contact";
        } else if (lowerContent.contains("hobbies") || lowerContent.contains("hobby") ||
                  lowerContent.contains("interests") || lowerContent.contains("personal interests") ||
                  lowerContent.contains("leisure") || lowerContent.contains("activities")) {
            return "hobbies";
        } else {
            return "general";
        }
    }
}
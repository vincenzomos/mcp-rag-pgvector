package com.ragpgvector.config;

import com.ragpgvector.dataIngestion.VectorIngestionService;
import com.ragpgvector.dataIngestion.RelationalDataIngestionService;
import com.ragpgvector.service.DatabaseDiagnosticService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class IngestionConfig {

    @Bean
    CommandLineRunner runner(VectorIngestionService vectorIngestionService,
                           RelationalDataIngestionService relationalIngestionService,
                           DatabaseDiagnosticService diagnosticService) {
        return args -> {
            try {
                // Run database diagnostics first
               log.info("Running database diagnostics...");
                diagnosticService.runDiagnostics();

                // Ingest CSV data into relational database
                log.info("Starting relational data ingestion...");
                relationalIngestionService.ingestTimesheetData();
                relationalIngestionService.logDataSummary();
                log.info("Relational data ingestion completed successfully.");

                // Ingest PDF data into vector database
                log.info("Starting vector data ingestion...");
                vectorIngestionService.ingestCvFiles();
                log.info("Vector data ingestion completed successfully.");

            } catch (Exception e) {
                log.error("Fout bij laden data: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}
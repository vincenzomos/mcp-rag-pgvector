package com.ragpgvector.config;

import com.ragpgvector.VectorIngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionConfig {

    @Bean
    CommandLineRunner runner(VectorIngestionService ingestionService) {
        return args -> {
            try {
                ingestionService.ingestExcelPdf();
            } catch (Exception e) {
                System.err.println("Fout bij laden PDF's: " + e.getMessage());
            }
        };
    }
}
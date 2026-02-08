package com.ragpgvector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@Slf4j
public class VectorIngestionService {

    private final VectorStore vectorStore;

    public VectorIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void ingestExcelPdf() throws IOException {

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/hoursheets/*.pdf");


        log.info("Start met laden van " + resources.length + " PDF bestanden...");

        for (Resource pdfResource : resources) {
            try {
                // 1. Configureer de reader
                // Omdat het uit Excel komt, willen we de kolommen zo goed mogelijk behouden
                log.debug("Configuring PDF reader with custom settings for Excel-based PDF");
                PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(ExtractedTextFormatter.builder()
                                .withNumberOfTopTextLinesToDelete(0)
                                .build())
                        .build();

                PagePdfDocumentReader reader = new PagePdfDocumentReader(pdfResource, config);
                log.debug("PDF reader initialized successfully");

                // 2. Lees de documenten (per pagina)
                log.info("Reading PDF documents...");
                var documents = reader.get();
                log.info("Successfully read {} document(s) from PDF", documents.size());

                // 3. Splitsen (Optioneel, maar aanbevolen voor grote Excels)
                // Voor een urenregistratie houden we de chunks relatief groot
                // zodat een hele maand/regel bij elkaar blijft.
                log.info("Splitting documents into chunks (max tokens: 1000, overlap: 400)");
                TokenTextSplitter splitter = new TokenTextSplitter(1000, 400, 10, 5000, true);
                var processedDocs = splitter.apply(documents);
                log.info("Documents split into {} chunk(s)", processedDocs.size());

                // 4. Opslaan in de Vector DB (bijv. PGVector, Pinecone, Weaviate)
                log.info("Storing {} chunk(s) in vector database...", processedDocs.size());
                vectorStore.accept(processedDocs);
                log.info("Successfully stored all chunks in vector database");

                log.info("PDF ingestion completed successfully for: {}", pdfResource.getFilename());
            } catch (Exception e) {
                log.error("Failed to ingest PDF resource: {}", pdfResource.getFilename(), e);
                throw new RuntimeException("PDF ingestion failed", e);
            }
        }
    }
}
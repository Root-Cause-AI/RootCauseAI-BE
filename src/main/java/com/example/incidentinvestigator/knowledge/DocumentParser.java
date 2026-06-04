package com.example.incidentinvestigator.knowledge;

import com.example.incidentinvestigator.exception.AnalysisException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Extracts plain text from uploaded files.
 *
 * <p>Supports PDF (via Apache PDFBox), Markdown, and plain text files.</p>
 */
@Component
public class DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(DocumentParser.class);

    /**
     * Extracts text content from a multipart file upload.
     *
     * @param file the uploaded file
     * @param fileType the file type (PDF, MARKDOWN, TXT)
     * @return the extracted text content
     */
    public String parse(MultipartFile file, String fileType) {
        try {
            return switch (fileType.toUpperCase()) {
                case "PDF" -> parsePdf(file);
                case "MARKDOWN", "MD", "TXT", "TEXT" -> parseText(file);
                default -> throw new AnalysisException("Unsupported file type: " + fileType);
            };
        } catch (IOException e) {
            log.error("Failed to parse file: {}", file.getOriginalFilename(), e);
            throw new AnalysisException("Failed to parse uploaded file: " + e.getMessage(), e);
        }
    }

    private String parsePdf(MultipartFile file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF: {}", text.length(), file.getOriginalFilename());
            return text;
        }
    }

    private String parseText(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("Read {} characters from text file: {}", text.length(), file.getOriginalFilename());
        return text;
    }

    /**
     * Determines the file type from the filename extension.
     */
    public String detectFileType(String filename) {
        if (filename == null) return "TXT";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "MARKDOWN";
        return "TXT";
    }
}

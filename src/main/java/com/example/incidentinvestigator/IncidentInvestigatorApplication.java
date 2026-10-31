package com.example.incidentinvestigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Main entry point for the AI Incident Investigator application.
 *
 * <p>This is a Spring Boot application that provides AI-powered
 * production incident analysis using LLM providers to generate
 * structured investigation reports.</p>
 *
 * <p>@EnableRetry activates Spring Retry AOP proxy support for
 * exponential backoff on LLM API calls.</p>
 */
@SpringBootApplication
@EnableRetry
public class IncidentInvestigatorApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(IncidentInvestigatorApplication.class, args);
    }

    private static void loadDotenvIfPresent() {
        java.io.File envFile = new java.io.File(".env");
        if (!envFile.exists()) {
            envFile = new java.io.File("Rootcause AI-BE/.env");
        }
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                        int idx = line.indexOf('=');
                        String key = line.substring(0, idx).trim();
                        String val = line.substring(idx + 1).trim();
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}

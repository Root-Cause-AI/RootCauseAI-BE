package com.example.incidentinvestigator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the AI Incident Investigator application.
 *
 * <p>This is a Spring Boot application that provides AI-powered
 * production incident analysis using LLM providers to generate
 * structured investigation reports.</p>
 */
@SpringBootApplication
public class IncidentInvestigatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IncidentInvestigatorApplication.class, args);
    }
}

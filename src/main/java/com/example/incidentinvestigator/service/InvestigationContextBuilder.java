package com.example.incidentinvestigator.service;

import com.example.incidentinvestigator.entity.Incident;
import com.example.incidentinvestigator.knowledge.RetrievalResult;
import com.example.incidentinvestigator.knowledge.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service responsible for building the investigation context.
 *
 * <p>Generates the search query from the incident data and retrieves
 * relevant knowledge base chunks to build a complete RAG context package.</p>
 */
@Service
public class InvestigationContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(InvestigationContextBuilder.class);

    private final RetrievalService retrievalService;

    public InvestigationContextBuilder(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    /**
     * Builds a query from the incident data and retrieves relevant
     * knowledge base chunks via hybrid search.
     *
     * @param incident the incident being analyzed
     * @return a list of relevant retrieval results
     */
    public List<RetrievalResult> buildContext(Incident incident) {
        // Build query from title + first 500 chars of logs
        StringBuilder queryBuilder = new StringBuilder(incident.getTitle());
        if (incident.getLogs() != null && !incident.getLogs().isBlank()) {
            String logsExcerpt = incident.getLogs().substring(0,
                    Math.min(500, incident.getLogs().length()));
            queryBuilder.append(" ").append(logsExcerpt);
        }

        String query = queryBuilder.toString();
        log.debug("RAG query (length: {} chars): {}", query.length(),
                query.substring(0, Math.min(100, query.length())));

        List<RetrievalResult> results = retrievalService.retrieve(query);
        log.info("Retrieved {} knowledge context chunks for investigation", results.size());
        return results;
    }
}

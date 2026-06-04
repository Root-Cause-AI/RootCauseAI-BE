package com.example.incidentinvestigator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimilarIncident {
    private String incidentId;
    private String reason;
    private String similarity; // HIGH, MEDIUM, LOW
}

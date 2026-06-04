package com.example.incidentinvestigator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing a citation from the AI analysis.
 *
 * <p>Citations link AI findings back to specific knowledge base documents
 * that influenced the analysis.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class Citation {

    @JsonProperty("documentTitle")
    private String documentTitle;

    @JsonProperty("documentType")
    private String documentType;

    @JsonProperty("excerpt")
    private String excerpt;

    @JsonProperty("relevance")
    private String relevance;
}

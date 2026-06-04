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
public class TimelineEvent {
    private String timestamp;
    private String event;
    private String type; // DEPLOYMENT, WARNING, ERROR, FAILURE
}

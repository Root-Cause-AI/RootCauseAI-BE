package com.example.incidentinvestigator.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrievedContextResult {
    @Builder.Default
    private List<String> runbookTitles = new ArrayList<>();
    @Builder.Default
    private List<String> similarIncidentIds = new ArrayList<>();
    private String pastIncidentInsights;
    private String deploymentSummary;
    @Builder.Default
    private List<String> citations = new ArrayList<>();
    private String summary;
}

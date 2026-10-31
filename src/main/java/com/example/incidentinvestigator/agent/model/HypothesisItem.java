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
public class HypothesisItem {
    private String hypothesis;
    private int confidence;
    @Builder.Default
    private List<String> supportingEvidence = new ArrayList<>();
    @Builder.Default
    private List<String> contradictingEvidence = new ArrayList<>();
    @Builder.Default
    private List<String> verifiedFacts = new ArrayList<>();
    private boolean needsMoreEvidence;
    private String targetedSearchQuery;
}

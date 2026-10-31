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
public class HypothesisResult {
    @Builder.Default
    private List<HypothesisItem> rankedHypotheses = new ArrayList<>();
    private HypothesisItem primaryHypothesis;
    private String reasoningSummary;
}

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
public class RemediationResult {
    @Builder.Default
    private List<String> immediateActions = new ArrayList<>();
    @Builder.Default
    private List<String> preventiveMeasures = new ArrayList<>();
    @Builder.Default
    private List<String> verificationSteps = new ArrayList<>();
    private String rollbackPlan;
    private String summary;
}

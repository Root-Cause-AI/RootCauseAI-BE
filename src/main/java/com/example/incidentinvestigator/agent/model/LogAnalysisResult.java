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
public class LogAnalysisResult {
    @Builder.Default
    private List<String> detectedErrors = new ArrayList<>();
    private String rootPattern;
    private String affectedComponent;
    private String severity;
    @Builder.Default
    private List<String> keyTimestamps = new ArrayList<>();
    private String summary;
}

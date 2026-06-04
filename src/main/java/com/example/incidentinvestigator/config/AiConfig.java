package com.example.incidentinvestigator.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class AiConfig {

    @Value("${spring.ai.google.genai.api-key}")
    private String apiKey;

    @Value("${spring.ai.google.genai.embedding.options.model:gemini-embedding-2}")
    private String model;

    @Bean
    public EmbeddingModel embeddingModel(RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder.baseUrl("https://generativelanguage.googleapis.com/v1beta/").build();
        
        return new EmbeddingModel() {
            @Override
            public float[] embed(Document document) {
                return embed(document.getText());
            }

            @Override
            public float[] embed(String text) {
                return call(new EmbeddingRequest(List.of(text), null))
                        .getResult().getOutput();
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                return call(new EmbeddingRequest(texts, null))
                        .getResults().stream()
                        .map(Embedding::getOutput)
                        .collect(Collectors.toList());
            }

            @Override
            public int dimensions() {
                return 768;
            }

            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> texts = request.getInstructions();
                List<Embedding> embeddings = new ArrayList<>();
                
                for (int i = 0; i < texts.size(); i++) {
                    String text = texts.get(i);
                    Map<String, Object> body = Map.of(
                            "model", "models/" + model,
                            "content", Map.of("parts", List.of(Map.of("text", text))),
                            "outputDimensionality", 768
                    );

                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = restClient.post()
                            .uri("models/{model}:embedContent?key={key}", model, apiKey)
                            .body(body)
                            .retrieve()
                            .body(Map.class);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> embeddingMap = (Map<String, Object>) response.get("embedding");
                    @SuppressWarnings("unchecked")
                    List<Double> values = (List<Double>) embeddingMap.get("values");
                    
                    float[] floatValues = new float[values.size()];
                    for (int j = 0; j < values.size(); j++) {
                        floatValues[j] = values.get(j).floatValue();
                    }
                    
                    embeddings.add(new Embedding(floatValues, i));
                }
                
                return new EmbeddingResponse(embeddings);
            }
        };
    }
}

package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * Spring AI implementation of the {@link LlmClient} interface.
 *
 * <p>Uses Spring AI's {@link ChatClient} to communicate with the
 * configured LLM API (e.g. Gemini, OpenAI). The model and parameters
 * are configured in {@code application.yml} under {@code spring.ai}.</p>
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmClient.class);

    private final ChatClient chatClient;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Sends the analysis prompt to the LLM and returns the response.
     *
     * @param prompt the fully constructed SRE analysis prompt
     * @return the raw model response text
     * @throws AiProviderException if the API call fails
     */
    @Override
    public String analyze(String prompt) {
        log.info("Sending analysis prompt to LLM (length: {} chars)", prompt.length());

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                throw new AiProviderException("LLM returned an empty response");
            }

            log.info("Received response from LLM (length: {} chars)", response.length());
            log.debug("Raw AI response: {}", response);

            return response;
        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            log.error("LLM API call failed: {}", e.getMessage(), e);
            throw new AiProviderException("Failed to communicate with LLM: " + e.getMessage(), e);
        }
    }
}

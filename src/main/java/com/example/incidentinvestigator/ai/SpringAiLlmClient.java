package com.example.incidentinvestigator.ai;

import com.example.incidentinvestigator.exception.AiProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/**
 * Spring AI implementation of the {@link LlmClient} interface.
 *
 * <p>Uses Spring AI's {@link ChatClient} to communicate with the
 * configured LLM API (Gemini). Implements exponential backoff retry
 * for transient API failures using Spring Retry annotations.</p>
 *
 * <p>Retry policy: up to 3 attempts with 2s initial delay, 2x multiplier
 * (2s → 4s → 8s). Recovers by throwing AiProviderException on exhaustion.</p>
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
     * Retries on transient failures with exponential backoff.
     *
     * @param prompt the fully constructed SRE analysis prompt
     * @return the raw model response text
     * @throws AiProviderException if the API call fails after all retries
     */
    @Override
    @Retryable(
        retryFor = { Exception.class },
        noRetryFor = { AiProviderException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 10000)
    )
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
            log.warn("LLM API call failed (will retry if attempts remain): {}", e.getMessage());
            throw new RuntimeException("Transient LLM failure: " + e.getMessage(), e);
        }
    }

    /**
     * Recovery method called after all retry attempts are exhausted.
     *
     * @param e the last exception thrown
     * @param prompt the original prompt
     * @return never returns — always throws AiProviderException
     */
    @Recover
    public String recover(Exception e, String prompt) {
        log.error("All LLM retry attempts exhausted. Prompt length: {}. Error: {}",
                prompt.length(), e.getMessage(), e);
        throw new AiProviderException(
                "LLM API unavailable after retries: " + e.getMessage(), e);
    }
}

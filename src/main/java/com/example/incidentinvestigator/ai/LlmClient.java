package com.example.incidentinvestigator.ai;

/**
 * Abstraction for LLM provider communication.
 *
 * <p>This interface decouples the analysis workflow from any specific
 * LLM provider. The initial implementation uses OpenAI, but this
 * design allows easy replacement with AWS Bedrock, Azure OpenAI,
 * or any other provider by implementing this interface.</p>
 *
 * <p>Provider implementations should:</p>
 * <ul>
 *   <li>Handle API communication and retries</li>
 *   <li>Throw {@link com.example.incidentinvestigator.exception.AiProviderException}
 *       on provider-level failures</li>
 *   <li>Return the raw text response from the model</li>
 * </ul>
 */
public interface LlmClient {

    /**
     * Sends a prompt to the LLM and returns the raw response text.
     *
     * @param prompt the fully constructed analysis prompt
     * @return the raw model response (expected to be JSON)
     */
    String analyze(String prompt);
}

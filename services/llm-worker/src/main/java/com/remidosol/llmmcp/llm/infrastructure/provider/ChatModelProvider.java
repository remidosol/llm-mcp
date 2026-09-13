package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.remidosol.llmmcp.llm.application.port.LlmProvider;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import com.remidosol.llmmcp.llm.domain.LlmResult;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Template Method for real providers: the Spring AI {@link ChatModel} call, usage extraction and
 * the retryable/non-retryable split are identical for every SDK; subclasses supply the per-call
 * options and know their SDK's exception types.
 */
abstract class ChatModelProvider implements LlmProvider {

    private final ChatModel chatModel;

    ChatModelProvider(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** Provider-specific options: model id and the {@code max_tokens} cost cap. */
    protected abstract ChatOptions options(String model, int maxTokens);

    /** Whether a failure is worth retrying (429, 5xx, I/O) — provider SDKs encode this differently. */
    protected abstract boolean retryable(RuntimeException failure);

    @Override
    public LlmResult complete(String model, String prompt, int maxTokens, int attemptNo) {
        try {
            ChatResponse response = chatModel.call(new Prompt(prompt, options(model, maxTokens)));
            String output = response.getResult() == null ? null : response.getResult().getOutput().getText();
            Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
            int promptTokens = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            int completionTokens = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
            return new LlmResult(output == null ? "" : output, promptTokens, completionTokens);
        } catch (LlmCallException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LlmCallException(name() + ": " + e.getMessage(), retryable(e), e);
        }
    }
}

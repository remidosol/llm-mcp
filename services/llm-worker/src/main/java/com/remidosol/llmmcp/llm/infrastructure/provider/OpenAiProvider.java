package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI adapter (Strategy). Retryable = rate limits, server errors and I/O failures; anything the
 * request itself caused (400/401/404/422) is not — ADR-0018 builds the retry/breaker policy on this.
 */
class OpenAiProvider extends ChatModelProvider {

    static final String NAME = "openai";

    OpenAiProvider(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected ChatOptions options(String model, int maxTokens) {
        return OpenAiChatOptions.builder().maxTokens(maxTokens).model(model).build();
    }

    @Override
    protected boolean retryable(RuntimeException failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof OpenAIServiceException service) {
                int status = service.statusCode();
                return status == 429 || status == 408 || status >= 500;
            }
            if (cause instanceof OpenAIIoException) {
                return true;
            }
            cause = cause.getCause();
        }
        return true; // unknown failure: let the retry policy decide (bounded by max attempts)
    }
}

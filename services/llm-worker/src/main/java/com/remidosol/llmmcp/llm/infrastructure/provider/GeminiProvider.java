package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.google.genai.errors.ApiException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/** Gemini adapter (Strategy); same classification rule as OpenAI, read from the google-genai SDK. */
class GeminiProvider extends ChatModelProvider {

    static final String NAME = "gemini";

    GeminiProvider(ChatModel chatModel) {
        super(chatModel);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    protected ChatOptions options(String model, int maxTokens) {
        return GoogleGenAiChatOptions.builder().maxOutputTokens(maxTokens).model(model).build();
    }

    @Override
    protected boolean retryable(RuntimeException failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof ApiException api) {
                int status = api.code();
                return status == 429 || status == 408 || status >= 500;
            }
            cause = cause.getCause();
        }
        return true;
    }
}

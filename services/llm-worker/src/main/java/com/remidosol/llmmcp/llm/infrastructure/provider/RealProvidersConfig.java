package com.remidosol.llmmcp.llm.infrastructure.provider;

import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import com.openai.core.Timeout;
import com.remidosol.llmmcp.llm.application.LlmProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Real providers exist only when their API key is configured (the fake provider is the
 * default). Spring AI's chat auto-configuration is disabled ({@code spring.ai.model.chat=none})
 * because with two model starters on the classpath it would demand both keys at startup; here the
 * models are built explicitly — with the SDK's own retries OFF, so Phase 5's Resilience4j policy
 * is the only retry/breaker layer (ADR-0018).
 */
@Configuration
class RealProvidersConfig {

    @Bean
    @ConditionalOnProperty("spring.ai.openai.api-key")
    OpenAiProvider openAiProvider(@Value("${spring.ai.openai.api-key}") String apiKey,
                                  @Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
                                  LlmProperties properties) {
        ClientOptions options = ClientOptions.builder()
                .httpClient(SpringAiOpenAiHttpClient.builder().build())
                .apiKey(apiKey)
                .baseUrl(baseUrl)          // overridable: WireMock stubs in tests, proxies in prod
                .maxRetries(0)
                .timeout(Timeout.builder().connect(Duration.ofSeconds(5)).read(Duration.ofSeconds(60))
                        .write(Duration.ofSeconds(10)).request(Duration.ofSeconds(70)).build()) // ADR-0018
                .build();
        OpenAIClient client = new OpenAIClientImpl(options);
        ChatModel model = OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(client.async()) // otherwise the builder assembles its own async client and demands its own key
                .options(OpenAiChatOptions.builder().maxTokens(properties.maxTokens()).build())
                .build();
        return new OpenAiProvider(model);
    }

    @Bean
    @ConditionalOnProperty("spring.ai.google.genai.api-key")
    GeminiProvider geminiProvider(@Value("${spring.ai.google.genai.api-key}") String apiKey, LlmProperties properties) {
        Client client = Client.builder().apiKey(apiKey).build();
        ChatModel model = GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().maxOutputTokens(properties.maxTokens()).build())
                .build();
        return new GeminiProvider(model);
    }
}

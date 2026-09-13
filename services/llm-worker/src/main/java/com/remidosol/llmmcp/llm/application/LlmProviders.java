package com.remidosol.llmmcp.llm.application;

import com.remidosol.llmmcp.llm.application.port.LlmProvider;
import com.remidosol.llmmcp.llm.domain.LlmCallException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry of the Strategy implementations, keyed by model prefix. Spring injects every
 * {@link LlmProvider} bean — the fake one always, the real ones only when their keys are set — so
 * adding a provider is one class, no switch statement.
 */
@Component
public class LlmProviders {

    /** {@code openai:gpt-4o-mini} → provider {@code openai}, model id {@code gpt-4o-mini}. */
    public record ModelRef(String provider, String modelId) {
        public static ModelRef parse(String model) {
            int colon = model.indexOf(':');
            if (colon <= 0 || colon == model.length() - 1) {
                throw new LlmCallException("model must look like provider:model-id, got '" + model + "'", false);
            }
            return new ModelRef(model.substring(0, colon), model.substring(colon + 1));
        }
    }

    private final Map<String, LlmProvider> byName;

    public LlmProviders(List<LlmProvider> providers) {
        this.byName = providers.stream().collect(Collectors.toUnmodifiableMap(LlmProvider::name, Function.identity()));
    }

    public LlmProvider forModel(ModelRef ref) {
        LlmProvider provider = byName.get(ref.provider());
        if (provider == null) {
            throw new LlmCallException("no provider configured for '" + ref.provider() + "' (available: "
                    + byName.keySet() + ")", false);
        }
        return provider;
    }

    public java.util.Set<String> names() {
        return byName.keySet();
    }
}

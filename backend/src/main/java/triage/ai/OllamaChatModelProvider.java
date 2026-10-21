package triage.ai;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;

/** Builds Ollama chat models from the same langchain4j.ollama.chat-model.* settings the starter uses. */
@Component
public class OllamaChatModelProvider implements ChatModelProvider {

    private final String baseUrl;
    private final String defaultModel;
    private final double temperature;
    private final Duration timeout;
    private final int numCtx;
    private final boolean logRequests;
    private final Map<String, ChatModel> cache = new ConcurrentHashMap<>();

    public OllamaChatModelProvider(
            @Value("${langchain4j.ollama.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.ollama.chat-model.model-name}") String defaultModel,
            @Value("${langchain4j.ollama.chat-model.temperature:0.0}") double temperature,
            @Value("${langchain4j.ollama.chat-model.timeout:PT600S}") Duration timeout,
            @Value("${langchain4j.ollama.chat-model.num-ctx:8192}") int numCtx,
            @Value("${langchain4j.ollama.chat-model.log-requests:false}") boolean logRequests) {
        this.baseUrl = baseUrl;
        this.defaultModel = defaultModel;
        this.temperature = temperature;
        this.timeout = timeout;
        this.numCtx = numCtx;
        this.logRequests = logRequests;
    }

    @Override
    public ChatModel get(String modelName) {
        return cache.computeIfAbsent(modelName == null ? defaultModel : modelName, name -> OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(name)
                .temperature(temperature)
                .numCtx(numCtx)
                .timeout(timeout)
                .think(false)
                .supportedCapabilities(Capability.RESPONSE_FORMAT_JSON_SCHEMA)
                .logRequests(logRequests)
                .logResponses(logRequests)
                .maxRetries(1)
                .build());
    }

    @Override
    public String defaultModel() {
        return defaultModel;
    }
}

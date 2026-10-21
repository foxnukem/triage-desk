package triage.it;

import java.util.Arrays;
import java.util.List;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import triage.TriageProperties;
import triage.ai.ChatModelProvider;

/** No Ollama in integration tests: a scripted chat model and a constant embedding (every article matches equally). */
@TestConfiguration
class StubAiConfig {

    @Bean
    ScriptedChatModel scriptedChatModel() {
        return new ScriptedChatModel();
    }

    @Bean
    @Primary
    ChatModelProvider stubChatModelProvider(ScriptedChatModel model) {
        return new ChatModelProvider() {
            @Override
            public ChatModel get(String modelName) {
                return model;
            }

            @Override
            public String defaultModel() {
                return "stub";
            }
        };
    }

    @Bean
    @Primary
    EmbeddingModel stubEmbeddingModel(TriageProperties props) {
        float[] v = new float[props.kb().dimension()];
        Arrays.fill(v, (float) (1 / Math.sqrt(v.length)));
        return new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                return Response.from(segments.stream().map(s -> Embedding.from(v.clone())).toList());
            }
        };
    }
}

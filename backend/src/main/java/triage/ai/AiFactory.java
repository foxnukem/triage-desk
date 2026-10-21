package triage.ai;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.service.AiServices;
import triage.tools.KnowledgeBaseTools;
import triage.tools.OrderTools;

/** AiServices per model name; built once and cached. */
@Component
public class AiFactory {

    private final ChatModelProvider models;
    private final KnowledgeBaseTools kbTools;
    private final OrderTools orderTools;
    private final Map<String, AiComponents> cache = new ConcurrentHashMap<>();

    public AiFactory(ChatModelProvider models, KnowledgeBaseTools kbTools, OrderTools orderTools) {
        this.models = models;
        this.kbTools = kbTools;
        this.orderTools = orderTools;
    }

    public String defaultModel() {
        return models.defaultModel();
    }

    public AiComponents forModel(String modelName) {
        String name = modelName == null || modelName.isBlank() ? models.defaultModel() : modelName;
        return cache.computeIfAbsent(name, n -> build(n, models.get(n)));
    }

    private AiComponents build(String name, ChatModel model) {
        return new AiComponents(
                name,
                AiServices.builder(TicketClassifier.class).chatModel(model).build(),
                AiServices.builder(EntityExtractor.class).chatModel(model).build(),
                AiServices.builder(ReplyAgent.class)
                        .chatModel(model)
                        .tools(kbTools, orderTools)
                        .maxSequentialToolsInvocations(6)
                        .chatRequestTransformer(AiFactory::toolsThenSchema)
                        .build(),
                AiServices.builder(ReplyCritic.class).chatModel(model).build(),
                AiServices.builder(MegaPromptTriage.class).chatModel(model).build());
    }

    /**
     * Ollama's {@code format} constrains decoding to the JSON Schema, which makes tool calls impossible.
     * So the agent gets tools without a schema until searchKb has returned, then the schema without
     * tools for the final answer. Keying on searchKb, not on any tool: a model that calls getOrder
     * first would otherwise lose the tools before it could search, and fail the citation check.
     */
    static ChatRequest toolsThenSchema(ChatRequest request) {
        boolean toolsRan = request.messages().stream().anyMatch(m -> m instanceof ToolExecutionResultMessage t
                && KnowledgeBaseTools.SEARCH_KB.equals(t.toolName()));
        ChatRequestParameters p = request.parameters();
        var builder = DefaultChatRequestParameters.builder().overrideWith(p);
        if (toolsRan) {
            builder.toolSpecifications((List<dev.langchain4j.agent.tool.ToolSpecification>) null);
        } else {
            builder.responseFormat((ResponseFormat) null);
        }
        List<ChatMessage> messages = request.messages();
        return ChatRequest.builder().messages(messages).parameters(builder.build()).build();
    }
}

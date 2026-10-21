package triage.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;

class ToolsThenSchemaTest {

    private static ChatRequest request(List<ChatMessage> messages) {
        return ChatRequest.builder().messages(messages)
                .toolSpecifications(ToolSpecification.builder().name("searchKb").build(),
                        ToolSpecification.builder().name("getOrder").build())
                .responseFormat(ResponseFormat.builder().type(dev.langchain4j.model.chat.request.ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder().name("ReplyDraft").rootElement(JsonObjectSchema.builder().build()).build())
                        .build())
                .build();
    }

    private static ToolExecutionResultMessage result(String tool) {
        return ToolExecutionResultMessage.from(ToolExecutionRequest.builder().id(tool).name(tool).arguments("{}").build(), "ok");
    }

    private static AiMessage call(String tool) {
        return AiMessage.from(ToolExecutionRequest.builder().id(tool).name(tool).arguments("{}").build());
    }

    @Test
    void toolsWithoutSchemaBeforeAnySearch() {
        var r = AiFactory.toolsThenSchema(request(List.of(UserMessage.from("ticket"))));
        assertThat(r.toolSpecifications()).hasSize(2);
        assertThat(r.responseFormat()).isNull();
    }

    @Test
    void getOrderAloneKeepsTheToolsSoTheModelCanStillSearch() {
        var r = AiFactory.toolsThenSchema(request(List.of(UserMessage.from("ticket"), call("getOrder"), result("getOrder"))));
        assertThat(r.toolSpecifications()).hasSize(2);
        assertThat(r.responseFormat()).isNull();
    }

    @Test
    void schemaWithoutToolsAfterSearch() {
        var r = AiFactory.toolsThenSchema(request(List.of(UserMessage.from("ticket"), call("searchKb"), result("searchKb"))));
        assertThat(r.toolSpecifications()).isNullOrEmpty();
        assertThat(r.responseFormat()).isNotNull();
    }
}

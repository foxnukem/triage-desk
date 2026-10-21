package triage.it;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import triage.tools.KnowledgeBaseTools;

/**
 * Stub LLM. Recognises the pipeline step by its system prompt and answers with canned JSON.
 * Tests can queue answers per step; the reply agent calls searchKb and cites the first article returned.
 */
class ScriptedChatModel implements ChatModel {

    enum Step { CLASSIFY, EXTRACT, REPLY, CRITIC, REWRITE, MEGA }

    private final Map<Step, Deque<String>> queued = new ConcurrentHashMap<>();
    private final Map<Step, Integer> calls = new ConcurrentHashMap<>();
    private volatile CountDownLatch gate;
    /** Reply agent that never calls tools, like qwen3:8b on some tickets. */
    private volatile boolean ignoreTools;

    void reset() {
        queued.clear();
        calls.clear();
        gate = null;
        ignoreTools = false;
    }

    void ignoreTools() {
        ignoreTools = true;
    }

    /** Next answers of this step, in order; after they run out the default answer is used. */
    void queue(Step step, String... answers) {
        queued.computeIfAbsent(step, s -> new ArrayDeque<>()).addAll(List.of(answers));
    }

    /** Classification blocks until the returned latch is released. */
    CountDownLatch holdClassification() {
        gate = new CountDownLatch(1);
        return gate;
    }

    int calls(Step step) {
        return calls.getOrDefault(step, 0);
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        Step step = stepOf(request.messages());
        calls.merge(step, 1, Integer::sum);
        if (step == Step.CLASSIFY && gate != null) {
            try {
                gate.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        String ticket = ticketOf(request.messages());
        if (step == Step.REPLY) {
            return reply(request.messages());
        }
        Deque<String> q = queued.get(step);
        String json = q != null && !q.isEmpty() ? q.poll() : defaults(step).apply(ticket);
        return text(json);
    }

    private static Function<String, String> defaults(Step step) {
        return switch (step) {
            case CLASSIFY -> t -> """
                    {"category":"RETURN_REFUND","priority":"P3","priorityReason":"a return request",
                     "sentiment":"NEUTRAL","language":"EN","confidence":0.9,"multiIntent":false}""";
            case EXTRACT -> t -> t.contains("GK-104233")
                    ? "{\"orderId\":\"GK-104233\",\"summary\":\"Customer wants to return an item.\"}"
                    : "{\"summary\":\"Customer asks for help.\"}";
            case CRITIC -> t -> "{\"compliant\":true,\"violations\":[],\"customerAsksOutOfPolicy\":false}";
            case REWRITE -> t -> "{\"text\":\"Rewritten. Gadgetko Support\",\"citedArticles\":[],\"promisesAction\":false}";
            case MEGA, REPLY -> t -> "{}";
        };
    }

    private ChatResponse reply(List<ChatMessage> messages) {
        if (ignoreTools) {
            String user = ticketOf(messages);
            String cited = KnowledgeBaseTools.idsIn(user).stream().findFirst().map(id -> "\"" + id + "\"").orElse("");
            return text("{\"text\":\"We are checking your parcel. Gadgetko Support\",\"citedArticles\":[" + cited
                    + "],\"promisesAction\":false}");
        }
        var toolResult = messages.stream().filter(m -> m instanceof ToolExecutionResultMessage)
                .map(m -> ((ToolExecutionResultMessage) m).text()).findFirst();
        if (toolResult.isEmpty()) {
            return ChatResponse.builder().aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                    .id("call-1").name(KnowledgeBaseTools.SEARCH_KB).arguments("{\"query\":\"return an item\"}").build())).build();
        }
        Deque<String> q = queued.get(Step.REPLY);
        if (q != null && !q.isEmpty()) {
            return text(q.poll());
        }
        String cited = KnowledgeBaseTools.idsIn(toolResult.get()).stream().findFirst().orElseThrow();
        return text("{\"text\":\"Thank you, we can help with the return within 14 days. Gadgetko Support\","
                + "\"citedArticles\":[\"" + cited + "\"],\"promisesAction\":false}");
    }

    private static ChatResponse text(String json) {
        return ChatResponse.builder().aiMessage(AiMessage.from(json)).build();
    }

    private static Step stepOf(List<ChatMessage> messages) {
        String system = messages.stream().filter(m -> m instanceof SystemMessage)
                .map(m -> ((SystemMessage) m).text()).findFirst().orElse("");
        if (system.startsWith("You classify")) {
            return Step.CLASSIFY;
        } else if (system.startsWith("You extract")) {
            return Step.EXTRACT;
        } else if (system.startsWith("You draft")) {
            return Step.REPLY;
        } else if (system.startsWith("You check")) {
            return Step.CRITIC;
        } else if (system.startsWith("You fix")) {
            return Step.REWRITE;
        } else if (system.startsWith("You triage")) {
            return Step.MEGA;
        }
        throw new IllegalStateException("unknown prompt: " + system.substring(0, Math.min(80, system.length())));
    }

    private static String ticketOf(List<ChatMessage> messages) {
        return messages.stream().filter(m -> m instanceof UserMessage).map(m -> ((UserMessage) m).singleText())
                .findFirst().orElse("");
    }
}

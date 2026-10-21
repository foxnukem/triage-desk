package triage.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;

import triage.ai.Category;
import triage.ai.Classification;
import triage.ai.Lang;
import triage.ai.Priority;
import triage.ai.Sentiment;
import triage.it.ScriptedChatModel.Step;
import triage.laya.LayaClassifier;
import triage.persistence.TicketRepository;

/** Whole backend against a real pgvector Postgres, with the LLM stubbed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(StubAiConfig.class)
class TriageDeskIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> db = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @Autowired
    TestRestTemplate http;

    @Autowired
    ScriptedChatModel llm;

    @Autowired
    TicketRepository tickets;

    /** No Laya model in tests: the arm's wiring is tested with the classifier mocked. */
    @MockitoBean
    LayaClassifier laya;

    @BeforeEach
    void reset() {
        llm.reset();
    }

    private JsonNode triage(String text) {
        var res = http.postForEntity("/api/triage", Map.of("text", text), JsonNode.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private static List<String> steps(JsonNode result) {
        return result.path("trace").path("steps").findValuesAsText("name");
    }

    @Test
    void widgetTicketLifecycle() {
        CountDownLatch release = llm.holdClassification();

        var created = http.postForEntity("/api/public/tickets",
                Map.of("email", "olena.koval@example.com", "orderNumber", "GK-104233", "text", "I want to return the headphones"),
                JsonNode.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        long id = created.getBody().path("id").asLong();
        assertThat(tickets.findById(id).orElseThrow().getStatus().name()).isEqualTo("NEW");

        release.countDown();
        await().atMost(Duration.ofSeconds(30)).until(() -> "TRIAGED".equals(detail(id).path("status").asText()));

        JsonNode d = detail(id);
        JsonNode r = d.path("result");
        assertThat(r.path("classification").path("category").asText()).isEqualTo("RETURN_REFUND");
        assertThat(r.path("extraction").path("orderId").asText()).isEqualTo("GK-104233");
        assertThat(r.path("queue").asText()).isEqualTo("returns");
        assertThat(r.path("needsHuman").asBoolean()).isFalse();
        assertThat(r.path("reply").path("citedArticles")).hasSize(1);
        assertThat(steps(r)).contains("rule-router", "classify", "extract", "order-check", "reply", "critic-review", "escalation");
        JsonNode reply = r.path("trace").path("steps").findParents("toolCalls").stream()
                .filter(s -> "reply".equals(s.path("name").asText())).findFirst().orElseThrow();
        assertThat(reply.path("toolCalls").get(0).path("name").asText()).isEqualTo("searchKb");

        var decided = http.postForEntity("/api/tickets/" + id + "/decision", Map.of("action", "ACCEPTED"), JsonNode.class);
        assertThat(decided.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(decided.getBody().path("status").asText()).isEqualTo("RESOLVED");
        assertThat(decided.getBody().path("decisions")).hasSize(1);

        JsonNode publicStatus = http.getForObject("/api/public/tickets/" + id + "/status", JsonNode.class);
        assertThat(publicStatus.path("status").asText()).isNotBlank();
        assertThat(publicStatus.has("result")).isFalse();
    }

    private JsonNode detail(long id) {
        return http.getForObject("/api/tickets/" + id, JsonNode.class);
    }

    @Test
    void doubleChargeRuleOverridesTheModel() {
        llm.queue(Step.CLASSIFY, """
                {"category":"SHIPPING","priority":"P4","priorityReason":"x","sentiment":"NEGATIVE",
                 "language":"EN","confidence":0.8,"multiIntent":false}""");

        JsonNode r = triage("You charged me twice for one order!");

        assertThat(r.path("classification").path("category").asText()).isEqualTo("BILLING");
        assertThat(r.path("classification").path("priority").asText()).isEqualTo("P2");
        assertThat(r.path("ruleOverride").path("ruleId").asText()).isEqualTo("double-charge");
        assertThat(r.path("ruleOverride").path("modelCategory").asText()).isEqualTo("SHIPPING");
        assertThat(r.path("queue").asText()).isEqualTo("billing");
    }

    @Test
    void hallucinatedOrderIdIsRejectedAndRetried() {
        llm.queue(Step.EXTRACT, "{\"orderId\":\"GK-999999\",\"summary\":\"Where is my parcel.\"}");

        JsonNode r = triage("Where is my parcel? I have been waiting for a week.");

        assertThat(llm.calls(Step.EXTRACT)).isEqualTo(2);
        assertThat(r.path("extraction").has("orderId")).isFalse();
        assertThat(r.path("needsHuman").asBoolean()).isFalse();
        JsonNode first = r.path("trace").path("steps").findParents("name").stream()
                .filter(s -> "extract".equals(s.path("name").asText())).findFirst().orElseThrow();
        assertThat(first.path("status").asText()).isEqualTo("INVALID");
        assertThat(first.path("errors").get(0).asText()).contains("GK-999999");
    }

    @Test
    void invalidAnswerThreeTimesEscalates() {
        String bad = "{\"category\":\"SHOES\"}";
        llm.queue(Step.CLASSIFY, bad, bad, bad);

        JsonNode r = triage("Hello, I have a question about my order.");

        assertThat(llm.calls(Step.CLASSIFY)).isEqualTo(3);
        assertThat(r.path("needsHuman").asBoolean()).isTrue();
        assertThat(r.path("escalationReason").asText()).contains("VALIDATION_FAILED");
        assertThat(r.path("trace").path("steps").findValuesAsText("status")).contains("FAILED");
    }

    @Test
    void unknownOrderIsNulledAfterDatabaseCheck() {
        llm.queue(Step.EXTRACT, "{\"orderId\":\"GK-999999\",\"summary\":\"Where is order GK-999999.\"}");

        JsonNode r = triage("Where is my order GK-999999?");

        assertThat(r.path("extraction").has("orderId")).isFalse();
        assertThat(steps(r)).contains("order-check");
    }

    @Test
    void criticRewritesADraftThatBreaksPolicy() {
        llm.queue(Step.CRITIC, "{\"compliant\":false,\"violations\":[\"promises compensation\"],\"customerAsksOutOfPolicy\":true}");

        JsonNode r = triage("My delivery is late, I demand compensation for the delay.");

        assertThat(llm.calls(Step.REWRITE)).isEqualTo(1);
        assertThat(r.path("reply").path("text").asText()).startsWith("Rewritten");
        assertThat(r.path("needsHuman").asBoolean()).isTrue();
        assertThat(r.path("escalationReason").asText()).contains("POLICY_EXCEPTION");
    }

    @Test
    void omittedOrderIdIsFilledFromTheText() {
        llm.queue(Step.EXTRACT, "{\"summary\":\"Customer asks for a receipt.\"}");

        JsonNode r = triage("Where can I download the receipt for my order GK-104233?");

        assertThat(r.path("extraction").path("orderId").asText()).isEqualTo("GK-104233");
        assertThat(steps(r)).contains("pattern-fill", "order-check");
        assertThat(r.path("diagnostics").path("firstExtraction").has("orderId")).isFalse();
    }

    @Test
    void pipelineSearchesWhenTheAgentIgnoresTheTools() {
        llm.ignoreTools();

        JsonNode r = triage("Where is my parcel? It has been a week.");

        var replies = r.path("trace").path("steps").findParents("name").stream()
                .filter(s -> "reply".equals(s.path("name").asText())).toList();
        assertThat(replies.get(0).path("status").asText()).isEqualTo("INVALID");
        assertThat(replies.get(1).path("status").asText()).isEqualTo("OK");
        assertThat(replies.get(1).path("toolCalls").get(0).path("name").asText()).isEqualTo("searchKb (by pipeline)");
        assertThat(r.path("reply").path("citedArticles")).hasSize(1);
        assertThat(r.path("escalationReason").asText()).doesNotContain("VALIDATION_FAILED");
    }

    @Test
    void layaArmClassifiesWithoutTheLlm() {
        when(laya.available()).thenReturn(true);
        when(laya.classify(anyString())).thenReturn(new LayaClassifier.Result(
                new Classification(Category.SHIPPING, Priority.P3, "Laya: P3 with p=0.70", Sentiment.NEUTRAL, Lang.EN, 0.7, false),
                Map.of("category", 0.7), Map.of(), false, 12, "laya-multilingual"));

        var res = http.postForEntity("/api/triage", Map.of("text", "Where is my parcel?", "classifier", "laya"), JsonNode.class);
        JsonNode r = res.getBody();

        assertThat(llm.calls(Step.CLASSIFY)).isZero();
        assertThat(llm.calls(Step.EXTRACT)).isEqualTo(1);
        assertThat(r.path("classification").path("category").asText()).isEqualTo("SHIPPING");
        assertThat(r.path("options").path("classifier").asText()).isEqualTo("laya");
        JsonNode classify = r.path("trace").path("steps").get(1);
        assertThat(classify.path("name").asText()).isEqualTo("classify");
        assertThat(classify.path("note").asText()).contains("no LLM call");
    }

    @Test
    void publicApiIsLimitedAndValidated() {
        var bad = http.postForEntity("/api/public/tickets", Map.of("text", "", "orderNumber", "abc"), JsonNode.class);
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(http.getForEntity("/api/public/tickets/999999/status", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}

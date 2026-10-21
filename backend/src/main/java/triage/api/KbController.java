package triage.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import org.springframework.beans.factory.annotation.Value;

import triage.kb.KbDocument;
import triage.kb.KbHit;
import triage.kb.KnowledgeBase;

@RestController
@RequestMapping("/api")
public class KbController {

    private final KnowledgeBase kb;
    private final RestClient ollama;

    public KbController(KnowledgeBase kb, @Value("${langchain4j.ollama.chat-model.base-url}") String ollamaUrl) {
        this.kb = kb;
        this.ollama = RestClient.create(ollamaUrl);
    }

    @GetMapping("/kb")
    public List<KbDocument> articles() {
        return List.copyOf(kb.documents().values());
    }

    /** Lets the agent check retrieval quality for a query. */
    @GetMapping("/kb/search")
    public List<KbHit> search(@RequestParam String q) {
        return kb.search(q);
    }

    /** Chat models installed in Ollama, for the model selectors. */
    @GetMapping("/models")
    public List<String> models() {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tags = ollama.get().uri("/api/tags").retrieve().body(Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> models = (List<Map<String, Object>>) tags.get("models");
            return models.stream().map(m -> (String) m.get("name")).filter(n -> !n.contains("embed")).sorted().toList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}

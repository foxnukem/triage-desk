package triage.tools;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import triage.kb.KbHit;
import triage.kb.KnowledgeBase;

@Component
public class KnowledgeBaseTools {

    public static final String SEARCH_KB = "searchKb";
    private static final Pattern ID = Pattern.compile("\\[(kb-[a-z0-9-]+)]");
    private static final int EXCERPT_CHARS = 650;

    private final KnowledgeBase kb;

    public KnowledgeBaseTools(KnowledgeBase kb) {
        this.kb = kb;
    }

    @Tool(name = SEARCH_KB, value = "Search the Gadgetko knowledge base of policies and FAQ. Returns matching articles with their ids.")
    public String searchKb(@P("short search query, e.g. 'refund for damaged item'") String query) {
        List<KbHit> hits = kb.search(query);
        if (hits.isEmpty()) {
            return "No matching articles.";
        }
        StringBuilder sb = new StringBuilder();
        for (KbHit h : hits) {
            String text = h.text().length() > EXCERPT_CHARS ? h.text().substring(0, EXCERPT_CHARS) + "…" : h.text();
            sb.append('[').append(h.articleId()).append("] ").append(h.title()).append('\n').append(text).append("\n\n");
        }
        return sb.toString().strip();
    }

    /** Article ids present in a searchKb result string. */
    public static List<String> idsIn(String toolResult) {
        if (toolResult == null) {
            return List.of();
        }
        Matcher m = ID.matcher(toolResult);
        return m.results().map(r -> r.group(1)).toList();
    }
}

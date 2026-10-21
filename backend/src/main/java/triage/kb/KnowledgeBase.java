package triage.kb;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import triage.TriageProperties;
import triage.persistence.KbArticleEntity;
import triage.persistence.KbArticleRepository;

/**
 * Ingests {@code data/kb/*.md} (packaged as classpath:kb/) into kb_article and the pgvector
 * store on startup when they are empty, and answers searches. One article = one segment: they are short.
 */
@Service
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final KbArticleRepository articles;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> store;
    private final JdbcTemplate jdbc;
    private final TriageProperties.Kb cfg;
    private final AtomicBoolean indexed = new AtomicBoolean(false);
    private volatile Map<String, KbDocument> documents;

    public KnowledgeBase(KbArticleRepository articles, EmbeddingModel embeddingModel,
                         EmbeddingStore<TextSegment> store, JdbcTemplate jdbc, TriageProperties props) {
        this.articles = articles;
        this.embeddingModel = embeddingModel;
        this.store = store;
        this.jdbc = jdbc;
        this.cfg = props.kb();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        try {
            ensureIndexed();
        } catch (RuntimeException e) {
            log.warn("KB indexing skipped ({}); it will be retried on the first search", e.getMessage());
        }
    }

    public synchronized void ensureIndexed() {
        if (indexed.get()) {
            return;
        }
        List<KbDocument> docs = loadFiles();
        if (articles.count() == 0) {
            articles.saveAll(docs.stream().map(d -> new KbArticleEntity(d.id(), d.title(), d.body())).toList());
            log.info("KB: stored {} articles", docs.size());
        }
        Integer rows = jdbc.queryForObject("SELECT count(*) FROM kb_embedding", Integer.class);
        if (rows == null || rows == 0) {
            List<TextSegment> segments = articles.findAllByOrderByIdAsc().stream()
                    .map(a -> TextSegment.from(a.getTitle() + "\n" + a.getBodyMd(), Metadata.from("article_id", a.getId())))
                    .toList();
            List<TextSegment> prefixed = segments.stream()
                    .map(s -> TextSegment.from(cfg.documentPrefix() + s.text(), s.metadata())).toList();
            List<Embedding> embeddings = embeddingModel.embedAll(prefixed).content();
            store.addAll(embeddings, segments);
            log.info("KB: embedded {} articles", segments.size());
        }
        indexed.set(true);
    }

    public List<KbHit> search(String query) {
        ensureIndexed();
        Embedding q = embeddingModel.embed(cfg.queryPrefix() + query).content();
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(q).maxResults(cfg.topK()).minScore(cfg.minScore()).build()).matches();
        List<KbHit> hits = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> m : matches) {
            String id = m.embedded().metadata().getString("article_id");
            KbDocument doc = documents().get(id);
            if (doc != null) {
                hits.add(new KbHit(id, doc.title(), m.score(), doc.plainBody()));
            }
        }
        hits.sort(Comparator.comparingDouble(KbHit::score).reversed());
        return hits;
    }

    public Map<String, KbDocument> documents() {
        if (documents == null) {
            List<KbArticleEntity> rows = articles.findAllByOrderByIdAsc();
            List<KbDocument> docs = rows.isEmpty() ? loadFiles()
                    : rows.stream().map(a -> new KbDocument(a.getId(), a.getTitle(), a.getBodyMd())).toList();
            documents = docs.stream().collect(Collectors.toMap(KbDocument::id, Function.identity(),
                    (a, b) -> a, java.util.LinkedHashMap::new));
        }
        return documents;
    }

    public Set<String> ids() {
        return documents().keySet();
    }

    static List<KbDocument> loadFiles() {
        try {
            Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:kb/*.md");
            List<KbDocument> docs = new ArrayList<>();
            for (Resource r : files) {
                String body = r.getContentAsString(StandardCharsets.UTF_8);
                String id = r.getFilename().replaceFirst("\\.md$", "");
                String title = body.lines().filter(l -> l.startsWith("# ")).findFirst().orElse(id).substring(2).strip();
                docs.add(new KbDocument(id, title, body.strip()));
            }
            docs.sort(Comparator.comparing(KbDocument::id));
            return docs;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

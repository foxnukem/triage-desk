package triage.kb;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import triage.TriageProperties;

@Configuration
public class KbConfig {

    /** Table kb_embedding is created by the store itself (vector extension comes from Flyway V1). */
    @Bean
    @DependsOn("flywayInitializer")
    EmbeddingStore<TextSegment> kbEmbeddingStore(DataSource dataSource, TriageProperties props) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("kb_embedding")
                .dimension(props.kb().dimension())
                .createTable(true)
                .skipCreateVectorExtension(true)
                .build();
    }
}

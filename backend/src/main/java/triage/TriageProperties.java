package triage;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("triage")
public record TriageProperties(
        @DefaultValue("2") int maxRetries,
        @DefaultValue("0.6") double confidenceThreshold,
        @DefaultValue("http://localhost:4200") List<String> publicCorsOrigins,
        @DefaultValue Pipeline pipeline,
        @DefaultValue Kb kb,
        @DefaultValue Eval eval,
        @DefaultValue Laya laya
) {

    /** Component switches; eval ablations flip them per run. */
    public record Pipeline(
            @DefaultValue("true") boolean ruleRouter,
            @DefaultValue("true") boolean validation,
            @DefaultValue("true") boolean critic
    ) {}

    public record Kb(
            @DefaultValue("768") int dimension,
            @DefaultValue("") String queryPrefix,
            @DefaultValue("") String documentPrefix,
            @DefaultValue("3") int topK,
            @DefaultValue("0.55") double minScore
    ) {}

    public record Eval(@DefaultValue("eval/reports") String reportsDir) {}

    /**
     * Laya, the encoder classifier arm. Off unless both directories exist.
     * {@code abShare} is the fraction of live tickets assigned to it (stable per ticket id).
     */
    public record Laya(
            @DefaultValue("laya/checkpoint") String checkpoint,
            @DefaultValue("laya/onnx") String graph,
            @DefaultValue("0.0") double abShare,
            @DefaultValue("0") int threads
    ) {}
}

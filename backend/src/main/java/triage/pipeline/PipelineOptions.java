package triage.pipeline;

import triage.TriageProperties;

/**
 * One pipeline configuration. The flags exist for ablations: the same code runs with components off.
 * {@code reply=false} stops after classification + extraction (cheap eval runs).
 */
public record PipelineOptions(
        String model,
        boolean ruleRouter,
        boolean validation,
        boolean critic,
        boolean megaPrompt,
        boolean reply,
        String classifier
) {

    public static final String LLM = "llm";
    public static final String LAYA = "laya";

    public static PipelineOptions defaults(TriageProperties props, String model) {
        var p = props.pipeline();
        return new PipelineOptions(model, p.ruleRouter(), p.validation(), p.critic(), false, true, LLM);
    }

    public PipelineOptions withModel(String m) {
        return new PipelineOptions(m, ruleRouter, validation, critic, megaPrompt, reply, classifier);
    }

    public PipelineOptions withClassifier(String c) {
        return new PipelineOptions(model, ruleRouter, validation, critic, megaPrompt, reply, c == null ? LLM : c);
    }

    public boolean laya() {
        return LAYA.equals(classifier);
    }
}

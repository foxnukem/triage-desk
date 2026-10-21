package triage.eval;

import java.util.List;

import triage.pipeline.PipelineOptions;

/** What to run: pipeline switches plus which cases. */
public record EvalConfig(
        String label,
        String model,
        boolean ruleRouter,
        boolean validation,
        boolean critic,
        boolean megaPrompt,
        boolean reply,
        Integer limit,
        String slice,
        List<String> caseIds,
        String classifier
) {

    public PipelineOptions options() {
        return new PipelineOptions(model, ruleRouter, validation, critic, megaPrompt, reply,
                classifier == null ? PipelineOptions.LLM : classifier);
    }

    public static EvalConfig preset(String name, String model, String compareModel) {
        return switch (name) {
            case "full" -> new EvalConfig("full", model, true, true, true, false, true, null, null, null, PipelineOptions.LLM);
            case "no-router" -> new EvalConfig("no-router", model, false, true, true, false, true, null, null, null, PipelineOptions.LLM);
            case "no-validation" -> new EvalConfig("no-validation", model, true, false, true, false, true, null, null, null, PipelineOptions.LLM);
            case "no-critic" -> new EvalConfig("no-critic", model, true, true, false, false, true, null, null, null, PipelineOptions.LLM);
            case "compare-model" -> new EvalConfig("compare-model", compareModel, true, true, true, false, true, null, null, null, PipelineOptions.LLM);
            case "mega-prompt" -> new EvalConfig("mega-prompt", model, false, false, false, true, true, null, null, null, PipelineOptions.LLM);
            // Not part of the spec's six: the same pipeline with Laya as the classifier (A/B arm).
            case "laya" -> new EvalConfig("laya", model, true, true, true, false, true, null, null, null, PipelineOptions.LAYA);
            default -> throw new IllegalArgumentException("unknown preset " + name
                    + " (full, no-router, no-validation, no-critic, compare-model, mega-prompt, laya)");
        };
    }

    public static final List<String> ABLATION = List.of("full", "no-router", "no-validation", "no-critic", "compare-model", "mega-prompt");

    public EvalConfig withCases(Integer limit, String slice, List<String> caseIds) {
        return new EvalConfig(label, model, ruleRouter, validation, critic, megaPrompt, reply, limit, slice, caseIds, classifier);
    }

    public EvalConfig withLabel(String l) {
        return new EvalConfig(l, model, ruleRouter, validation, critic, megaPrompt, reply, limit, slice, caseIds, classifier);
    }

    public EvalConfig withReply(boolean r) {
        return new EvalConfig(label, model, ruleRouter, validation, critic, megaPrompt, r, limit, slice, caseIds, classifier);
    }
}

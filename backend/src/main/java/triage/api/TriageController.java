package triage.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import triage.eval.EvalDataset;
import triage.pipeline.PipelineOptions;
import triage.pipeline.TriagePipeline;
import triage.pipeline.TriageResult;

/** Playground: synchronous triage, nothing persisted. */
@RestController
@RequestMapping("/api/triage")
public class TriageController {

    public record TriageRequest(@NotBlank @Size(max = 2000) String text, String model, Boolean ruleRouter,
                                Boolean validation, Boolean critic, Boolean megaPrompt, String classifier) {}

    public record Sample(String id, String slice, String lang, List<String> tags, Integer demo, String text, JsonNode expected) {}

    private final TriagePipeline pipeline;
    private final EvalDataset dataset;

    public TriageController(TriagePipeline pipeline, EvalDataset dataset) {
        this.pipeline = pipeline;
        this.dataset = dataset;
    }

    @PostMapping
    public TriageResult triage(@Valid @RequestBody TriageRequest req) {
        PipelineOptions d = pipeline.defaultOptions();
        var options = new PipelineOptions(
                req.model() == null || req.model().isBlank() ? d.model() : req.model(),
                or(req.ruleRouter(), d.ruleRouter()), or(req.validation(), d.validation()),
                or(req.critic(), d.critic()), or(req.megaPrompt(), false), true,
                req.classifier() == null || req.classifier().isBlank() ? PipelineOptions.LLM : req.classifier());
        return pipeline.run(req.text(), options);
    }

    @GetMapping("/samples")
    public List<Sample> samples() {
        return dataset.all().stream()
                .map(c -> new Sample(c.id(), c.slice(), c.lang(), c.tags(), c.demo(), c.text(), c.expected()))
                .toList();
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of("defaults", pipeline.defaultOptions(),
                "classifiers", pipeline.layaAvailable() ? List.of(PipelineOptions.LLM, PipelineOptions.LAYA) : List.of(PipelineOptions.LLM));
    }

    private static boolean or(Boolean v, boolean d) {
        return v == null ? d : v;
    }
}

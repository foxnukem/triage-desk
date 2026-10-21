package triage.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import triage.eval.EvalConfig;
import triage.eval.EvalService;
import triage.persistence.EvalCaseResultRepository;
import triage.persistence.EvalRunEntity;
import triage.persistence.EvalRunRepository;
import triage.tickets.TicketNotFoundException;

@RestController
@RequestMapping("/api/eval")
public class EvalController {

    public record RunSummary(long id, String label, JsonNode config, String status, int totalCases, int doneCases,
                             String error, String startedAt, String finishedAt, JsonNode metrics) {}

    public record CaseRow(String caseId, String slice, boolean correct, long latencyMs, JsonNode detail) {}

    public record RunDetail(RunSummary run, List<CaseRow> cases) {}

    private final EvalService service;
    private final EvalRunRepository runs;
    private final EvalCaseResultRepository cases;
    private final ObjectMapper mapper;

    public EvalController(EvalService service, EvalRunRepository runs, EvalCaseResultRepository cases, ObjectMapper mapper) {
        this.service = service;
        this.runs = runs;
        this.cases = cases;
        this.mapper = mapper;
    }

    @PostMapping("/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> run(@RequestBody EvalConfig cfg) {
        return Map.of("runId", service.start(cfg));
    }

    @GetMapping("/presets")
    public List<String> presets() {
        return java.util.stream.Stream.concat(EvalConfig.ABLATION.stream(), java.util.stream.Stream.of("laya")).toList();
    }

    @GetMapping("/runs")
    public List<RunSummary> list() {
        return runs.findAllByOrderByIdDesc().stream().map(this::summary).toList();
    }

    @GetMapping("/runs/{id}")
    public RunDetail get(@PathVariable long id) {
        EvalRunEntity run = runs.findById(id).orElseThrow(() -> new TicketNotFoundException(id));
        return new RunDetail(summary(run), cases.findByRunIdOrderByCaseIdAsc(id).stream()
                .map(c -> new CaseRow(c.getCaseId(), c.getSlice(), c.isCorrect(), c.getLatencyMs(), tree(c.getDetail())))
                .toList());
    }

    private RunSummary summary(EvalRunEntity r) {
        return new RunSummary(r.getId(), r.getLabel(), tree(r.getConfig()), r.getStatus(), r.getTotalCases(), r.getDoneCases(),
                r.getError(), r.getStartedAt().toString(), r.getFinishedAt() == null ? null : r.getFinishedAt().toString(),
                tree(r.getMetrics()));
    }

    private JsonNode tree(String json) {
        try {
            return json == null ? null : mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}

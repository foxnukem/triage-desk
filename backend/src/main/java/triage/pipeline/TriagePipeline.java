package triage.pipeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import triage.TriageProperties;
import triage.ai.AiComponents;
import triage.ai.AiFactory;
import triage.ai.Category;
import triage.ai.Classification;
import triage.ai.Extraction;
import triage.ai.Lang;
import triage.ai.MegaTriage;
import triage.ai.PolicyReview;
import triage.ai.Priority;
import triage.ai.ReplyDraft;
import triage.ai.Sentiment;
import triage.kb.KnowledgeBase;
import triage.laya.LayaClassifier;
import triage.persistence.OrderRepository;
import triage.pipeline.RuleRouter.RuleHit;
import triage.pipeline.StepTrace.Status;
import triage.tools.KnowledgeBaseTools;
import triage.tools.OrderTools;
import triage.validation.CitationValidator;
import triage.validation.EntityGroundingValidator;
import triage.validation.PatternEntities;
import triage.validation.PolicyLint;

/**
 * The fixed pipeline: rules → classify → extract → order check → reply agent (tools) → critic → escalation.
 * The LLM only does the narrow steps; this class routes, validates and decides when a human is needed.
 */
@Service
public class TriagePipeline {

    private static final int TOOL_RESULT_PREVIEW = 600;

    private final AiFactory ai;
    private final StepRunner runner;
    private final RuleRouter router;
    private final ReplyGuidance guidance;
    private final EntityGroundingValidator grounding;
    private final CitationValidator citations;
    private final PolicyLint lint;
    private final OrderRepository orders;
    private final KnowledgeBase kb;
    private final TriageProperties props;
    private final LayaClassifier laya;
    private final KnowledgeBaseTools kbTools;

    public TriagePipeline(AiFactory ai, StepRunner runner, RuleRouter router, ReplyGuidance guidance,
                          EntityGroundingValidator grounding, CitationValidator citations, PolicyLint lint,
                          OrderRepository orders, KnowledgeBase kb, TriageProperties props, LayaClassifier laya,
                          KnowledgeBaseTools kbTools) {
        this.ai = ai;
        this.runner = runner;
        this.router = router;
        this.guidance = guidance;
        this.grounding = grounding;
        this.citations = citations;
        this.lint = lint;
        this.orders = orders;
        this.kb = kb;
        this.props = props;
        this.laya = laya;
        this.kbTools = kbTools;
    }

    public PipelineOptions defaultOptions() {
        return PipelineOptions.defaults(props, ai.defaultModel());
    }

    public boolean layaAvailable() {
        return laya.available();
    }

    /**
     * Live A/B: a stable share of tickets goes to the Laya arm, by a hash of the ticket id, so a
     * re-triage without an explicit choice stays in the same arm.
     */
    public String assignClassifier(long ticketId) {
        double share = props.laya().abShare();
        if (share <= 0 || !laya.available()) {
            return PipelineOptions.LLM;
        }
        return inLayaArm(ticketId, share) ? PipelineOptions.LAYA : PipelineOptions.LLM;
    }

    /** Fibonacci hash of the id to [0, 1): consecutive ids spread evenly over the arms. */
    static boolean inLayaArm(long ticketId, double share) {
        long h = (ticketId * 0x9E3779B97F4A7C15L) >>> 11;
        return h / (double) (1L << 53) < share;
    }

    public TriageResult run(String text) {
        return run(text, defaultOptions());
    }

    public TriageResult run(String text, PipelineOptions opt) {
        AiComponents c = ai.forModel(opt.model());
        PipelineOptions options = opt.withModel(c.model());
        Trace trace = new Trace();
        return options.megaPrompt() ? runMega(text, c, options, trace) : runSteps(text, c, options, trace);
    }

    private TriageResult runSteps(String text, AiComponents c, PipelineOptions opt, Trace trace) {
        int retries = props.maxRetries();
        boolean validate = opt.validation();
        Map<String, Boolean> firstValid = new LinkedHashMap<>();
        Map<String, Boolean> succeeded = new LinkedHashMap<>();

        // 1. Deterministic rules
        Optional<RuleHit> hit = Optional.empty();
        if (opt.ruleRouter()) {
            long t0 = System.nanoTime();
            hit = router.match(text);
            trace.add(StepTrace.info("rule-router", hit.isPresent() ? Status.INFO : Status.OK, ms(t0), hit.orElse(null),
                    hit.map(h -> "rule " + h.ruleId() + " matched \"" + h.matched() + "\"").orElse("no rule matched")));
        } else {
            trace.add(StepTrace.info("rule-router", Status.SKIPPED, 0, null, "disabled"));
        }

        // 2. Classification: the LLM (JSON Schema + retries), or the Laya encoder arm
        Classification classification;
        if (opt.laya()) {
            classification = layaClassify(text, trace);
            firstValid.put("classify", true);
            succeeded.put("classify", true);
        } else {
            var cls = runner.run("classify", trace, fb -> c.classifier().classify(text, fb).content(),
                    v -> List.of(), validate, retries);
            firstValid.put("classify", cls.firstAttemptValid());
            succeeded.put("classify", cls.succeeded());
            classification = cls.value() != null ? cls.value() : fallbackClassification();
        }

        RuleOverride override = null;
        if (hit.isPresent()) {
            RuleHit h = hit.get();
            Category modelCat = classification.category();
            Priority modelPrio = classification.priority();
            boolean categoryChanged = h.category() != null && h.category() != modelCat;
            boolean raised = modelPrio.isLowerThan(h.minPriority());
            if (categoryChanged || raised) {
                override = new RuleOverride(h.ruleId(), h.reason(), h.matched(), modelCat, modelPrio, categoryChanged, raised);
                if (categoryChanged) {
                    classification = classification.withCategory(h.category());
                }
                if (raised) {
                    classification = classification.withPriority(h.minPriority());
                }
                trace.add(StepTrace.info("priority-rules", Status.OVERRIDE, 0, override,
                        (categoryChanged ? "category " + modelCat + " → " + h.category() + "; " : "")
                                + (raised ? "priority " + modelPrio + " → " + h.minPriority() : "priority kept")));
            }
        }

        // 3. Entity extraction, grounded in the ticket text
        var ext = runner.run("extract", trace, fb -> c.extractor().extract(text, fb).content(),
                e -> grounding.check(e, text), validate, retries);
        firstValid.put("extract", ext.firstAttemptValid());
        succeeded.put("extract", ext.succeeded());
        Extraction extraction = ext.value() != null ? ext.value() : repairExtraction(ext.lastValue(), text, validate);
        if (validate) {
            var fill = PatternEntities.fill(extraction, text);
            if (!fill.filled().isEmpty()) {
                extraction = fill.extraction();
                trace.add(StepTrace.info("pattern-fill", Status.OVERRIDE, 0, fill.filled(),
                        "model omitted " + String.join(", ", fill.filled().keySet()) + "; taken from the ticket text by pattern"));
            }
        }

        String orderCheck = extraction.orderId() == null ? "none" : "unchecked";
        String unverifiedOrder = null;
        if (validate && extraction.orderId() != null) {
            long t0 = System.nanoTime();
            String id = OrderTools.normalize(extraction.orderId());
            if (orders.existsById(id)) {
                orderCheck = "found";
                trace.add(StepTrace.info("order-check", Status.OK, ms(t0), Map.of("orderId", id, "exists", true), "order exists"));
            } else {
                orderCheck = "not-found";
                unverifiedOrder = extraction.orderId();
                extraction = extraction.withOrderId(null);
                trace.add(StepTrace.info("order-check", Status.OVERRIDE, ms(t0), Map.of("orderId", id, "exists", false),
                        "order not in the database, orderId set to null"));
            }
        }

        // 4. Reply agent with tools, prompt specialised by category
        ReplyDraft draft = null;
        Set<String> retrieved = new LinkedHashSet<>();
        PolicyReview review = null;
        ReplyDraft beforeCritic = null;
        List<String> lintBefore = List.of();
        String lang = classification.language().displayName();
        if (opt.reply()) {
            String topic = guidance.forCategory(classification.category());
            String context = context(classification, extraction, orderCheck, unverifiedOrder);
            String kbQuery = extraction.summary();
            var rep = runner.run("reply", trace,
                    fb -> draftReply(c, text, topic, lang, context, fb, kbQuery),
                    r -> citations.check(r.draft(), r.searchCalled(), r.retrieved(), kb.ids()),
                    AgentReply::draft, AgentReply::toolCalls, validate, retries);
            firstValid.put("reply", rep.firstAttemptValid());
            succeeded.put("reply", rep.succeeded());
            AgentReply agentReply = rep.value() != null ? rep.value() : rep.lastValue();
            if (agentReply != null) {
                retrieved.addAll(agentReply.retrieved());
                draft = agentReply.draft();
                if (rep.value() == null && validate) {
                    // Keep the text for the human, drop citations that failed the check.
                    Set<String> ok = agentReply.retrieved();
                    draft = new ReplyDraft(draft.text(), draft.citedArticles().stream().filter(ok::contains).toList(), draft.promisesAction());
                }
            }

            // 5. Critic: one review, at most one rewrite
            beforeCritic = draft;
            lintBefore = draft == null ? List.of() : lint.check(draft.text());
            if (opt.critic() && draft != null) {
                String draftText = draft.text();
                var rev = runner.run("critic-review", trace, fb -> c.critic().review(text, draftText, fb).content(),
                        v -> List.of(), validate, retries);
                firstValid.put("critic-review", rev.firstAttemptValid());
                succeeded.put("critic-review", rev.succeeded());
                review = rev.value();
                List<String> violations = new ArrayList<>();
                if (review != null) {
                    violations.addAll(review.violations());
                    if (!review.compliant() && violations.isEmpty()) {
                        violations.add("promise outside policy");
                    }
                }
                lintBefore.forEach(l -> violations.add("promises " + l));
                if (!violations.isEmpty()) {
                    Set<String> allowed = new LinkedHashSet<>(retrieved);
                    String violationText = String.join("; ", violations);
                    String allowedText = allowed.isEmpty() ? "(none)" : String.join(", ", allowed);
                    var rw = runner.run("critic-rewrite", trace,
                            fb -> c.critic().rewrite(text, draftText, violationText, allowedText, lang, fb).content(),
                            d -> citations.check(d, true, allowed, kb.ids()), validate, retries);
                    firstValid.put("critic-rewrite", rw.firstAttemptValid());
                    succeeded.put("critic-rewrite", rw.succeeded());
                    if (rw.value() != null) {
                        draft = rw.value();
                    }
                }
            } else {
                trace.add(StepTrace.info("critic", Status.SKIPPED, 0, null, opt.critic() ? "no draft" : "disabled"));
            }
        }

        return finish(text, opt, trace, classification, extraction, draft, override, review,
                firstValid, succeeded, ext.firstValue(), orderCheck, beforeCritic, lintBefore, retrieved);
    }

    private TriageResult runMega(String text, AiComponents c, PipelineOptions opt, Trace trace) {
        String articles = kb.documents().values().stream()
                .map(d -> d.id() + ": " + d.title()).collect(Collectors.joining("\n"));
        var out = runner.run("mega-prompt", trace, fb -> c.mega().triage(text, articles, fb).content(),
                v -> List.of(), false, 0);
        MegaTriage m = out.value();
        Classification classification = fallbackClassification();
        Extraction extraction = repairExtraction(null, text, false);
        ReplyDraft draft = null;
        if (m != null) {
            classification = new Classification(
                    m.category() == null ? Category.OTHER : m.category(),
                    m.priority() == null ? Priority.P3 : m.priority(),
                    m.priorityReason() == null ? "" : m.priorityReason(),
                    m.sentiment() == null ? Sentiment.NEUTRAL : m.sentiment(),
                    m.language() == null ? Lang.OTHER : m.language(),
                    m.confidence(), m.multiIntent());
            extraction = new Extraction(m.orderId(), m.email(), m.product(), m.amount(), m.summary());
            if (m.replyText() != null) {
                draft = new ReplyDraft(m.replyText(), m.citedArticles() == null ? List.of() : m.citedArticles(), false);
            }
        }
        Map<String, Boolean> ok = Map.of("mega-prompt", out.succeeded());
        TriageResult base = finish(text, opt, trace, classification, extraction, draft, null, null,
                Map.of("mega-prompt", out.firstAttemptValid()), ok, extraction, "unchecked", draft,
                draft == null ? List.of() : lint.check(draft.text()), Set.of());
        // The baseline decides escalation itself.
        boolean needsHuman = m == null || m.needsHuman();
        return new TriageResult(base.classification(), base.extraction(), base.reply(), base.queue(), needsHuman,
                m == null ? EscalationReason.VALIDATION_FAILED.name() : needsHuman ? "MODEL_DECISION" : null,
                null, base.model(), base.options(), base.diagnostics(), base.trace());
    }

    private TriageResult finish(String text, PipelineOptions opt, Trace trace, Classification classification,
                                Extraction extraction, ReplyDraft draft, RuleOverride override, PolicyReview review,
                                Map<String, Boolean> firstValid, Map<String, Boolean> succeeded, Extraction firstExtraction,
                                String orderCheck, ReplyDraft beforeCritic, List<String> lintBefore, Set<String> retrieved) {
        List<String> lintFinal = draft == null ? List.of() : lint.check(draft.text());
        List<EscalationReason> reasons = new ArrayList<>();
        if (succeeded.containsValue(false)) {
            reasons.add(EscalationReason.VALIDATION_FAILED);
        }
        if (classification.confidence() < props.confidenceThreshold()) {
            reasons.add(EscalationReason.LOW_CONFIDENCE);
        }
        if (classification.priority() == Priority.P1) {
            reasons.add(EscalationReason.P1_PRIORITY);
        }
        if (review != null && review.customerAsksOutOfPolicy()) {
            reasons.add(EscalationReason.POLICY_EXCEPTION);
        }
        if (!lintFinal.isEmpty()) {
            reasons.add(EscalationReason.POLICY_RISK);
        }
        boolean needsHuman = !reasons.isEmpty();
        String reason = needsHuman ? reasons.stream().map(Enum::name).collect(Collectors.joining(", ")) : null;
        String queue = router.queueFor(classification.category(), classification.priority());
        trace.add(StepTrace.info("escalation", needsHuman ? Status.INFO : Status.OK, 0,
                Map.of("needsHuman", needsHuman, "queue", queue),
                needsHuman ? "needs a human: " + reason : "draft ready for agent review"));

        Diagnostics diagnostics = new Diagnostics(
                firstValid, succeeded, firstExtraction,
                EntityGroundingValidator.countEntities(firstExtraction),
                grounding.countUngrounded(firstExtraction, text),
                grounding.countUngrounded(extraction, text),
                orderCheck, beforeCritic, lintBefore, lintFinal, List.copyOf(retrieved));
        return new TriageResult(classification, extraction, draft, queue, needsHuman, reason, override,
                opt.model(), opt, diagnostics, trace.finish());
    }

    /**
     * One reply attempt. When the previous attempt was rejected for not calling searchKb (qwen3:8b
     * sometimes answers without touching the tools), the pipeline runs the search itself, on the
     * extractor's English summary, and hands the articles to the agent. They count as retrieved,
     * and the trace shows the search as a pipeline-made tool call.
     */
    private AgentReply draftReply(AiComponents c, String text, String topic, String lang, String context,
                                  String feedback, String kbQuery) {
        if (!feedback.contains(CitationValidator.NO_SEARCH)) {
            return toAgentReply(c.replyAgent().draft(text, topic, lang, context, feedback));
        }
        long t0 = System.nanoTime();
        String found = kbTools.searchKb(kbQuery);
        long searchMs = ms(t0);
        String withKb = context + "\nsearchKb was run for you (\"" + kbQuery + "\"); use these articles and cite their ids:\n" + found;
        AgentReply r = toAgentReply(c.replyAgent().draft(text, topic, lang, withKb, feedback));
        List<StepTrace.ToolCall> calls = new ArrayList<>();
        calls.add(new StepTrace.ToolCall(KnowledgeBaseTools.SEARCH_KB + " (by pipeline)", "{\"query\":\"" + kbQuery + "\"}",
                found.length() > TOOL_RESULT_PREVIEW ? found.substring(0, TOOL_RESULT_PREVIEW) + "…" : found, searchMs));
        calls.addAll(r.toolCalls());
        Set<String> retrieved = new LinkedHashSet<>(KnowledgeBaseTools.idsIn(found));
        retrieved.addAll(r.retrieved());
        return new AgentReply(r.draft(), calls, retrieved, true);
    }

    private static AgentReply toAgentReply(Result<ReplyDraft> result) {
        List<StepTrace.ToolCall> calls = new ArrayList<>();
        Set<String> retrieved = new LinkedHashSet<>();
        boolean searchCalled = false;
        if (result.toolExecutions() != null) {
            for (ToolExecution te : result.toolExecutions()) {
                String name = te.request().name();
                String out = te.result();
                if (KnowledgeBaseTools.SEARCH_KB.equals(name)) {
                    searchCalled = true;
                    retrieved.addAll(KnowledgeBaseTools.idsIn(out));
                }
                long dur = te.duration() == null ? 0 : te.duration().toMillis();
                String preview = out != null && out.length() > TOOL_RESULT_PREVIEW ? out.substring(0, TOOL_RESULT_PREVIEW) + "…" : out;
                calls.add(new StepTrace.ToolCall(name, te.request().arguments(), preview, dur));
            }
        }
        return new AgentReply(result.content(), calls, retrieved, searchCalled);
    }

    private static String context(Classification cl, Extraction ex, String orderCheck, String unverifiedOrder) {
        String order = switch (orderCheck) {
            case "found" -> "order " + ex.orderId() + " exists (use getOrder for details)";
            case "not-found" -> "the customer wrote order number " + unverifiedOrder
                    + ", which does not exist in our system: ask them to double-check it";
            case "unchecked" -> "order number " + ex.orderId();
            default -> "no order number in the ticket";
        };
        return "category " + cl.category() + ", priority " + cl.priority() + "; " + order + ".";
    }

    /** One forward pass, no retries: the answer is a choice among the options, so it cannot be malformed. */
    private Classification layaClassify(String text, Trace trace) {
        long t0 = System.nanoTime();
        try {
            LayaClassifier.Result r = laya.classify(text);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("classification", r.classification());
            out.put("answerConfidence", r.answerConfidence());
            out.put("probabilities", r.probabilities());
            out.put("truncated", r.truncated());
            trace.add(new StepTrace("classify", 1, ms(t0), Status.OK, List.of(), out, List.of(),
                    r.model() + ": one encoder pass, no LLM call"));
            return r.classification();
        } catch (RuntimeException e) {
            trace.add(new StepTrace("classify", 1, ms(t0), Status.ERROR, List.of(String.valueOf(e.getMessage())), null,
                    List.of(), "laya"));
            throw new StepRunner.InfrastructureException("classify (laya): " + e.getMessage(), e);
        }
    }

    private static Classification fallbackClassification() {
        return new Classification(Category.OTHER, Priority.P3, "classification failed", Sentiment.NEUTRAL, Lang.OTHER, 0.0, false);
    }

    /** After exhausted retries keep only grounded entities; with validation off keep the raw answer. */
    private Extraction repairExtraction(Extraction last, String text, boolean validate) {
        if (last == null) {
            return new Extraction(null, null, null, null, "(extraction failed)");
        }
        if (!validate) {
            return last;
        }
        Extraction e = last;
        if (e.orderId() != null && !e.orderId().matches("GK-\\d{6}")) {
            e = e.withOrderId(null);
        }
        List<String> errors = grounding.check(e, text);
        String orderId = errors.stream().anyMatch(s -> s.startsWith("orderId")) ? null : e.orderId();
        String email = errors.stream().anyMatch(s -> s.startsWith("email")) ? null : e.email();
        var amount = errors.stream().anyMatch(s -> s.startsWith("amount")) ? null : e.amount();
        String summary = e.summary() == null || e.summary().isBlank() ? "(no summary)"
                : e.summary().length() > 160 ? e.summary().substring(0, 157) + "..." : e.summary();
        return new Extraction(orderId, email, e.product(), amount, summary);
    }

    private static long ms(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }
}

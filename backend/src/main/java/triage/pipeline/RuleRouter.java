package triage.pipeline;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import triage.ai.Category;
import triage.ai.Priority;

/**
 * Deterministic routing: keyword rules that set a priority floor (and sometimes the category),
 * and the category to queue mapping. The rules are the P1/P2 safety net under the LLM classifier.
 */
@Component
public class RuleRouter {

    public record Rule(String id, Pattern pattern, Category category, Priority minPriority, String reason) {}

    public record RuleHit(String ruleId, Category category, Priority minPriority, String reason, String matched) {}

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final List<Rule> RULES = List.of(
            new Rule("safety-hazard", Pattern.compile(
                    "\\bsmok(e|ing|ed)\\b|caught fire|on fire|explod|swollen|swelling|burning smell|\\bspark(s|ing|ed)?\\b"
                            + "|\\bдим(ить|ів|ок|у)?\\b|задимі|\\bгорить|загорі|вибух|здув|здул|роздув|роздул|іскр|гарим|плавит", FLAGS),
                    null, Priority.P1, "possible safety hazard"),
            new Rule("account-compromise", Pattern.compile(
                    "hacked|someone (else )?(logged|accessed|is using)|unauthori[sz]ed (login|access|order)|orders? i didn'?t (make|place)"
                            + "|зламал|хтось (зайшов|увійшов|змінив)|чужі замовлення|замовлення,? як(і|их) я не (робив|робила|оформлював)", FLAGS),
                    Category.ACCOUNT, Priority.P1, "possible account compromise"),
            new Rule("double-charge", Pattern.compile(
                    "charged (me )?twice|double[- ]charg|duplicate (charge|payment)|two charges|charged two times"
                            + "|списали (гроші |кошти )?(двічі|два рази)|двічі (списали|зняли)|зняли (гроші |кошти )?(двічі|два рази)|подвійн\\S* (списан|оплат)", FLAGS),
                    Category.BILLING, Priority.P2, "money charged twice"),
            new Rule("payment-outage", Pattern.compile(
                    "payment (is )?(not working|doesn'?t work|fails|failed)|can'?t pay|cannot pay|checkout (error|is broken)"
                            + "|оплата не (проходить|працює)|не (можу|вдається) оплатити|не працює оплата|не проходить оплата", FLAGS),
                    Category.BILLING, Priority.P2, "payment not working")
    );

    /** First matching rule, strongest priority first (rules are listed in that order). */
    public Optional<RuleHit> match(String text) {
        for (Rule r : RULES) {
            var m = r.pattern().matcher(text);
            if (m.find()) {
                return Optional.of(new RuleHit(r.id(), r.category(), r.minPriority(), r.reason(), m.group()));
            }
        }
        return Optional.empty();
    }

    public String queueFor(Category category, Priority priority) {
        if (priority == Priority.P1) {
            return "urgent";
        }
        if (category == null) {
            return "general";
        }
        return switch (category) {
            case BILLING -> "billing";
            case SHIPPING -> "logistics";
            case RETURN_REFUND -> "returns";
            case TECHNICAL -> "tech-support";
            case ACCOUNT -> "accounts";
            case OTHER -> "general";
        };
    }
}

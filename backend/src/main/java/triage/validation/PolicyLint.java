package triage.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Deterministic, keyword-based check for promises outside policy. It is the eval metric for
 * "drafts that violate policy" (no LLM judge) and a second opinion after the critic's rewrite.
 */
@Component
public class PolicyLint {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private static final Map<String, Pattern> RULES = Map.of(
            "compensation", Pattern.compile("compensat|компенсу|компенсац", FLAGS),
            "voucher or gift", Pattern.compile("voucher|gift card|coupon|ваучер|подарунков\\S* сертифікат|купон", FLAGS),
            "discount as apology", Pattern.compile(
                    "(give|offer|provide|send)\\s+(you\\s+)?(a|an|some)?\\s*\\d*\\s*%?\\s*(discount|promo ?code)"
                            + "|(надамо|надаємо|даруємо|нарахуємо|надішлемо)\\s+(вам\\s+)?(знижк|промокод|бонус)", FLAGS),
            "claims an action is done", Pattern.compile(
                    "(we have|we've)\\s+(already\\s+)?(refunded|issued\\s+(a|the|your)\\s+refund|returned the money)"
                            + "|(кошти|гроші)\\s+(вже\\s+)?(повернуто|повернули)|ми вже повернули", FLAGS),
            "price difference refund", Pattern.compile("refund the (price )?difference|повернемо (вам )?різниц", FLAGS),
            "instant refund", Pattern.compile(
                    "refund\\S*\\s+(you\\s+)?(today|immediately|right away)|(повернемо|повернуто)\\s+(кошти|гроші)?\\s*(сьогодні|негайно|одразу)", FLAGS)
    );

    public List<String> check(String text) {
        List<String> hits = new ArrayList<>();
        if (text == null) {
            return hits;
        }
        RULES.forEach((name, p) -> {
            if (p.matcher(text).find()) {
                hits.add(name);
            }
        });
        hits.sort(null);
        return hits;
    }
}

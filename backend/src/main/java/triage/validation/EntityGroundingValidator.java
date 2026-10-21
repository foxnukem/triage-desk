package triage.validation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import triage.ai.Extraction;

/** Every extracted orderId / email / amount must literally appear in the ticket text. */
@Component
public class EntityGroundingValidator {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[ \\u00A0.,]\\d+)*");

    public List<String> check(Extraction e, String ticketText) {
        List<String> errors = new ArrayList<>();
        if (e == null) {
            return errors;
        }
        if (e.orderId() != null && !orderIdGrounded(e.orderId(), ticketText)) {
            errors.add("orderId \"" + e.orderId() + "\" does not appear in the ticket; use null if there is no order number");
        }
        if (e.email() != null && !ticketText.toLowerCase(Locale.ROOT).contains(e.email().toLowerCase(Locale.ROOT))) {
            errors.add("email \"" + e.email() + "\" does not appear in the ticket; use null");
        }
        if (e.amount() != null && !amountGrounded(e.amount(), ticketText)) {
            errors.add("amount " + e.amount().toPlainString() + " does not appear in the ticket; use null");
        }
        return errors;
    }

    /** Number of extracted entities that are not grounded (the hallucination metric). */
    public int countUngrounded(Extraction e, String ticketText) {
        return check(e, ticketText).size();
    }

    public static int countEntities(Extraction e) {
        if (e == null) {
            return 0;
        }
        return (e.orderId() != null ? 1 : 0) + (e.email() != null ? 1 : 0) + (e.amount() != null ? 1 : 0);
    }

    /** The 6 digits must be in the text as a standalone number; "GK-" may be missing or written differently. */
    static boolean orderIdGrounded(String orderId, String text) {
        String digits = orderId.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return false;
        }
        return Pattern.compile("(?<!\\d)" + digits + "(?!\\d)").matcher(text).find();
    }

    static boolean amountGrounded(BigDecimal amount, String text) {
        for (BigDecimal candidate : numbersIn(text)) {
            if (candidate.compareTo(amount) == 0) {
                return true;
            }
        }
        return false;
    }

    /** Each number token is read two ways: all separators as thousands, or the last one as a decimal point. */
    static Set<BigDecimal> numbersIn(String text) {
        Set<BigDecimal> out = new HashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String token = m.group();
            out.add(new BigDecimal(token.replaceAll("[ \\u00A0.,]", "")));
            int last = Math.max(token.lastIndexOf('.'), token.lastIndexOf(','));
            if (last > 0) {
                String intPart = token.substring(0, last).replaceAll("[ \\u00A0.,]", "");
                String frac = token.substring(last + 1).replaceAll("[ \\u00A0]", "");
                if (!intPart.isEmpty() && !frac.isEmpty()) {
                    out.add(new BigDecimal(intPart + "." + frac));
                }
            }
            // "1 299" style tokens may also be two separate numbers.
            for (String part : token.split("[ \\u00A0]")) {
                if (!part.isEmpty() && part.chars().allMatch(Character::isDigit)) {
                    out.add(new BigDecimal(part));
                }
            }
        }
        return out;
    }
}

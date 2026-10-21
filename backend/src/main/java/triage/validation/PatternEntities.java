package triage.validation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import triage.ai.Extraction;

/**
 * Deterministic extraction of fixed-format entities. Used to fill fields the LLM left out:
 * with an optional schema field, constrained decoding lets the model skip it silently.
 */
public final class PatternEntities {

    private static final Pattern ORDER = Pattern.compile("(?i)(?<![\\w-])GK[-\\s]?(\\d{6})(?!\\d)");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)*\\.[A-Za-z]{2,}");

    private PatternEntities() {}

    public record Fill(Extraction extraction, Map<String, String> filled) {}

    public static List<String> orderIds(String text) {
        return ORDER.matcher(text).results().map(m -> "GK-" + m.group(1)).distinct().toList();
    }

    public static List<String> emails(String text) {
        return EMAIL.matcher(text).results().map(m -> m.group().toLowerCase(Locale.ROOT)).distinct().toList();
    }

    /** Fills orderId / email only when the model omitted them and the text has exactly one candidate. */
    public static Fill fill(Extraction e, String text) {
        Map<String, String> filled = new LinkedHashMap<>();
        Extraction out = e;
        List<String> orders = orderIds(text);
        if (out.orderId() == null && orders.size() == 1) {
            out = out.withOrderId(orders.get(0));
            filled.put("orderId", orders.get(0));
        }
        List<String> mails = emails(text);
        if (out.email() == null && mails.size() == 1) {
            out = out.withEmail(mails.get(0));
            filled.put("email", mails.get(0));
        }
        return new Fill(out, filled);
    }
}

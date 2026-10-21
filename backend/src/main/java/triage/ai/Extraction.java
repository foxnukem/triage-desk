package triage.ai;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import dev.langchain4j.model.output.structured.Description;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Entity fields are optional in the schema, so the model can leave them out instead of inventing values. */
public record Extraction(
        @Description("order number from the ticket, normalised to GK- plus its six digits; omit if none")
        @Pattern(regexp = "GK-\\d{6}") String orderId,
        @Description("email address written in the ticket; omit if none")
        @Email String email,
        @Description("product name mentioned in the ticket; omit if none")
        String product,
        @Description("money amount written in the ticket, as a number; omit if none")
        @PositiveOrZero BigDecimal amount,
        @JsonProperty(required = true) @Description("one neutral English sentence describing the request")
        @NotBlank @Size(max = 160) String summary
) {

    /** Small models fill missing fields with "null", "N/A" or 0 instead of omitting them. */
    public Extraction {
        orderId = blankToNull(orderId);
        email = blankToNull(email);
        product = blankToNull(product);
        if (amount != null && amount.signum() == 0) {
            amount = null;
        }
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.strip();
        return t.isEmpty() || PLACEHOLDERS.contains(t.toLowerCase(java.util.Locale.ROOT)) ? null : t;
    }

    private static final java.util.Set<String> PLACEHOLDERS = java.util.Set.of("null", "none", "n/a", "na", "-", "unknown", "немає");

    public Extraction withOrderId(String id) {
        return new Extraction(id, email, product, amount, summary);
    }

    public Extraction withEmail(String e) {
        return new Extraction(orderId, e, product, amount, summary);
    }
}

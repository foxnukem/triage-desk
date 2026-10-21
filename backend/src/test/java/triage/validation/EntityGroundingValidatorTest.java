package triage.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import triage.ai.Extraction;

class EntityGroundingValidatorTest {

    private final EntityGroundingValidator v = new EntityGroundingValidator();

    private static Extraction ext(String orderId, String email, String amount) {
        return new Extraction(orderId, email, null, amount == null ? null : new BigDecimal(amount), "summary");
    }

    @Test
    void groundedEntitiesPass() {
        String text = "Замовлення 104233, пишіть на olena@example.com. Списали 1 299,50 грн.";
        assertThat(v.check(ext("GK-104233", "Olena@example.com", "1299.50"), text)).isEmpty();
    }

    @Test
    void inventedOrderIdIsRejected() {
        assertThat(v.check(ext("GK-123456", null, null), "Where is my order?"))
                .singleElement().asString().contains("GK-123456");
    }

    @Test
    void orderIdDigitsMustStandAlone() {
        assertThat(v.check(ext("GK-104233", null, null), "tracking 9104233771")).hasSize(1);
    }

    @Test
    void inventedEmailAndAmountAreRejected() {
        String text = "I paid 500 UAH and nothing happened";
        assertThat(v.check(ext(null, "someone@example.com", "499"), text)).hasSize(2);
        assertThat(v.countUngrounded(ext(null, null, "500"), text)).isZero();
    }

    @Test
    void countsEntities() {
        assertThat(EntityGroundingValidator.countEntities(ext("GK-104233", "a@example.com", null))).isEqualTo(2);
        assertThat(EntityGroundingValidator.countEntities(null)).isZero();
    }
}

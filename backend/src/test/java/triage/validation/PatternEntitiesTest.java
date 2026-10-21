package triage.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import triage.ai.Extraction;

class PatternEntitiesTest {

    private static Extraction empty() {
        return new Extraction(null, null, null, null, "summary");
    }

    @Test
    void fillsOmittedOrderAndEmail() {
        var f = PatternEntities.fill(empty(), "Замовлення gk 104289 не прийшло, пишіть на Olena@example.com");
        assertThat(f.extraction().orderId()).isEqualTo("GK-104289");
        assertThat(f.extraction().email()).isEqualTo("olena@example.com");
        assertThat(f.filled()).containsOnlyKeys("orderId", "email");
    }

    @Test
    void keepsModelValuesAndSkipsAmbiguousText() {
        var withOrder = new Extraction("GK-104233", null, null, null, "s");
        assertThat(PatternEntities.fill(withOrder, "GK-104233").filled()).isEmpty();
        assertThat(PatternEntities.fill(empty(), "orders GK-104233 and GK-104260").filled()).isEmpty();
        assertThat(PatternEntities.fill(empty(), "tracking 59001234567890").filled()).isEmpty();
    }
}

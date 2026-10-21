package triage.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class ExtractionTest {

    @Test
    void placeholdersBecomeNull() {
        var e = new Extraction("null", " N/A ", "", BigDecimal.ZERO, "summary");
        assertThat(e.orderId()).isNull();
        assertThat(e.email()).isNull();
        assertThat(e.product()).isNull();
        assertThat(e.amount()).isNull();
    }

    @Test
    void realValuesAreKept() {
        var e = new Extraction("GK-104233", "a@example.com", "AirPods Pro", new BigDecimal("12.50"), "summary");
        assertThat(e.orderId()).isEqualTo("GK-104233");
        assertThat(e.product()).isEqualTo("AirPods Pro");
        assertThat(e.amount()).isEqualByComparingTo("12.50");
    }
}

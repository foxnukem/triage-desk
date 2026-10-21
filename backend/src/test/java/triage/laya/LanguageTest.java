package triage.laya;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import triage.ai.Lang;

class LanguageTest {

    @Test
    void scriptMajorityDecides() {
        assertThat(LayaClassifier.language("Де моя посилка?")).isEqualTo(Lang.UK);
        assertThat(LayaClassifier.language("Where is my parcel?")).isEqualTo(Lang.EN);
        assertThat(LayaClassifier.language("Order GK-104233 досі не прийшло, де воно?")).isEqualTo(Lang.UK);
        assertThat(LayaClassifier.language("12345 !!!")).isEqualTo(Lang.OTHER);
    }

    @Test
    void modelNameComesFromTheEncoder() {
        assertThat(LayaClassifier.encoderName(java.nio.file.Path.of("../laya/checkpoint"))).isIn("mmBERT-base", "checkpoint");
    }

    @Test
    void questionsUseTheBackendEnumsInOrder() {
        var q = LayaClassifier.questions();
        assertThat(q).containsOnlyKeys("category", "priority", "sentiment", "multiIntent");
    }
}

package triage.laya;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import triage.TriageProperties;
import triage.ai.Category;

/**
 * The real Laya model on a few tickets. Needs the files under laya/ (LAYA_DIR, default
 * ../laya/english from backend/). Run with {@code ./mvnw -pl backend test -Dtest.excludedGroups= -Dgroups=laya}.
 */
@Tag("laya")
class LayaModelTest {

    static LayaClassifier laya;

    @BeforeAll
    static void open() {
        Path dir = Path.of(System.getenv().getOrDefault("LAYA_DIR", "../laya/english"));
        assumeTrue(Files.isDirectory(dir.resolve("onnx")), "no Laya model at " + dir.toAbsolutePath());
        var props = new Binder(new MapConfigurationPropertySource(java.util.Map.of(
                "triage.laya.checkpoint", dir.resolve("checkpoint").toString(),
                "triage.laya.graph", dir.resolve("onnx").toString())))
                .bindOrCreate("triage", TriageProperties.class);
        laya = new LayaClassifier(props);
        assumeTrue(laya.available(), "Laya did not open");
    }

    @Test
    void classifiesEnglishTickets() {
        record Case(String text, Category expected) {}
        List<Case> cases = List.of(
                new Case("You charged my card twice for order GK-104260, please refund one payment.", Category.BILLING),
                new Case("My parcel has been stuck in tracking for 5 days, where is it?", Category.SHIPPING),
                new Case("I forgot my password and the reset email never arrives.", Category.ACCOUNT),
                new Case("The headphones arrived with a broken hinge, I want my money back.", Category.RETURN_REFUND));
        int correct = 0;
        for (Case c : cases) {
            var r = laya.classify(c.text());
            System.out.printf("laya %-13s p=%.2f %s %s %dms | %s%n", r.classification().category(), r.classification().confidence(),
                    r.classification().priority(), r.classification().sentiment(), r.ms(), c.text());
            if (r.classification().category() == c.expected()) {
                correct++;
            }
        }
        assertThat(correct).as("English category hits").isGreaterThanOrEqualTo(2);
    }

    @Test
    void reportsOnUkrainian() {
        // The English checkpoint is not expected to handle Ukrainian; this records what it does.
        for (String t : List.of("Замовлення GK-104233 прийшло з тріснутим екраном, хочу повернути гроші.",
                "Шо там з моєю посилкою, вже тиждень чекаю")) {
            var r = laya.classify(t);
            System.out.printf("laya %-13s p=%.2f %dms | %s%n", r.classification().category(), r.classification().confidence(), r.ms(), t);
            assertThat(r.classification().category()).isNotNull();
        }
    }
}

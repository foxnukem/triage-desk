package triage.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import triage.ai.ReplyDraft;

class CitationValidatorTest {

    private final CitationValidator v = new CitationValidator();
    private final Set<String> kb = Set.of("kb-returns-14days", "kb-defective-goods", "kb-refund-timing");

    private static ReplyDraft draft(String... cited) {
        return new ReplyDraft("text", List.of(cited), false);
    }

    @Test
    void citedArticlesReturnedBySearchPass() {
        assertThat(v.check(draft("kb-defective-goods"), true, Set.of("kb-defective-goods", "kb-refund-timing"), kb)).isEmpty();
    }

    @Test
    void searchMustBeCalled() {
        assertThat(v.check(draft(), false, Set.of(), kb)).singleElement().asString().contains("searchKb");
    }

    @Test
    void unknownAndUnretrievedArticlesAreRejected() {
        List<String> errors = v.check(draft("kb-made-up", "kb-refund-timing"), true, Set.of("kb-defective-goods"), kb);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("not a knowledge base article");
        assertThat(errors.get(1)).contains("did not return");
    }
}

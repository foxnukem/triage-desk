package triage.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import triage.ai.ReplyDraft;

/** Cited articles must exist in the KB and must have been returned by searchKb in this request. */
@Component
public class CitationValidator {

    /** The error text for a reply without a searchKb call; the pipeline keys its search fallback on it. */
    public static final String NO_SEARCH = "you did not call searchKb";

    public List<String> check(ReplyDraft draft, boolean searchCalled, Set<String> retrieved, Set<String> kbIds) {
        List<String> errors = new ArrayList<>();
        if (draft == null) {
            return errors;
        }
        if (!searchCalled) {
            errors.add(NO_SEARCH + "; call it first and base the reply on the returned articles");
            return errors;
        }
        for (String id : draft.citedArticles()) {
            if (!kbIds.contains(id)) {
                errors.add("citedArticles contains \"" + id + "\", which is not a knowledge base article id");
            } else if (!retrieved.contains(id)) {
                errors.add("citedArticles contains \"" + id + "\", which searchKb did not return in this conversation");
            }
        }
        return errors;
    }
}

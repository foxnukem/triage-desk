package triage.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import triage.ai.Category;

/** Category → specialised part of the reply prompt (prompts/guidance/*.txt). */
@Component
public class ReplyGuidance {

    private final Map<Category, String> byCategory = new EnumMap<>(Category.class);

    public ReplyGuidance() {
        for (Category c : Category.values()) {
            try {
                byCategory.put(c, new ClassPathResource("prompts/guidance/" + c + ".txt")
                        .getContentAsString(StandardCharsets.UTF_8).strip());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    public String forCategory(Category c) {
        return byCategory.get(c == null ? Category.OTHER : c);
    }
}

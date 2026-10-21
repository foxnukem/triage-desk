package triage.eval;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class EvalDataset {

    private final List<EvalCase> cases;

    public EvalDataset(ObjectMapper mapper) {
        List<EvalCase> list = new ArrayList<>();
        try (var in = new BufferedReader(new InputStreamReader(
                new ClassPathResource("eval/tickets.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    list.add(mapper.readValue(line, EvalCase.class));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.cases = List.copyOf(list);
    }

    public List<EvalCase> all() {
        return cases;
    }
}

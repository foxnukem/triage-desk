package triage;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Ollama serves one request at a time on a laptop, so triage and eval each get a single worker. */
@Configuration
public class AsyncConfig {

    @Bean
    Executor triageExecutor() {
        return single("triage-");
    }

    @Bean
    Executor evalExecutor() {
        return single("eval-");
    }

    private static Executor single(String prefix) {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(10_000);
        ex.setThreadNamePrefix(prefix);
        ex.initialize();
        return ex;
    }
}

package triage.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import triage.TriageProperties;

/** Only the public widget API is reachable cross-origin; the admin API is same-origin (behind nginx). */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TriageProperties props;

    public WebConfig(TriageProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/public/**")
                .allowedOrigins(props.publicCorsOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type");
    }
}

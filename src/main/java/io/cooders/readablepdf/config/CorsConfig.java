package io.cooders.readablepdf.config;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    private final List<String> allowedOrigins;
    private final List<String> allowedOriginPatterns;

    public CorsConfig(
            @Value("${readable-pdf.cors.allowed-origins:null,https://www.figma.com,https://figma.com}") String allowedOrigins,
            @Value("${readable-pdf.cors.allowed-origin-patterns:http://localhost:*}") String allowedOriginPatterns
    ) {
        this.allowedOrigins = splitValues(allowedOrigins);
        this.allowedOriginPatterns = splitValues(allowedOriginPatterns);
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        allowedOrigins.forEach(config::addAllowedOrigin);
        allowedOriginPatterns.forEach(config::addAllowedOriginPattern);
        config.setAllowedHeaders(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        config.setExposedHeaders(List.of("Content-Disposition", "Content-Length", "Content-Type"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    private List<String> splitValues(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }
}

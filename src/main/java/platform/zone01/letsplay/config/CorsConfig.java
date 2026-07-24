package platform.zone01.letsplay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Which origins can call the API
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",    // future React/Angular frontend
                "http://localhost:4200",    // future Angular frontend
                "https://yourdomain.com"    // future production frontend
        ));

        // Which HTTP methods are allowed
        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"
        ));

        // Which headers are allowed — critical for JWT
        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        // Allow Authorization header to be exposed to the browser
        configuration.setExposedHeaders(List.of("Authorization"));

        // Allow cookies/credentials to be sent
        configuration.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

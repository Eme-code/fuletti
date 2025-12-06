package it.eme.fuletti.configuration;

import it.eme.fuletti.service.FulettiService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FulettiConfiguration {

    @Bean
    public FulettiService service() {
        return new FulettiService();
    }
}

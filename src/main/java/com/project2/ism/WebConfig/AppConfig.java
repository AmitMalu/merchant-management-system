package com.project2.ism.WebConfig;



import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class AppConfig {

    /**
     * Global WebClient.Builder bean.
     * Allows:
     * - Connection pooling
     * - Timeouts
     * - Reuse across entire application
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create(
                        ConnectionProvider.builder("vendor-pool")
                                .maxConnections(200)
                                .pendingAcquireMaxCount(500)
                                .build()
                )
                .compress(true); // gzip

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(
                        ExchangeStrategies.builder()
                                .codecs(configurer -> configurer
                                        .defaultCodecs()
                                        .maxInMemorySize(16 * 1024 * 1024)  // 16 MB buffer
                                )
                                .build()
                );
    }

    /**
     * Global ObjectMapper bean for:
     * - ISO date/time
     * - Ignoring unknown JSON fields
     * - Non-failing on empty beans
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        mapper.findAndRegisterModules(); // for Java time

        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        return mapper;
    }
}

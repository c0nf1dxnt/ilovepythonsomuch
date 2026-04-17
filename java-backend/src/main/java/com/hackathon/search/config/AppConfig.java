package com.hackathon.search.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final AppProperties props;

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private WebClient buildClient(String baseUrl, ObjectMapper mapper) {
        int readTimeout = props.http().readTimeoutMs();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, props.http().connectTimeoutMs())
                .responseTimeout(Duration.ofMillis(readTimeout))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> {
                    c.defaultCodecs().maxInMemorySize(64 * 1024 * 1024);
                    c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                    c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
                })
                .build();
    }

    @Bean
    public WebClient pythonClient(ObjectMapper mapper) {
        return buildClient(props.python().baseUrl(), mapper);
    }

    @Bean
    public WebClient qdrantClient(ObjectMapper mapper) {
        return buildClient(props.qdrant().baseUrl(), mapper);
    }
}

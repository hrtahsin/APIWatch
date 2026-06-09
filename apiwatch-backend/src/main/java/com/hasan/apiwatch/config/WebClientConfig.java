package com.hasan.apiwatch.config;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create().followRedirect(false)
                ))
                .defaultHeader("User-Agent", "APIWatch/1.0");
    }
}

package com.hotel.concierge.config;

import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WeaviateConfig {

    @Value("${app.weaviate.scheme}")
    private String scheme;

    @Value("${app.weaviate.host}")
    private String host;

    @Value("${app.weaviate.class-name}")
    private String className;

    @Value("${app.weaviate.top-k}")
    private int topK;

    @Bean
    public WeaviateClient weaviateClient() {
        Config config = new Config(scheme, host);
        return new WeaviateClient(config);
    }

    public String getClassName() {
        return className;
    }

    public int getTopK() {
        return topK;
    }
}

package com.shiyi.module.rag.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class OllamaConfig {

    @Bean
    public OllamaChatModel ollamaChatModel(@Value("${langchain4j.ollama.chat-model.base-url:http://localhost:11434}") String baseUrl) {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("qwen2.5:7b")
                .temperature(0.7)
                .timeout(Duration.ofMinutes(2))
                .build();
    }

    @Bean
    public OllamaEmbeddingModel ollamaEmbeddingModel(@Value("${langchain4j.ollama.chat-model.base-url:http://localhost:11434}") String baseUrl) {
        return OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName("bge-m3:latest")
                .timeout(Duration.ofMinutes(2))
                .build();
    }
}

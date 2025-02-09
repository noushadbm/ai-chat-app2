package org.rayshan.simple.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatModelConfig {
    @Bean
    ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .modelName("deepseek-r1:1.5b") //llama2:latest
                .baseUrl("http://localhost:11434")
                .build();
    }
}

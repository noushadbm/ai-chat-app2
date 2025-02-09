package org.rayshan.simple.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.rayshan.simple.store.SpringJdbcPgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
//import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class EmbeddingStoreConfig {
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        //return new InMemoryEmbeddingStore<>();
        return new SpringJdbcPgVectorEmbeddingStore("DATASET_ID_1", jdbcTemplate);
    }
}

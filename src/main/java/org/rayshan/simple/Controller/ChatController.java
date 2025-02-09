package org.rayshan.simple.Controller;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import lombok.extern.log4j.Log4j2;
import org.rayshan.simple.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/v1")
@Log4j2
public class ChatController {
    private final ChatLanguageModel chatLanguageModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    public ChatController(ChatLanguageModel chatLanguageModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.chatLanguageModel = chatLanguageModel;
        this.embeddingStore = embeddingStore;
    }

    @GetMapping("/chat")
    public String getLLMResponse(@RequestParam("userQuery") String userQuery) {
        log.info("Request received: {}", userQuery);
        ChatService chatService = AiServices.builder(ChatService.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(EmbeddingStoreContentRetriever.from(embeddingStore))
                .build();

        return chatService.chat(userQuery);
    }

    @GetMapping("/load")
    public void loadDocumentToDB() {
        log.info("Loading document");
        //Document document = FileSystemDocumentLoader.loadDocument("/Users/macbook/Development/SpringBoot/ai-rag-app/ai-rag-app2/src/main/resources/About_Mastra.txt");
        Document document = loadDocument(Path.of("/Users/macbook/Documents/Hacking/Hacking_notes_.pdf"), new ApachePdfBoxDocumentParser());
        EmbeddingStoreIngestor.ingest(document, embeddingStore);
        log.info("Document loaded");
    }
}

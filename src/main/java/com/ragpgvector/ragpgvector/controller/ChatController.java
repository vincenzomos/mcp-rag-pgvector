package com.ragpgvector.ragpgvector.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatOptions;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;

@RestController
//@RequestMapping("/api/ai")
@Slf4j
public class ChatController {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.chatClient =  chatClientBuilder.defaultOptions(
                VertexAiGeminiChatOptions.builder()
                        .temperature(0.7)
                        .build())
                        .defaultAdvisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                        .build();
    }
//    @GetMapping("/chat")
//    public String chat(@RequestParam String prompt) {
//        return chatService.ask(prompt);
//    }

    @PostMapping("/chat")
    public String chat(@RequestParam String message) {
        return  chatClient
                .prompt(message)
                .call()
                .content();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatWithStream(@RequestParam String message) {
        return chatClient
                .prompt()
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .user(message)
                .stream()
                .content()
                .delayElements(Duration.ofMillis(50))
                .doOnNext(chunk -> log.info("Emitting chunk: {}", chunk))
                .concatWith(Flux.just("[DONE]"));// verify server emits
    }

    @GetMapping(value = "/test-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStream() {
        return Flux.interval(Duration.ofMillis(200))
                .take(10)
                .map(i -> "Chunk " + i + " at " + System.currentTimeMillis())
                .concatWith(Flux.just("[DONE]"));
    }

}

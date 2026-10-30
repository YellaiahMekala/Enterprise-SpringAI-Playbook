package com.springai.explore.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real HTTP, security, services, memory advisor and H2; a latch controls only the paid model. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.api-keys.alice=alice-chat-key",
        "app.security.api-keys.bob=bob-chat-key"
})
@ActiveProfiles("test")
class ChatConcurrencyIntegrationTests {

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @Autowired
    ChatMemory memory;

    @MockitoBean
    ChatModel model;

    private CountDownLatch entered;
    private CountDownLatch release;

    @BeforeEach
    void prepareControlledModel() {
        memory.clear("alice");
        memory.clear("bob");
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            String text = invocation.<Prompt>getArgument(0).getLastUserOrToolResponseMessage().getText();
            if (text.equals("first")) {
                entered.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release the first turn.");
                }
            }
            return answer(text);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "/chat, /chat/tutor",
            "/chat/tutor, /assistant/ask",
            "/assistant/ask, /assistant/plan-my-day",
            "/assistant/plan-my-day, /chat"
    })
    void overlappingUseCasesRejectTheSecondTurnWithoutTouchingMemoryOrModel(String firstPath, String secondPath)
            throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> post(firstPath, "alice", "first"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var conflict = post(secondPath, "alice", "second");
                assertThat(conflict.statusCode()).isEqualTo(409);
                assertThat(conflict.headers().firstValue("Content-Type")).hasValue("application/problem+json");
                assertThat(JSON.readTree(conflict.body()).get("status").asInt()).isEqualTo(409);
                assertThat(JSON.readTree(conflict.body()).get("detail").asText())
                        .isEqualTo("A conversation request is already in progress. Retry after it completes.");
                // A rejected request must not release the first request's guard.
                assertThat(post(secondPath, "alice", "second").statusCode()).isEqualTo(409);
                verify(model, times(1)).call(any(Prompt.class));
                assertThat(memory.get("alice")).extracting(Message::getText).containsExactly("first");
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(post(secondPath, "alice", "second").statusCode()).isEqualTo(200);
            assertThat(memory.get("alice")).extracting(Message::getText)
                    .containsExactly("first", "answer:first", "second", "answer:second");
        }
    }

    @Test
    void anotherCallerCanCompleteWhileTheFirstCallerIsWaiting() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> post("/chat", "alice", "first"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(post("/chat", "bob", "independent").statusCode()).isEqualTo(200);
                assertThat(memory.get("bob")).extracting(Message::getText)
                        .containsExactly("independent", "answer:independent");
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void statelessRecommendationsAreNotBlockedByAConversationTurn() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> post("/chat", "alice", "first"));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var response = HTTP.send(builder("/recommendations/movie?type=comedy&year=2024&lang=English", "alice")
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(memory.get("alice")).extracting(Message::getText).containsExactly("first");
            } finally {
                release.countDown();
            }
            assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider-error", "empty-output"})
    void aFailedTurnDoesNotLeaveTheCallerPermanentlyBusy(String failure) throws Exception {
        if (failure.equals("provider-error")) {
            when(model.call(any(Prompt.class)))
                    .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Synthetic provider failure."))
                    .thenReturn(answer("recovered"));
        } else {
            when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()), answer("recovered"));
        }
        assertThat(post("/chat", "alice", "fail").statusCode()).isEqualTo(502);
        assertThat(post("/chat", "alice", "retry").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> post(String path, String caller, String prompt) throws Exception {
        return HTTP.send(builder(path, caller).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"prompt\":\"" + prompt + "\"}"))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder builder(String path, String caller) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(10)).header("X-API-Key", caller + "-chat-key");
    }

    private static ChatResponse answer(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("answer:" + text))));
    }
}

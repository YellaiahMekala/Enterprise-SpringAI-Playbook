package com.springai.explore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Exercises the real {@link ChatClient} and memory advisor against a fake model that reports how many
 * messages it was sent. No network and no API key needed.
 */
class ChatServiceMemoryTests {

    private ChatService service;
    private ChatModel model;

    @BeforeEach
    void setUp() {
        model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenAnswer(inv -> {
            Prompt sent = inv.getArgument(0);
            return new ChatResponse(List.of(new Generation(new AssistantMessage("seen " + sent.getInstructions().size()))));
        });
        var memory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository()).maxMessages(20).build();
        ChatClient client = ChatClient.builder(model)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(memory).build())
                .build();
        service = new ChatService(client, client, client, client);
    }

    @Test
    void historyIsReplayedForTheSameCaller() {
        assertThat(service.chat("alice", "My name is Asha").answer()).isEqualTo("seen 1");
        // user + assistant from turn one, plus the new user message
        assertThat(service.chat("alice", "What is my name?").answer()).isEqualTo("seen 3");
    }

    @Test
    void conversationsAreIsolatedPerCaller() {
        service.chat("alice", "My name is Asha");
        assertThat(service.chat("bob", "What is my name?").answer()).isEqualTo("seen 1");
    }

    @Test
    void emptyModelResponseBecomesAModelResponseException() {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of()));

        assertThatThrownBy(() -> service.chat("alice", "hi")).isInstanceOf(ModelResponseException.class);
    }
}

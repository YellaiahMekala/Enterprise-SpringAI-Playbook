package com.springai.explore.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatServiceResponseTests {

    static Stream<Arguments> blankResponses() {
        return Stream.of("chat", "tutor", "assistant", "planner", "movie")
                .flatMap(useCase -> Stream.of("", "   ", "\t\r\n")
                        .map(text -> Arguments.of(useCase, text)));
    }

    /** Exercise every entry point through the real ChatClient, without a provider or API key. */
    @ParameterizedTest(name = "{index}: {0} response")
    @MethodSource("blankResponses")
    void rejectsBlankAnswers(String useCase, String text) {
        ChatService service = serviceReturning(text);
        assertThatThrownBy(() -> invoke(service, useCase))
                .isInstanceOf(ModelResponseException.class)
                .hasMessage("The model returned an empty response.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"chat", "tutor", "assistant", "planner", "movie"})
    void preservesNonBlankAnswerFormatting(String useCase) {
        String text = "  A useful answer.\n";
        assertThat(invoke(serviceReturning(text), useCase)).isEqualTo(text);
    }

    private static ChatService serviceReturning(String text) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(text)))));
        ChatClient client = ChatClient.builder(model).build();
        return new ChatService(client, client, client, client);
    }

    private static String invoke(ChatService service, String useCase) {
        return switch (useCase) {
            case "chat" -> service.chat("alice", "hello").answer();
            case "tutor" -> service.chatAsTutor("alice", "hello").answer();
            case "assistant" -> service.askAssistant("alice", "hello").answer();
            case "planner" -> service.planMyDay("alice", "hello").answer();
            case "movie" -> service.recommendMovie("comedy", "2024", "English").answer();
            default -> throw new IllegalArgumentException(useCase);
        };
    }
}

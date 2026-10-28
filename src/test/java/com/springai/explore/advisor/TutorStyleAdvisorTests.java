package com.springai.explore.advisor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TutorStyleAdvisorTests {

    private final TutorStyleAdvisor advisor = new TutorStyleAdvisor();

    @Test
    void addsTutorInstructionToSystemMessageAndKeepsTheConversationIntact() {
        List<Message> conversation = List.of(
                new SystemMessage("base system"),
                new UserMessage("earlier question"),
                new AssistantMessage("earlier answer"),
                new UserMessage("explain virtual threads"));
        var request = ChatClientRequest.builder().prompt(new Prompt(conversation)).build();

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(mock(ChatClientResponse.class));

        advisor.adviseCall(request, chain);

        var captor = ArgumentCaptor.forClass(ChatClientRequest.class);
        verify(chain).nextCall(captor.capture());
        List<Message> forwarded = captor.getValue().prompt().getInstructions();

        // Roles and history survive: the previous implementation flattened everything into one user message.
        assertThat(forwarded).hasSize(4);
        assertThat(forwarded.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(forwarded.get(0).getText()).contains("base system").contains(TutorStyleAdvisor.TUTOR_INSTRUCTION);
        assertThat(forwarded.get(3).getText()).isEqualTo("explain virtual threads");
    }
}

package com.springai.explore.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * Cross-cutting prompt policy: asks the model for tutor-style answers.
 * <p>
 * The instruction is appended to the <em>system</em> message, so the user's message, the memory history,
 * tool definitions and chat options are all preserved and nothing policy-related is persisted into chat memory.
 */
public class TutorStyleAdvisor implements CallAdvisor {

    static final String TUTOR_INSTRUCTION =
            "Answer in a tutor style: explain clearly, build up step by step and give examples where helpful.";

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // augmentSystemMessage(String) would replace the system text, so append through the function form.
        var tutorPrompt = request.prompt().augmentSystemMessage(system ->
                system.mutate().text(system.getText() + "\n\n" + TUTOR_INSTRUCTION).build());
        return chain.nextCall(request.mutate().prompt(tutorPrompt).build());
    }

    @Override
    public String getName() {
        return "TutorStyleAdvisor";
    }

    /** Lower runs first. Kept after the memory advisor's default precedence so history is already in the prompt. */
    @Override
    public int getOrder() {
        return 0;
    }
}

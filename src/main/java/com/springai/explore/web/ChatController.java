package com.springai.explore.web;

import com.springai.explore.service.ChatService;
import com.springai.explore.web.dto.ChatReply;
import com.springai.explore.web.dto.PromptRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Conversational chat backed by durable per-caller memory. The caller id comes from the credential, not the request. */
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatReply chat(@Valid @RequestBody PromptRequest request, Principal caller) {
        return chatService.chat(caller.getName(), request.prompt());
    }

    @PostMapping("/tutor")
    public ChatReply chatAsTutor(@Valid @RequestBody PromptRequest request, Principal caller) {
        return chatService.chatAsTutor(caller.getName(), request.prompt());
    }
}

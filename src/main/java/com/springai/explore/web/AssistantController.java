package com.springai.explore.web;

import com.springai.explore.service.ChatService;
import com.springai.explore.web.dto.ChatReply;
import com.springai.explore.web.dto.PromptRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

/** Assistant endpoints where the model may call tools (date/time, weather, news). */
@RestController
@RequestMapping("/api/v1")
public class AssistantController {

    private final ChatService chatService;

    public AssistantController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/assistant/ask")
    public ChatReply ask(@Valid @RequestBody PromptRequest request, Principal caller) {
        return chatService.askAssistant(caller.getName(), request.prompt());
    }

    @PostMapping("/assistant/plan-my-day")
    public ChatReply planMyDay(@Valid @RequestBody PromptRequest request, Principal caller) {
        return chatService.planMyDay(caller.getName(), request.prompt());
    }

    /** Free-text request parameters are restricted to letters and digits so they cannot carry instructions. */
    @GetMapping("/recommendations/movie")
    public ChatReply recommendMovie(
            @RequestParam @Pattern(regexp = "[\\p{L} ]{2,30}") String type,
            @RequestParam @Pattern(regexp = "\\d{4}") String year,
            @RequestParam @Pattern(regexp = "[\\p{L} ]{2,30}") String lang) {
        return chatService.recommendMovie(type, year, lang);
    }
}

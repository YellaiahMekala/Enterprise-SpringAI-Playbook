package com.springai.explore.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** OpenAI limits text-to-speech input to 4096 characters. */
public record SpeechRequest(@NotBlank @Size(max = 4096) String text) {
}

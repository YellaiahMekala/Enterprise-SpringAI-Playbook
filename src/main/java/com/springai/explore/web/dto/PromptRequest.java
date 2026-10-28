package com.springai.explore.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PromptRequest(@NotBlank @Size(max = 4000) String prompt) {
}

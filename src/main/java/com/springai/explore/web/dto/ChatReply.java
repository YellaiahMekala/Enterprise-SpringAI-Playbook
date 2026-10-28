package com.springai.explore.web.dto;

/** @param model the model that served the request, as reported by the provider (may be null) */
public record ChatReply(String answer, String model) {
}

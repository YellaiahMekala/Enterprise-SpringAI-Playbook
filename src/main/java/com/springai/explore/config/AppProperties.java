package com.springai.explore.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;

/**
 * Typed, validated application settings. A bad value fails the startup, not the first request.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue @Valid @NotNull Security security,
        @DefaultValue @Valid @NotNull Chat chat,
        @DefaultValue @Valid @NotNull Tools tools,
        @DefaultValue @Valid @NotNull Image image) {

    /** API keys by caller id. The caller id becomes the chat-memory conversation id (max 36 chars). */
    public record Security(@DefaultValue Map<String, String> apiKeys) {
    }

    public record Chat(@DefaultValue("20") @Min(1) @Max(200) int maxMessages) {
    }

    public record Tools(@DefaultValue @Valid @NotNull Weather weather,
                        @DefaultValue @Valid @NotNull News news) {

        public record Weather(@DefaultValue("https://wttr.in") @NotBlank String baseUrl,
                              @DefaultValue("5s") @NotNull Duration timeout) {
        }

        public record News(@DefaultValue("https://newsapi.org/v2") @NotBlank String baseUrl,
                           @DefaultValue("") String apiKey,
                           @DefaultValue("5") @Min(1) @Max(20) int pageSize,
                           @DefaultValue("5s") @NotNull Duration timeout) {
        }
    }

    public record Image(@DefaultValue("./generated") @NotBlank String outputDir) {
    }
}

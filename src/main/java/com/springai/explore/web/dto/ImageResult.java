package com.springai.explore.web.dto;

import java.util.UUID;

/**
 * @param id          identifier to download the image from {@code GET /api/v1/images/{id}}
 * @param totalTokens tokens billed for the generation, or null when the provider does not report usage
 */
public record ImageResult(UUID id, Integer totalTokens) {
}

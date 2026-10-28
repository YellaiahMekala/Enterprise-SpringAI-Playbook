package com.springai.explore.web;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Rejects empty or unsupported uploads before any paid model call is made.
 * The content type is the client-declared one; it narrows accidents, it is not proof of file content.
 */
@Component
public class UploadValidator {

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> AUDIO_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/mp4", "audio/m4a",
            "audio/x-m4a", "audio/webm", "audio/ogg", "audio/flac");

    public void requireImage(MultipartFile file) {
        require(file, IMAGE_TYPES, "image");
    }

    public void requireAudio(MultipartFile file) {
        require(file, AUDIO_TYPES, "audio");
    }

    private static void require(MultipartFile file, Set<String> allowed, String kind) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded " + kind + " file is empty.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!allowed.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported " + kind + " type. Allowed: " + String.join(", ", new java.util.TreeSet<>(allowed)));
        }
    }
}

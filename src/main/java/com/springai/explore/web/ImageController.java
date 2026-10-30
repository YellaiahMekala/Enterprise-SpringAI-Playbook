package com.springai.explore.web;

import com.springai.explore.service.ImageService;
import com.springai.explore.web.dto.ImageResult;
import com.springai.explore.web.dto.PromptRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageService imageService;
    private final UploadValidator uploads;

    public ImageController(ImageService imageService, UploadValidator uploads) {
        this.imageService = imageService;
        this.uploads = uploads;
    }

    @PostMapping
    public ImageResult generate(@Valid @RequestBody PromptRequest request, Principal caller) {
        return imageService.generate(caller.getName(), request.prompt());
    }

    @GetMapping(value = "/{id}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> download(@PathVariable UUID id, Principal caller) {
        return ResponseEntity.ok(imageService.load(caller.getName(), id));
    }

    @PostMapping(value = "/descriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.Map<String, String> describe(
            @RequestParam @NotBlank @Size(max = 1000) String prompt,
            @RequestParam MultipartFile file) {
        uploads.requireImage(file);
        return java.util.Map.of("description", imageService.describe(prompt, file));
    }
}

package com.springai.explore.service;

import com.springai.explore.config.AppProperties;
import com.springai.explore.web.dto.ImageResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import static com.springai.explore.config.AiConfig.PLAIN_CLIENT;

@Service
public class ImageService {

    private final ImageModel imageModel;
    private final ChatClient visionClient;
    private final Path outputDir;

    public ImageService(ImageModel imageModel, @Qualifier(PLAIN_CLIENT) ChatClient visionClient, AppProperties props) {
        this.imageModel = imageModel;
        this.visionClient = visionClient;
        this.outputDir = Path.of(props.image().outputDir()).toAbsolutePath().normalize();
    }

    public ImageResult generate(String callerId, String prompt) {
        Path directory = callerDirectory(callerId);
        ImageResponse response = imageModel.call(new ImagePrompt(prompt,
                OpenAiImageOptions.builder().quality("high").width(1024).height(1024).build()));

        String base64 = response.getResult() == null ? null : response.getResult().getOutput().getB64Json();
        if (base64 == null) {
            throw new ModelResponseException("The image model returned no image data.");
        }
        //response.getMetadata().getUsage().getTotalTokens();
        // The image API may report no usage at all (an empty usage reads as 0): report "unknown", not "free".
        Integer reported = response.getMetadata() == null || response.getMetadata().getUsage() == null
                ? null : response.getMetadata().getUsage().getTotalTokens();
        Integer totalTokens = reported == null || reported <= 0 ? null : reported;

        // The server picks the file name: no client-controlled path ever reaches the file system.
        UUID id = UUID.randomUUID();
        try {
            Files.createDirectories(directory);
            Files.write(directory.resolve(id + ".png"), Base64.getDecoder().decode(base64));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the generated image", e);
        }
        return new ImageResult(id, totalTokens);
    }

    public Resource load(String callerId, UUID id) {
        Path file = callerDirectory(callerId).resolve(id + ".png");
        if (!Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
        }
        return new FileSystemResource(file);
    }

    /** Stable ownership on disk; caller ids never become raw path components. No legacy-root fallback. */
    private Path callerDirectory(String callerId) {
        Assert.hasText(callerId, "An authenticated caller id is required.");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(callerId.getBytes(StandardCharsets.UTF_8));
            return outputDir.resolve("caller-" + HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    public String describe(String prompt, MultipartFile file) {
        return visionClient.prompt()
                .user(u -> u.text(prompt).media(MimeType.valueOf(file.getContentType()), file.getResource()))
                .call()
                .content();
    }
}

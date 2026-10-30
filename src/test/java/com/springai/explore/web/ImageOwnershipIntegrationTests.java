package com.springai.explore.web;

import com.springai.explore.config.AppProperties;
import com.springai.explore.service.ImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Real HTTP, authentication, service and disk; only the paid image provider is replaced. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.api-keys.alice=alice-image-key",
        "app.security.api-keys.bob=bob-image-key"
})
@ActiveProfiles("test")
class ImageOwnershipIntegrationTests {

    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aX1sAAAAASUVORK5CYII=";
    private static final byte[] PNG = Base64.getDecoder().decode(PNG_BASE64);
    private static final Path OUTPUT = Path.of("target", "image-ownership-" + UUID.randomUUID()).toAbsolutePath();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    int port;

    @MockitoBean
    ImageModel imageModel;

    @Autowired
    AppProperties properties;

    @DynamicPropertySource
    static void imageDirectory(DynamicPropertyRegistry registry) {
        registry.add("app.image.output-dir", () -> OUTPUT.toString());
    }

    @BeforeEach
    void providerReturnsAnImage() {
        when(imageModel.call(any(ImagePrompt.class))).thenReturn(new ImageResponse(
                List.of(new ImageGeneration(new Image(null, PNG_BASE64)))));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alice", "bob"})
    void onlyTheCreatorCanDownloadTheGeneratedImage(String owner) throws Exception {
        UUID id = generate(owner, "");
        var ownDownload = download(owner, id);
        assertThat(ownDownload.statusCode()).isEqualTo(200);
        assertThat(ownDownload.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(ownDownload.body()).isEqualTo(PNG);

        String other = owner.equals("alice") ? "bob" : "alice";
        assertNotFound(download(other, id));
    }

    @Test
    void callerQueryParameterCannotSelectAnotherOwnersStorage() throws Exception {
        UUID id = generate("alice", "?callerId=bob");
        assertThat(download("alice", id).statusCode()).isEqualTo(200);
        assertNotFound(download("bob", id));
    }

    @Test
    void anotherOwnersImageAndAMissingImageHaveTheSamePublicError() throws Exception {
        UUID id = generate("alice", "");
        var forbidden = download("bob", id);
        var missing = download("bob", UUID.randomUUID());
        assertNotFound(forbidden);
        assertNotFound(missing);
        assertThat(JSON.readTree(forbidden.body()).get("detail").asText())
                .isEqualTo(JSON.readTree(missing.body()).get("detail").asText());
    }

    @Test
    void legacyUnownedFilesAreNotServedToAnyCaller() throws Exception {
        UUID id = UUID.randomUUID();
        Files.createDirectories(OUTPUT);
        Files.write(OUTPUT.resolve(id + ".png"), PNG);
        assertNotFound(download("alice", id));
        assertNotFound(download("bob", id));
    }

    @Test
    void unauthenticatedRequestsNeverReachTheImageProvider() throws Exception {
        assertThat(request("POST", "/api/v1/images", null, "{\"prompt\":\"a dot\"}").statusCode()).isEqualTo(401);
        assertThat(request("GET", "/api/v1/images/" + UUID.randomUUID(), null, null).statusCode()).isEqualTo(401);
        verifyNoInteractions(imageModel);
    }

    @Test
    void ownershipDoesNotDependOnAnInMemoryIndex() throws Exception {
        UUID id = generate("alice", "");
        var recreated = new ImageService(imageModel, mock(ChatClient.class), properties);
        assertThat(recreated.load("alice", id).getContentAsByteArray()).isEqualTo(PNG);
        assertNotFound(download("bob", id));
    }

    @Test
    void pathLikeCallerIdsCannotEscapeTheOutputDirectory() throws Exception {
        var service = new ImageService(imageModel, mock(ChatClient.class), properties);
        String caller = "../../outside";
        UUID id = service.generate(caller, "a dot").id();
        Path stored = service.load(caller, id).getFile().toPath();
        assertThat(stored.getParent().getParent()).isEqualTo(OUTPUT);
        assertThat(stored.getParent().getFileName().toString()).matches("caller-[0-9a-f]{64}");
        assertThat(service.load(caller, id).getContentAsByteArray()).isEqualTo(PNG);
    }

    private UUID generate(String owner, String query) throws Exception {
        var response = request("POST", "/api/v1/images" + query, owner + "-image-key", "{\"prompt\":\"a dot\"}");
        assertThat(response.statusCode()).isEqualTo(200);
        return UUID.fromString(JSON.readTree(response.body()).get("id").asText());
    }

    private HttpResponse<byte[]> download(String caller, UUID id) throws Exception {
        return request("GET", "/api/v1/images/" + id, caller + "-image-key", null);
    }

    private HttpResponse<byte[]> request(String method, String path, String key, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (key != null) {
            builder.header("X-API-Key", key);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static void assertNotFound(HttpResponse<byte[]> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
        assertThat(JSON.readTree(response.body()).get("detail").asText()).isEqualTo("Image not found.");
    }
}

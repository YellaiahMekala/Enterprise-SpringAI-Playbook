package com.springai.explore.web;

import com.springai.explore.service.AudioService;
import com.springai.explore.service.ChatService;
import com.springai.explore.service.ImageService;
import com.springai.explore.service.ModelResponseException;
import com.springai.explore.web.dto.ChatReply;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Security, validation and error-contract tests through the real filter chain, with the AI layer mocked. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiWebTests {

    private static final String KEY = "test-api-key";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChatService chatService;
    @MockitoBean
    ImageService imageService;
    @MockitoBean
    AudioService audioService;

    // ---- security ----

    @Test
    void requestWithoutApiKeyIsRejectedAsProblemJson() throws Exception {
        mvc.perform(post("/api/v1/chat").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(chatService);
    }

    @Test
    void wrongApiKeyIsRejected() throws Exception {
        mvc.perform(post("/api/v1/chat").header("X-API-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthProbeIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }

    // ---- happy path: caller id comes from the credential ----

    @Test
    void chatUsesTheCallerIdOfTheApiKeyAsConversationId() throws Exception {
        when(chatService.chat("demo-user", "hi")).thenReturn(new ChatReply("hello", "gpt-test"));

        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("hello"))
                .andExpect(jsonPath("$.model").value("gpt-test"));
        verify(chatService).chat("demo-user", "hi");
    }

    // ---- validation ----

    @Test
    void blankPromptIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(chatService);
    }

    @Test
    void oversizedPromptIsABadRequest() throws Exception {
        String body = "{\"prompt\":\"" + "a".repeat(4001) + "\"}";
        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void movieParametersAreValidated() throws Exception {
        mvc.perform(get("/api/v1/recommendations/movie").header("X-API-Key", KEY)
                        .param("type", "comedy").param("year", "20x0").param("lang", "English"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(chatService);
    }

    @Test
    void imageIdMustBeAUuid() throws Exception {
        mvc.perform(get("/api/v1/images/../../etc/passwd").header("X-API-Key", KEY))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/images/not-a-uuid").header("X-API-Key", KEY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(imageService);
    }

    // ---- uploads ----

    @Test
    void emptyImageUploadIsRejectedBeforeAnyModelCall() throws Exception {
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        mvc.perform(multipart("/api/v1/images/descriptions").file(file).param("prompt", "describe")
                        .header("X-API-Key", KEY))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(imageService);
    }

    @Test
    void unsupportedAudioTypeIsRejectedWith415() throws Exception {
        var file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/v1/audio/transcriptions").file(file).header("X-API-Key", KEY))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(audioService);
    }

    // ---- error contract ----

    @Test
    void emptyModelResponseMapsTo502() throws Exception {
        when(chatService.chat(any(), any())).thenThrow(new ModelResponseException("The model returned an empty response."));

        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.detail").value("The model returned an empty response."));
    }

    @Test
    void unexpectedFailuresNeverLeakInternals() throws Exception {
        when(chatService.chat(any(), any())).thenThrow(new IllegalStateException("jdbc:mysql://internal-db-host connection details"));

        mvc.perform(post("/api/v1/chat").header("X-API-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"hi\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-db-host"))));
    }
}

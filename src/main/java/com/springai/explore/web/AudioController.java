package com.springai.explore.web;

import com.springai.explore.service.AudioService;
import com.springai.explore.web.dto.SpeechRequest;
import com.springai.explore.web.dto.TranscriptionReply;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/audio")
public class AudioController {

    private final AudioService audioService;
    private final UploadValidator uploads;

    public AudioController(AudioService audioService, UploadValidator uploads) {
        this.audioService = audioService;
        this.uploads = uploads;
    }

    /** Transcribes the audio, then translates the transcript into {@code targetLanguage} (default Telugu). */
    @PostMapping(value = "/transcriptions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionReply transcribe(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "Telugu") @Pattern(regexp = "[\\p{L} ]{2,30}") String targetLanguage) {
        uploads.requireAudio(file);
        return audioService.transcribeAndTranslate(file, targetLanguage);
    }

    @PostMapping(value = "/speech", produces = "audio/mpeg")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeechRequest request) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(audioService.speak(request.text()));
    }
}

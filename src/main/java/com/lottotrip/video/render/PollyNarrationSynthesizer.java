package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.VoiceId;

/** Seoyeon(ko-KR) neural 보이스로 대사를 mp3로 합성한다. */
@Component
public class PollyNarrationSynthesizer implements NarrationSynthesizer {

    private final PollyClient pollyClient;

    public PollyNarrationSynthesizer(PollyClient pollyClient) {
        this.pollyClient = pollyClient;
    }

    @Override
    public Path synthesize(String script) throws IOException {
        SynthesizeSpeechRequest request = SynthesizeSpeechRequest.builder()
                .text(script)
                .voiceId(VoiceId.SEOYEON)
                .engine(Engine.NEURAL)
                .outputFormat(OutputFormat.MP3)
                .build();

        Path tempFile = Files.createTempFile("narration-", ".mp3");
        try (ResponseInputStream<SynthesizeSpeechResponse> audioStream = pollyClient.synthesizeSpeech(request)) {
            Files.copy(audioStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }
}

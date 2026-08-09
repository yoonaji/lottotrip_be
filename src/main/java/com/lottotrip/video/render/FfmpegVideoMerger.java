package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 로컬 ffmpeg 바이너리를 그대로 shell-out해서 클립 병합 + 내레이션 합성을 처리한다.
 * 컨테이너 이미지(Dockerfile)에 ffmpeg가 설치돼 있어야 하고, 로컬 실행 시에도 PATH에 ffmpeg가 있어야 한다.
 * 실제 ffmpeg 실행 환경에서 검증되지 않은 커맨드라 클립 포맷에 따라 플래그 조정이 필요할 수 있다.
 */
@Component
public class FfmpegVideoMerger implements VideoMerger {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);

    @Override
    public Path merge(List<Path> clipFiles, Path narrationAudio) throws IOException, InterruptedException {
        Path concatenated = concatenateClips(clipFiles);
        try {
            return overlayNarration(concatenated, narrationAudio);
        } finally {
            Files.deleteIfExists(concatenated);
        }
    }

    private Path concatenateClips(List<Path> clipFiles) throws IOException, InterruptedException {
        Path listFile = Files.createTempFile("concat-list-", ".txt");
        String listContent = clipFiles.stream()
                .map(path -> "file '%s'".formatted(path.toAbsolutePath()))
                .collect(Collectors.joining("\n"));
        Files.writeString(listFile, listContent);

        Path output = Files.createTempFile("merged-", ".mp4");
        try {
            runFfmpeg(List.of(
                    "ffmpeg", "-y",
                    "-f", "concat", "-safe", "0", "-i", listFile.toString(),
                    "-c:v", "libx264", "-c:a", "aac",
                    output.toString()
            ));
        } finally {
            Files.deleteIfExists(listFile);
        }
        return output;
    }

    private Path overlayNarration(Path video, Path narrationAudio) throws IOException, InterruptedException {
        Path output = Files.createTempFile("final-", ".mp4");
        runFfmpeg(List.of(
                "ffmpeg", "-y",
                "-i", video.toString(),
                "-i", narrationAudio.toString(),
                "-map", "0:v", "-map", "1:a",
                "-c:v", "copy", "-c:a", "aac",
                "-shortest",
                output.toString()
        ));
        return output;
    }

    private void runFfmpeg(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("ffmpeg 실행 시간 초과: " + command);
        }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IOException("ffmpeg 실패(exit=%d): %s".formatted(process.exitValue(), output));
        }
    }
}

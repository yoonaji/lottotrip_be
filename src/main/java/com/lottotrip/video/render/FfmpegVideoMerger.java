package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 로컬 ffmpeg 바이너리를 그대로 shell-out해서 클립별 자막 번인 + 병합 + 내레이션 합성을 처리한다.
 * 컨테이너 이미지(Dockerfile)에 ffmpeg와 한글 폰트(fonts-noto-cjk)가 설치돼 있어야 하고,
 * 로컬 실행 시에도 PATH에 ffmpeg + fontconfig에 한글 폰트가 잡혀 있어야 한다.
 * 실제 ffmpeg 실행 환경에서 검증되지 않은 커맨드라 클립 포맷/자막 렌더링은 실사용 전에 반드시 직접 확인 필요.
 */
@Component
public class FfmpegVideoMerger implements VideoMerger {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(5);
    private static final String CAPTION_FONT = "Noto Sans CJK KR"; // fontconfig family name (fontfile 경로 하드코딩 대신)

    @Override
    public Path merge(List<CaptionedClip> clips, Path narrationAudio) throws IOException, InterruptedException {
        List<Path> captionedFiles = new ArrayList<>();
        try {
            for (CaptionedClip clip : clips) {
                captionedFiles.add(burnCaption(clip));
            }
            Path concatenated = concatenateClips(captionedFiles);
            try {
                return overlayNarration(concatenated, narrationAudio);
            } finally {
                Files.deleteIfExists(concatenated);
            }
        } finally {
            for (Path file : captionedFiles) {
                Files.deleteIfExists(file);
            }
        }
    }

    private Path burnCaption(CaptionedClip clip) throws IOException, InterruptedException {
        if (!StringUtils.hasText(clip.caption())) {
            // 자막 없는 클립은 그대로 복사만 해서 이후 단계와 파일 생명주기를 동일하게 맞춘다.
            Path copy = Files.createTempFile("captioned-", ".mp4");
            Files.copy(clip.file(), copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return copy;
        }

        Path output = Files.createTempFile("captioned-", ".mp4");
        String drawtext = "drawtext=font='%s':text='%s':fontsize=42:fontcolor=white:borderw=3:bordercolor=black:x=(w-text_w)/2:y=h-th-60"
                .formatted(CAPTION_FONT, escapeForDrawtext(clip.caption()));

        runFfmpeg(List.of(
                "ffmpeg", "-y",
                "-i", clip.file().toString(),
                "-vf", drawtext,
                "-c:v", "libx264", "-c:a", "copy",
                output.toString()
        ));
        return output;
    }

    private String escapeForDrawtext(String text) {
        String singleLine = text.replace("\n", " ").replace("\r", " ");
        return singleLine
                .replace("\\", "\\\\")
                .replace(":", "\\:")
                .replace("%", "\\%")
                .replace("'", "’"); // 작은따옴표는 필터 문법과 충돌이 잦아 오른쪽 홑따옴표(’)로 치환
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

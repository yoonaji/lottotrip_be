package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface VideoMerger {

    Path merge(List<CaptionedClip> clips, Path narrationAudio) throws IOException, InterruptedException;

    /** caption이 null/blank면 그 클립은 자막 없이 그대로 들어간다. */
    record CaptionedClip(Path file, String caption) {
    }
}

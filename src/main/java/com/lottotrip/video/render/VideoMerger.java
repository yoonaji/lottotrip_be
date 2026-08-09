package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface VideoMerger {

    Path merge(List<Path> clipFiles, Path narrationAudio) throws IOException, InterruptedException;
}

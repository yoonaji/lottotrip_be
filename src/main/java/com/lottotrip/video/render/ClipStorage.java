package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Path;

public interface ClipStorage {

    Path download(String clipUrl) throws IOException;

    String uploadFinalVideo(Path localFile, String jobId) throws IOException;
}

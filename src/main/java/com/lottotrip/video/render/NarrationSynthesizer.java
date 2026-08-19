package com.lottotrip.video.render;

import java.io.IOException;
import java.nio.file.Path;

public interface NarrationSynthesizer {

    Path synthesize(String script) throws IOException;
}

package com.lottotrip.video.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RenderRequest(
        @NotEmpty @Valid List<ClipItem> clips,
        @NotBlank String ttsScript,
        String narrationType
) {

    public record ClipItem(
            @NotBlank String clipUrl,
            @NotNull Integer order
    ) {
    }
}

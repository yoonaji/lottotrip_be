package com.lottotrip.video.render;

/** job.failReason에 그대로 저장되는 코드(TTS_SYNTHESIS_FAILED 등)를 들고 다니는 내부 예외. */
class RenderStageException extends RuntimeException {

    private final String reasonCode;

    RenderStageException(String reasonCode, Throwable cause) {
        super(reasonCode, cause);
        this.reasonCode = reasonCode;
    }

    String getReasonCode() {
        return reasonCode;
    }
}

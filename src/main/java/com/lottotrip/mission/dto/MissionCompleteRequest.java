package com.lottotrip.mission.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 미션 완료 요청. (tour_api_erd.md 4-5)
 *
 * @param latitude  위도. -90 ~ 90
 * @param longitude 경도. -180 ~ 180
 */
public record MissionCompleteRequest(

        /*
         * Double로 받는 이유: double(기본형)으로 두면 값을 안 보냈을 때 0.0이 들어가
         * "적도-그리니치 앞바다"라는 멀쩡한 좌표가 되어 버린다. @NotNull이 걸리지도 않는다.
         */
        @NotNull
        @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
        Double latitude,

        @NotNull
        @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
        Double longitude) {
}

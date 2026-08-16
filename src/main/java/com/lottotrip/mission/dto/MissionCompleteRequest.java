package com.lottotrip.mission.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * 미션 완료 요청. (tour_api_erd.md 4-5)
 *
 * **"완료했다"를 그대로 받지 않고 좌표를 받는다.** 완료 여부를 클라이언트가 정하면
 * 집에서도 모든 미션을 완료할 수 있다. 좌표를 받아 서버가 장소와의 거리를 재고(8-1),
 * 반경 안일 때만 완료로 인정한다.
 *
 * ⚠️ **좌표 자체를 위조하는 것은 막지 못한다.** 앱이 보내는 값이라 마음먹으면 꾸밀 수 있다.
 * 막으려면 사진 인증이나 단말 무결성 검사가 필요한데, 어느 쪽도 아직 정해지지 않았다(미확정 항목 8-1).
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

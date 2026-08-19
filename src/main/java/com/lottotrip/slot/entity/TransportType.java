package com.lottotrip.slot.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 이동수단. ERD의 `transport_type` enum에 대응한다.
 *
 * 검색 반경을 enum이 직접 갖는다. ERD 2-2는 서비스에 `resolveRadiusKm(String)`을 두는
 * 형태로 예시를 적어 두었지만, 그렇게 하면 "이동수단"과 "그 이동수단의 반경"이 서로 다른 곳에
 * 놓여 한쪽만 바뀔 수 있다. `trip_sessions`는 두 값을 함께 저장하므로 어긋나면 안 된다.
 * 동작(walk → 10km, car → 30km)은 결정 2 그대로다.
 */
@Getter
@AllArgsConstructor
public enum TransportType {

    WALK(10),
    CAR(30);

    /**
     * 이 이동수단으로 움직인다고 볼 때 탐색할 반경(km). (tour_api_erd.md 결정 2)
     *
     * **반경 값이 이 두 숫자에만 존재한다.** 2026-08-13 회의에서 1/20km → 10/30km로 올릴 때
     * 고친 곳도 여기뿐이다. 바꾼 이유는 실측상 강릉 시내 1km 안에 장소가 7건(관광지는 1건)밖에 없어
     * `NO_PLACE_FOUND`가 사실상 기본 응답이 될 상황이었기 때문이다.
     *
     * `WALK`은 도보가 아니라 **택시 기준**으로 재해석한 값이다.
     * `CAR`는 "10km 이상은 자차"라는 회의 결론에 따라 10/20/30 중 가장 넓은 값을 택했다.
     */
    private final int searchRadiusKm;

    /**
     * 요청의 문자열 값(`"walk"` / `"car"`)을 이동수단으로 바꾼다.
     *
     * 정의되지 않은 값이면 400으로 거절한다. 여기서 걸러 내지 않으면 조건에 맞는 장소가
     * 없다는 뜻의 404(`NO_PLACE_FOUND`)가 나가서, 잘못 보낸 쪽이 원인을 알기 어렵다.
     */
    public static TransportType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }
}

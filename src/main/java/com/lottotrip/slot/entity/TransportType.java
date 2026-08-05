package com.lottotrip.slot.entity;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 이동수단. ERD의 {@code transport_type} enum에 대응한다.
 *
 * <p>검색 반경을 enum이 직접 갖는다. ERD 2-2는 서비스에 {@code resolveRadiusKm(String)}을 두는
 * 형태로 예시를 적어 두었지만, 그렇게 하면 "이동수단"과 "그 이동수단의 반경"이 서로 다른 곳에
 * 놓여 한쪽만 바뀔 수 있다. {@code trip_sessions}는 두 값을 함께 저장하므로 어긋나면 안 된다.
 * 동작(walk → 1km, car → 20km)은 결정 2 그대로다.
 */
@Getter
@AllArgsConstructor
public enum TransportType {

    WALK(1),
    CAR(20);

    /** 이 이동수단으로 움직인다고 볼 때 탐색할 반경(km). (tour_api_erd.md 결정 2) */
    private final int searchRadiusKm;

    /**
     * 요청의 문자열 값({@code "walk"} / {@code "car"})을 이동수단으로 바꾼다.
     *
     * <p>정의되지 않은 값이면 400으로 거절한다. 여기서 걸러 내지 않으면 조건에 맞는 장소가
     * 없다는 뜻의 404({@code NO_PLACE_FOUND})가 나가서, 잘못 보낸 쪽이 원인을 알기 어렵다.
     */
    public static TransportType from(String value) {
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }
}

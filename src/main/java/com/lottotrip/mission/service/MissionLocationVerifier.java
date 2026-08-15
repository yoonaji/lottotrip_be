package com.lottotrip.mission.service;

import com.lottotrip.common.geo.Haversine;
import com.lottotrip.place.entity.Place;
import org.springframework.stereotype.Component;

/**
 * 미션을 완료할 만큼 그 장소에 가까이 있었는지 판정한다. (roadmap 8-1, tour_api_erd.md 4-5)
 *
 * <h2>이 클래스가 판정하는 것은 "거기 있었나"뿐이다</h2>
 * 미션 문구가 무엇이든 실제 검사는 <b>반경 안에 있는가</b> 하나다. 그래서 미션 생성 쪽에서
 * <b>"도착하면 달성되는 것"만 만들도록</b> 맞춰 둔다. 문구가 "일몰 사진 찍기"인데 판정은
 * 위치뿐이면, 해가 중천일 때 서 있기만 해도 완료되어 <b>사용자가 속았다고 느낀다.</b>
 *
 * <p>사진 인증을 붙일지는 아직 정해지지 않았다(미확정 항목 8-1). {@code user_missions}의
 * media 컬럼이 nullable이라 나중에 추가해도 스키마는 그대로 쓸 수 있다.
 */
@Component
public class MissionLocationVerifier {

    /**
     * 인증으로 인정하는 최대 거리(km). <b>500m</b> (사용자 결정 2026-08-16).
     *
     * <p><b>왜 0이 아니라 여유를 두나.</b> 두 가지가 겹친다.
     * <ul>
     *   <li><b>휴대폰 GPS는 정확하지 않다.</b> 트인 곳에서도 수십 m, 건물 사이나 실내에서는
     *       100m 넘게 어긋난다. 여유가 없으면 <b>진짜 그 자리에 서 있는데도 인증이 실패한다.</b></li>
     *   <li><b>장소는 점이 아니다.</b> TourAPI가 주는 좌표는 대표 지점 하나뿐이라,
     *       해수욕장·공원처럼 넓은 곳은 안에 있어도 중심에서 수백 m 떨어질 수 있다.</li>
     * </ul>
     *
     * <p>반대로 너무 넓히면(예: 5km) 지나가기만 해도 완료되어 미션이 의미를 잃는다.
     * 500m는 그 사이에서 고른 값이고, <b>이 상수 하나만 고치면 조정된다.</b>
     */
    static final double ALLOWED_RADIUS_KM = 0.5;

    /**
     * 이 좌표가 장소의 허용 반경 안인가.
     *
     * <p><b>경계는 포함이다</b>(500m 정확히도 인증). 사용자가 딱 그 거리에 서 있을 일은 드물지만,
     * 정해 두지 않으면 부동소수점 오차에 따라 결과가 갈린다.
     *
     * <p><b>예외를 던지지 않고 참·거짓만 돌려준다.</b> "인증 실패를 어떤 에러로 답할지"는
     * API의 문제이고, 여기는 거리만 잰다. 나눠 두면 나중에 "실패했지만 얼마나 가까웠는지 알려주기"
     * 같은 요구가 생겨도 이 클래스는 그대로 쓸 수 있다.
     */
    public boolean isAtPlace(Place place, double latitude, double longitude) {
        double distanceKm = Haversine.distanceKm(
                place.getLatitude(), place.getLongitude(), latitude, longitude);
        return distanceKm <= ALLOWED_RADIUS_KM;
    }
}

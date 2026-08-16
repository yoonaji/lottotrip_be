package com.lottotrip.place.service;

import com.lottotrip.place.dto.PlaceDetail;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiDetailItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 뽑힌 장소의 세부 정보를 실시간으로 받아 온다. (roadmap 5-10)
 *
 * **공모전 규정(결정 7 — 오픈API 실시간 호출 필수)을 만족시키는 호출 중 하나다.**
 * 한때는 유일한 지점이었으나(결정 10에서는 추첨이 DB 조회였다),
 * 결정 12로 `draw`도 실시간 호출을 하게 되어 **지금은 두 곳이다.**
 * 부를 수 있는 이유는 `places.content_id`를 담아 두었기 때문이다(5-7).
 *
 * ## 이 서비스의 성격
 * **정보를 더해 주는 역할이지, 없으면 안 되는 역할이 아니다.**
 * 공공데이터포털이 멈추거나 할당량이 떨어져도 사용자는 자기가 뽑은 장소를 볼 수 있어야 한다.
 * 그래서 바깥 호출의 실패를 **여기서 삼키고** DB에 있는 정보만이라도 돌려준다.
 *
 * `GET /slot/results/{slotId}`가 이것을 쓴다(6-7에서 연결).
 *
 * ⚠️ `draw`에는 이 폴백이 없다. 거기는 실패하면 **뽑을 후보 자체가 없어서** 응답할 것이 남지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceDetailService {

    /**
     * TourAPI의 홈페이지 값에서 주소만 뽑는 패턴.
     *
     * `<a href="https://...">강릉시청</a>` 형태의 **HTML로 온다.**
     * 그대로 내려보내면 앱이 링크로 쓸 수 없어서 주소만 걷어낸다.
     */
    private static final Pattern HREF = Pattern.compile("href=[\"']([^\"']+)[\"']");

    private final TourApiClient tourApiClient;

    /**
     * DB에 담아 둔 장소 정보에 실시간 세부 정보를 얹어 돌려준다.
     * -> DB에서 가져온 장소 정보 + 실시간 세부정보 api 조회
     *
     * 실패해도 예외를 던지지 않는다. 받는 쪽이 매번 try-catch를 쓰게 하면 언젠가 빠뜨리고,
     * 그러면 공공 API 장애가 슬롯 조회 전체를 500으로 만든다.
     */
    public PlaceDetail describe(Place place) {
        PlaceDetail stored = PlaceDetail.storedOnly(place);

        // 코드가 없으면 무엇을 조회할지 알 수 없다. 빈 값으로 부르면 할당량만 깎인다.
        if (place.getContentId() == null || place.getContentId().isBlank()) {
            log.debug("장소 코드가 없어 세부조회를 건너뜁니다 — placeId {}", place.getId());
            return stored;
        }

        return fetchDetail(place.getContentId())
                .map(detail -> stored.withLiveDetail(detail.overview(), homepageUrl(detail.homepage())))
                .orElse(stored);
    }

    /**
     * 바깥 호출을 감싸 실패를 삼킨다.
     *
     * {@link Exception}을 통째로 잡는 것은 보통 피해야 하지만 여기서는 의도적이다.
     * 잡는 목적이 "무엇이 잘못됐는지 구분"이 아니라 **"어떤 이유로든 세부 정보 없이 계속 간다"**이기 때문이다.
     * 타임아웃·연결 끊김·인증키 오류·응답 형식 변경 중 무엇이 와도 사용자는 장소를 볼 수 있어야 한다.
     * 대신 원인을 잃지 않도록 로그에 남긴다.
     */
    private Optional<TourApiDetailItem> fetchDetail(String contentId) {
        try {
            return tourApiClient.fetchDetailCommon(contentId);
        } catch (Exception e) {
            log.warn("장소 세부조회 실패 — contentId {} ({}). DB 정보만 응답합니다",
                    contentId, e.getMessage());
            return Optional.empty();
        }
    }

    /** `<a href="...">이름</a>`에서 주소만 뽑는다. 태그가 없으면 값을 그대로 쓴다. */
    private String homepageUrl(String homepage) {
        if (homepage == null || homepage.isBlank()) {
            return null;
        }
        Matcher matcher = HREF.matcher(homepage);
        return matcher.find() ? matcher.group(1) : homepage.trim();
    }
}

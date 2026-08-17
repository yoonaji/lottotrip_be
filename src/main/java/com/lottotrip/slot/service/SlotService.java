package com.lottotrip.slot.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.service.MissionMatcher;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.service.PlaceUpserter;
import com.lottotrip.place.service.RealtimePlaceFinder;
import com.lottotrip.place.tourapi.TourApiPlaceItem;
import com.lottotrip.place.tourapi.TourApiService;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.dto.SlotDrawResponse;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.slot.entity.TransportType;
import com.lottotrip.slot.entity.TripSession;
import com.lottotrip.slot.repository.TripSessionRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 슬롯 큐레이팅. (roadmap 6단계)
 *
 * 세션 확보(6-1) → 반경(6-2) → 실시간 조회·추첨(6-11) → 장소 저장(6-12) → 미션 매칭(6-5)을 엮어
 * `POST /api/v1/slot/draw`(6-13)를 이룬다. 각 단계는 별도 클래스가 맡고
 * 이 클래스는 **순서와 실패 처리**만 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    /**
     * 세션을 같은 것으로 볼 수 있는 시간(시간 단위). (tour_api_erd.md 결정 1)
     *
     * "하루 여행 한 번"을 묶는 느슨한 기준이다. 정확한 결과값의 근원이 아니라
     * **같은 회원이 12시간 안에 슬롯을 여러 번 돌렸다**를 이어 주는 그릇이다.
     */
    private static final int SESSION_VALID_HOURS = 12;

    private final TripSessionRepository tripSessionRepository;
    private final UserRepository userRepository;
    private final RealtimePlaceFinder realtimePlaceFinder;
    private final PlaceUpserter placeUpserter;
    private final MissionMatcher missionMatcher;
    private final SavedSlotRepository savedSlotRepository;

    /**
     * 슬롯을 돌린다. 이 도메인의 핵심 흐름. (tour_api_erd.md 2-4, roadmap 6-13)
     *
     * 세션 확보 → 반경 결정 → TourAPI 실시간 조회+추첨 → places 저장 → 미션 확보
     *          → saved_slots 저장 → 응답 조립
     *
     * 공공 API를 실시간으로 부른다
     * 거리 반경은 세션이 들고 있는 값을 쓴다.
     * `accessible`·`contentTypeId`는 세션이 아니라 요청 값을 쓴다.
     * 세션에 담는 값이 아니라 이번 추첨에만 적용되는 조건이기 때문
     *
     * @throws CustomException : 반경 안에 후보가 없으면 {@link ErrorCode#NO_PLACE_FOUND}
     */
    @Transactional
    public SlotDrawResponse draw(Long userId, SlotDrawRequest request) {
        TripSession session = getOrCreateActiveSession(userId, request);

        TourApiService service = request.accessible()
                ? TourApiService.ACCESSIBLE
                : TourApiService.KOREAN;

        //받은 반경 내 장소 TourApi로 호출
        Optional<TourApiPlaceItem> picked = realtimePlaceFinder.drawOne(
                service,
                session.getAccommodationLatitude(),
                session.getAccommodationLongitude(),
                session.getSearchRadiusKm(),
                request.contentTypeId());

        if (picked.isEmpty()) {
            log.debug("반경 내 후보 없음 — 기준 ({}, {}) / 반경 {}km / 무장애 {} / 종류 {}",
                    session.getAccommodationLatitude(), session.getAccommodationLongitude(),
                    session.getSearchRadiusKm(), request.accessible(), request.contentTypeId());
            throw new CustomException(ErrorCode.NO_PLACE_FOUND);
        }

        TourApiPlaceItem item = picked.get();
        Place place = placeUpserter.upsert(item);

        // 미션은 곁들이는 정보다. 확보하지 못해도 장소는 이미 뽑혔으므로 응답을 실패시키지 않는다.
        Mission mission = missionMatcher.matchFor(place).orElse(null);

        // 미션을 함께 남긴다. 남기지 않으면 슬롯 조회 시 다른 미션을 돌려준다.
        SavedSlot savedSlot = savedSlotRepository.save(SavedSlot.create(session, place, mission));

        return SlotDrawResponse.of(savedSlot.getId(), place, distanceKmOf(item), item.thumbnailUrl(), mission);
    }

    /**
     * 기준 좌표로부터의 거리(km).
     * 우리가 계산하지 않는다. 좌표 기반 조회의 응답에 `dist`(미터)가 함께 옴
     */
    private Double distanceKmOf(TourApiPlaceItem item) {
        Double meters = item.distanceMeters();
        return meters == null ? null : Math.round(meters / 100.0) / 10.0;
    }

    /**
     * 쓸 수 있는 세션을 찾고, 없으면 만든다. 슬롯 API의 진입점.
     *
     * 프론트는 세션을 모른다. `sessionId`를 보내지 않으므로 서버가 매 요청마다
     * "이 회원의 마지막 세션이 아직 12시간 이내인가"를 판단한다. 프론트에 세션을 들려 보내면
     * 앱을 껐다 켜거나 기기를 바꿀 때마다 세션이 끊기고, 그 관리 책임이 클라이언트로 넘어간다.
     *
     * 재사용할 때 세션 값을 갱신하지 않는다. 세션에 담긴 예산·이동수단·좌표는
     * 그 세션의 첫 슬롯 기준 참고값이다. 두 번째 슬롯을 다른 조건으로 돌려도 덮어쓰지 않는다 —
     * 각 슬롯의 실제 조건은 `saved_slots`가 따로 보존하므로 여기서 덮어쓰면
     * "이 세션이 무슨 조건으로 시작했는지"를 잃을 뿐이다. {@link TripSession}에 값을 바꾸는
     * 메서드가 아예 없는 것도 같은 이유다.
     */
    @Transactional
    public TripSession getOrCreateActiveSession(Long userId, SlotDrawRequest request) {
        return tripSessionRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .filter(this::isStillActive)
                .orElseGet(() -> createSession(userId, request));
    }

    /**
     * 이 세션이 아직 유효한지 판단.
     * 경계(`정확히 12시간`)는 만료로 본다.
     */
    private boolean isStillActive(TripSession session) {
        return session.getCreatedAt().isAfter(LocalDateTime.now().minusHours(SESSION_VALID_HOURS));
    }

    /**
     * 새 세션을 만든다..
     */
    private TripSession createSession(Long userId, SlotDrawRequest request) {
        // 이동수단을 먼저 해석한다. 잘못된 값이면 회원을 조회하기 전에 400으로 끝난다.
        TransportType transport = TransportType.from(request.transport());

        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 없는 회원으로 세션을 만들면
        // user_id FK가 가리킬 곳이 없어 저장 시점에 터진다. 여기서 인증 문제로 돌려준다.
        // ⚠️ 탈퇴는 소프트 삭제라 findById로는 탈퇴자가 통과한다(9-5).
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 회원의 슬롯 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });

        return tripSessionRepository.save(TripSession.create(
                user,
                BudgetLevel.from(request.budget()),
                transport,
                request.latitude(),
                request.longitude()));
    }
}

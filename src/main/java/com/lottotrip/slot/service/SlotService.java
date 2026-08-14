package com.lottotrip.slot.service;

import com.lottotrip.common.enums.BudgetLevel;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.service.MissionMatcher;
import com.lottotrip.place.dto.PlaceCandidate;
import com.lottotrip.place.entity.Place;
import com.lottotrip.place.entity.PlaceMedia;
import com.lottotrip.place.repository.PlaceMediaRepository;
import com.lottotrip.place.service.NearbyPlaceFinder;
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
import java.util.List;

/**
 * 슬롯 큐레이팅. (roadmap 6단계)
 *
 * <p>세션 확보(6-1) → 반경(6-2) → 후보 조회(6-3) → 추첨(6-4) → 미션 매칭(6-5)을 엮어
 * {@code POST /api/v1/slot/draw}(6-6)를 이룬다. 각 단계는 별도 클래스가 맡고
 * 이 클래스는 <b>순서와 실패 처리</b>만 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotService {

    /**
     * 세션을 같은 것으로 볼 수 있는 시간(시간 단위). (tour_api_erd.md 결정 1)
     *
     * <p>"하루 여행 한 번"을 묶는 느슨한 기준이다. 정확한 결과값의 근원이 아니라
     * <b>같은 회원이 12시간 안에 슬롯을 여러 번 돌렸다</b>를 이어 주는 그릇이다.
     */
    private static final int SESSION_VALID_HOURS = 12;

    private final TripSessionRepository tripSessionRepository;
    private final UserRepository userRepository;
    private final NearbyPlaceFinder nearbyPlaceFinder;
    private final PlaceDrawer placeDrawer;
    private final MissionMatcher missionMatcher;
    private final SavedSlotRepository savedSlotRepository;
    private final PlaceMediaRepository placeMediaRepository;

    /**
     * 슬롯을 돌린다. <b>이 도메인의 핵심 흐름이다.</b> (tour_api_erd.md 2-4)
     *
     * <pre>
     * 세션 확보 → 반경 결정 → DB에서 후보 조회 → 추첨 → saved_slots 저장 → 미션 매칭 → 응답 조립
     * </pre>
     *
     * <p><b>공공 API를 부르지 않는다</b>(결정 10). 후보를 DB에서만 찾으므로 응답이 빠르고,
     * 공공데이터포털이 멈춰도 슬롯은 돌아간다. 실시간 호출은 세부조회(6-7)가 담당한다.
     *
     * <p><b>반경은 세션이 들고 있는 값을 쓴다.</b> 요청의 {@code transport}가 아니라 세션의 것을 보는 이유:
     * 기존 세션을 재사용할 때 세션 값을 갱신하지 않기로 했으므로(결정 1의 A안), 요청 값을 쓰면
     * 세션에 기록된 반경과 실제로 뽑은 반경이 어긋난다.
     *
     * @throws CustomException 반경 안에 후보가 없으면 {@link ErrorCode#NO_PLACE_FOUND}.
     *                         적재가 강원 한정이라 강원 밖 좌표로 돌리면 실제로 이 상황이 된다
     *                         (2026-08-14 사용자 결정 — 폴백 없이 404를 그대로 내보낸다)
     */
    @Transactional
    public SlotDrawResponse draw(Long userId, SlotDrawRequest request) {
        TripSession session = getOrCreateActiveSession(userId, request);

        List<PlaceCandidate> candidates = nearbyPlaceFinder.findWithin(
                session.getAccommodationLatitude(),
                session.getAccommodationLongitude(),
                session.getSearchRadiusKm());

        if (candidates.isEmpty()) {
            log.debug("반경 내 후보 없음 — 기준 ({}, {}) / 반경 {}km",
                    session.getAccommodationLatitude(), session.getAccommodationLongitude(),
                    session.getSearchRadiusKm());
            throw new CustomException(ErrorCode.NO_PLACE_FOUND);
        }

        PlaceCandidate picked = placeDrawer.draw(candidates);
        SavedSlot savedSlot = savedSlotRepository.save(
                SavedSlot.create(session, picked.place()));

        // 미션은 곁들이는 정보다. 확보하지 못해도 장소는 이미 뽑혔으므로 응답을 실패시키지 않는다.
        Mission mission = missionMatcher.matchFor(picked.place()).orElse(null);

        return SlotDrawResponse.of(savedSlot.getId(), picked, thumbnailOf(picked.place()), mission);
    }

    /** 대표 이미지 주소. 실측 채움률이 18%라 대개 비어 있다. */
    private String thumbnailOf(Place place) {
        return placeMediaRepository.findFirstByPlaceIdOrderByIdAsc(place.getId())
                .map(PlaceMedia::getMediaUrl)
                .orElse(null);
    }

    /**
     * 쓸 수 있는 세션을 찾고, 없으면 만든다. <b>슬롯 API의 진입점이다.</b>
     *
     * <p><b>프론트는 세션을 모른다.</b> {@code sessionId}를 보내지 않으므로 서버가 매 요청마다
     * "이 회원의 마지막 세션이 아직 12시간 이내인가"를 판단한다. 프론트에 세션을 들려 보내면
     * 앱을 껐다 켜거나 기기를 바꿀 때마다 세션이 끊기고, 그 관리 책임이 클라이언트로 넘어간다.
     *
     * <p><b>재사용할 때 세션 값을 갱신하지 않는다</b>(결정 1의 A안). 세션에 담긴 예산·이동수단·좌표는
     * <b>그 세션의 첫 슬롯 기준 참고값</b>이다. 두 번째 슬롯을 다른 조건으로 돌려도 덮어쓰지 않는다 —
     * 각 슬롯의 실제 조건은 {@code saved_slots}가 따로 보존하므로 여기서 덮어쓰면
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
     * 이 세션이 아직 유효한가.
     *
     * <p>경계({@code 정확히 12시간})는 <b>만료로 본다.</b> {@code isAfter}는 같은 시각을 거짓으로
     * 보므로 자연스럽게 그렇게 된다. 경계를 열어 두면 "12시간 정각"의 판정이 실행 순간의
     * 밀리초에 따라 달라져, 같은 상황에서 결과가 갈린다.
     */
    private boolean isStillActive(TripSession session) {
        return session.getCreatedAt().isAfter(LocalDateTime.now().minusHours(SESSION_VALID_HOURS));
    }

    /**
     * 새 세션을 만든다.
     *
     * <p>반경을 넘기지 않는다는 점이 중요하다. {@link TripSession}이 이동수단에서 스스로 채우므로
     * "walk인데 30km" 같은 어긋난 세션이 만들어질 수 없다.
     */
    private TripSession createSession(Long userId, SlotDrawRequest request) {
        // 이동수단을 먼저 해석한다. 잘못된 값이면 회원을 조회하기 전에 400으로 끝난다.
        TransportType transport = TransportType.from(request.transport());

        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 없는 회원으로 세션을 만들면
        // user_id FK가 가리킬 곳이 없어 저장 시점에 터진다. 여기서 인증 문제로 돌려준다.
        User user = userRepository.findById(userId)
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

package com.lottotrip.course.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemRemoveResponse;
import com.lottotrip.course.dto.CourseItemsResponse;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.course.entity.TravelCourse;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
import com.lottotrip.mission.entity.Mission;
import com.lottotrip.mission.repository.UserMissionRepository;
import com.lottotrip.place.entity.Place;
import com.lottotrip.slot.entity.SavedSlot;
import com.lottotrip.slot.repository.SavedSlotRepository;
import com.lottotrip.user.entity.User;
import com.lottotrip.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 여행 코스. (roadmap 7단계, tour_api_erd.md 4-4)
 *
 * <p>슬롯로 뽑은 장소 중 마음에 드는 것을 담아 두는 곳이다.
 *
 * <h2>⚠️ 코스를 만드는 API가 명세에 없다</h2>
 * {@code POST /course/items}는 {@code slotId}만 받고, {@code GET /course/items}도 {@code courseId}를
 * 받지 않으며, {@code travel_courses.title}을 채울 입력이 어디에도 없다.
 * 그래서 {@code trip_sessions}(결정 1)와 같은 방식으로 <b>회원당 코스 하나를 서버가 find-or-create</b>한다.
 *
 * <p><b>잠정 결정이다.</b> "여행마다 코스를 따로 만든다"가 되면 코스 생성 API가 새로 필요하고
 * 여기도 바뀐다. Entity는 어느 쪽이든 그대로 쓸 수 있게 돼 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    /**
     * 코스를 자동으로 만들 때 붙이는 이름.
     *
     * <p>{@code travel_courses.title}이 NOT NULL인데 <b>사용자에게 받을 입력이 없다.</b>
     * 이름을 바꾸는 API가 생기기 전까지 쓰는 임시 값이다.
     */
    private static final String DEFAULT_COURSE_TITLE = "내 여행 코스";

    /** 첫 항목의 순번. 사람이 보는 값이라 0이 아니라 1부터 센다. */
    private static final int FIRST_SEQUENCE = 1;

    private final TravelCourseRepository travelCourseRepository;
    private final CourseItemRepository courseItemRepository;
    private final SavedSlotRepository savedSlotRepository;
    private final UserRepository userRepository;
    private final UserMissionRepository userMissionRepository;

    /**
     * 뽑은 슬롯을 코스에 담는다.
     *
     * <pre>
     * 슬롯 확인(내 것인가) → 코스 확보(없으면 생성) → 순번 채번 → 저장
     * </pre>
     *
     * <p><b>장소는 요청이 아니라 슬롯에서 가져온다.</b> 프론트가 {@code placeId}를 보내게 하면
     * 뽑지도 않은 장소를 담을 수 있다. 슬롯을 거치면 "내가 실제로 뽑은 것"만 담긴다.
     *
     * @throws CustomException 슬롯이 없거나 남의 것이면 {@link ErrorCode#RESULT_NOT_FOUND},
     *                         이미 담긴 장소면 {@link ErrorCode#ALREADY_ADDED}
     */
    @Transactional
    public CourseItemResponse addItem(Long userId, CourseItemAddRequest request) {
        SavedSlot slot = findOwnedSlot(userId, request.slotId());
        TravelCourse course = getOrCreateCourse(userId);
        Place place = slot.getPlace();

        requireNotAlreadyAdded(course, place);

        try {
            CourseItem item = courseItemRepository.saveAndFlush(
                    CourseItem.create(course, slot, nextSequence(course)));
            return CourseItemResponse.from(item);
        } catch (DataIntegrityViolationException e) {
            // 위 검사를 통과한 두 요청이 동시에 저장까지 온 경우다. UNIQUE 제약이 한쪽을 막아 준다.
            // 사용자 입장에서는 "이미 담겨 있다"가 맞는 답이므로 같은 에러로 돌려준다.
            log.debug("코스 항목 동시 저장 충돌: courseId={}, placeId={}", course.getId(), place.getId());
            throw new CustomException(ErrorCode.ALREADY_ADDED);
        }
    }

    /**
     * 코스에 담긴 것을 담은 순서대로 돌려준다. (roadmap 7-3)
     *
     * <p><b>한 번도 담지 않았으면 빈 목록이다.</b> 오류가 아니다 — 아직 아무것도 안 담은 상태는
     * 정상이고, 프론트는 빈 목록을 그대로 그리면 된다.
     *
     * <p><b>조회만으로 코스를 만들지 않는다.</b> 만들면 화면을 열어 보기만 한 회원에게도
     * 빈 코스가 쌓인다. 코스는 처음 담을 때 생긴다.
     *
     * <p>✅ <b>미션은 draw 때 제시한 바로 그것이다.</b> {@code course_items.slot_id}를 거쳐
     * {@code saved_slots.mission_id}에 닿는다(roadmap 7-6). 예전에는 장소만 가리켜서
     * 그 장소의 미션 중 아무거나 하나를 돌려줬고, 장소에 미션이 3개까지 붙으므로
     * <b>사용자가 본 적 없는 미션이 코스에 뜰 수 있었다.</b>
     *
     * <p>✅ <b>완료 여부는 {@code user_missions}를 보고 판정한다</b>(roadmap 9-1-1).
     * 그 줄은 GPS 인증을 통과했을 때만 생기므로(8-1·8-2), 결국 "그 장소에 실제로 다녀왔는가"다.
     * 8단계 전에는 기록할 방법 자체가 없어 항상 false로 내보내고 있었다.
     */
    @Transactional(readOnly = true)
    public CourseItemsResponse getItems(Long userId) {
        return travelCourseRepository.findByUserId(userId).stream()
                .findFirst()
                .map(course -> toResponse(userId, courseItemRepository.findByCourseIdOrderBySequenceAsc(course.getId())))
                .orElseGet(() -> new CourseItemsResponse(List.of()));
    }

    private CourseItemsResponse toResponse(Long userId, List<CourseItem> items) {
        Set<Long> completed = completedMissionIds(userId, items);
        return new CourseItemsResponse(items.stream()
                .map(item -> {
                    Mission mission = missionOf(item);
                    return CourseItemsResponse.Item.of(item, mission,
                            mission != null && completed.contains(mission.getId()));
                })
                .toList());
    }

    /**
     * 이 회원이 완료한 미션 번호들.
     *
     * <p><b>항목마다 묻지 않고 한 번에 묻는다.</b> 항목이 10개면 조회가 10번 나가는데(N+1 문제),
     * 목록 조회는 자주 열리는 화면이라 그 차이가 그대로 응답 시간이 된다.
     *
     * <p>미션이 붙지 않은 항목은 애초에 물어볼 것이 없어 제외한다. 물어볼 것이 하나도 없으면
     * 조회 자체를 건너뛴다 — {@code IN ()}은 DB에 따라 문법 오류가 된다.
     */
    private Set<Long> completedMissionIds(Long userId, List<CourseItem> items) {
        Set<Long> missionIds = items.stream()
                .map(this::missionOf)
                .filter(Objects::nonNull)
                .map(Mission::getId)
                .collect(Collectors.toSet());

        if (missionIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(userMissionRepository.findCompletedMissionIds(userId, missionIds));
    }

    /**
     * 이 항목을 담을 때 제시했던 미션. 없으면 null이다.
     *
     * <p><b>장소의 다른 미션으로 폴백하지 않는다.</b> 슬롯의 미션이 비었다는 것은 draw 때
     * {@code MissionMatcher}가 하나도 확보하지 못했다는 뜻인데, 여기서 장소의 아무 미션이나
     * 끼워 넣으면 <b>사용자가 본 적 없는 미션</b>이 코스에 나타난다.
     * 미션은 곁들이는 정보라 비어 있어도 항목은 그대로 나간다.
     */
    private Mission missionOf(CourseItem item) {
        return item.getSlot().getMission();
    }

    /**
     * 코스에서 항목을 뺀다. (roadmap 7-4)
     *
     * <p><b>남은 항목의 순번을 다시 매기지 않는다.</b> 3개 중 2번을 지우면 1·3이 남는데,
     * 순서를 재현하는 데는 이걸로 충분하고 다시 매기면 <b>남은 항목을 전부 UPDATE</b>해야 한다.
     * 새 항목은 마지막 순번 + 1로 붙으므로 번호가 겹칠 일도 없다.
     *
     * @throws CustomException 항목이 없거나 남의 것이면 {@link ErrorCode#ITEM_NOT_FOUND}
     */
    @Transactional
    public CourseItemRemoveResponse removeItem(Long userId, Long itemId) {
        CourseItem item = courseItemRepository.findById(itemId)
                .filter(found -> found.getCourse().getUser().getId().equals(userId))
                .orElseThrow(() -> {
                    // 남의 항목도 "없음"으로 답한다. 담기(7-1)·슬롯 조회(6-7)와 같은 원칙이다.
                    log.debug("지울 수 없는 코스 항목: itemId={}, userId={}", itemId, userId);
                    return new CustomException(ErrorCode.ITEM_NOT_FOUND);
                });

        courseItemRepository.delete(item);
        return CourseItemRemoveResponse.of(itemId);
    }

    /**
     * 이미 담긴 장소인지 본다.
     *
     * <p><b>중복 판정 기준이 슬롯이 아니라 장소다.</b> 온디맨드 추첨이라 인기 있는 곳은 반복해서 뽑히는데,
     * 슬롯 번호로 막으면 <b>같은 장소가 코스에 여러 줄로 쌓인다.</b>
     *
     * <p><b>이 검사만으로는 부족하다.</b> 같은 요청이 동시에 두 번 들어오면 둘 다 "없음"을 보고
     * 통과한다. {@code (course_id, place_id)} UNIQUE 제약이 마지막 방어선이고,
     * 여기서는 <b>흔한 경우를 예외 없이 걸러 주는 역할</b>을 한다.
     */
    private void requireNotAlreadyAdded(TravelCourse course, Place place) {
        if (courseItemRepository.existsByCourseIdAndPlaceId(course.getId(), place.getId())) {
            log.debug("이미 담긴 장소: courseId={}, placeId={}", course.getId(), place.getId());
            throw new CustomException(ErrorCode.ALREADY_ADDED);
        }
    }

    /**
     * 이 회원의 슬롯을 찾는다.
     *
     * <p><b>남의 슬롯도 "없음"으로 답한다.</b> 403(권한 없음)으로 답하면 <b>그 번호의 슬롯이
     * 존재한다는 사실을 알려 주는 셈</b>이라, 번호를 훑어 남이 무엇을 얼마나 뽑았는지 세어 볼 수 있다.
     * 슬롯 결과 조회(6-7)와 같은 원칙이다.
     */
    private SavedSlot findOwnedSlot(Long userId, Long slotId) {
        return savedSlotRepository.findById(slotId)
                .filter(slot -> slot.getSession().getUser().getId().equals(userId))
                .orElseThrow(() -> {
                    log.debug("담을 수 없는 슬롯: slotId={}, userId={}", slotId, userId);
                    return new CustomException(ErrorCode.RESULT_NOT_FOUND);
                });
    }

    /**
     * 회원의 코스를 찾고, 없으면 만든다.
     *
     * <p>코스 생성 API가 없으므로 <b>처음 담는 순간이 곧 코스가 생기는 순간</b>이다.
     * 회원가입 때 미리 만들지 않는 이유는, 한 번도 담지 않은 회원에게도 빈 코스가 쌓이기 때문이다.
     */
    private TravelCourse getOrCreateCourse(Long userId) {
        return travelCourseRepository.findByUserId(userId).stream()
                .findFirst()
                .orElseGet(() -> createCourse(userId));
    }

    private TravelCourse createCourse(Long userId) {
        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 없는 회원으로 코스를 만들면
        // user_id FK가 가리킬 곳이 없어 저장 시점에 터진다. 여기서 인증 문제로 돌려준다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 회원의 코스 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });
        return travelCourseRepository.save(TravelCourse.create(user, DEFAULT_COURSE_TITLE));
    }

    /**
     * 다음 순번. 마지막 순번 + 1이다.
     *
     * <p>항목 수를 세지 않고 마지막 순번을 보는 이유: 중간을 지우면(7-4) 개수와 순번이 어긋난다.
     * 3개를 담고 2번을 지우면 개수는 2인데 마지막 순번은 3이라, 개수로 채번하면 <b>3번이 두 개</b>가 된다.
     */
    private int nextSequence(TravelCourse course) {
        Integer lastSequence = courseItemRepository.findMaxSequence(course.getId());
        return lastSequence == null ? FIRST_SEQUENCE : lastSequence + 1;
    }
}

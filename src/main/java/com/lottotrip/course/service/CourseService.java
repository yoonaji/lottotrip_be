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
 * 슬롯로 뽑은 장소 중 실제 간 곳을 저장하는 곳
 *
 * ## ⚠️ 코스를 만드는 API가 명세에 없다
 * 그래서 `trip_sessions`와 같은 방식으로 회원당 코스 하나를 서버가 find-or-create한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseService {

    /**
     * 코스를 자동으로 만들 때 붙이는 이름.
     *
     * `travel_courses.title`이 NOT NULL인데 사용자에게 받을 입력이 없다.
     * 이름을 바꾸는 API가 생기기 전까지 쓰는 임시 값.
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
     * 슬롯 확인(내 것인가) → 코스 확보(없으면 생성) → 순번 채번 → 저장
     * 장소는 요청이 아니라 슬롯에서 가져옴
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
     * 한 번도 담지 않았으면 빈 목록.
     *
     * 완료 여부는 `user_missions`를 보고 판정.(roadmap 9-1-1).
     * 그 줄은 GPS 인증을 통과했을 때만 생기므로(8-1·8-2), 결국 "그 장소에 실제로 다녀왔는가"다.
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
     * 항목마다 묻지 않고 한 번에 묻는다. 항목이 10개면 조회가 10번 나가는데(N+1 문제),
     * 목록 조회는 자주 열리는 화면이라 그 차이가 그대로 응답 시간이 된다.
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

    /**이 항목을 담을 때 제시했던 미션. 없으면 null이다.*/
    private Mission missionOf(CourseItem item) {
        return item.getSlot().getMission();
    }

    /**
     * 코스에서 항목을 뺀다. (roadmap 7-4)
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

    /**이미 담긴 장소인지 본다.*/
    private void requireNotAlreadyAdded(TravelCourse course, Place place) {
        if (courseItemRepository.existsByCourseIdAndPlaceId(course.getId(), place.getId())) {
            log.debug("이미 담긴 장소: courseId={}, placeId={}", course.getId(), place.getId());
            throw new CustomException(ErrorCode.ALREADY_ADDED);
        }
    }

    /**
     * 이 회원의 슬롯을 찾는다.
     *
     * 남의 슬롯도 "없음"으로 답한다. 403(권한 없음)으로 답하면 그 번호의 슬롯이
     * 존재한다는 사실을 알려 주는 셈
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
     * 코스 생성 API가 없으므로 처음 담는 순간이 곧 코스가 생기는 순간.
     */
    private TravelCourse getOrCreateCourse(Long userId) {
        return travelCourseRepository.findByUserId(userId).stream()
                .findFirst()
                .orElseGet(() -> createCourse(userId));
    }

    private TravelCourse createCourse(Long userId) {
        // 토큰이 유효해도 그 회원이 아직 있는지는 별개다(탈퇴). 없는 회원으로 코스를 만들면
        // user_id FK가 가리킬 곳이 없어 저장 시점에 터진다. 여기서 인증 문제로 돌려준다.
        // ⚠️ 탈퇴는 소프트 삭제라 findById로는 탈퇴자가 통과한다(9-5).
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> {
                    log.debug("존재하지 않는 회원의 코스 요청: userId={}", userId);
                    return new CustomException(ErrorCode.UNAUTHORIZED);
                });
        return travelCourseRepository.save(TravelCourse.create(user, DEFAULT_COURSE_TITLE));
    }

    /**
     * 다음 순번. 마지막 순번 + 1이다.
     *
     * 항목 수를 세지 않고 마지막 순번을 보는 이유: 중간을 지우면 개수와 순번이 어긋난다.
     * 3개를 담고 2번을 지우면 개수는 2인데 마지막 순번은 3이라, 개수로 채번하면 3번이 두 개가 된다.
     */
    private int nextSequence(TravelCourse course) {
        Integer lastSequence = courseItemRepository.findMaxSequence(course.getId());
        return lastSequence == null ? FIRST_SEQUENCE : lastSequence + 1;
    }
}

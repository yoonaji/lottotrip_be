package com.lottotrip.course.service;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.entity.CourseItem;
import com.lottotrip.course.entity.TravelCourse;
import com.lottotrip.course.repository.CourseItemRepository;
import com.lottotrip.course.repository.TravelCourseRepository;
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
                    CourseItem.create(course, place, nextSequence(course)));
            return CourseItemResponse.from(item);
        } catch (DataIntegrityViolationException e) {
            // 위 검사를 통과한 두 요청이 동시에 저장까지 온 경우다. UNIQUE 제약이 한쪽을 막아 준다.
            // 사용자 입장에서는 "이미 담겨 있다"가 맞는 답이므로 같은 에러로 돌려준다.
            log.debug("코스 항목 동시 저장 충돌: courseId={}, placeId={}", course.getId(), place.getId());
            throw new CustomException(ErrorCode.ALREADY_ADDED);
        }
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

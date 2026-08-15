package com.lottotrip.course.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemRemoveResponse;
import com.lottotrip.course.dto.CourseItemsResponse;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 코스 API. (tour_api_erd.md 4-4)
 */
@RestController
@RequestMapping("/api/v1/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * 슬롯으로 뽑은 장소를 코스에 담는다.
     *
     * <p>{@code @AuthenticationPrincipal}로 회원 번호를 받는다. 요청 본문으로 받지 않는 이유는
     * 그러면 <b>남의 번호를 적어 보낼 수 있기</b> 때문이다.
     *
     * <p><b>201을 돌려준다.</b> 이 요청은 조회가 아니라 <b>새 항목을 만드는</b> 일이라,
     * 명세도 201로 적혀 있다(다른 API는 대부분 200이다).
     */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseItemResponse> addItem(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody CourseItemAddRequest request) {
        return ApiResponse.success(courseService.addItem(userId, request));
    }

    /**
     * 내 코스 조회.
     *
     * <p><b>{@code courseId}를 받지 않는다.</b> 명세에 그런 파라미터가 없고, 코스는 회원당 하나라
     * 토큰만으로 어느 코스인지 정해진다.
     *
     * <p>한 번도 담지 않았으면 빈 목록이 나간다 — 404가 아니다.
     */
    @GetMapping("/items")
    public ApiResponse<CourseItemsResponse> getItems(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(courseService.getItems(userId));
    }

    /**
     * 코스에서 항목 빼기.
     *
     * <p>남의 항목을 지우려 하면 403이 아니라 404로 답한다. 담기(7-1)·슬롯 조회(6-7)와 같은 원칙이다.
     */
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CourseItemRemoveResponse> removeItem(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long itemId) {
        return ApiResponse.success(courseService.removeItem(userId, itemId));
    }
}

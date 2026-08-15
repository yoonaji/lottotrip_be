package com.lottotrip.course.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
}

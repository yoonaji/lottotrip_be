package com.lottotrip.course.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.course.dto.CourseItemAddRequest;
import com.lottotrip.course.dto.CourseItemRemoveResponse;
import com.lottotrip.course.dto.CourseItemsResponse;
import com.lottotrip.course.dto.CourseItemResponse;
import com.lottotrip.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "코스", description = "여행 코스 담기 · 조회 · 삭제")
@RestController
@RequestMapping("/api/v1/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * 슬롯으로 뽑은 장소를 코스에 담는다.
     *
     *`@AuthenticationPrincipal`로 회원 번호를 받는다.
     * api 명세에 맞춰 201을 돌려준다.
     */
    @Operation(summary = "코스에 담기", description = "슬롯 결과를 여행 코스에 추가한다. slotId만 보내면 장소·미션은 서버가 찾는다. 코스가 없으면 자동으로 만든다. 성공 시 201.")
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CourseItemResponse> addItem(@AuthenticationPrincipal Long userId,
                                                   @Valid @RequestBody CourseItemAddRequest request) {
        return ApiResponse.success(courseService.addItem(userId, request));
    }

    /**
     * 내 코스 조회.
     *
     * `courseId`를 받지 않는다. 명세에 그런 파라미터가 없고, 코스는 회원당 하나라
     * 토큰만으로 어느 코스인지 정해진다.
     *
     * 한 번도 담지 않았으면 빈 목록이 나간다 — 404가 아님
     */
    @Operation(summary = "코스 조회", description = "담긴 목적지를 미션 정보와 함께 준다. 코스가 없으면 빈 배열이고, 조회만으로 코스를 만들지는 않는다.")
    @GetMapping("/items")
    public ApiResponse<CourseItemsResponse> getItems(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(courseService.getItems(userId));
    }

    /**
     * 코스에서 항목 빼기.
     *
     * 남의 항목을 지우려 하면 403이 아니라 404로 답한다.
     */
    @Operation(summary = "코스 항목 삭제", description = "코스에서 항목 하나를 뺀다. placeId가 아니라 itemId를 넘긴다. 삭제하면 그 장소를 다시 담을 수 있다.")
    @DeleteMapping("/items/{itemId}")
    public ApiResponse<CourseItemRemoveResponse> removeItem(@AuthenticationPrincipal Long userId,
                                                            @PathVariable Long itemId) {
        return ApiResponse.success(courseService.removeItem(userId, itemId));
    }
}

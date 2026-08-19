package com.lottotrip.mission.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.mission.dto.MissionCompleteRequest;
import com.lottotrip.mission.dto.MissionCompleteResponse;
import com.lottotrip.mission.service.MissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미션 API. (tour_api_erd.md 4-5)
 */
@Tag(name = "미션", description = "미션 완료 처리")
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    /**
     * 미션 완료 처리
     *
     * 200을 돌려준다. 코스 담기(7-1)와 달리 명세가 200으로 적혀 있다.
     * 기록이 새로 생기긴 하지만, 프론트가 다루는 것은 "그 미션이 완료됐다"는 상태이지
     * 새로 만들어진 `user_missions` 행이 아니다.
     *
     * 회원 번호는 `@AuthenticationPrincipal`로 받는다.
     */
    @Operation(summary = "미션 완료 처리", description = "현재 좌표가 장소에서 500m 이내면 완료 처리한다. 반경 밖이면 422, 이미 완료했으면 409.")
    @PostMapping("/{missionId}/complete")
    public ApiResponse<MissionCompleteResponse> complete(@AuthenticationPrincipal Long userId,
                                                         @PathVariable Long missionId,
                                                         @Valid @RequestBody MissionCompleteRequest request) {
        return ApiResponse.success(missionService.complete(userId, missionId, request));
    }
}

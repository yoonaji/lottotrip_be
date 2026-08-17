package com.lottotrip.slot.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.dto.SlotDrawResponse;
import com.lottotrip.slot.dto.SlotResultResponse;
import com.lottotrip.slot.service.SlotResultService;
import com.lottotrip.slot.service.SlotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 슬롯 API. (tour_api_erd.md 4-2)
 */
@Tag(name = "슬롯", description = "룰렛 돌리기 · 결과 상세 조회")
@RestController
@RequestMapping("/api/v1/slot")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;
    private final SlotResultService slotResultService;

    /**
     * 슬롯 돌리기. 랜덤 여행지 한 곳과 미션을 발급한다.
     *
     * `@AuthenticationPrincipal`로 회원 번호를 받는다. 요청 본문으로 받지 않는 이유는
     * 그러면 남의 번호를 적어 보낼 수 있기 때문이다. 4-2의 JWT 필터가 토큰에서 읽어 둔 값을 쓴다.
     *
     * `@Valid`가 붙어 있어 좌표·예산·이동수단이 비면 서비스에 들어오기 전에 400으로 끝난다.
     * 그래서 서비스가 "값이 있는지" 확인하는 if문으로 차지 않는다.
     *
     * 반경과 세션은 요청에 없다. 반경은 `transport`에서(결정 2),
     * 세션은 회원 기준 12시간 find-or-create로(결정 1) 서버가 알아서 정한다.
     */
    @Operation(summary = "슬롯 돌리기", description = "현재 위치 기준 반경 안에서 여행지 1곳과 미션을 뽑는다. 반경은 transport로 정해진다(walk 10km / car 30km). 숙박은 후보에서 제외된다. 평균 응답 4.6초(미션 AI 생성 포함).")
    @PostMapping("/draw")
    public ApiResponse<SlotDrawResponse> draw(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody SlotDrawRequest request) {
        return ApiResponse.success(slotService.draw(userId, request));
    }

    /**
     * 슬롯 결과 조회. 룰렛 세부사항 조회를 겸한다.
     *
     * 이 요청이 들어올 때 TourAPI를 실시간으로 부른다.
     * 결정 12로 `draw`도 실시간 호출을 하므로 공공데이터 호출 지점은 이 둘이다(결정 7).
     *
     * 남의 슬롯을 조회하면 403이 아니라 404로 답한다. 403은 "그 번호는 존재한다"를
     * 알려 주는 셈이라 번호를 훑어 남의 기록을 세어 볼 수 있다.
     */
    @Operation(summary = "슬롯 결과 상세 조회", description = "뽑은 장소의 소개글·홈페이지를 공공 API로 실시간 조회해 함께 준다. 외부 호출이 실패해도 200이며 liveDetailLoaded=false로 알린다. 남의 슬롯은 404.")
    @GetMapping("/results/{slotId}")
    public ApiResponse<SlotResultResponse> getResult(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long slotId) {
        return ApiResponse.success(slotResultService.getResult(userId, slotId));
    }
}

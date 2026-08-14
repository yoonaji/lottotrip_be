package com.lottotrip.slot.controller;

import com.lottotrip.common.response.ApiResponse;
import com.lottotrip.slot.dto.SlotDrawRequest;
import com.lottotrip.slot.dto.SlotDrawResponse;
import com.lottotrip.slot.service.SlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 슬롯 API. (tour_api_erd.md 4-2)
 */
@RestController
@RequestMapping("/api/v1/slot")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    /**
     * 슬롯 돌리기. 랜덤 여행지 한 곳과 미션을 발급한다.
     *
     * <p>{@code @AuthenticationPrincipal}로 회원 번호를 받는다. 요청 본문으로 받지 않는 이유는
     * 그러면 <b>남의 번호를 적어 보낼 수 있기</b> 때문이다. 4-2의 JWT 필터가 토큰에서 읽어 둔 값을 쓴다.
     *
     * <p>{@code @Valid}가 붙어 있어 좌표·예산·이동수단이 비면 서비스에 들어오기 전에 400으로 끝난다.
     * 그래서 서비스가 "값이 있는지" 확인하는 if문으로 차지 않는다.
     *
     * <p><b>반경과 세션은 요청에 없다.</b> 반경은 {@code transport}에서(결정 2),
     * 세션은 회원 기준 12시간 find-or-create로(결정 1) 서버가 알아서 정한다.
     */
    @PostMapping("/draw")
    public ApiResponse<SlotDrawResponse> draw(@AuthenticationPrincipal Long userId,
                                              @Valid @RequestBody SlotDrawRequest request) {
        return ApiResponse.success(slotService.draw(userId, request));
    }
}

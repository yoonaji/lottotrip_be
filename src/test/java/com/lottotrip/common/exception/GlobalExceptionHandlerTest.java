package com.lottotrip.common.exception;

import com.lottotrip.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("CustomException은 ErrorCode에 정의된 상태 코드와 코드·메시지로 응답한다")
    void handleCustomException() throws Exception {
        mockMvc.perform(get("/test/custom"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error.code").value("SLOT_001"))
                .andExpect(jsonPath("$.error.message").value("반경 내 후보 장소가 없습니다."));
    }

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("어떤 ErrorCode로 던지든 그 ErrorCode에 정의된 상태 코드와 코드로 응답한다")
    void handleCustomExceptionForEveryErrorCode(ErrorCode errorCode) {
        ResponseEntity<ApiResponse<Void>> response =
                new GlobalExceptionHandler().handleCustomException(new CustomException(errorCode));

        assertThat(response.getStatusCode()).isEqualTo(errorCode.getHttpStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getError().getCode()).isEqualTo(errorCode.getCode());
        assertThat(response.getBody().getError().getMessage()).isEqualTo(errorCode.getMessage());
    }

    @Test
    @DisplayName("@Valid 검증에 실패하면 400 BAD_REQUEST로 응답한다")
    void handleValidationException() throws Exception {
        mockMvc.perform(post("/test/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_400"))
                .andExpect(jsonPath("$.error.message").value("잘못된 요청입니다."));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500 INTERNAL_ERROR로 응답하고 내부 메시지를 노출하지 않는다")
    void handleUnexpectedException() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_500"))
                .andExpect(jsonPath("$.error.message").value("서버 내부 오류입니다."));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/custom")
        void throwCustom() {
            throw new CustomException(ErrorCode.NO_PLACE_FOUND);
        }

        @PostMapping("/test/valid")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/boom")
        void throwUnexpected() {
            throw new IllegalStateException("DB 커넥션 풀 고갈 — 외부에 노출되면 안 되는 내부 사정");
        }
    }

    record TestRequest(@NotBlank String name) {
    }
}

package com.lottotrip.common.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lottotrip.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("success()는 success=true, data는 담은 값 그대로, error는 null이다")
    void success() {
        ApiResponse<String> response = ApiResponse.success("hello");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("hello");
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("success()는 반환할 데이터가 없어도 만들 수 있다")
    void successWithoutData() {
        ApiResponse<Void> response = ApiResponse.success(null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("fail()은 success=false, data는 null, error에 ErrorCode의 코드와 메시지를 담는다")
    void fail() {
        ApiResponse<Void> response = ApiResponse.fail(ErrorCode.NO_PLACE_FOUND);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getData()).isNull();
        assertThat(response.getError().getCode()).isEqualTo("SLOT_001");
        assertThat(response.getError().getMessage()).isEqualTo("반경 내 후보 장소가 없습니다.");
    }

    @Test
    @DisplayName("성공 응답을 JSON으로 바꾸면 error가 null로 함께 내려간다")
    void serializeSuccessKeepsNullError() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.success("hello"));

        assertThat(json).contains("\"success\":true");
        assertThat(json).contains("\"data\":\"hello\"");
        assertThat(json).contains("\"error\":null");
    }

    @Test
    @DisplayName("실패 응답을 JSON으로 바꾸면 data가 null로 함께 내려간다")
    void serializeFailKeepsNullData() throws Exception {
        String json = objectMapper.writeValueAsString(ApiResponse.fail(ErrorCode.NO_PLACE_FOUND));

        assertThat(json).contains("\"success\":false");
        assertThat(json).contains("\"data\":null");
        assertThat(json).contains("\"code\":\"SLOT_001\"");
    }
}

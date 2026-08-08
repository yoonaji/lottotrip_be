package com.lottotrip.place.tourapi;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 한국관광공사 TourAPI 호출 담당. (roadmap 5-1)
 *
 * <p>이 클래스는 <b>바깥과 통신하는 일만</b> 한다. 받아온 값을 우리 테이블에 넣는 것은 5-2가 한다.
 * 이렇게 나눠 두면 "API가 이상한가, 우리 저장 로직이 이상한가"를 따로 볼 수 있다.
 *
 * <p>구조는 4-4-1 {@code KakaoTokenVerifier}와 같다. {@code RestClient}로 부르고, 응답을 record로 받고,
 * 실패는 {@link ErrorCode#SERVICE_UNAVAILABLE}로 모은다.
 *
 * <h2>이 API를 다룰 때 조심할 점</h2>
 * <ol>
 *   <li><b>HTTP 200인데 실패일 수 있다.</b> 인증키 오류·할당량 초과가 본문 {@code resultCode}로만 온다.</li>
 *   <li><b>인증키를 두 번 인코딩하면 안 된다.</b> 키에 {@code + / =}가 섞여 있어 흔히 깨진다.</li>
 *   <li><b>결과가 0건이면 {@code items}가 빈 문자열로 온다.</b> 객체를 기대하면 파싱이 터진다.</li>
 *   <li><b>키가 거부되면 JSON이 아니라 XML 에러 문서가 온다.</b></li>
 * </ol>
 */
@Slf4j
@Component
public class TourApiClient {

    private static final String OP_AREA_BASED_LIST = "areaBasedList2";
    private static final String OP_DETAIL_COMMON = "detailCommon2";
    private static final String OP_DETAIL_IMAGE = "detailImage2";
    private static final String OP_AREA_CODE = "areaCode2";

    /**
     * 목록 정렬 기준(A = 제목순).
     *
     * <p>정렬을 지정하지 않으면 페이지를 넘길 때마다 서버가 주는 순서가 달라질 수 있다.
     * 그러면 같은 장소를 두 번 받거나 아예 놓친다. 순서를 고정해야 페이지 반복이 믿을 만해진다.
     */
    private static final String ARRANGE_BY_TITLE = "A";

    /** 지역 코드 조회는 건수가 적다(시도 17개, 시군구 최대 수십 개). 한 번에 받아 온다. */
    private static final int AREA_CODE_ROWS = 100;

    private final RestClient restClient;
    private final TourApiProperties properties;

    public TourApiClient(RestClient.Builder restClientBuilder, TourApiProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .configureMessageConverters(converters -> converters.withJsonConverter(lenientJsonConverter()))
                .build();
        if (!properties.isConfigured()) {
            log.warn("TourAPI 인증키(TOUR_API_SERVICE_KEY)가 설정되지 않아 장소 데이터 수집이 비활성 상태입니다.");
        }
    }

    /**
     * 이 클라이언트 전용 JSON 변환기.
     *
     * <p>기본 변환기를 그대로 쓰면 두 가지에서 걸린다.
     * <ul>
     *   <li>결과 0건일 때의 {@code "items": ""} → 객체 자리에 문자열이 와서 파싱 실패</li>
     *   <li>{@code Content-Type}이 {@code application/json}이 아닌 경우 → 처리할 변환기가 없음</li>
     * </ul>
     *
     * <p>전역 설정을 건드리지 않고 <b>이 클라이언트에만</b> 적용하는 이유는,
     * 관대한 설정이 서비스 전체에 퍼지면 다른 곳에서 진짜 잘못된 데이터도 조용히 넘어가기 때문이다.
     * 개발자 B의 코드에도 영향을 준다.
     */
    private static JacksonJsonHttpMessageConverter lenientJsonConverter() {
        JsonMapper mapper = JsonMapper.builder()
                // "" 를 "객체 없음"으로 읽는다 — 결과 0건일 때의 items 대응
                .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
                // 항목이 1건일 때 배열이 아니라 객체 하나로 오는 경우 대비
                .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
                // 우리가 안 쓰는 필드가 아무리 늘어나도 무시한다
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter(mapper);
        converter.setSupportedMediaTypes(List.of(
                MediaType.APPLICATION_JSON,
                new MediaType("text", "json"),
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_OCTET_STREAM));
        return converter;
    }

    // ---------- 공개 조회 메서드 ----------

    /**
     * 지역 기반 관광 정보 목록. {@code places}의 주 재료다.
     *
     * @param areaCode 시도 코드 (1 = 서울 …)
     * @param pageNo   1부터 시작하는 페이지 번호
     */
    public TourApiPage<TourApiPlaceItem> fetchAreaBasedList(int areaCode, int pageNo) {
        URI uri = uri(OP_AREA_BASED_LIST, params(
                "numOfRows", properties.numOfRows(),
                "pageNo", pageNo,
                "areaCode", areaCode,
                "arrange", ARRANGE_BY_TITLE));

        TourApiResponse<TourApiPlaceItem> response =
                call(uri, new ParameterizedTypeReference<TourApiResponse<TourApiPlaceItem>>() {
                });
        return TourApiPage.from(response);
    }

    /**
     * 장소 하나의 공통 상세 정보. {@code places.description}을 채운다.
     *
     * <p>없을 수도 있으므로 {@link Optional}로 돌려준다. null을 돌려주면 받는 쪽이
     * 검사를 잊기 쉬운데, Optional은 "비어 있을 수 있다"를 타입으로 알려 준다.
     */
    public Optional<TourApiDetailItem> fetchDetailCommon(String contentId) {
        URI uri = uri(OP_DETAIL_COMMON, params("contentId", contentId));

        TourApiResponse<TourApiDetailItem> response =
                call(uri, new ParameterizedTypeReference<TourApiResponse<TourApiDetailItem>>() {
                });
        return response.items().stream().findFirst();
    }

    /** 장소 이미지 목록. {@code place_media}를 채운다. */
    public List<TourApiImageItem> fetchDetailImages(String contentId) {
        URI uri = uri(OP_DETAIL_IMAGE, params(
                "contentId", contentId,
                "imageYN", "Y"));

        TourApiResponse<TourApiImageItem> response =
                call(uri, new ParameterizedTypeReference<TourApiResponse<TourApiImageItem>>() {
                });
        return response.items();
    }

    /**
     * 지역 코드 목록. (5-3에서 {@code states}·{@code cities} 적재에 쓴다)
     *
     * @param areaCode null이면 시도 목록, 값을 주면 그 시도의 시군구 목록
     */
    public List<TourApiAreaCodeItem> fetchAreaCodes(Integer areaCode) {
        Map<String, Object> params = params("numOfRows", AREA_CODE_ROWS, "pageNo", 1);
        if (areaCode != null) {
            params.put("areaCode", areaCode);
        }

        TourApiResponse<TourApiAreaCodeItem> response =
                call(uri(OP_AREA_CODE, params),
                        new ParameterizedTypeReference<TourApiResponse<TourApiAreaCodeItem>>() {
                        });
        return response.items();
    }

    // ---------- 호출 공통 ----------

    /**
     * 실제 호출과 실패 처리. 모든 조회가 이 통로를 지난다.
     *
     * <p>실패를 한곳에 모으는 이유는, 오퍼레이션마다 따로 처리하면 <b>어느 하나에서 빠뜨리기</b> 때문이다.
     * 배치가 조용히 빈 결과를 받아 넘어가는 것이 가장 나쁘다.
     */
    private <T> TourApiResponse<T> call(URI uri, ParameterizedTypeReference<TourApiResponse<T>> type) {
        try {
            TourApiResponse<T> response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        // 4xx든 5xx든 배치 입장에서는 "지금 데이터를 못 받는다"로 같다.
                        // 사용자에게 돌려줄 400이 없는 자리라 구분해도 쓸 데가 없다.
                        log.warn("TourAPI 오류 응답: status={}, uri={}", res.getStatusCode(), maskServiceKey(uri));
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .body(type);
            return validate(response, uri);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // XML 에러 문서가 오거나 연결이 끊긴 경우 여기로 온다.
            log.warn("TourAPI 호출 실패 uri={}: {}", maskServiceKey(uri), e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /** HTTP는 200이지만 본문이 실패를 말하는 경우를 걸러낸다. */
    private <T> TourApiResponse<T> validate(TourApiResponse<T> response, URI uri) {
        if (response == null || response.header() == null) {
            log.warn("TourAPI 응답 본문이 비어 있음: uri={}", maskServiceKey(uri));
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!response.isSuccess()) {
            log.warn("TourAPI 실패 코드: resultCode={}, resultMsg={}, uri={}",
                    response.header().resultCode(), response.header().resultMsg(), maskServiceKey(uri));
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return response;
    }

    // ---------- URL 조립 ----------

    /**
     * 요청 URL을 만든다.
     *
     * <p><b>인증키는 우리가 직접 한 번만 인코딩한다.</b> 그리고 {@code build(true)}로
     * "이미 인코딩된 값이니 손대지 말라"고 알린다.
     *
     * <p>왜 이렇게까지 하나? 인증키에는 {@code +} {@code /} {@code =}가 섞여 있다.
     * 그런데 {@code +}는 URL 쿼리에서 <b>공백</b>으로 해석되는 관례가 있어 그대로 두면 키가 망가지고,
     * 이미 인코딩된 키(%2B…)를 다시 인코딩하면 {@code %252B}가 되어 역시 망가진다.
     * 둘 다 "인증되지 않은 키" 오류로 나타나서 원인을 찾기 어렵다.
     */
    private URI uri(String operation, Map<String, Object> params) {
        requireConfigured();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path("/" + operation)
                .queryParam("serviceKey", URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                .queryParam("MobileOS", properties.mobileOs())
                .queryParam("MobileApp", properties.mobileApp())
                .queryParam("_type", "json");
        params.forEach(builder::queryParam);

        return builder.build(true).toUri();
    }

    /**
     * 인증키가 없으면 네트워크를 타기 전에 멈춘다.
     *
     * <p>{@code CustomException}이 아니라 {@link IllegalStateException}인 이유:
     * 이것은 사용자 요청 처리 중 생긴 문제가 아니라 <b>서버 설정 실수</b>다.
     * 사용자에게 돌려줄 응답 코드가 필요한 자리가 아니고, 조용히 넘어가면 안 되는 종류다.
     */
    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new IllegalStateException(
                    "TourAPI 인증키가 없습니다. 환경변수 TOUR_API_SERVICE_KEY를 확인하세요.");
        }
    }

    /** 파라미터를 넣은 순서대로 유지한다. URL이 매번 같은 모양이라야 로그를 비교하기 쉽다. */
    private Map<String, Object> params(Object... keyValues) {
        Map<String, Object> params = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            params.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return params;
    }

    /**
     * 로그에 남길 URL에서 인증키를 가린다.
     *
     * <p>실패 로그에 URL을 통째로 찍으면 인증키가 로그 파일·모니터링 도구에 그대로 남는다.
     * 로그는 지우기도 어렵고 여러 곳에 복제되므로, 애초에 찍지 않는 편이 낫다.
     */
    static String maskServiceKey(String url) {
        return url == null ? null : url.replaceAll("(?i)(serviceKey=)[^&]*", "$1***");
    }

    private static String maskServiceKey(URI uri) {
        return maskServiceKey(uri.toString());
    }
}

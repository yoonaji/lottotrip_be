package com.lottotrip.place.tourapi;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private static final String OP_LOCATION_BASED_LIST = "locationBasedList2";
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

    /**
     * 좌표 기반 목록의 정렬 기준(E = 거리순).
     *
     * <p>가까운 곳부터 받으므로, 페이지를 끝까지 넘기지 않아도 쓸 만한 후보를 확보할 수 있다.
     */
    private static final String ARRANGE_BY_DISTANCE = "E";

    /** 지역 코드 조회는 건수가 적다(시도 17개, 시군구 최대 수십 개). 한 번에 받아 온다. */
    private static final int AREA_CODE_ROWS = 100;

    /** 일일 호출 잔량을 알려 주는 응답 헤더. 공공데이터포털이 모든 응답에 실어 준다(2026-08-15 실측). */
    private static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";

    /**
     * 이 값 이하로 남으면 경고를 남긴다.
     *
     * <p><b>왜 감시하는가.</b> 2026-08-14에 페이지 순회 버그로 하루치 1,000회를 통째로 태웠는데,
     * 그 사실을 <b>429를 맞고 나서야</b> 알았다. 잔량이 응답에 실려 오므로 <b>소진되기 전에</b> 알 수 있다.
     *
     * <p><b>왜 매번 찍지 않는가.</b> 호출마다 로그를 남기면 정상 운영 중에도 줄이 계속 쌓여
     * 정작 위험한 순간에 아무도 보지 않는다. 드물게 나와야 눈에 띈다.
     */
    private static final int QUOTA_WARNING_THRESHOLD = 100;

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
        return TourApiPage.from(response, pageNo, properties.numOfRows());
    }

    /**
     * 좌표 기반 관광 정보 목록. <b>슬롯 추첨의 후보가 전부 여기서 나온다.</b> (roadmap 6-11, 결정 12)
     *
     * <p>국문 관광정보({@link TourApiService#KOREAN})를 부르는 짧은 형태다.
     *
     * @see #fetchLocationBasedList(TourApiService, double, double, int, String, int)
     */
    public TourApiPage<TourApiPlaceItem> fetchLocationBasedList(
            double latitude, double longitude, int radiusMeters, String contentTypeId, int pageNo) {
        return fetchLocationBasedList(
                TourApiService.KOREAN, latitude, longitude, radiusMeters, contentTypeId, pageNo);
    }

    /**
     * 좌표 기반 관광 정보 목록. (roadmap 6-11, 결정 12)
     *
     * <p>응답의 {@code dist}가 요청 좌표로부터의 거리(미터)라 <b>거리 계산을 따로 하지 않아도 된다.</b>
     *
     * <p><b>⚠️ 반경에 상한이 없다.</b> 예전에는 20,000m를 넘기면 이 메서드가 막았다. "넘기면 API가
     * 조용히 잘린 결과를 준다"고 적혀 있었으나 <b>실측(2026-08-15) 결과 사실이 아니었다</b> —
     * 강릉 기준 20km 763건 / 30km 880건 / 50km 1,290건으로 정직하게 늘어난다.
     * 이 허구의 상한 때문에 {@code CAR}(30km)를 태울 수 없다고 오해하고 있었다.
     *
     * <p><b>⚠️ 진짜 상한은 {@code numOfRows}의 1,000이다.</b> 후보가 그보다 많으면
     * {@code arrange=E}(거리순) 덕에 <b>먼 곳부터 잘린다.</b> 서울 30km는 후보 4,181건 중
     * 가까운 1,000건만 와서 실질 반경이 6.4km가 된다. 수도권을 다루게 되면 이 지점을 먼저 봐야 한다(결정 15).
     *
     * <p>⚠️ <b>{@code mapX}에 경도, {@code mapY}에 위도를 넣는다.</b> 수학 좌표계의 x·y라서
     * 흔히 쓰는 "위도·경도" 순서와 반대다. 뒤집어 보내도 API는 오류 대신 0건이나 엉뚱한 장소를
     * 정상 응답으로 주기 때문에, 틀려도 드러나지 않고 추첨 결과만 조용히 이상해진다.
     *
     * @param service       어느 서비스에 물어볼 것인가. {@link TourApiService#ACCESSIBLE}이면 무장애 등록분만 온다
     * @param latitude      기준 위도 (숙소 좌표)
     * @param longitude     기준 경도
     * @param radiusMeters  반경(미터). 우리 도메인은 km로 말하므로 부르는 쪽이 1000을 곱해 넘긴다
     * @param contentTypeId 관광 타입 코드(12=관광지, 39=음식점 …). null이면 종류를 가리지 않는다
     * @param pageNo        1부터 시작하는 페이지 번호
     */
    public TourApiPage<TourApiPlaceItem> fetchLocationBasedList(
            TourApiService service, double latitude, double longitude,
            int radiusMeters, String contentTypeId, int pageNo) {

        requireValidRadius(radiusMeters);

        Map<String, Object> params = params(
                "numOfRows", properties.numOfRows(),
                "pageNo", pageNo,
                "mapX", longitude,   // x = 경도
                "mapY", latitude,    // y = 위도
                "radius", radiusMeters,
                "arrange", ARRANGE_BY_DISTANCE);

        // 빈 값으로 보내면 API가 "종류 없음"으로 해석해 0건을 줄 수 있다. 없으면 아예 뺀다.
        if (contentTypeId != null && !contentTypeId.isBlank()) {
            params.put("contentTypeId", contentTypeId);
        }

        TourApiResponse<TourApiPlaceItem> response =
                call(uri(service, OP_LOCATION_BASED_LIST, params),
                        new ParameterizedTypeReference<TourApiResponse<TourApiPlaceItem>>() {
                        });
        return TourApiPage.from(response, pageNo, properties.numOfRows());
    }

    /**
     * 반경이 말이 되는 값인지 확인한다.
     *
     * <p><b>상한은 없다</b>(위 설명 참조). 0 이하만 막는다.
     *
     * <p>{@code CustomException}이 아니라 {@link IllegalArgumentException}인 이유는
     * {@link #requireConfigured()}와 같다. 반경은 사용자가 보내는 값이 아니라 {@code TransportType}에서
     * <b>우리가 계산하는 값</b>이므로, 0 이하라면 사용자 입력이 잘못된 것이 아니라 <b>우리 코드의 잘못</b>이다.
     * 사용자에게 보여줄 에러가 아니라 개발 중에 터져야 하는 신호다.
     */
    private void requireValidRadius(int radiusMeters) {
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("반경은 1 이상(미터)이어야 합니다: " + radiusMeters);
        }
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
            // body(...) 대신 toEntity(...)로 받는다. 본문만 받으면 응답 헤더를 볼 수 없는데,
            // 일일 잔량이 헤더로만 오기 때문이다.
            ResponseEntity<TourApiResponse<T>> entity = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        // 4xx든 5xx든 부르는 쪽 입장에서는 "지금 데이터를 못 받는다"로 같다.
                        // 사용자에게 돌려줄 400이 없는 자리라 구분해도 쓸 데가 없다.
                        log.warn("TourAPI 오류 응답: status={}, uri={}", res.getStatusCode(), maskServiceKey(uri));
                        throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
                    })
                    .toEntity(type);

            checkRemainingQuota(entity.getHeaders(), uri);
            return validate(entity.getBody(), uri);
        } catch (CustomException e) {
            throw e;
        } catch (RestClientException e) {
            // XML 에러 문서가 오거나 연결이 끊긴 경우 여기로 온다.
            log.warn("TourAPI 호출 실패 uri={}: {}", maskServiceKey(uri), e.getMessage());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 남은 일일 호출이 얼마 없으면 경고를 남긴다. (roadmap 6-9)
     *
     * <p><b>실패해도 조용히 넘어간다.</b> 이건 부가 정보를 읽는 감시 장치이므로,
     * 헤더가 없거나 숫자가 아니라고 조회 자체를 실패시키면 <b>감시 장치가 본체를 망가뜨리는</b> 셈이 된다.
     *
     * <p>할당량은 <b>서비스·오퍼레이션마다 따로</b> 계산된다(2026-08-15 실측).
     * 그래서 어느 URI에서 나온 값인지 함께 남긴다 — "무엇이 소진되고 있는가"를 알아야 조치할 수 있다.
     */
    private void checkRemainingQuota(HttpHeaders headers, URI uri) {
        String raw = headers.getFirst(HEADER_RATE_LIMIT_REMAINING);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            int remaining = Integer.parseInt(raw.trim());
            if (remaining <= QUOTA_WARNING_THRESHOLD) {
                log.warn("TourAPI 일일 할당량이 얼마 남지 않았습니다 — 남은 호출 {}회, uri={}",
                        remaining, maskServiceKey(uri));
            }
        } catch (NumberFormatException e) {
            log.debug("할당량 헤더를 숫자로 읽지 못했습니다: {}", raw);
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
    /** 국문 관광정보를 부르는 짧은 형태. */
    private URI uri(String operation, Map<String, Object> params) {
        return uri(TourApiService.KOREAN, operation, params);
    }

    private URI uri(TourApiService service, String operation, Map<String, Object> params) {
        requireConfigured();

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrlOf(service))
                .path("/" + operation)
                .queryParam("serviceKey", URLEncoder.encode(properties.serviceKey(), StandardCharsets.UTF_8))
                .queryParam("MobileOS", properties.mobileOs())
                .queryParam("MobileApp", properties.mobileApp())
                .queryParam("_type", "json");
        params.forEach(builder::queryParam);

        return builder.build(true).toUri();
    }

    /** 서비스에 맞는 주소를 고른다. 인증키·파라미터는 둘이 같아서 호스트만 갈아 끼우면 된다. */
    private String baseUrlOf(TourApiService service) {
        return service == TourApiService.ACCESSIBLE ? properties.withBaseUrl() : properties.baseUrl();
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

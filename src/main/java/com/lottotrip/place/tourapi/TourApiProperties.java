package com.lottotrip.place.tourapi;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TourAPI 접속 설정. (roadmap 5-1)
 *
 * <p>{@code @ConfigurationProperties}는 {@code application.yml}의 {@code tourapi.*} 값을
 * 이 record의 필드에 <b>이름을 맞춰 자동으로 담아 주는</b> 표시다.
 * {@code base-url} 같은 하이픈 표기가 {@code baseUrl}에 들어간다.
 *
 * <p>설정을 이렇게 따로 묶는 이유는 <b>값이 코드에 박히지 않게</b> 하기 위해서다.
 * 인증키를 소스에 적으면 저장소에 그대로 올라간다.
 *
 * @param baseUrl     국문 관광정보({@code KorService2}) 주소
 * @param withBaseUrl 무장애 여행 정보({@code KorWithService2}) 주소. 인증키는 같은 것을 쓴다
 * @param serviceKey  공공데이터포털 인증키. <b>디코딩(Decoding) 값</b>을 넣는다.
 *                    인코딩 값을 넣으면 클라이언트가 한 번 더 인코딩해 이중 인코딩이 된다.
 * @param numOfRows   한 번에 받아올 건수. 크게 잡을수록 호출 횟수가 준다. <b>API 상한은 1,000이다</b>
 */
@ConfigurationProperties(prefix = "tourapi")
public record TourApiProperties(
        String baseUrl,
        String withBaseUrl,
        String serviceKey,
        String mobileOs,
        String mobileApp,
        int numOfRows
) {

    private static final String DEFAULT_MOBILE_OS = "ETC";
    private static final String DEFAULT_MOBILE_APP = "lottotrip";
    private static final int DEFAULT_NUM_OF_ROWS = 100;

    /**
     * 무장애 서비스 주소의 기본값.
     *
     * <p>국문 관광정보와 <b>같은 호스트·같은 인증키</b>를 쓰고 경로 끝만 다르다.
     * 설정에서 빠뜨려도 무장애 조회가 통째로 죽지 않도록 기본값을 준다.
     */
    private static final String DEFAULT_WITH_BASE_URL = "https://apis.data.go.kr/B551011/KorWithService2";

    /**
     * record의 <b>compact 생성자</b>. 값이 필드에 담기기 <i>직전</i>에 끼어들어 다듬을 수 있다.
     *
     * <p>설정이 비어 있을 때 기본값을 여기서 채워 두면, 이 record를 쓰는 쪽은
     * "null일 수도 있다"를 신경 쓰지 않아도 된다.
     */
    public TourApiProperties {
        if (withBaseUrl == null || withBaseUrl.isBlank()) {
            withBaseUrl = DEFAULT_WITH_BASE_URL;
        }
        if (mobileOs == null || mobileOs.isBlank()) {
            mobileOs = DEFAULT_MOBILE_OS;
        }
        if (mobileApp == null || mobileApp.isBlank()) {
            mobileApp = DEFAULT_MOBILE_APP;
        }
        if (numOfRows <= 0) {
            numOfRows = DEFAULT_NUM_OF_ROWS;
        }
    }

    /** 인증키가 채워져 있는지. 비어 있으면 호출해 봐야 인증 오류만 돌아온다. */
    public boolean isConfigured() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}

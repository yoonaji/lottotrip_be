package com.lottotrip.place.batch;

import com.lottotrip.place.entity.City;
import com.lottotrip.place.entity.State;
import com.lottotrip.place.repository.CityRepository;
import com.lottotrip.place.repository.StateRepository;
import com.lottotrip.place.tourapi.TourApiClient;
import com.lottotrip.place.tourapi.TourApiProperties;
import com.lottotrip.support.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 지역코드 시드 검증. (roadmap 5-8, 결정 10)
 *
 * <p>TourAPI는 장소의 지역을 <b>코드로만</b> 준다. 이 시드가 코드와 이름을 미리 이어 두어야
 * 장소 적재(5-9)에서 {@code places.city_id}를 채울 수 있다.
 *
 * <p>DB는 진짜를 쓰고(중복 저장 여부는 실제 제약으로만 확인된다) 바깥 호출만 막는다.
 * {@link MockRestServiceServer}가 {@code RestClient} 안쪽에서 요청을 가로채므로 네트워크를 타지 않는다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RegionSeederTest extends PostgresContainerSupport {

    private static final String GANGWON = "32";

    /** 시도 목록 응답. {@code areaCode2}를 지역 코드 없이 부르면 이 모양이 온다. */
    private static final String STATE_LIST = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      { "rnum": 1, "code": "1",  "name": "서울" },
                      { "rnum": 2, "code": "32", "name": "강원" }
                    ]
                  },
                  "numOfRows": 100, "pageNo": 1, "totalCount": 2
                }
              }
            }
            """;

    /** 강원 시군구 목록. 지역 코드를 주면 그 시도의 시군구가 <b>같은 모양</b>으로 온다. */
    private static final String GANGWON_CITY_LIST = """
            {
              "response": {
                "header": { "resultCode": "0000", "resultMsg": "OK" },
                "body": {
                  "items": {
                    "item": [
                      { "rnum": 1, "code": "1", "name": "강릉시" },
                      { "rnum": 2, "code": "2", "name": "고성군" },
                      { "rnum": 3, "code": "3", "name": "동해시" }
                    ]
                  },
                  "numOfRows": 100, "pageNo": 1, "totalCount": 3
                }
              }
            }
            """;

    @Autowired
    private StateRepository stateRepository;

    @Autowired
    private CityRepository cityRepository;

    private MockRestServiceServer mockServer;
    private RegionSeeder seeder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        TourApiClient client = new TourApiClient(builder,
                new TourApiProperties("https://apis.data.go.kr/B551011/KorService2", null, "test-key", null, null, 100));
        seeder = new RegionSeeder(client, stateRepository, cityRepository);
    }

    /** 시도 목록 → 강원 시군구 목록 순으로 응답을 준비한다. */
    private void expectBothCalls() {
        mockServer.expect(requestTo(not(containsString("areaCode="))))
                .andRespond(withSuccess(STATE_LIST, MediaType.APPLICATION_JSON));
        mockServer.expect(requestTo(containsString("areaCode=32")))
                .andRespond(withSuccess(GANGWON_CITY_LIST, MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("시도와 시군구를 코드와 함께 저장한다")
    void seedsStatesAndCitiesWithCodes() {
        expectBothCalls();

        seeder.seed(GANGWON);

        State gangwon = stateRepository.findByTourAreaCode("32").orElseThrow();
        assertThat(gangwon.getStateName()).isEqualTo("강원");
        assertThat(stateRepository.findByTourAreaCode("1")).isPresent();

        City gangneung = cityRepository
                .findByStateIdAndTourSigunguCode(gangwon.getId(), "1").orElseThrow();
        assertThat(gangneung.getCityName()).isEqualTo("강릉시");
        assertThat(cityRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("호출은 2회뿐이다 — 시도 목록 1회 + 대상 시도의 시군구 1회")
    void callsAreaCodeApiTwice() {
        // 강원만 적재하므로 나머지 16개 시도의 시군구는 받지 않는다.
        // 전국을 받으면 18회가 되고, 지금 필요하지 않은 데이터가 절반 이상 쌓인다.
        expectBothCalls();

        seeder.seed(GANGWON);

        mockServer.verify();
    }

    @Test
    @DisplayName("대상 시도의 시군구만 받는다 — 다른 시도는 건드리지 않는다")
    void seedsCitiesOnlyForTargetState() {
        expectBothCalls();

        seeder.seed(GANGWON);

        State seoul = stateRepository.findByTourAreaCode("1").orElseThrow();
        // 서울은 시도 행만 생기고 시군구는 비어 있어야 한다.
        assertThat(cityRepository.findByStateIdAndTourSigunguCode(seoul.getId(), "1")).isEmpty();
    }

    @Test
    @DisplayName("두 번 돌려도 지역이 중복되지 않는다")
    void isIdempotent() {
        // 시드는 여러 번 돈다(적재 실패 후 재시도, 지역이 늘었을 때 다시 받기).
        // 중복되면 places.city_id를 이을 때 어느 행에 붙일지 정할 수 없다.
        expectBothCalls();
        seeder.seed(GANGWON);

        mockServer.reset();
        expectBothCalls();
        seeder.seed(GANGWON);

        assertThat(stateRepository.findAll()).hasSize(2);
        assertThat(cityRepository.findAll()).hasSize(3);
    }

    @Test
    @DisplayName("이름이 바뀌면 기존 행을 고친다 — 코드가 같으면 같은 지역이다")
    void updatesNameWhenCodeAlreadyExists() {
        // TourAPI의 지역명은 바뀔 수 있다("강원" → "강원특별자치도").
        // 코드가 같으면 같은 지역이므로 새 행을 만들지 않고 이름만 갱신한다.
        stateRepository.save(State.create("옛이름", "32"));

        expectBothCalls();
        seeder.seed(GANGWON);

        assertThat(stateRepository.findAll()).hasSize(2);
        assertThat(stateRepository.findByTourAreaCode("32").orElseThrow().getStateName())
                .isEqualTo("강원");
    }
}

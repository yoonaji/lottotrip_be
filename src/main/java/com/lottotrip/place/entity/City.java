package com.lottotrip.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시·군·구. (tour_api_erd.md 1 — cities)
 *
 * 장소는 시·군에 속하고, 시·군은 광역 지역에 속한다.
 */
@Entity
@Table(
        name = "cities",
        // 시군구 코드는 시·도 안에서만 유일하다. 강원의 "1"은 강릉시, 서울의 "1"은 강남구다.
        // tour_sigungu_code 단독으로 UNIQUE를 걸면 전국으로 넓히는 순간 깨진다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cities_state_sigungu", columnNames = {"state_id", "tour_sigungu_code"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "city_id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id", nullable = false)
    private State state;

    @Column(name = "city_name", nullable = false, length = 30)
    private String cityName;

    /**
     * TourAPI 시군구 코드. 강원 기준 강릉시 = `"1"`. (roadmap 5-8)
     *
     * 이 값만으로는 시·군을 특정할 수 없다. 시군구 코드는 시·도 안에서만 유일해서,
     * 강원의 `"1"`은 강릉시지만 서울의 `"1"`은 강남구다.
     * 항상 {@link #state}와 함께 봐야 한다 — UNIQUE 제약도 두 컬럼을 묶어 건 이유가 이것이다.
     *
     * nullable인 이유는 시드 전에도 시·군 행을 만들 수 있어야 하기 때문이다.
     * PostgreSQL의 UNIQUE는 NULL을 서로 다른 값으로 보므로, 코드가 없는 시·군이 여럿이어도 걸리지 않는다.
     */
    @Column(name = "tour_sigungu_code", length = 10)
    private String tourSigunguCode;

    private City(State state, String cityName, String tourSigunguCode) {
        this.state = state;
        this.cityName = cityName;
        this.tourSigunguCode = tourSigunguCode;
    }

    /** 지역명만 아는 경우. TourAPI 코드는 나중에 시드로 채운다. */
    public static City create(State state, String cityName) {
        return new City(state, cityName, null);
    }

    /** TourAPI 시드가 쓰는 생성 경로. 코드까지 함께 담는다. */
    public static City create(State state, String cityName, String tourSigunguCode) {
        return new City(state, cityName, tourSigunguCode);
    }

    /**
     * 시·군 이름을 갱신한다. 시드가 **같은 시도 안에서 코드가 같은 기존 행을 찾았을 때**만 쓴다.
     *
     * {@link State#rename(String)}과 같은 이유다. 소속 시도와 코드는 이 행이 무엇인지 정하는 값이라
     * 바꾸지 않는다.
     */
    public void rename(String cityName) {
        this.cityName = cityName;
    }
}

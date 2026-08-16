package com.lottotrip.place.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 광역 지역(시·도). (tour_api_erd.md 1 — states)
 *
 * "강원특별자치도 → 강릉시"처럼 지역을 2단계로 나눈 것 중 윗단계다.
 * 전국 17개 수준이라 `Integer`로 충분하다(회원·장소는 `Long`).
 */
@Entity
@Table(name = "states")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class State {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "state_id")
    private Integer id;

    @Column(name = "state_name", nullable = false, length = 20)
    private String stateName;

    /**
     * TourAPI 시도 코드. 강원 = `"32"`, 서울 = `"1"`. (roadmap 5-8, 결정 10)
     *
     * TourAPI는 장소의 지역을 **코드로만** 준다(이름은 주지 않는다). 이 코드를 담아 두어야
     * 장소를 적재할 때 어느 지역 행에 붙일지 정할 수 있다.
     *
     * **숫자가 아니라 문자열인 이유:** 우리가 계산에 쓰는 값이 아니라 API가 준 식별자를
     * 그대로 보관하는 것이다. 앞자리 0이 붙거나 숫자가 아닌 코드가 생겨도 그대로 담긴다.
     *
     * nullable인 이유는 시드 전에도 지역 행을 만들 수 있어야 하기 때문이다(테스트 픽스처 등).
     */
    @Column(name = "tour_area_code", length = 10)
    private String tourAreaCode;

    private State(String stateName, String tourAreaCode) {
        this.stateName = stateName;
        this.tourAreaCode = tourAreaCode;
    }

    /** 지역명만 아는 경우. TourAPI 코드는 나중에 시드로 채운다. */
    public static State create(String stateName) {
        return new State(stateName, null);
    }

    /** TourAPI 시드가 쓰는 생성 경로. 코드까지 함께 담는다. */
    public static State create(String stateName, String tourAreaCode) {
        return new State(stateName, tourAreaCode);
    }

    /**
     * 지역명을 갱신한다. 시드가 **코드가 같은 기존 행을 찾았을 때**만 쓴다.
     *
     * TourAPI의 지역명은 바뀔 수 있다("강원" → "강원특별자치도"). 코드가 같으면 같은 지역이므로
     * 행을 새로 만들지 않고 이름만 맞춘다. 새로 만들면 `places.city_id`가 어느 쪽을 가리켜야 할지
     * 알 수 없게 된다.
     *
     * 바꿀 수 있는 것은 이름뿐이다. `tourAreaCode`는 이 행이 무엇인지 정하는 값이라
     * 바뀌면 다른 지역이 되어 버린다.
     */
    public void rename(String stateName) {
        this.stateName = stateName;
    }
}

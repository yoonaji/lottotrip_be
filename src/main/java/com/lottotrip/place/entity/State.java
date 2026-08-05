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
 * <p>"강원특별자치도 → 강릉시"처럼 지역을 2단계로 나눈 것 중 윗단계다.
 * 전국 17개 수준이라 {@code Integer}로 충분하다(회원·장소는 {@code Long}).
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

    private State(String stateName) {
        this.stateName = stateName;
    }

    public static State create(String stateName) {
        return new State(stateName);
    }
}

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시·군·구. (tour_api_erd.md 1 — cities)
 *
 * <p>장소는 시·군에 속하고, 시·군은 광역 지역에 속한다.
 */
@Entity
@Table(name = "cities")
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

    private City(State state, String cityName) {
        this.state = state;
        this.cityName = cityName;
    }

    public static City create(State state, String cityName) {
        return new City(state, cityName);
    }
}

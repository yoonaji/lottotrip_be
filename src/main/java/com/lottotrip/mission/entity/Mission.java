package com.lottotrip.mission.entity;

import com.lottotrip.place.entity.Place;
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
 * 장소별 미션 마스터. (tour_api_erd.md 1 — missions)
 *
 * 회원이 수행하는 기록이 아니라 **미리 등록해 두는 후보**다.
 * 슬롯이 장소를 뽑으면 그 장소에 등록된 미션 중 하나를 골라 함께 내려준다.
 */
@Entity
@Table(name = "missions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(nullable = false, length = 100)
    private String title;

    /** 미션 수행 방법 안내. 없어도 미션은 성립하므로 nullable이다. */
    @Column(name = "guide_description", columnDefinition = "TEXT")
    private String guideDescription;

    @Column(name = "guide_image_url", columnDefinition = "TEXT")
    private String guideImageUrl;

    @Column(name = "reward_point", nullable = false)
    private Integer rewardPoint;

    private Mission(Place place, String title, String guideDescription, String guideImageUrl, Integer rewardPoint) {
        this.place = place;
        this.title = title;
        this.guideDescription = guideDescription;
        this.guideImageUrl = guideImageUrl;
        this.rewardPoint = rewardPoint;
    }

    public static Mission create(Place place, String title, String guideDescription,
                                 String guideImageUrl, Integer rewardPoint) {
        return new Mission(place, title, guideDescription, guideImageUrl, rewardPoint);
    }
}

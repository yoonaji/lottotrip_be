package com.lottotrip.slot.entity;

import com.lottotrip.mission.entity.Mission;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 슬롯 추첨 결과. (tour_api_erd.md 1 — saved_slots)
 *
 * PK인 `slot_id`가 API 응답의 `slotId`이고, 코스에 담을 때 참조하는 값이다.
 * (결정 3 — `resultId`가 아니라 `slotId`, Entity명은 `SlotResult`가 아니라 `SavedSlot`)
 *
 * 코스(`course_items`)와 달리 같은 장소가 다시 뽑히는 것을 막지 않는다.
 * 랜덤 추첨이므로 중복은 정상적인 결과다.
 */
@Entity
@Table(name = "saved_slots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SavedSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "slot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private TripSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    /**
     * 이 슬롯에서 **실제로 제시한** 미션. (roadmap 6-13, 결정 14)
     *
     * **왜 필요한가.** 이 컬럼이 없을 때는 결과 조회가 "그 장소의 가장 먼저 등록된 미션"을
     * 돌려줬는데, draw가 보여 준 것과 다를 수 있었다. 2026-08-15 실측에서 실제로 재현됐다 —
     * draw는 `missionId 3`, 같은 슬롯의 조회는 `1`. 사용자 입장에서는
     * **같은 슬롯을 다시 열었더니 미션이 바뀌어 있는** 셈이다.
     *
     * **nullable인 이유.** 미션은 곁들이는 정보라 확보하지 못해도 장소는 이미 뽑혔다.
     * NOT NULL로 두면 미션 생성이 실패했을 때 슬롯 저장까지 통째로 실패한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id")
    private Mission mission;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private SavedSlot(TripSession session, Place place, Mission mission) {
        this.session = session;
        this.place = place;
        this.mission = mission;
    }

    /**
     * 미션까지 함께 남긴다.
     *
     * @param mission 제시한 미션. 확보하지 못했으면 null
     */
    public static SavedSlot create(TripSession session, Place place, Mission mission) {
        return new SavedSlot(session, place, mission);
    }
}

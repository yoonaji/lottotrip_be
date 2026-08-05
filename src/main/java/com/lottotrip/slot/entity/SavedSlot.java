package com.lottotrip.slot.entity;

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
 * <p>PK인 {@code slot_id}가 API 응답의 {@code slotId}이고, 코스에 담을 때 참조하는 값이다.
 * (결정 3 — {@code resultId}가 아니라 {@code slotId}, Entity명은 {@code SlotResult}가 아니라 {@code SavedSlot})
 *
 * <p>코스({@code course_items})와 달리 같은 장소가 다시 뽑히는 것을 막지 않는다.
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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private SavedSlot(TripSession session, Place place) {
        this.session = session;
        this.place = place;
    }

    public static SavedSlot create(TripSession session, Place place) {
        return new SavedSlot(session, place);
    }
}

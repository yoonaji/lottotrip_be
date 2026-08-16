package com.lottotrip.mission.entity;

import com.lottotrip.common.enums.MediaType;
import com.lottotrip.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 회원의 미션 수행 기록. (tour_api_erd.md 1 — user_missions)
 *
 * 미션을 완료 처리할 때 한 줄이 생긴다. 줄이 있다는 것 자체가 "이미 완료했다"는 뜻이다.
 */
@Entity
@Table(
        name = "user_missions",
        // 명세의 409 ALREADY_COMPLETED를 DB 차원에서도 보장한다.
        // 서비스에서 "이미 있나 조회 → 없으면 저장"만 하면, 같은 요청이 동시에 두 번 들어올 때
        // 둘 다 조회를 통과해 포인트가 두 번 지급될 수 있다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_missions_user_mission",
                columnNames = {"user_id", "mission_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_mission_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    /**
     * 인증 사진·영상 주소.
     *
     * 인증은 GPS 좌표 비교 방식으로 우선 구현하므로 **비어 있을 수 있다.**
     * 사진 인증을 전제로 NOT NULL로 잡으면 GPS 인증 자체가 막힌다.
     */
    @Column(name = "certified_media_url", columnDefinition = "TEXT")
    private String certifiedMediaUrl;

    /** ERD 컬럼명은 `m_type`이다. 사진 인증이 아니면 비어 있다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "m_type", length = 20)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MissionStatus status;

    @CreationTimestamp
    @Column(name = "certified_at", updatable = false)
    private LocalDateTime certifiedAt;

    private UserMission(User user, Mission mission, String certifiedMediaUrl, MediaType mediaType) {
        this.user = user;
        this.mission = mission;
        this.certifiedMediaUrl = certifiedMediaUrl;
        this.mediaType = mediaType;
        this.status = MissionStatus.COMPLETED;
    }

    /** GPS 좌표로 인증해 완료 처리한다. 현재 명세의 기본 방식이다. */
    public static UserMission complete(User user, Mission mission) {
        return new UserMission(user, mission, null, null);
    }

    /** 사진·영상으로 인증해 완료 처리한다. (추후 도입) */
    public static UserMission completeWithMedia(User user, Mission mission,
                                                String certifiedMediaUrl, MediaType mediaType) {
        return new UserMission(user, mission, certifiedMediaUrl, mediaType);
    }
}

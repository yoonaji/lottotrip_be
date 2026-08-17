package com.lottotrip.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 회원. (tour_api_erd.md 1 — users)
 *
 * `@Entity`는 "이 클래스는 DB 테이블 한 줄에 대응한다"는 표시다.
 * 붙여 두면 JPA가 객체를 INSERT/SELECT 문으로 바꿔 준다.
 */
@Entity
@Table(name = "users") // user는 PostgreSQL 예약어라 테이블명을 users로 명시한다
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /**
     * `IDENTITY`는 "번호를 DB가 매긴다"는 뜻이다.
     * ERD의 `BIGSERIAL`이 PostgreSQL에서 자동 증가 컬럼이므로 여기에 맞춘다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 애플 로그인에서 사용자가 이메일 가리기를 선택하면 이메일이 오지 않는다. 그래서 nullable이다. */
    @Column(length = 100)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(name = "profile_image_url", columnDefinition = "TEXT")
    private String profileImageUrl;

    /** 저장할 때 현재 시각이 자동으로 들어간다. 서비스 코드에서 매번 넣으면 언젠가 빠뜨린다. */
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 탈퇴 시각. NULL이면 정상 회원이다. (roadmap 9-5, 결정 20)
     *
     * 행을 지우지 않고 이 값으로 표시하는 이유는 `users`를 참조하는 FK 네 개
     * (`social_auth`·`trip_sessions`·`travel_courses`·`user_missions`)가 전부 `NO ACTION`이라
     * 삭제하면 제약 위반으로 실패하고, CASCADE로 바꾸면 여행·미션 이력이 통째로 사라지기 때문이다.
     *
     * ⚠️ **이 값이 채워졌다고 개인정보가 남아 있는 것은 아니다.** 식별정보는 {@link #withdraw()}가
     * 실제로 지운다. 남는 것은 식별력 없는 껍데기와 이용기록뿐이다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private User(String email, String nickname, String profileImageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 회원을 새로 만든다.
     *
     * 생성자를 직접 열지 않고 이름 있는 메서드를 두면 "언제 쓰는 것인지"가 드러난다.
     * 나중에 가입 경로가 늘어나도 여기에 메서드를 하나 더 추가하면 된다.
     */
    public static User create(String email, String nickname, String profileImageUrl) {
        return new User(email, nickname, profileImageUrl);
    }

    /**
     * 탈퇴 처리. **식별정보를 실제로 지우고** 탈퇴 시각을 남긴다. (roadmap 9-5, 결정 20)
     *
     * "플래그만 세우는 소프트 삭제"로는 안 된다. 개인정보가 그대로 남으면
     * 앱스토어 심사 요건(5.1.1(v))도 개인정보 파기 의무도 만족하지 못한다.
     * 그래서 여기서 이메일·닉네임·프로필 사진을 **NULL로 덮는다.** 세 컬럼 모두 원래
     * nullable이라(애플 이메일 가리기 대응) 컬럼을 손대지 않고 지울 수 있다.
     *
     * 지우지 않는 것은 `user_id`와 `created_at`뿐이다. 둘 다 그 자체로는 사람을 가리키지 못하고,
     * 여행·미션 이력이 매달려 있어야 하는 자리다.
     *
     * 두 번 불러도 안전하지만, **호출하는 쪽이 이미 탈퇴한 회원을 걸러 낸다**
     * (`findByIdAndDeletedAtIsNull`). 여기서 다시 검사하지 않는 이유는 탈퇴 시각이
     * 나중 호출로 덮이면 "언제 탈퇴했는가"가 흐려지기 때문이다.
     */
    public void withdraw() {
        this.email = null;
        this.nickname = null;
        this.profileImageUrl = null;
        this.deletedAt = LocalDateTime.now();
    }

    /** 탈퇴한 회원인지. 조회 조건으로 거르는 것이 원칙이고, 이 메서드는 확인용이다. */
    public boolean isDeleted() {
        return deletedAt != null;
    }
}

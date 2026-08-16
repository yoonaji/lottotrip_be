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
}

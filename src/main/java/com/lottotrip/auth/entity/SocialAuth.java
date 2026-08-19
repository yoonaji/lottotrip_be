package com.lottotrip.auth.entity;

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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 소셜 로그인 연동 정보. (tour_api_erd.md 1 — social_auth)
 *
 * 한 회원이 카카오·애플·구글을 각각 연결할 수 있으므로 회원 하나에 여러 줄이 붙는다.
 * 로그인은 `provider + providerUserId`로 사람을 찾는다.
 */
@Entity
@Table(
        name = "social_auth",
        // 같은 소셜 계정으로 회원이 두 명 생기면 로그인할 때마다 다른 사람이 된다.
        // 조회 조건이 곧 유일해야 하는 조합이므로 DB 차원에서 막는다.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_social_auth_provider_user",
                columnNames = {"provider", "provider_user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "auth_id")
    private Long id;

    /**
     * `LAZY`는 "회원 정보는 실제로 쓸 때 가져온다"는 뜻이다.
     * 기본값(`EAGER`)으로 두면 소셜 계정을 읽을 때마다 회원 테이블까지 항상 함께 조회한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * `STRING`은 enum을 이름 그대로 저장한다.
     * 기본값(`ORDINAL`)은 순서를 숫자로 저장하는데, 나중에 값을 중간에 끼워 넣으면
     * 이미 저장된 데이터의 의미가 통째로 뒤바뀐다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderType provider;

    /** 소셜 서비스가 발급한 사용자 식별자. 카카오는 숫자, 애플·구글은 문자열이라 문자열로 받는다. */
    @Column(name = "provider_user_id", nullable = false, length = 100)
    private String providerUserId;

    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private SocialAuth(User user, ProviderType provider, String providerUserId,
                       String accessToken, String refreshToken) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public static SocialAuth create(User user, ProviderType provider, String providerUserId,
                                    String accessToken, String refreshToken) {
        return new SocialAuth(user, provider, providerUserId, accessToken, refreshToken);
    }

    /**
     * 이미 가입한 회원이 다시 로그인했을 때 소셜 토큰만 새 값으로 바꾼다.
     *
     * `setAccessToken(...)` 같은 setter를 열어 두지 않는 이유는, 아무 데서나
     * 값을 바꿀 수 있게 되면 "언제 왜 바뀌었는지"를 코드에서 추적할 수 없게 되기 때문이다.
     * 바꿔야 하는 상황마다 이름 있는 메서드를 만든다.
     */
    public void updateTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }
}

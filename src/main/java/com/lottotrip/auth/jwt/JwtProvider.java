package com.lottotrip.auth.jwt;

import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급 · 검증 · 클레임 추출을 담당한다. (roadmap 4-1)
 *
 * <h2>JWT가 무엇인가</h2>
 * <p>"이 요청을 보낸 사람은 3번 회원이다"를 담은 문자열이다. 세 부분이 점으로 이어져 있다.
 * <pre>{@code 헤더.내용(클레임).서명}</pre>
 * 앞의 두 부분은 그냥 인코딩된 것이라 누구나 읽을 수 있다. <b>비밀은 담지 않는다.</b>
 * 마지막 서명은 서버만 아는 비밀키로 만든 값이라, 내용이 한 글자라도 바뀌면 검증에 실패한다.
 * 덕분에 서버는 로그인 상태를 DB나 메모리에 들고 있지 않아도 된다(무상태, stateless).
 *
 * <h2>토큰이 두 종류인 이유</h2>
 * <ul>
 *   <li><b>액세스 토큰</b> — API를 호출할 때마다 들고 다닌다. 자주 노출되므로 수명을 짧게(1시간) 둔다.</li>
 *   <li><b>리프레시 토큰</b> — 액세스 토큰이 만료됐을 때 새로 받아오는 용도로만 쓴다.
 *       수명이 길지만(2주) 쓰이는 횟수가 적어 노출 위험이 낮다.</li>
 * </ul>
 * 짧은 수명 하나로만 운영하면 사용자가 한 시간마다 다시 로그인해야 하고,
 * 긴 수명 하나로만 운영하면 탈취당했을 때 2주 동안 뚫린다. 그래서 나눈다.
 *
 * <p>두 토큰은 생김새가 같고 둘 다 우리 키로 서명한 진짜 토큰이라 서명 검증만으로는 구분되지 않는다.
 * 그래서 내용에 용도({@code type})를 심어두고 꺼낼 때 대조한다.
 */
@Slf4j
@Component
public class JwtProvider {

    /** 토큰의 용도를 담는 클레임 이름. */
    private static final String CLAIM_TYPE = "type";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /** HS256이 요구하는 최소 키 길이(바이트). 256비트 = 32바이트. */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtProvider(JwtProperties properties) {
        this.key = toKey(properties.secret());
        this.accessTokenValiditySeconds = properties.accessTokenValiditySeconds();
        this.refreshTokenValiditySeconds = properties.refreshTokenValiditySeconds();
    }

    /**
     * 설정된 비밀키 문자열을 서명용 키로 바꾼다.
     *
     * <p>키가 없거나 짧으면 <b>여기서 즉시 실패</b>시킨다. 이 클래스는 스프링이 뜰 때 만들어지므로,
     * 잘못된 설정은 서버가 뜨는 순간 드러난다. 이 검사가 없으면 서버는 멀쩡히 떠 있다가
     * 첫 로그인 요청에서야 500을 내고, 그때는 이미 배포가 끝난 뒤다.
     */
    private SecretKey toKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret은 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다. 환경변수 JWT_SECRET을 확인하세요.");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // ---------- 발급 ----------

    public String createAccessToken(Long userId) {
        return createToken(userId, TYPE_ACCESS, accessTokenValiditySeconds);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, TYPE_REFRESH, refreshTokenValiditySeconds);
    }

    private String createToken(Long userId, String type, long validitySeconds) {
        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + validitySeconds * 1000L);

        return Jwts.builder()
                // subject는 "이 토큰이 누구의 것인가"를 담는 표준 자리다. 문자열만 담을 수 있다.
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, type)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    // ---------- 검증 & 추출 ----------

    /**
     * 액세스 토큰에서 userId를 꺼낸다.
     *
     * <p>없거나·위조됐거나·만료됐거나·용도가 다르면 모두 {@link ErrorCode#UNAUTHORIZED}다.
     * 이유를 응답으로 구분해주지 않는 이유는, 공격자에게 "서명은 맞는데 만료됐다" 같은
     * 힌트를 주지 않기 위해서다. 원인은 로그에만 남긴다. (tour_api_erd.md 4-1)
     */
    public Long getUserIdFromAccessToken(String token) {
        return getUserId(token, TYPE_ACCESS, ErrorCode.UNAUTHORIZED);
    }

    /** 리프레시 토큰에서 userId를 꺼낸다. 실패는 모두 {@link ErrorCode#INVALID_REFRESH_TOKEN}이다. */
    public Long getUserIdFromRefreshToken(String token) {
        return getUserId(token, TYPE_REFRESH, ErrorCode.INVALID_REFRESH_TOKEN);
    }

    private Long getUserId(String token, String expectedType, ErrorCode errorOnFailure) {
        Claims claims = parseClaims(token, errorOnFailure);

        if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            log.debug("토큰 용도 불일치: 기대={}, 실제={}", expectedType, claims.get(CLAIM_TYPE, String.class));
            throw new CustomException(errorOnFailure);
        }

        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            log.debug("토큰 subject가 userId 형식이 아님: {}", claims.getSubject());
            throw new CustomException(errorOnFailure);
        }
    }

    /**
     * 토큰의 서명과 만료를 검사하고 내용을 꺼낸다.
     *
     * <p>{@code parseSignedClaims}는 서명이 맞지 않거나 이미 만료된 토큰이면 예외를 던진다.
     * jjwt가 던지는 예외는 모두 {@link JwtException}의 자식이므로 한 번에 잡아
     * 우리 쪽 예외 체계로 바꿔준다. 라이브러리 예외가 서비스 계층까지 새어 나가면
     * 나중에 라이브러리를 갈아탈 때 손댈 곳이 많아진다.
     */
    private Claims parseClaims(String token, ErrorCode errorOnFailure) {
        if (token == null || token.isBlank()) {
            throw new CustomException(errorOnFailure);
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 검증 실패: {}", e.getMessage());
            throw new CustomException(errorOnFailure);
        }
    }
}

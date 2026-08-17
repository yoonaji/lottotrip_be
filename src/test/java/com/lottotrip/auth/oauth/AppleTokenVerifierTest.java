package com.lottotrip.auth.oauth;

import com.lottotrip.auth.entity.ProviderType;
import com.lottotrip.common.exception.CustomException;
import com.lottotrip.common.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 애플 identity token 검증 구현체 검증. (roadmap 4-4-3)
 *
 * 방식은 구글과 같아 {@link JwksProvider}를 그대로 재사용한다. 우리가 만든 RSA 키 쌍으로
 * 토큰에 서명하고 그 공개키를 담은 가짜 JWKS를 돌려준다 — 서명 검증은 진짜 알고리즘이 돈다.
 *
 * 구글과 다른 점: 이름·사진이 토큰에 없어 항상 null / `iss`가 하나뿐 /
 * 이메일이 없거나 익명 주소일 수 있음 / `email_verified`가 문자열 `"true"`로 온다.
 */
class AppleTokenVerifierTest {

    private static final String JWKS_URI = "https://appleid.apple.com/auth/keys";
    private static final String ISSUER = "https://appleid.apple.com";
    /** 애플의 `aud`는 iOS 앱의 번들 ID(웹이면 Service ID)다. 구글처럼 클라이언트 ID 문자열이 아니다. */
    private static final String AUDIENCE = "com.lottotrip.app";
    private static final String KID = "apple-key-1";

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private AppleTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        if (keyPair == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048); // RS256은 2048비트 이상을 요구한다
            keyPair = generator.generateKeyPair();
            otherKeyPair = generator.generateKeyPair();
        }
        builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        verifier = newVerifier(List.of(AUDIENCE));
    }

    private AppleTokenVerifier newVerifier(List<String> audiences) {
        return new AppleTokenVerifier(builder, new AppleOAuthProperties(JWKS_URI, audiences));
    }

    // ---------- 테스트용 토큰·JWKS 만들기 ----------

    /** 애플이 줄 법한 정상 토큰. 클레임 구성을 애플 문서에 맞췄다. */
    private String signedToken(PrivateKey privateKey, String kid, String issuer, String audience, Date expiration) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject("001234.abcdef0123456789.1234")   // 애플의 sub는 이런 모양이다
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("email", "potato@privaterelay.appleid.com")
                .claim("email_verified", "true")           // ⚠️ 애플은 문자열로 준다
                .claim("is_private_email", "true")
                .issuedAt(new Date())
                .expiration(expiration)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    private String validToken() {
        return signedToken(keyPair.getPrivate(), KID, ISSUER, AUDIENCE, oneHourLater());
    }

    private Date oneHourLater() {
        return new Date(System.currentTimeMillis() + 3_600_000L);
    }

    private String jwksJson(KeyPair pair, String kid) {
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return """
                {"keys":[{
                  "kty":"RSA","use":"sig","alg":"RS256","kid":"%s","n":"%s","e":"%s"
                }]}
                """.formatted(
                kid,
                encoder.encodeToString(publicKey.getModulus().toByteArray()),
                encoder.encodeToString(publicKey.getPublicExponent().toByteArray()));
    }

    private void expectJwks(String body) {
        mockServer.expect(requestTo(JWKS_URI))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    // ---------- 성공 ----------

    @Test
    @DisplayName("정상 토큰이면 클레임을 OAuthUserInfo로 변환한다")
    void returnsUserInfoOnValidToken() {
        expectJwks(jwksJson(keyPair, KID));

        OAuthUserInfo info = verifier.verify(validToken());

        assertThat(info.providerUserId()).isEqualTo("001234.abcdef0123456789.1234");
        assertThat(info.email()).isEqualTo("potato@privaterelay.appleid.com");
        mockServer.verify();
    }

    @Test
    @DisplayName("이름·사진은 항상 없다 — 애플은 identity token에 담지 않는다")
    void neverHasNameOrPicture() {
        // 애플은 이름을 최초 인증 응답 본문에 한 번만 준다. 여기서 억지로 채우려 하면
        // "있는 줄 알았는데 없는" 필드가 생긴다. 없는 것은 없는 대로 null로 넘긴다.
        // 프론트가 최초 로그인 때 이름을 따로 보내 주기로 하면 그때 명세가 바뀐다.
        expectJwks(jwksJson(keyPair, KID));

        OAuthUserInfo info = verifier.verify(validToken());

        assertThat(info.nickname()).isNull();
        assertThat(info.profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("이메일 가리기로 이메일이 없어도 sub만 있으면 통과한다")
    void allowsMissingEmail() {
        // 애플 Private Relay. users.email이 nullable인 이유가 이것이다. (결정 4)
        expectJwks(jwksJson(keyPair, KID));
        String noEmail = Jwts.builder()
                .header().keyId(KID).and()
                .subject("001234.abcdef0123456789.1234")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .expiration(oneHourLater())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        OAuthUserInfo info = verifier.verify(noEmail);

        assertThat(info.providerUserId()).isEqualTo("001234.abcdef0123456789.1234");
        assertThat(info.email()).isNull();
    }

    // ---------- 토큰이 잘못된 경우 ----------

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongSignature() {
        expectJwks(jwksJson(keyPair, KID));
        String forged = signedToken(otherKeyPair.getPrivate(), KID, ISSUER, AUDIENCE, oneHourLater());

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("aud가 우리 앱(번들 ID)이 아니면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongAudience() {
        // 서명이 진짜여도 남의 앱을 위해 발급된 토큰이면 받으면 안 된다.
        // 공격자가 자기 앱에 애플 로그인을 붙여 받은 진짜 토큰을 우리 서버로 보내는 것을 막는다.
        expectJwks(jwksJson(keyPair, KID));
        String otherApp = signedToken(keyPair.getPrivate(), KID, ISSUER, "com.someone.else", oneHourLater());

        assertThatThrownBy(() -> verifier.verify(otherApp))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("issuer가 애플이 아니면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongIssuer() {
        // ⚠️ 구글과 다르다. 구글은 스킴이 없는 형태(accounts.google.com)도 발급하지만
        // 애플은 https://appleid.apple.com 하나뿐이다.
        expectJwks(jwksJson(keyPair, KID));
        String token = signedToken(keyPair.getPrivate(), KID, "appleid.apple.com", AUDIENCE, oneHourLater());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("만료된 토큰이면 INVALID_PROVIDER_TOKEN")
    void rejectsExpiredToken() {
        expectJwks(jwksJson(keyPair, KID));
        Date past = new Date(System.currentTimeMillis() - 60_000L);
        String expired = signedToken(keyPair.getPrivate(), KID, ISSUER, AUDIENCE, past);

        assertThatThrownBy(() -> verifier.verify(expired))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("sub 없는 토큰이면 INVALID_PROVIDER_TOKEN")
    void rejectsTokenWithoutSubject() {
        expectJwks(jwksJson(keyPair, KID));
        String noSub = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .expiration(oneHourLater())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.verify(noSub))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("JWT 형식이 아니면 INVALID_PROVIDER_TOKEN")
    void rejectsNonJwtToken() {
        assertThatThrownBy(() -> verifier.verify("this-is-not-a-jwt"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("빈 토큰이면 JWKS를 호출하지 않는다")
    void doesNotCallJwksOnBlankToken() {
        assertThatThrownBy(() -> verifier.verify(" "))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);

        mockServer.verify();
    }

    // ---------- 공개키(JWKS) 다루기 ----------

    @Test
    @DisplayName("공개키는 캐시한다 — 두 번 검증해도 JWKS는 한 번만 부른다")
    void cachesJwks() {
        mockServer.expect(ExpectedCount.once(), requestTo(JWKS_URI))
                .andRespond(withSuccess(jwksJson(keyPair, KID), MediaType.APPLICATION_JSON));

        verifier.verify(validToken());
        verifier.verify(validToken());

        mockServer.verify();
    }

    @Test
    @DisplayName("모르는 kid면 키가 교체된 것으로 보고 JWKS를 한 번 더 받아온다")
    void refetchesOnUnknownKeyId() {
        // 애플도 공개키를 주기적으로 교체한다. 캐시에 없다고 바로 실패시키면
        // 교체 직후 모든 로그인이 막힌다.
        mockServer.expect(ExpectedCount.times(2), requestTo(JWKS_URI))
                .andRespond(withSuccess(jwksJson(keyPair, "another-kid"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> verifier.verify(validToken()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);

        mockServer.verify();
    }

    @Test
    @DisplayName("JWKS 조회가 실패하면 SERVICE_UNAVAILABLE (토큰 탓이 아니다)")
    void mapsJwksFailureToServiceUnavailable() {
        mockServer.expect(requestTo(JWKS_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> verifier.verify(validToken()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
    }

    // ---------- 설정 ----------

    @Test
    @DisplayName("번들 ID가 설정되지 않았으면 BAD_REQUEST로 거절한다")
    void rejectsWhenAudienceNotConfigured() {
        // 번들 ID를 모르면 aud 검증을 할 수 없고, 검증을 건너뛰면 남의 앱 토큰이 통과한다.
        // 그래서 "검사를 못 하면 아예 받지 않는" 쪽을 택한다. 구글과 같은 취급이다.
        AppleTokenVerifier unconfigured = newVerifier(List.of());

        assertThatThrownBy(() -> unconfigured.verify(validToken()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        // 설정이 없으면 애플에 공개키를 받으러 가지도 않는다.
        mockServer.verify();
    }

    @Test
    @DisplayName("번들 ID를 여러 개 등록하면 그중 하나만 맞아도 통과한다")
    void allowsMultipleAudiences() {
        // iOS 앱(번들 ID)과 웹(Service ID)이 서로 다른 값을 쓴다. 둘 다 받아야 하는 경우가 있다.
        expectJwks(jwksJson(keyPair, KID));
        AppleTokenVerifier multi = newVerifier(List.of("com.lottotrip.web", AUDIENCE));

        assertThat(multi.verify(validToken()).providerUserId())
                .isEqualTo("001234.abcdef0123456789.1234");
    }

    // ---------- 구현체 식별 ----------

    @Test
    @DisplayName("담당 provider는 APPLE이다")
    void handlesApple() {
        assertThat(verifier.getType()).isEqualTo(ProviderType.APPLE);
    }
}

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
 * 구글 identity token 검증 구현체 검증. (roadmap 4-4-2)
 *
 * 카카오와 달리 구글은 **사용자 정보 API를 부르지 않는다.** 구글이 주는 identity token은
 * JWT라서 정보가 토큰 안에 이미 들어 있고, 서명만 확인하면 위조 여부를 알 수 있다.
 *
 * 대신 서명을 검증하려면 구글의 **공개키**가 필요하고, 그 공개키를 받아오는 주소가
 * JWKS(JSON Web Key Set)다. 이 주소만 HTTP로 부른다.
 *
 * 테스트에서는 **우리가 직접 만든 RSA 키 쌍**으로 토큰에 서명하고, 그 공개키를 담은 가짜 JWKS를
 * 돌려준다. 서명 검증 자체는 진짜 알고리즘이 그대로 돌아가므로 "서명이 틀리면 걸러내는가"를
 * 실제로 확인할 수 있다.
 */
class GoogleTokenVerifierTest {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";
    private static final String AUDIENCE = "ios-client-id.apps.googleusercontent.com";
    private static final String KID = "test-key-1";

    private static KeyPair keyPair;
    private static KeyPair otherKeyPair;

    private RestClient.Builder builder;
    private MockRestServiceServer mockServer;
    private GoogleTokenVerifier verifier;

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

    private GoogleTokenVerifier newVerifier(List<String> audiences) {
        return new GoogleTokenVerifier(builder, new GoogleOAuthProperties(JWKS_URI, audiences));
    }

    // ---------- 테스트용 토큰·JWKS 만들기 ----------

    /** 구글이 줄 법한 정상 토큰. 서명은 우리 개인키로 한다. */
    private String signedToken(PrivateKey privateKey, String kid, String issuer, String audience, Date expiration) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject("google-sub-1234567890")
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("email", "potato@example.com")
                .claim("email_verified", true)
                .claim("name", "감자러버")
                .claim("picture", "https://img.google.com/potato.png")
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

    /** 공개키를 구글 JWKS 응답 모양(JSON Web Key Set)으로 바꾼다. */
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

        // sub가 구글의 사용자 식별자다. 이메일은 바뀔 수 있으므로 사람 식별에 쓰지 않는다.
        assertThat(info.providerUserId()).isEqualTo("google-sub-1234567890");
        assertThat(info.email()).isEqualTo("potato@example.com");
        assertThat(info.nickname()).isEqualTo("감자러버");
        assertThat(info.profileImageUrl()).isEqualTo("https://img.google.com/potato.png");
        mockServer.verify();
    }

    @Test
    @DisplayName("이름·사진이 없어도 sub만 있으면 통과한다")
    void allowsMissingOptionalClaims() {
        // 구글은 이름·이메일을 최초 가입 시에만 주는 경우가 있다. (tour_api_erd.md 결정 4)
        expectJwks(jwksJson(keyPair, KID));
        String minimal = Jwts.builder()
                .header().keyId(KID).and()
                .subject("google-sub-1234567890")
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .expiration(oneHourLater())
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        OAuthUserInfo info = verifier.verify(minimal);

        assertThat(info.providerUserId()).isEqualTo("google-sub-1234567890");
        assertThat(info.email()).isNull();
        assertThat(info.nickname()).isNull();
    }

    @Test
    @DisplayName("issuer는 accounts.google.com 형태도 허용한다")
    void allowsIssuerWithoutScheme() {
        // 구글은 https가 붙은 형태와 붙지 않은 형태를 모두 발급한다. 둘 다 정상이다.
        expectJwks(jwksJson(keyPair, KID));
        String token = signedToken(keyPair.getPrivate(), KID, "accounts.google.com", AUDIENCE, oneHourLater());

        assertThat(verifier.verify(token).providerUserId()).isEqualTo("google-sub-1234567890");
    }

    // ---------- 토큰이 잘못된 경우 ----------

    @Test
    @DisplayName("다른 키로 서명된 토큰이면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongSignature() {
        // 위조 토큰을 잡아내는 핵심 검사다. JWKS는 정상 공개키를 주지만 토큰은 다른 키로 서명됐다.
        expectJwks(jwksJson(keyPair, KID));
        String forged = signedToken(otherKeyPair.getPrivate(), KID, ISSUER, AUDIENCE, oneHourLater());

        assertThatThrownBy(() -> verifier.verify(forged))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("aud가 우리 앱이 아니면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongAudience() {
        // 서명이 진짜여도 '남의 앱을 위해 발급된 토큰'이면 받아들이면 안 된다.
        // 이 검사가 없으면 다른 구글 앱의 토큰으로 우리 서비스에 로그인할 수 있다.
        expectJwks(jwksJson(keyPair, KID));
        String otherApp = signedToken(keyPair.getPrivate(), KID, ISSUER, "someone-else.apps.googleusercontent.com", oneHourLater());

        assertThatThrownBy(() -> verifier.verify(otherApp))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PROVIDER_TOKEN);
    }

    @Test
    @DisplayName("issuer가 구글이 아니면 INVALID_PROVIDER_TOKEN")
    void rejectsWrongIssuer() {
        expectJwks(jwksJson(keyPair, KID));
        String token = signedToken(keyPair.getPrivate(), KID, "https://evil.example.com", AUDIENCE, oneHourLater());

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
        // 로그인마다 구글에 공개키를 받으러 가면 응답이 느려지고, 구글이 잠깐 느려질 때
        // 우리 로그인도 같이 느려진다. 공개키는 자주 바뀌지 않으므로 캐시한다.
        mockServer.expect(ExpectedCount.once(), requestTo(JWKS_URI))
                .andRespond(withSuccess(jwksJson(keyPair, KID), MediaType.APPLICATION_JSON));

        verifier.verify(validToken());
        verifier.verify(validToken());

        mockServer.verify();
    }

    @Test
    @DisplayName("모르는 kid면 키가 교체된 것으로 보고 JWKS를 한 번 더 받아온다")
    void refetchesOnUnknownKeyId() {
        // 구글은 공개키를 주기적으로 교체한다. 캐시에 없는 kid가 왔다고 바로 실패시키면
        // 교체 직후 모든 로그인이 실패한다. 그래서 한 번 더 받아본다.
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
    @DisplayName("클라이언트 ID가 설정되지 않았으면 BAD_REQUEST로 거절한다")
    void rejectsWhenAudienceNotConfigured() {
        // iOS 클라이언트 ID는 아직 미확보다(roadmap 4-5). 설정이 없으면 aud 검증을 할 수 없는데,
        // 검증을 건너뛰면 남의 앱 토큰으로 로그인이 뚫린다. 그래서 '지원하지 않는 provider'와
        // 같은 취급으로 막는다. 애플이 BAD_REQUEST를 받는 것과 같은 이유다.
        GoogleTokenVerifier unconfigured = newVerifier(List.of());

        assertThatThrownBy(() -> unconfigured.verify(validToken()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);

        // 설정이 없으면 구글에 물어보러 가지도 않는다.
        mockServer.verify();
    }

    @Test
    @DisplayName("클라이언트 ID를 여러 개 등록하면 그중 하나만 맞아도 통과한다")
    void allowsMultipleAudiences() {
        // iOS 앱과 웹이 서로 다른 클라이언트 ID를 쓰는 경우가 있다.
        expectJwks(jwksJson(keyPair, KID));
        GoogleTokenVerifier multi = newVerifier(List.of("web-client-id.apps.googleusercontent.com", AUDIENCE));

        assertThat(multi.verify(validToken()).providerUserId()).isEqualTo("google-sub-1234567890");
    }

    // ---------- 구현체 식별 ----------

    @Test
    @DisplayName("담당 provider는 GOOGLE이다")
    void handlesGoogle() {
        assertThat(verifier.getType()).isEqualTo(ProviderType.GOOGLE);
    }
}

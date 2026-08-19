package com.lottotrip.auth.oauth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * JWKS(JSON Web Key Set) 응답. 구글·애플이 공개키를 이 모양으로 공개한다.
 *
 * 프론트에서 구글이 개인키로 발급한 토큰을 보내면, 백엔드에서 공개키를 통해 검증한다.
 *
 * @param keys 키가 여러 개인 이유는 교체 중이기 때문이다. 새 키로 바꾸는 동안에도
 *             옛 키로 서명된 토큰이 살아 있어야 하므로 한동안 둘 다 공개한다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JwkSetResponse(List<Jwk> keys) {

    /**
     * 키 하나.
     *
     * @param kid 키 식별자. 토큰 헤더에 같은 값이 들어 있어 "어느 키로 확인해야 하는지" 알 수 있다
     * @param kty 키 종류. 구글·애플은 RSA를 쓴다
     * @param n   RSA 공개키의 modulus(계수)
     * @param e   RSA 공개키의 exponent(지수)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Jwk(String kid, String kty, String alg, String n, String e) {

        private static final String RSA = "RSA";

        public boolean isRsa() {
            return RSA.equals(kty);
        }

        /**
         * JSON에 담긴 숫자 두 개를 자바가 쓸 수 있는 공개키 객체로 조립한다.
         *
         * RSA 공개키는 사실 큰 정수 두 개(n, e)가 전부다. JWKS는 이 숫자를
         * base64url이라는 방식으로 문자열에 담아 보낸다. 그래서 문자열 → 바이트 → 정수 → 키
         * 순서로 되돌린다.
         *
         * `new BigInteger(1, bytes)`의 `1`은 "이 숫자는 양수다"라는 뜻이다.
         * 이걸 빼면 맨 앞 바이트가 큰 값일 때 음수로 해석되어 엉뚱한 키가 만들어진다.
         */
        public PublicKey toPublicKey() throws Exception {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            RSAPublicKeySpec spec = new RSAPublicKeySpec(
                    new BigInteger(1, decoder.decode(n)),
                    new BigInteger(1, decoder.decode(e)));
            return KeyFactory.getInstance(RSA).generatePublic(spec);
        }
    }
}

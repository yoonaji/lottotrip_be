package com.lottotrip.common.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 인증된 유저의 userId를 컨트롤러 파라미터로 주입한다.
 * SecurityContext의 Authentication#getName()이 userId(문자열)라는 계약에 의존한다.
 * A파트의 JWT 인증 필터가 이 계약대로 Authentication을 채워주면 그대로 동작한다.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUserId {
}

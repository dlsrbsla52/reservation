package com.media.bus.contract.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Controller 메서드 파라미터에 현재 인증된 MemberPrincipal을 주입하는 어노테이션.
/// CurrentMemberArgumentResolver가 처리하며,
/// request attribute에서 MemberPrincipal을 꺼내 파라미터에 바인딩합니다.
/// required=true(기본값)이면 미인증 시 NoAuthenticationException(401) 발생.
/// required=false이면 미인증 시 null 반환 — 선택적 인증 엔드포인트에 사용.
/// 사용 예)
/// `public ResponseEntity<?> myApi(@CurrentMember MemberPrincipal principal) { ... }`
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {

    boolean required() default true;
}

package com.media.bus.contract.security.annotation;

import com.media.bus.contract.entity.member.MemberCategory;
import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.contract.entity.member.Permission;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// 선언적 인가 어노테이션.
/// HandlerInterceptor(AuthorizeHandlerInterceptor)가 preHandle 단계에서 평가합니다.
/// 평가 규칙:
/// - categories + types 모두 비어있으면 "인증된 사용자면 통과"
/// - categories / types 는 OR 조건: 하나라도 매칭이면 통과
/// - permissions 는 AND 조건: 전부 보유해야 통과
/// - 클래스 레벨 + 메서드 레벨 동시 존재 시 메서드 레벨 우선(override)
/// 사용 예)
/// `@Authorize(categories = {MemberCategory.ADMIN}, permissions = {Permission.WRITE})`
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Authorize {

    /// 허용할 회원 카테고리 목록 (OR 조건)
    MemberCategory[] categories() default {};

    /// 허용할 회원 유형 목록 (OR 조건, categories와도 OR)
    MemberType[] types() default {};

    /// 필수 권한 목록 (AND 조건)
    Permission[] permissions() default {};

    /// true이면 이메일 인증 완료된 사용자만 통과
    boolean requireEmailVerified() default false;
}

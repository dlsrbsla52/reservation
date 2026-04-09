package com.media.bus.contract.security.annotation

/**
 * ## Controller 메서드 파라미터에 현재 인증된 MemberPrincipal을 주입하는 어노테이션
 *
 * [CurrentMemberArgumentResolver][com.media.bus.contract.security.resolver.CurrentMemberArgumentResolver]가 처리하며,
 * request attribute에서 MemberPrincipal을 꺼내 파라미터에 바인딩합니다.
 *
 * - `required = true`(기본값): 미인증 시 `NoAuthenticationException`(401) 발생
 * - `required = false`: 미인증 시 null 반환 — 선택적 인증 엔드포인트에 사용
 *
 * 사용 예)
 * ```kotlin
 * fun myApi(@CurrentMember principal: MemberPrincipal) { ... }
 * ```
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CurrentMember(
    val required: Boolean = true,
)

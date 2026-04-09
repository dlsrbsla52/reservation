package com.media.bus.common.security

/**
 * ## S2S(System-to-System) 토큰 발급을 위한 인프라 인터페이스
 *
 * common 모듈의 RestClientConfig는 구체 구현체(JwtProvider)를 직접 알 필요 없이
 * 이 인터페이스에만 의존한다.
 * 구현체는 auth-contract 모듈의 JwtProvider가 담당한다.
 */
interface TokenProvider {
    fun generateS2SToken(): String
}

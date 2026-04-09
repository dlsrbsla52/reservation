package com.media.bus.contract.filter

import com.media.bus.contract.security.JwtProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter

/**
 * ## S2S(Service-to-Service) 토큰 검증 필터 공통 구현체
 *
 * 내부 서비스(Gateway 등)에서 X-Service-Token 헤더로 S2S 토큰을 전달해야 합니다.
 * 각 마이크로서비스는 이 필터를 빈으로 등록 시 검증이 필요한 경로(applicablePaths)를 주입합니다.
 *
 * 토큰 검증 기준:
 * 1. X-Service-Token 헤더 존재 여부
 * 2. JWT 서명 및 만료 검증
 * 3. type 클레임이 "s2s"인지 확인
 */
class S2STokenFilter(
    private val jwtProvider: JwtProvider,
    private val applicablePaths: List<String> = emptyList(),
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val S2S_TOKEN_HEADER = "X-Service-Token"
        private const val S2S_TOKEN_TYPE = "s2s"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = request.getHeader(S2S_TOKEN_HEADER)

        // 헤더 없거나 유효하지 않은 S2S 토큰 -> 401
        if (token == null || !isValidS2SToken(token)) {
            log.warn("[S2STokenFilter] 유효하지 않은 S2S 토큰. URI={}", request.requestURI)
            sendUnauthorized(response)
            return
        }

        filterChain.doFilter(request, response)
    }

    /** JWT 서명 검증 및 type 클레임 확인. tryParseClaims() 단일 호출로 이중 파싱 제거. */
    private fun isValidS2SToken(token: String): Boolean =
        jwtProvider.tryParseClaims(token)
            ?.let { claims -> S2S_TOKEN_TYPE == claims.get("type", String::class.java) }
            ?: false

    /** 401 Unauthorized 응답을 JSON 형식으로 반환합니다. */
    private fun sendUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(
            """{"code":"00205","message":"S2S 토큰 검증에 실패하였습니다."}"""
        )
    }

    /**
     * 주입받은 경로(applicablePaths) 중 하나라도 일치하면 필터를 적용(false).
     * 어느 것도 일치하지 않으면 필터를 건너뜁니다(true).
     */
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (applicablePaths.isEmpty()) {
            return true
        }
        val uri = request.requestURI
        return applicablePaths.none { uri.startsWith(it) }
    }
}

package com.media.bus.app.security

import com.media.bus.contract.security.MemberPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.*

/**
 * ## 신뢰 헤더를 숨기는 요청 래퍼
 *
 * 예전 gateway는 클라이언트가 보낸 `X-User-*` 헤더를 지우고 검증한 값으로 덮어썼다. 지금은 아무 코드도 이 헤더를
 * 읽지 않지만(회원 정보는 토큰에서만 만든다), 나중에 누가 헤더를 다시 읽어도 위조값이 들어오지 않도록 이중으로 막는다.
 *
 * 제거 목록은 [MemberPrincipal.TRUSTED_HEADERS] 6개다. `X-Email-Verified`가 `X-User-` 접두가 아니므로 접두 검사를 쓰지 않는다.
 * 헤더 이름은 대소문자를 구분하지 않는다.
 */
class TrustedHeaderStrippingRequest(request: HttpServletRequest) : HttpServletRequestWrapper(request) {

    override fun getHeader(name: String): String? = if (isTrusted(name)) null else super.getHeader(name)

    override fun getHeaders(name: String): Enumeration<String> =
        if (isTrusted(name)) Collections.emptyEnumeration() else super.getHeaders(name)

    override fun getHeaderNames(): Enumeration<String> =
        Collections.enumeration(Collections.list(super.getHeaderNames()).filterNot(::isTrusted))

    override fun getIntHeader(name: String): Int = if (isTrusted(name)) -1 else super.getIntHeader(name)

    override fun getDateHeader(name: String): Long = if (isTrusted(name)) -1L else super.getDateHeader(name)

    private fun isTrusted(name: String): Boolean = TRUSTED_LOWER.contains(name.lowercase(Locale.ROOT))

    private companion object {
        val TRUSTED_LOWER: Set<String> = MemberPrincipal.TRUSTED_HEADERS.map { it.lowercase(Locale.ROOT) }.toSet()
    }
}

package com.media.bus.app.security

import com.media.bus.contract.security.MemberPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.util.*

/** 신뢰 헤더를 숨기는 요청 래퍼 */
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

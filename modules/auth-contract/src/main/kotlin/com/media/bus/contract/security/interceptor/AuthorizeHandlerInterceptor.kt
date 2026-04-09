package com.media.bus.contract.security.interceptor

import com.media.bus.common.exceptions.NoAuthenticationException
import com.media.bus.common.exceptions.NoAuthorizationException
import com.media.bus.contract.entity.member.MemberCategory
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * ## `@Authorize` 어노테이션 기반 선언적 인가를 처리하는 HandlerInterceptor
 *
 * AOP(AspectJ) 대신 HandlerInterceptor를 사용하는 이유:
 * - AOP는 HttpServletRequest 접근 시 RequestContextHolder(ThreadLocal 기반)를 필요로 함
 * - 프로젝트 Virtual Thread + ThreadLocal 금지 규칙에 위배
 * - HandlerInterceptor는 HttpServletRequest를 직접 받아 attribute를 읽으므로 ThreadLocal 불필요
 *
 * preHandle 흐름:
 * 1. HandlerMethod가 아니면 통과 (정적 리소스 등)
 * 2. 메서드 레벨 @Authorize 조회 -> 없으면 클래스 레벨 -> 없으면 통과
 * 3. request attribute에서 MemberPrincipal 조회 -> null이면 401
 * 4. categories/types OR 조건 매칭 -> 실패 시 403
 * 5. permissions AND 조건 매칭 -> 실패 시 403
 * 6. requireEmailVerified 체크 -> 미인증 시 403
 */
class AuthorizeHandlerInterceptor : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        // HandlerMethod가 아닌 요청(정적 리소스 등)은 인가 체크 생략
        if (handler !is HandlerMethod) {
            return true
        }

        // 메서드 레벨 우선, 없으면 클래스 레벨 @Authorize 조회
        var authorize = handler.getMethodAnnotation(Authorize::class.java)
        if (authorize == null) {
            authorize = handler.beanType.getAnnotation(Authorize::class.java)
        }

        // @Authorize 없으면 인가 체크 생략 (공개 엔드포인트)
        if (authorize == null) {
            return true
        }

        // 인증 정보 조회 — 없으면 401
        val principal = request.getAttribute(MemberPrincipal.REQUEST_ATTRIBUTE_KEY) as? MemberPrincipal
            ?: throw NoAuthenticationException()

        checkCategoryOrType(principal, authorize)
        checkPermissions(principal, authorize)
//        checkEmailVerified(principal, authorize)

        return true
    }

    /**
     * categories / types OR 조건 검증.
     * 둘 다 비어있으면 "인증된 사용자면 통과" 처리합니다.
     */
    private fun checkCategoryOrType(principal: MemberPrincipal, authorize: Authorize) {
        val categories: Array<MemberCategory> = authorize.categories
        val types: Array<MemberType> = authorize.types

        if (categories.isEmpty() && types.isEmpty()) {
            return
        }

        val categoryMatch = categories.any { it == principal.category() }
        val typeMatch = types.any { it == principal.memberType }

        if (!categoryMatch && !typeMatch) {
            throw NoAuthorizationException()
        }
    }

    /**
     * permissions AND 조건 검증.
     * 지정된 권한 중 하나라도 없으면 403 발생.
     */
    private fun checkPermissions(principal: MemberPrincipal, authorize: Authorize) {
        for (permission in authorize.permissions) {
            if (!principal.hasPermission(permission)) {
                throw NoAuthorizationException()
            }
        }
    }

    /**
     * 이메일 인증 여부 검증.
     * requireEmailVerified=true인데 미인증이면 403 발생.
     * TODO : 현재는 이메일 인증을 붙일 수 없기 때문에 추후 이메일 서버 구축 후 처리
     */
    @Suppress("unused")
    private fun checkEmailVerified(principal: MemberPrincipal, authorize: Authorize) {
        if (authorize.requireEmailVerified && !principal.emailVerified) {
            throw NoAuthorizationException()
        }
    }
}

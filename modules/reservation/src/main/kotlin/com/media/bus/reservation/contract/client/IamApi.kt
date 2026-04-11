package com.media.bus.reservation.contract.client

import com.media.bus.reservation.contract.dto.response.internal.IamMemberResponse
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.HttpExchange
import org.springframework.web.service.annotation.PostExchange

/**
 * ## IAM 서비스 내부 API의 선언적 HTTP 인터페이스
 *
 * 설계 의도:
 * - `@HttpExchange` 기반 프록시가 HTTP 호출을 자동 처리하므로 RestClient 직접 조작 코드가 불필요하다.
 * - 비즈니스 로직(null 체크)은 포함하지 않는다.
 *   `IamServiceClient`가 이 인터페이스를 위임받아 비즈니스 로직을 처리한다.
 */
@HttpExchange
interface IamApi {

    /** JWT 토큰으로 IAM DB 회원 조회 */
    @PostExchange(url = "/api/v1/member/internal/jwt")
    fun findMemberByJwt(@RequestBody body: Map<String, String>): IamMemberResponse?
}

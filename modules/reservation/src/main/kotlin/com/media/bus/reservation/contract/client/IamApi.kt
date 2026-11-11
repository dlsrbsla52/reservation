package com.media.bus.reservation.contract.client

import com.media.bus.reservation.contract.dto.request.internal.FindIamMemberByIdRequest
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
 *
 * 대상: iam `MemberInternalController` (`/api/v1/internal/iam/member`).
 * 경로·메서드가 어긋나면 컴파일로는 잡히지 않으므로 `IamApiContractTest`가 함께 고정한다.
 */
@HttpExchange(url = "/api/v1/internal/iam/member")
interface IamApi {

    /** 회원 UUID로 IAM DB 회원 조회. 회원이 없으면 iam이 4xx를 돌려준다. */
    @PostExchange("/id")
    fun findMemberById(@RequestBody request: FindIamMemberByIdRequest): IamMemberResponse?
}

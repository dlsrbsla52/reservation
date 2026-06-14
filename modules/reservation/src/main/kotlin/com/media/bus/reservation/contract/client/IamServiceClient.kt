package com.media.bus.reservation.contract.client

import com.media.bus.reservation.contract.dto.response.MemberInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

/**
 * ## IAM 서비스 내부 API 클라이언트
 *
 * 설계 의도:
 * - `IamApi`(`@HttpExchange` 프록시)에 HTTP 호출을 위임하고,
 *   응답 변환(null 체크) 등 비즈니스 로직을 이 클래스에서 처리한다.
 * - 소비자의 공개 API는 변경하지 않는다.
 */
@Component
class IamServiceClient(
    private val iamApi: IamApi,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 회원 UUID로 IAM DB에서 회원을 조회한다.
     * 회원이 존재하지 않거나 응답이 null이면 null을 반환한다.
     *
     * @param memberId 조회할 회원의 UUID
     * @return 조회된 회원 정보, 없으면 null
     */
    fun findMemberById(memberId: UUID): MemberInfo? {
        log.debug("[IamServiceClient] UUID로 회원 조회 요청: memberId={}", memberId)
        return iamApi.findMemberById(memberId)?.data
    }
}

package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.StorageException
import com.media.bus.reservation.contract.client.IamServiceClient
import com.media.bus.reservation.contract.dto.response.MemberInfo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * ## IAM 서비스와의 연동을 담당하는 서비스
 *
 * 설계 의도:
 * - `StopResolutionService`와 동일한 패턴으로 구성한다.
 * - Facade에서 직접 `IamServiceClient`를 호출하지 않고 이 서비스를 경유한다.
 * - IAM 응답 구조 변경 시 이 클래스만 수정한다 (Facade 변경 불필요).
 * - JWT 클레임만으로는 DB 상의 회원 상태 변경을 반영할 수 없으므로,
 *   계약 생성 시 IAM DB에서 회원을 재검증한다.
 */
@Service
class MemberVerificationService(
    private val iamServiceClient: IamServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * JWT 토큰으로 IAM DB에서 회원을 조회하고 반환한다.
     * 존재하지 않으면 `StorageException`(404)을 던진다.
     *
     * @param jwt 사용자 Access JWT 토큰 (Bearer 접두사 제외)
     * @return IAM DB에서 조회된 회원 정보
     * @throws StorageException 회원을 찾을 수 없거나 비활성 상태인 경우
     */
    fun verifyMember(jwt: String): MemberInfo {
        val member = iamServiceClient.findMemberByJwt(jwt)
            ?: run {
                log.warn("[MemberVerificationService] IAM DB에서 회원을 찾을 수 없음")
                throw StorageException("요청한 회원을 찾을 수 없습니다.")
            }
        log.debug("[MemberVerificationService] IAM 회원 재검증 완료: memberId={}", member.id)
        return member
    }
}

package com.media.bus.reservation.contract.service

import com.media.bus.common.exceptions.StorageException
import com.media.bus.reservation.contract.client.IamServiceClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

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
     * 요청의 memberId가 실제 IAM DB에 존재하는지 검증한다.
     * memberId가 null이면 비회원 계약으로 간주하고 검증을 건너뛴다.
     *
     * @param memberId 검증할 회원 UUID (null이면 검증 생략)
     * @throws StorageException 회원을 찾을 수 없는 경우
     */
    fun verifyMemberIfPresent(memberId: UUID?) {
        if (memberId == null) return
        iamServiceClient.findMemberById(memberId)
            ?: run {
                log.warn("[MemberVerificationService] IAM DB에서 회원을 찾을 수 없음: memberId={}", memberId)
                throw StorageException(message = "요청한 회원을 찾을 수 없습니다.")
            }
        log.debug("[MemberVerificationService] IAM 회원 검증 완료: memberId={}", memberId)
    }
}

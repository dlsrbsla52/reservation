package com.media.bus.reservation.contract.service

import com.media.bus.reservation.contract.port.MemberLookupPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## iam 모듈과의 연동(회원 재검증)을 담당하는 서비스
 *
 * 설계 의도:
 * - `StopResolutionService`와 동일한 패턴으로 구성한다.
 * - Facade에서 직접 `MemberLookupPort`를 호출하지 않고 이 서비스를 경유한다.
 * - IAM 응답 구조 변경 시 이 클래스만 수정한다 (Facade 변경 불필요).
 * - JWT 클레임만으로는 DB 상의 회원 상태 변경을 반영할 수 없으므로,
 *   계약 생성 시 IAM DB에서 회원을 재검증한다.
 */
@Service
class MemberVerificationService(
    private val memberLookupPort: MemberLookupPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 요청의 memberId가 실제 IAM DB에 존재하는지 검증한다.
     * memberId가 null이면 비회원 계약으로 간주하고 검증을 건너뛴다.
     *
     * @param memberId 검증할 회원 UUID (null이면 검증 생략)
     * @throws com.media.bus.common.exceptions.BusinessException 회원이 없으면 iam의 `USER_NOT_FOUND_FAIL`(404/`00256`)
     */
    fun verifyMemberIfPresent(memberId: UUID?) {
        if (memberId == null) return
        memberLookupPort.findMemberById(memberId)
        log.debug("[MemberVerificationService] IAM 회원 검증 완료: memberId={}", memberId)
    }
}

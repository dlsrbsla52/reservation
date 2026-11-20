package com.media.bus.reservation.contract.service

import com.media.bus.reservation.contract.port.MemberLookupPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/** iam 모듈과의 연동(회원 재검증)을 담당하는 서비스 */
@Service
class MemberVerificationService(
    private val memberLookupPort: MemberLookupPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 요청의 memberId가 실제 IAM DB에 존재하는지 검증한다. */
    fun verifyMemberIfPresent(memberId: UUID?) {
        if (memberId == null) return
        memberLookupPort.findMemberById(memberId)
        log.debug("[MemberVerificationService] IAM 회원 검증 완료: memberId={}", memberId)
    }
}

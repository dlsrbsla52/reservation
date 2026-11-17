package com.media.bus.reservation.contract.port

import com.media.bus.reservation.contract.dto.response.MemberInfo
import java.util.*

/**
 * ## 계약 흐름이 쓰는 회원 조회 포트
 *
 * 설계 의도:
 * - reservation은 iam 모듈을 컴파일 의존하지 않는다. 회원 재검증에 필요한 조회만 이 포트로 정의한다.
 * - 구현은 `app` 모듈의 `ReservationMemberLookupAdapter`이고, iam `MemberService.findByMemberId`를 직접 호출한다.
 */
interface MemberLookupPort {

    /**
     * 회원 UUID로 회원을 조회한다.
     *
     * @throws com.media.bus.common.exceptions.BusinessException 회원이 없으면 iam의 `USER_NOT_FOUND_FAIL`(404/`00256`)
     */
    fun findMemberById(memberId: UUID): MemberInfo
}

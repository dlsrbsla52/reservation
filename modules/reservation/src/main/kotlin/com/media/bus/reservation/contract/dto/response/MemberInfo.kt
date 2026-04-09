package com.media.bus.reservation.contract.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.*

/**
 * ## IAM 서비스 회원 조회 응답의 내부 표현
 *
 * 설계 의도:
 * - `MemberPrincipal`은 JWT 클레임 파싱 결과이므로 IAM DB 재검증 결과와 분리한다.
 * - IAM 응답 구조 변경 시 이 클래스만 수정한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MemberInfo(
    val id: UUID,
    val loginId: String,
    val email: String,
    val memberType: String,
    val status: String,
)

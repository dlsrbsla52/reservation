package com.media.bus.reservation.contract.dto.request.internal

/**
 * ## IAM 회원 조회(`POST /api/v1/internal/iam/member/id`) 요청 본문
 *
 * 모듈 경계상 iam의 `FindMemberByMemberIdRequest`를 참조하지 않고 사본으로 둔다.
 * iam 쪽이 `memberId`를 문자열로 받으므로 여기서도 문자열로 보낸다.
 */
data class FindIamMemberByIdRequest(val memberId: String)

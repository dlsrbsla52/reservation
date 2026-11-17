package com.media.bus.iam.member.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * ## 아이디 찾기 응답
 *
 * `POST /api/v1/member/find/me`는 로그인하지 못한 사용자가 쓰는 **공개 API**(토큰 없음)다.
 * 이름과 전화번호(또는 이메일)만 알면 누구나 호출할 수 있으므로 로그인 아이디 외의 회원 정보는 담지 않는다.
 * 예전에는 [MemberResponse] 전체를 돌려줘서 이름·전화번호만으로 이메일·사업자번호·회원 ID·상태까지 조회됐다.
 * 필드를 추가하지 않는다.
 */
@Schema(description = "아이디 찾기 응답. 로그인 아이디만 담는다.")
data class FindLoginIdResponse(
    @param:Schema(name = "loginId", description = "로그인 아이디", example = "user123")
    val loginId: String,
)

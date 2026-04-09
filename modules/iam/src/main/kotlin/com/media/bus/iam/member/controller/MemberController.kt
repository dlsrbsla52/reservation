package com.media.bus.iam.member.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.iam.member.dto.*
import com.media.bus.iam.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "유저(Member) API", description = "회원조회, 회원 수정 등 회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService,
) {
    /// 로그인시 사용된 jwt 토큰으로 회원 조회.
    @Operation(summary = "회원조회", description = "jwt를 기준으로 회원을 조회합니다.")
    @PostMapping("/jwt")
    fun findByJwtMember(@RequestBody @Valid request: FindMemberByJwtRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findByJwtMember(request.jwt))

    /// 유저 아이디를 통한 회원 조회.
    @Operation(summary = "회원조회", description = "memberId를 기준으로 회원을 조회합니다.")
    @PostMapping("/id")
    fun findByMemberId(@RequestBody @Valid request: FindMemberByMemberIdRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findByMemberId(request.memberId))

    /// 로그인 아이디를 통한 회원 조회.
    @Operation(summary = "회원조회", description = "loginId를 기준으로 회원을 조회합니다.")
    @PostMapping("/login-id")
    fun findByLoginId(@RequestBody @Valid request: FindMemberByLoginIdRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findByLoginId(request.loginId))

    /// 유저 이메일을 통한 회원 조회.
    @Operation(summary = "회원조회", description = "email을 기준으로 회원을 조회합니다.")
    @PostMapping("/email")
    fun findByEmail(@RequestBody @Valid request: FindMemberByEmailRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findByEmail(request.email))
}

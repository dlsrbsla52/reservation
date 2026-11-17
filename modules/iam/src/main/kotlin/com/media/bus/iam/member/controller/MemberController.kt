package com.media.bus.iam.member.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.contract.security.annotation.CurrentMember
import com.media.bus.iam.member.dto.*
import com.media.bus.iam.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@Tag(name = "유저(Member) API", description = "External 전용 회원조회, 회원 수정 등 회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService,
) {

    /**
     * 현재 로그인한 회원 본인 정보 조회.
     * 인증 필터가 검증한 토큰의 회원(`@CurrentMember`) 기준으로 본인 정보를 반환한다.
     */
    @Authorize
    @Operation(summary = "내 정보 조회", description = "JWT 인증된 본인의 회원 정보를 조회합니다.")
    @GetMapping("/me")
    fun me(@CurrentMember member: MemberPrincipal): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findByMemberId(member.id.toString()))


    /**
     * 이름으로 아이디 찾기
     */
    @Operation(
        summary = "아이디 찾기",
        description = "회원 이름과 전화번호 또는 email로 로그인 아이디를 찾습니다. 토큰 없이 호출하는 공개 API라 로그인 아이디만 돌려줍니다.",
    )
    @GetMapping("/find/me")
    fun findMe(@RequestBody @Valid request: FindMeRequest): ApiResponse<FindLoginIdResponse> =
        ApiResponse.success(memberService.findMe(request))


    /**
     * 회원 정보 수정
     * 2차 본인 인증 완료 후에만 호출 가능하다.
     * 회원 정보 수정 후 2차 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Authorize
    @Operation(summary = "회원 정보 수정", description = "회원 정보를 수정합니다.")
    @PostMapping("/modify")
    fun modify(@CurrentMember member: MemberPrincipal, @RequestBody request: MemberModifyRequest): ApiResponse<Unit?> =
        ApiResponse.success(memberService.modify(member.id.toString(), request))


    /**
     * 회원 탈퇴.
     * 2차 본인 인증 완료 후에만 호출 가능하다.
     * 탈퇴 처리 완료 후 2차 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Authorize
    @Operation(summary = "회원 탈퇴", description = "2차 본인 인증 완료 후 회원 탈퇴를 처리합니다.")
    @DeleteMapping("/withdraw")
    fun withdraw(@CurrentMember member: MemberPrincipal): ApiResponse<Unit?> =
        ApiResponse.successWith { memberService.withdraw(member.id.toString()) }


    /**
     * 계정 일시 비활성화.
     * 2차 본인 인증 완료 후에만 호출 가능하며, 탈퇴와 달리 추후 재활성화가 가능하다.
     * 처리 완료 후 2차 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Authorize
    @Operation(summary = "계정 비활성화", description = "2차 본인 인증 완료 후 계정을 일시 비활성화 상태(INACTIVE)로 전환합니다.")
    @PostMapping("/deactivate")
    fun deactivate(
        @CurrentMember member: MemberPrincipal,
        @RequestBody(required = false) @Valid request: DeactivateRequest?,
    ): ApiResponse<Unit?> =
        ApiResponse.successWith { memberService.deactivate(member.id.toString(), request?.reason ?: "") }
}
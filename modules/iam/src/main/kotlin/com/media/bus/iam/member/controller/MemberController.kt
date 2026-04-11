package com.media.bus.iam.member.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.contract.security.annotation.Authorize
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberModifyRequest
import com.media.bus.iam.member.dto.MemberResponse
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
     * 이름으로 아이디 찾기
     */
    @Operation(summary = "아이디 찾기", description = "회원 이름, 전화번호, email로 아이디를 검색합니다.")
    @GetMapping("/find/me")
    fun findMe(@RequestBody @Valid request: FindMeRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findMe(request))


    /**
     * 회원 정보 수정
     * 2차 본인 인증 완료 후에만 호출 가능하다.
     * 회원 정보 수정 후 2차 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Authorize
    @Operation(summary = "회원 정보 수정", description = "회원 정보를 수정합니다.")
    @PostMapping("/modify")
    fun modify(@RequestHeader("X-User-Id") memberId: String, @RequestBody request: MemberModifyRequest): ApiResponse<Unit?> =
        ApiResponse.success(memberService.modify(memberId, request))


    /**
     * 회원 탈퇴.
     * 2차 본인 인증 완료 후에만 호출 가능하다.
     * 탈퇴 처리 완료 후 2차 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Authorize
    @Operation(summary = "회원 탈퇴", description = "2차 본인 인증 완료 후 회원 탈퇴를 처리합니다.")
    @DeleteMapping("/withdraw")
    fun withdraw(@RequestHeader("X-User-Id") memberId: String): ApiResponse<Unit?> =
        ApiResponse.successWith { memberService.withdraw(memberId) }
}
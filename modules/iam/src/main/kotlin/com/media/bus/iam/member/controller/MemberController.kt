package com.media.bus.iam.member.controller

import com.media.bus.common.web.response.ApiResponse
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberResponse
import com.media.bus.iam.member.service.MemberService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "유저(Member) API", description = "External 전용 회원조회, 회원 수정 등 회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
class MemberController(
    private val memberService: MemberService
) {

    /**
     * 이름으로 아이디 찾기
     */
    @Operation(summary = "아이디 찾기", description = "회원 이름, 전화번호, email로 아이디를 검색합니다.")
    @GetMapping("/find/me")
    fun findMe(@RequestBody @Valid request: FindMeRequest): ApiResponse<MemberResponse> =
        ApiResponse.success(memberService.findMe(request))
}
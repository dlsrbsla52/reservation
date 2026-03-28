package com.media.bus.iam.member.controller;

import com.media.bus.common.result.type.CommonResult;
import com.media.bus.common.web.response.DataView;
import com.media.bus.iam.member.dto.FindMemberByEmailRequest;
import com.media.bus.iam.member.dto.FindMemberByJwtRequest;
import com.media.bus.iam.member.dto.FindMemberByLoginIdRequest;
import com.media.bus.iam.member.dto.FindMemberByMemberIdRequest;
import com.media.bus.iam.member.dto.MemberResponse;
import com.media.bus.iam.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "유저(Member) API", description = "회원조회, 회원 수정 등 회원 관련 API")
@RestController
@RequestMapping("/api/v1/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    /// 로그인시 사용된 jwt 토큰으로 회원 조회.
    @Operation(summary = "회원조회", description = "jtw를 기준으로 회원을 조회합니다.")
    @PostMapping("/jwt")
    public DataView<MemberResponse> findByJwtMember(@RequestBody @Valid FindMemberByJwtRequest request) {
        return DataView.<MemberResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(memberService.findByJwtMember(request.jwt()))
            .build();
    }

    /// 유저 아이디를 통한 회원 조회.
    @Operation(summary = "회원조회", description = "memberId를 기준으로 회원을 조회합니다.")
    @PostMapping("/id")
    public DataView<MemberResponse> findByMemberId(@RequestBody @Valid FindMemberByMemberIdRequest request) {
        return DataView.<MemberResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(memberService.findByMemberId(request.memberId()))
            .build();
    }

    /// 유저 아이디를 통한 회원 조회.
    @Operation(summary = "회원조회", description = "memberId를 기준으로 회원을 조회합니다.")
    @PostMapping("/login-id")
    public DataView<MemberResponse> findByLoginId(@RequestBody @Valid FindMemberByLoginIdRequest request) {
        return DataView.<MemberResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(memberService.findByLoginId(request.loginId()))
            .build();
    }

    /// 유저 이메일을 통한 회원 조회.
    @Operation(summary = "회원조회", description = "memberId를 기준으로 회원을 조회합니다.")
    @PostMapping("/email")
    public DataView<MemberResponse> findByEmail(@RequestBody @Valid FindMemberByEmailRequest request) {
        return DataView.<MemberResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(memberService.findByEmail(request.email()))
            .build();
    }

}

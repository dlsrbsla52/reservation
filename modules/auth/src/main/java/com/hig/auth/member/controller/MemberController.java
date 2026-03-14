package com.hig.auth.member.controller;

import com.hig.auth.member.dto.FindMemberByJwtRequest;
import com.hig.auth.member.dto.MemberResponse;
import com.hig.auth.member.service.MemberService;
import com.hig.result.type.CommonResult;
import com.hig.web.response.DataView;
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

    /**
     * 로그인시 사용된 jwt 토큰으로 회원 조회.
     */
    @Operation(summary = "회원조회", description = "jtw를 기준으로 회원을 조회합니다.")
    @PostMapping("/jwt")
    public DataView<MemberResponse> findByJwtMember(@RequestBody @Valid FindMemberByJwtRequest request) {
        return DataView.<MemberResponse>builder()
            .result(CommonResult.SUCCESS)
            .data(memberService.findByJwtMember(request.jwt()))
            .build();
    }

}

package com.media.bus.auth.modules.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "로그인 id를 통한 회원 조회 요청 객체")
public record FindMemberByLoginIdRequest(

    @NotBlank
    @NotEmpty
    @Schema(name = "loginId", description = "로그인 id", example = "vhkdrb52")
    String loginId
) {
}

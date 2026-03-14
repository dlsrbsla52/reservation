package com.hig.auth.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "로그인 id를 통한 회원 조회 요청 객체")
public record FindMemberByEmailRequest(

    @NotBlank
    @NotEmpty
    @Schema(name = "email", description = "로그인 email", example = "vhkdrb52@gmail.com")
    String email
) {
}

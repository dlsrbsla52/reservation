package com.media.bus.auth.modules.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

@Schema(description = "jwt를 통한 회원 조회 요청 객체")
public record FindMemberByJwtRequest(

    @NotBlank
    @NotEmpty
    @Schema(name = "jwt", description = "리프레시 토큰이 아닌, 로그인시 사용된 jwt 토큰", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lI")
    String jwt
) {
}

package com.media.bus.iam.admin.dto;

import com.media.bus.contract.entity.member.MemberType;
import com.media.bus.iam.member.entity.Member;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/// 어드민 멤버 생성 응답 DTO.
@Schema(description = "어드민 멤버 생성 응답 DTO")
public record AdminMemberResponse(

    @Schema(description = "생성된 멤버 ID")
    UUID memberId,

    @Schema(description = "로그인 아이디")
    String loginId,

    @Schema(description = "이메일 주소")
    String email,

    @Schema(description = "부여된 어드민 회원 유형")
    MemberType memberType

) {

    /// Member 엔티티와 MemberType으로 응답 DTO를 생성합니다.
    /// MemberType은 엔티티에 없으므로 Role 조회 결과를 별도 전달합니다.
    public static AdminMemberResponse of(Member member, MemberType memberType) {
        return new AdminMemberResponse(
            member.getId(),
            member.getLoginId(),
            member.getEmail(),
            memberType
        );
    }
}

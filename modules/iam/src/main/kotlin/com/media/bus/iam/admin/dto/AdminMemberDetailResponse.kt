package com.media.bus.iam.admin.dto

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.*

/** 어드민 회원 상세 조회 응답 DTO — 역할 및 권한 포함 */
@Schema(description = "어드민 회원 상세 응답 DTO")
data class AdminMemberDetailResponse(

    @param:Schema(description = "회원 ID")
    val memberId: UUID,

    @param:Schema(description = "로그인 아이디")
    val loginId: String,

    @param:Schema(description = "이메일")
    val email: String,

    @param:Schema(description = "핸드폰 번호")
    val phoneNumber: String,

    @param:Schema(description = "회원 이름")
    val memberName: String,

    @param:Schema(description = "회원 유형")
    val memberType: MemberType,

    @param:Schema(description = "계정 상태")
    val status: MemberStatus,

    @param:Schema(description = "사업자등록번호")
    val businessNumber: String?,

    @param:Schema(description = "역할 정보")
    val role: RoleResponse,

    @param:Schema(description = "권한 목록")
    val permissions: List<PermissionResponse>,

    @param:Schema(description = "이메일 인증 여부")
    val emailVerified: Boolean,

    @param:Schema(description = "생성일시")
    val createdAt: OffsetDateTime,

    @param:Schema(description = "수정일시")
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun of(
            member: MemberEntity,
            memberType: MemberType,
            role: RoleEntity,
            permissions: List<PermissionEntity>,
        ): AdminMemberDetailResponse =
            AdminMemberDetailResponse(
                memberId = member.id.value,
                loginId = member.loginId,
                email = member.email,
                phoneNumber = member.phoneNumber,
                memberName = member.memberName,
                memberType = memberType,
                status = member.status,
                businessNumber = member.businessNumber,
                role = RoleResponse.of(role),
                permissions = permissions.map { PermissionResponse.of(it) },
                emailVerified = member.emailVerified,
                createdAt = member.createdAt,
                updatedAt = member.updatedAt,
            )
    }
}

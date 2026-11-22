package com.media.bus.iam.admin.dto

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.member.dto.MemberResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/** 어드민 전용 매니저별 계약 결합 응답 */
@Schema(description = "어드민 전용 매니저별 계약 결합 응답")
data class AdminMemberContractListResponse(
    @param:Schema(description = "매니저 요약 정보") val member: MemberSummary,
    @param:Schema(description = "매니저 계약 목록(페이지)") val contracts: PageResult<AdminBusinessContractResponse>,
) {

    /** 매니저 식별 및 컨텍스트 노출에 필요한 최소 필드. */
    @Schema(description = "매니저 요약 정보")
    data class MemberSummary(
        @param:Schema(description = "매니저 회원 ID") val id: UUID,
        @param:Schema(description = "로그인 아이디") val loginId: String,
        @param:Schema(description = "이메일") val email: String,
        @param:Schema(description = "회원 유형") val memberType: String,
        @param:Schema(description = "계정 상태") val status: String,
    ) {
        companion object {
            fun from(member: MemberResponse): MemberSummary = MemberSummary(
                id = member.id,
                loginId = member.loginId,
                email = member.email,
                memberType = member.memberType.name,
                status = member.status.name,
            )
        }
    }

    companion object {
        fun of(
            member: MemberResponse,
            contracts: PageResult<AdminBusinessContractResponse>,
        ): AdminMemberContractListResponse = AdminMemberContractListResponse(
            member = MemberSummary.from(member),
            contracts = contracts,
        )
    }
}

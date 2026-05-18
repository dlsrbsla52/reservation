package com.media.bus.iam.admin.dto

import com.media.bus.common.web.wrapper.PageResult
import com.media.bus.iam.member.dto.MemberResponse
import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

/**
 * ## 어드민 전용 매니저별 계약 결합 응답
 *
 * 설계 의도:
 * - 어드민이 매니저 한 명의 계약 현황을 한 번에 확인할 수 있도록 매니저 요약 정보와 계약 목록을 함께 반환한다.
 * - 매니저 정보는 iam 내부 `MemberService`에서, 계약 정보는 reservation S2S 호출로 결합한다.
 *
 * 호출 측 어드민 UI는 응답 한 번으로 매니저 컨텍스트와 계약 리스트를 동시 렌더할 수 있다.
 */
@Schema(description = "어드민 전용 매니저별 계약 결합 응답")
data class AdminMemberContractListResponse(
    @param:Schema(description = "매니저 요약 정보") val member: MemberSummary,
    @param:Schema(description = "매니저 계약 목록(페이지)") val contracts: PageResult<AdminBusinessContractResponse>,
) {

    /**
     * 매니저 식별 및 컨텍스트 노출에 필요한 최소 필드.
     * 전화번호·생성일 등 민감/불필요 정보는 노출하지 않는다 (필요 시 별도 `/admin/members/{memberId}` 호출).
     */
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

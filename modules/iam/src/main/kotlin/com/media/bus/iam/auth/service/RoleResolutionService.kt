package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.result.AuthResult
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

/**
 * ## 회원 역할 조회 공통 서비스
 *
 * AuthService, MemberService, AdminMemberService에서 반복되던
 * "역할 조회 → 검증 → MemberType 반환" 패턴을 통합한다.
 */
@Service
class RoleResolutionService(
    private val memberRoleRepository: MemberRoleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 회원 ID로 역할을 조회하여 MemberType을 반환한다.
     *
     * @param memberId 회원 UUID
     * @throws BaseException 역할이 없거나 MemberType 매핑 실패 시
     */
    fun resolveMemberType(
        memberId: UUID,
    ): MemberType {
        val memberRoles = memberRoleRepository.findWithRoleByMemberId(memberId)
        if (memberRoles.isEmpty()) {
            throw BusinessException(AuthResult.ROLE_NOT_FOUND)
        }
        if (memberRoles.size > 1) {
            log.warn("[RoleResolutionService] 회원 [{}]에게 복수 역할이 존재합니다. 첫 번째 역할을 사용합니다.", memberId)
        }
        return MemberType.fromName(memberRoles.first().role.name)
            ?: throw BusinessException(AuthResult.ROLE_NOT_FOUND)
    }
}

package com.media.bus.iam.member.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.member.dto.MemberResponse
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class MemberService(
    private val memberRepository: MemberRepository,
    private val jwtProvider: JwtProvider,
    private val memberRoleRepository: MemberRoleRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 로그인시 사용된 jwt 토큰으로 회원 조회. */
    @Transactional(readOnly = true)
    fun findByJwtMember(jwt: String): MemberResponse {
        val principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt),
        )
        val member = memberRepository.findById(principal.id)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** memberId를 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByMemberId(memberId: String): MemberResponse {
        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 로그인 아이디를 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByLoginId(loginId: String): MemberResponse {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 이메일을 통한 회원 조회. */
    @Transactional(readOnly = true)
    fun findByEmail(email: String): MemberResponse {
        val member = memberRepository.findByEmail(email)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)
        return toResponse(member)
    }

    /** 회원 ID로 역할을 조회하여 MemberType을 반환한다. */
    private fun resolveMemberType(memberId: UUID): MemberType {
        val memberRoles = memberRoleRepository.findWithRoleByMemberId(memberId)
        if (memberRoles.isEmpty()) {
            throw BaseException(CommonResult.USERNAME_NOT_FOUND_FAIL)
        }
        return MemberType.fromName(memberRoles.first().role.name)
            ?: throw BaseException(CommonResult.INTERNAL_ERROR)
    }

    /**
     * Member 엔티티를 MemberResponse로 변환한다.
     * 역할 조회, MemberType 결정, 응답 생성을 통합 처리한다.
     */
    private fun toResponse(member: MemberEntity): MemberResponse {
        val memberType = resolveMemberType(member.id.value)
        return MemberResponse(
            id = member.id.value,
            loginId = member.loginId,
            email = member.email,
            phoneNumber = member.phoneNumber,
            memberType = memberType,
            status = member.status,
            businessNumber = member.businessNumber,
            createdAt = member.createdAt,
            updatedAt = member.updatedAt,
        )
    }
}

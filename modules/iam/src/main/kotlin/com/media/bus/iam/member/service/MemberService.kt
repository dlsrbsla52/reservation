package com.media.bus.iam.member.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.common.result.type.CommonResult
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.auth.service.AuthService
import com.media.bus.iam.auth.service.RoleResolutionService
import com.media.bus.iam.member.dto.FindMeRequest
import com.media.bus.iam.member.dto.MemberModifyRequest
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
    private val roleResolutionService: RoleResolutionService,
    private val authService: AuthService,
) {
    private val log = LoggerFactory.getLogger(MemberService::class.java)

    /** 로그인시 사용된 jwt 토큰으로 회원 조회. */
    @Transactional(readOnly = true)
    fun findByJwtMember(jwt: String): MemberResponse {
        val principal = jwtProvider.getPrincipalFromClaims(
            jwtProvider.parseClaimsFromToken(jwt),
        )

        log.debug("요청 ID : {}", principal.id)
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

    /**
     * 회원이 아이디를 분실했을 경우 처리.
     * phoneNumber, email 중 최소 하나는 필수 — 둘 다 없으면 예외.
     */
    @Transactional(readOnly = true)
    fun findMe(request: FindMeRequest): MemberResponse {
        require(request.phoneNumber != null || request.email != null) {
            "phoneNumber 또는 email 중 하나는 필수입니다."
        }

        val member = memberRepository.findMe(request)
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        return toResponse(member)
    }

    /**
     * 회원 탈퇴 처리.
     * 2차 본인 인증 완료 여부를 확인한 뒤 회원 상태를 `WITHDRAWN`으로 변경한다.
     * 처리 완료 후 인증 상태를 삭제하여 1회성으로 사용한다.
     */
    @Transactional
    fun withdraw(memberId: String) {
        authService.checkVerified(memberId)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        member.withdraw()
        authService.clearVerification(memberId)
        log.debug("[MemberService.withdraw] 회원 탈퇴 처리 완료. memberId={}", memberId)
    }

    fun modify(memberId: String, request: MemberModifyRequest) {
        authService.checkVerified(memberId)

        val member = memberRepository.findById(UUID.fromString(memberId))
            ?: throw BusinessException(CommonResult.USER_NOT_FOUND_FAIL)

        member.modify(request)
        log.debug("[MemberService.modify] 회원 정보 수정 완료. memberId={}", memberId)
    }

    /**
     * Member 엔티티를 MemberResponse로 변환한다.
     * 역할 조회, MemberType 결정, 응답 생성을 통합 처리한다.
     */
    private fun toResponse(member: MemberEntity): MemberResponse {
        val memberType = roleResolutionService.resolveMemberType(member.id.value)
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

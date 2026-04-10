package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.iam.admin.dto.AdminMemberResponse
import com.media.bus.iam.admin.dto.CreateAdminMemberRequest
import com.media.bus.iam.admin.guard.AdminRegisterRequestValidator
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.repository.MemberRepository
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * ## 어드민 멤버 생성 비즈니스 로직 서비스
 *
 * 일반 가입(`AuthService.register`)과의 차이점:
 * - `emailVerified = true`로 즉시 설정 (manager가 직접 생성하므로 인증 불필요)
 * - 이메일 인증 토큰 Redis 저장 생략
 * - ADMIN 계열 타입만 허용 (`AdminRegisterRequestValidator` 위임)
 */
@Service
class AdminMemberService(
    private val memberRepository: MemberRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val adminRegisterRequestValidator: AdminRegisterRequestValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 어드민 멤버를 생성한다.
     * 1. 입력 검증 (`AdminRegisterRequestValidator`에 위임)
     * 2. 이메일 인증 완료 상태로 Member 저장
     * 3. 역할(Role) 조회 및 MemberRole 저장
     */
    @Transactional
    fun createAdminMember(request: CreateAdminMemberRequest): AdminMemberResponse {
        // 입력 검증
        adminRegisterRequestValidator.validate(request)

        // 어드민은 manager가 직접 생성하므로 emailVerified = true로 설정
        val member = MemberEntity.createAdmin(
            loginId = request.loginId,
            encodedPassword = passwordEncoder.encode(request.password)!!,
            email = request.email,
            phoneNumber = request.phoneNumber,
            memberName = request.memberName,
        )

        // role 마스터 테이블에서 MemberType에 해당하는 Role 조회 및 역할 부여
        val role = roleRepository.findByName(request.memberType.name)
            ?: throw BaseException(AuthResult.ROLE_NOT_FOUND)
        MemberRoleEntity.of(member, role)

        log.info(
            "[AdminMemberService.createAdminMember] 어드민 멤버 생성 완료. memberId={}, memberType={}",
            member.id.value, request.memberType,
        )

        return AdminMemberResponse.of(member, request.memberType)
    }
}

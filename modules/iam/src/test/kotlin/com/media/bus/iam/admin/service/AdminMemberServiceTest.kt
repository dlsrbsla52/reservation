package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.common.exceptions.BusinessException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.JwtProvider
import com.media.bus.iam.admin.entity.MemberStatusHistoryEntity
import com.media.bus.iam.admin.guard.AdminRegisterRequestValidator
import com.media.bus.iam.admin.repository.MemberStatusHistoryRepository
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.entity.RolePermissionEntity
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.auth.service.RoleResolutionService
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.OffsetDateTime
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("AdminMemberService 단위 테스트")
class AdminMemberServiceTest {

    @MockK private lateinit var roleRepository: RoleRepository
    @MockK private lateinit var passwordEncoder: PasswordEncoder
    @MockK private lateinit var adminRegisterRequestValidator: AdminRegisterRequestValidator
    @MockK private lateinit var memberRepository: MemberRepository
    @MockK private lateinit var roleResolutionService: RoleResolutionService
    @MockK private lateinit var jwtProvider: JwtProvider
    @MockK private lateinit var memberRoleRepository: MemberRoleRepository
    @MockK private lateinit var rolePermissionRepository: RolePermissionRepository
    @MockK private lateinit var memberStatusHistoryRepository: MemberStatusHistoryRepository
    @MockK(relaxed = true) private lateinit var auditLogService: com.media.bus.iam.audit.service.AuditLogService

    @InjectMockKs private lateinit var adminMemberService: AdminMemberService

    private val requesterId = UUID.randomUUID()
    private val targetMemberId = UUID.randomUUID()

    private fun mockMember(
        id: UUID = targetMemberId,
        status: MemberStatus = MemberStatus.ACTIVE,
    ): MemberEntity {
        val member = mockk<MemberEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns id
        every { member.id } returns entityId
        every { member.loginId } returns "testuser"
        every { member.email } returns "test@example.com"
        every { member.phoneNumber } returns "01012345678"
        every { member.memberName } returns "테스트"
        every { member.status } returns status
        every { member.emailVerified } returns true
        every { member.businessNumber } returns null
        every { member.createdAt } returns OffsetDateTime.now()
        every { member.updatedAt } returns OffsetDateTime.now()
        return member
    }

    private fun mockRole(name: String = "MEMBER"): RoleEntity {
        val role = mockk<RoleEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns UUID.randomUUID()
        every { role.id } returns entityId
        every { role.name } returns name
        every { role.displayName } returns "일반 회원"
        return role
    }

    private fun mockPermission(name: String = "READ"): PermissionEntity {
        val perm = mockk<PermissionEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns UUID.randomUUID()
        every { perm.id } returns entityId
        every { perm.name } returns name
        every { perm.displayName } returns "조회"
        return perm
    }

    private fun mockRolePermission(permission: PermissionEntity): RolePermissionEntity {
        val rp = mockk<RolePermissionEntity>(relaxed = true)
        every { rp.permission } returns permission
        return rp
    }

    // ──────────────────────────────────────────────────────────────
    // findAllMembers
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findAllMembers")
    inner class FindAllMembers {

        @Test
        @DisplayName("정상 페이지네이션 조회")
        fun `정상 조회`() {
            val member = mockMember()
            every { memberRepository.findAllPaged(0, 20) } returns listOf(member)
            every { memberRepository.count() } returns 1L
            every { roleResolutionService.resolveMemberType(targetMemberId) } returns MemberType.MEMBER

            val result = adminMemberService.findAllMembers(0, 20)

            assertThat(result.items).hasSize(1)
            assertThat(result.totalCnt).isEqualTo(1L)
            assertThat(result.pageRows).isEqualTo(20)
            assertThat(result.pageNum).isEqualTo(0)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // findMemberDetail
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findMemberDetail")
    inner class FindMemberDetail {

        @Test
        @DisplayName("정상 상세 조회")
        fun `정상 조회`() {
            val member = mockMember()
            val role = mockRole()
            val permission = mockPermission()
            val memberRole = mockk<MemberRoleEntity>(relaxed = true)
            every { memberRole.role } returns role

            every { memberRepository.findById(targetMemberId) } returns member
            every { memberRoleRepository.findByMemberId(targetMemberId) } returns memberRole
            every { rolePermissionRepository.findByRoleId(any()) } returns listOf(mockRolePermission(permission))

            val result = adminMemberService.findMemberDetail(targetMemberId)

            assertThat(result.memberId).isEqualTo(targetMemberId)
            assertThat(result.role.name).isEqualTo("MEMBER")
            assertThat(result.permissions).hasSize(1)
        }

        @Test
        @DisplayName("회원 없으면 MEMBER_NOT_FOUND 예외")
        fun `회원 없음`() {
            every { memberRepository.findById(targetMemberId) } returns null

            assertThatThrownBy { adminMemberService.findMemberDetail(targetMemberId) }
                .isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.MEMBER_NOT_FOUND)
                })
        }
    }

    // ──────────────────────────────────────────────────────────────
    // suspendMember
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("suspendMember")
    inner class SuspendMember {

        @Test
        @DisplayName("정상 정지 처리")
        fun `정상 정지`() {
            val member = mockMember()
            val requester = mockMember(id = requesterId)
            every { memberRepository.findById(targetMemberId) } returns member
            every { memberRepository.findById(requesterId) } returns requester
            every { roleResolutionService.resolveMemberType(targetMemberId) } returns MemberType.MEMBER
            every { jwtProvider.deleteRefreshToken(targetMemberId.toString()) } just Runs

            // Exposed static factory mock — 트랜잭션 컨텍스트 없이 동작하도록
            mockkObject(MemberStatusHistoryEntity.Companion)
            every { MemberStatusHistoryEntity.create(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)

            adminMemberService.suspendMember(requesterId, targetMemberId, "이용약관 위반")

            verify { member.suspend() }
            verify { jwtProvider.deleteRefreshToken(targetMemberId.toString()) }

            unmockkObject(MemberStatusHistoryEntity.Companion)
        }

        @Test
        @DisplayName("자기 자신 정지 시 CANNOT_SUSPEND_SELF 예외")
        fun `자기 자신 정지`() {
            assertThatThrownBy { adminMemberService.suspendMember(targetMemberId, targetMemberId, "사유") }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.CANNOT_SUSPEND_SELF)
                })
        }

        @Test
        @DisplayName("ADMIN_MASTER 정지 시 CANNOT_SUSPEND_ADMIN_MASTER 예외")
        fun `ADMIN_MASTER 정지`() {
            val member = mockMember()
            every { memberRepository.findById(targetMemberId) } returns member
            every { roleResolutionService.resolveMemberType(targetMemberId) } returns MemberType.ADMIN_MASTER

            assertThatThrownBy { adminMemberService.suspendMember(requesterId, targetMemberId, "사유") }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.CANNOT_SUSPEND_ADMIN_MASTER)
                })
        }

        @Test
        @DisplayName("비ACTIVE 회원 정지 시 MEMBER_NOT_ACTIVE 예외")
        fun `비ACTIVE 회원`() {
            val member = mockMember(status = MemberStatus.SUSPENDED)
            every { memberRepository.findById(targetMemberId) } returns member

            assertThatThrownBy { adminMemberService.suspendMember(requesterId, targetMemberId, "사유") }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.MEMBER_NOT_ACTIVE)
                })
        }
    }

    // ──────────────────────────────────────────────────────────────
    // unsuspendMember
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("unsuspendMember")
    inner class UnsuspendMember {

        @Test
        @DisplayName("정상 정지 해제")
        fun `정상 해제`() {
            val member = mockMember(status = MemberStatus.SUSPENDED)
            val requester = mockMember(id = requesterId)
            every { memberRepository.findById(targetMemberId) } returns member
            every { memberRepository.findById(requesterId) } returns requester

            mockkObject(MemberStatusHistoryEntity.Companion)
            every { MemberStatusHistoryEntity.create(any(), any(), any(), any(), any()) } returns mockk(relaxed = true)

            adminMemberService.unsuspendMember(requesterId, targetMemberId, "경고 조치 완료")

            verify { member.unsuspend() }

            unmockkObject(MemberStatusHistoryEntity.Companion)
        }

        @Test
        @DisplayName("비SUSPENDED 회원 해제 시 MEMBER_NOT_SUSPENDED 예외")
        fun `비SUSPENDED 회원`() {
            val member = mockMember(status = MemberStatus.ACTIVE)
            every { memberRepository.findById(targetMemberId) } returns member

            assertThatThrownBy { adminMemberService.unsuspendMember(requesterId, targetMemberId, "사유") }
                .isInstanceOf(BusinessException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BusinessException).result).isEqualTo(AuthResult.MEMBER_NOT_SUSPENDED)
                })
        }
    }

    // ──────────────────────────────────────────────────────────────
    // searchMembers
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("키워드 검색 정상 처리")
    fun `searchMembers 정상`() {
        val member = mockMember()
        every { memberRepository.searchByKeyword("test") } returns listOf(member)
        every { roleResolutionService.resolveMemberType(targetMemberId) } returns MemberType.MEMBER

        val result = adminMemberService.searchMembers("test")

        assertThat(result).hasSize(1)
        assertThat(result[0].loginId).isEqualTo("testuser")
    }
}

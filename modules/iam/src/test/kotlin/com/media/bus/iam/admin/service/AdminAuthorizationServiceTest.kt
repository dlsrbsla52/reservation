package com.media.bus.iam.admin.service

import com.media.bus.common.exceptions.BaseException
import com.media.bus.iam.admin.dto.AssignPermissionRequest
import com.media.bus.iam.admin.dto.ChangeMemberRoleRequest
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.PermissionEntity
import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.entity.RolePermissionEntity
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.repository.PermissionRepository
import com.media.bus.iam.auth.repository.RolePermissionRepository
import com.media.bus.iam.auth.repository.RoleRepository
import com.media.bus.iam.auth.result.AuthResult
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.repository.MemberRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("AdminAuthorizationService 단위 테스트")
class AdminAuthorizationServiceTest {

    @MockK private lateinit var roleRepository: RoleRepository

    @MockK private lateinit var permissionRepository: PermissionRepository

    @MockK private lateinit var rolePermissionRepository: RolePermissionRepository

    @MockK private lateinit var memberRoleRepository: MemberRoleRepository

    @MockK private lateinit var memberRepository: MemberRepository

    @InjectMockKs private lateinit var service: AdminAuthorizationService

    private val testRoleId = UUID.randomUUID()
    private val testMemberId = UUID.randomUUID()

    private fun mockRole(name: String = "MEMBER"): RoleEntity {
        val role = mockk<RoleEntity>(relaxed = true)
        val entityId = mockk<EntityID<UUID>>()
        every { entityId.value } returns testRoleId
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

    @Test
    fun `findAllRoles 정상`() {
        val role = mockRole()
        every { roleRepository.findAll() } returns listOf(role)

        val result = service.findAllRoles()

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("MEMBER")
    }

    @Nested
    @DisplayName("findRoleDetail")
    inner class FindRoleDetail {

        @Test
        fun `정상 조회`() {
            val role = mockRole()
            val permission = mockPermission()
            every { roleRepository.findById(testRoleId) } returns role
            every { rolePermissionRepository.findPermissionsByRoleId(testRoleId) } returns listOf(permission)

            val result = service.findRoleDetail(testRoleId)

            assertThat(result.name).isEqualTo("MEMBER")
            assertThat(result.permissions).hasSize(1)
        }

        @Test
        fun `역할 없음`() {
            every { roleRepository.findById(testRoleId) } returns null

            assertThatThrownBy { service.findRoleDetail(testRoleId) }
                .isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.ROLE_NOT_FOUND)
                })
        }
    }

    @Test
    fun `findAllPermissions 정상`() {
        val perm = mockPermission()
        every { permissionRepository.findAll() } returns listOf(perm)

        val result = service.findAllPermissions()

        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("READ")
    }

    @Nested
    @DisplayName("findPermissionsByRoleId")
    inner class FindPermissionsByRoleId {

        @Test
        fun `정상 조회`() {
            val role = mockRole()
            val perm = mockPermission()
            every { roleRepository.findById(testRoleId) } returns role
            every { rolePermissionRepository.findPermissionsByRoleId(testRoleId) } returns listOf(perm)

            val result = service.findPermissionsByRoleId(testRoleId)

            assertThat(result).hasSize(1)
        }

        @Test
        fun `역할 없음`() {
            every { roleRepository.findById(testRoleId) } returns null

            assertThatThrownBy { service.findPermissionsByRoleId(testRoleId) }
                .isInstanceOf(BaseException::class.java)
        }
    }

    @Nested
    @DisplayName("assignPermissionToRole")
    inner class AssignPermission {

        @Test
        fun `역할 없음`() {
            every { roleRepository.findById(testRoleId) } returns null

            assertThatThrownBy {
                service.assignPermissionToRole(testRoleId, AssignPermissionRequest("READ"))
            }.isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.ROLE_NOT_FOUND)
                })
        }

        @Test
        fun `권한 없음`() {
            val role = mockRole()
            every { roleRepository.findById(testRoleId) } returns role
            every { permissionRepository.findByName("UNKNOWN") } returns null

            assertThatThrownBy {
                service.assignPermissionToRole(testRoleId, AssignPermissionRequest("UNKNOWN"))
            }.isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.PERMISSION_NOT_FOUND)
                })
        }

        @Test
        fun `이미 할당됨`() {
            val role = mockRole()
            val perm = mockPermission()
            every { roleRepository.findById(testRoleId) } returns role
            every { permissionRepository.findByName("READ") } returns perm
            every { rolePermissionRepository.findByRoleIdAndPermissionName(testRoleId, "READ") } returns mockRolePermission(perm)

            assertThatThrownBy {
                service.assignPermissionToRole(testRoleId, AssignPermissionRequest("READ"))
            }.isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.ROLE_PERMISSION_ALREADY_EXISTS)
                })
        }
    }

    @Nested
    @DisplayName("revokePermissionFromRole")
    inner class RevokePermission {

        @Test
        fun `역할 없음`() {
            every { roleRepository.findById(testRoleId) } returns null

            assertThatThrownBy { service.revokePermissionFromRole(testRoleId, "READ") }
                .isInstanceOf(BaseException::class.java)
        }

        @Test
        fun `매핑 없음`() {
            val role = mockRole()
            every { roleRepository.findById(testRoleId) } returns role
            every { rolePermissionRepository.findByRoleIdAndPermissionName(testRoleId, "READ") } returns null

            assertThatThrownBy { service.revokePermissionFromRole(testRoleId, "READ") }
                .isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.ROLE_PERMISSION_NOT_FOUND)
                })
        }
    }

    @Nested
    @DisplayName("findMemberRole")
    inner class FindMemberRole {

        @Test
        fun `정상 조회`() {
            val role = mockRole()
            val perm = mockPermission()
            val memberRole = mockk<MemberRoleEntity>(relaxed = true)
            every { memberRole.role } returns role
            every { memberRoleRepository.findByMemberId(testMemberId) } returns memberRole
            every { rolePermissionRepository.findPermissionsByRoleId(any()) } returns listOf(perm)

            val result = service.findMemberRole(testMemberId)

            assertThat(result.role.name).isEqualTo("MEMBER")
        }

        @Test
        fun `역할 없음`() {
            every { memberRoleRepository.findByMemberId(testMemberId) } returns null

            assertThatThrownBy { service.findMemberRole(testMemberId) }
                .isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.MEMBER_ROLE_NOT_FOUND)
                })
        }
    }

    @Nested
    @DisplayName("changeMemberRole")
    inner class ChangeMemberRole {

        @Test
        fun `회원 없음`() {
            every { memberRepository.findById(testMemberId) } returns null

            assertThatThrownBy {
                service.changeMemberRole(testMemberId, ChangeMemberRoleRequest("ADMIN_USER"))
            }.isInstanceOf(BaseException::class.java)
        }

        @Test
        fun `역할 없음`() {
            val member = mockk<MemberEntity>(relaxed = true)
            every { memberRepository.findById(testMemberId) } returns member
            every { roleRepository.findByName("UNKNOWN") } returns null

            assertThatThrownBy {
                service.changeMemberRole(testMemberId, ChangeMemberRoleRequest("UNKNOWN"))
            }.isInstanceOf(BaseException::class.java)
                .satisfies(Consumer { ex ->
                    assertThat((ex as BaseException).result).isEqualTo(AuthResult.ROLE_NOT_FOUND)
                })
        }
    }
}

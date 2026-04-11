package com.media.bus.iam.auth.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.contract.entity.member.MemberType
import com.media.bus.iam.auth.entity.MemberRoleEntity
import com.media.bus.iam.auth.entity.RoleEntity
import com.media.bus.iam.auth.repository.MemberRoleRepository
import com.media.bus.iam.auth.result.AuthResult
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*
import java.util.function.Consumer

@ExtendWith(MockKExtension::class)
@DisplayName("RoleResolutionService 단위 테스트")
class RoleResolutionServiceTest {

    @MockK
    private lateinit var memberRoleRepository: MemberRoleRepository

    @InjectMockKs
    private lateinit var roleResolutionService: RoleResolutionService

    private val testMemberId = UUID.randomUUID()

    @Test
    @DisplayName("정상 역할 반환")
    fun `정상 역할 반환`() {
        val role = mockk<RoleEntity>(relaxed = true)
        every { role.name } returns "MEMBER"

        val memberRole = mockk<MemberRoleEntity>(relaxed = true)
        every { memberRole.role } returns role

        every { memberRoleRepository.findWithRoleByMemberId(testMemberId) } returns listOf(memberRole)

        val result = roleResolutionService.resolveMemberType(testMemberId)

        assertThat(result).isEqualTo(MemberType.MEMBER)
    }

    @Test
    @DisplayName("역할이 없으면 ROLE_NOT_FOUND 예외")
    fun `역할 없음`() {
        every { memberRoleRepository.findWithRoleByMemberId(testMemberId) } returns emptyList()

        assertThatThrownBy { roleResolutionService.resolveMemberType(testMemberId) }
            .isInstanceOf(BusinessException::class.java)
            .satisfies(Consumer { ex ->
                assertThat((ex as BusinessException).result).isEqualTo(AuthResult.ROLE_NOT_FOUND)
            })
    }

    @Test
    @DisplayName("ADMIN_MASTER 역할 반환")
    fun `ADMIN_MASTER 반환`() {
        val role = mockk<RoleEntity>(relaxed = true)
        every { role.name } returns "ADMIN_MASTER"

        val memberRole = mockk<MemberRoleEntity>(relaxed = true)
        every { memberRole.role } returns role

        every { memberRoleRepository.findWithRoleByMemberId(testMemberId) } returns listOf(memberRole)

        val result = roleResolutionService.resolveMemberType(testMemberId)

        assertThat(result).isEqualTo(MemberType.ADMIN_MASTER)
    }
}

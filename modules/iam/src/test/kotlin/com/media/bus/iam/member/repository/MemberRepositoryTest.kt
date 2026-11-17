package com.media.bus.iam.member.repository

import com.media.bus.common.entity.common.UuidV7
import com.media.bus.iam.member.entity.MemberEntity
import com.media.bus.iam.member.entity.MemberTable
import com.media.bus.iam.member.entity.enumerated.MemberStatus
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * ## MemberRepository Exposed 단위 테스트
 *
 * H2 인메모리 DB로 Exposed DAO 동작을 검증한다.
 * - UUID 지정 생성 시 해당 ID가 그대로 저장되는지 확인
 * - UuidV7.generate()로 생성 시 UUID가 자동으로 부여되는지 확인
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberRepositoryTest {

    private lateinit var memberRepository: MemberRepository

    @BeforeAll
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:iamtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            exec("CREATE SCHEMA IF NOT EXISTS auth")
            SchemaUtils.create(MemberTable)
        }
        memberRepository = MemberRepository()
    }

    @Test
    @DisplayName("UUID를 지정하여 멤버를 생성하면, 해당 ID가 그대로 저장되어야 한다.")
    fun `createWithSpecificUuid_shouldPersistGivenId`() {
        transaction {
            val specificId = UuidV7.generate()
            val member = MemberEntity.new(specificId) {
                loginId = "manual_user"
                password = "password123!"
                email = "manual@example.com"
                phoneNumber = "01012345678"
                emailVerified = false
                status = MemberStatus.ACTIVE
                businessNumber = null
                memberName = "테스트"
            }

            val found = memberRepository.findById(member.id.value)
            assertThat(found).isNotNull
            assertThat(found!!.loginId).isEqualTo("manual_user")
            assertThat(found.id.value).isEqualTo(specificId)
        }
    }

    @Test
    @DisplayName("UuidV7.generate()로 멤버를 생성하면, UUID가 자동으로 부여되어야 한다.")
    fun `createWithGeneratedUuid_shouldHaveNonNullId`() {
        transaction {
            val ts = System.currentTimeMillis()
            val member = MemberEntity.new(UuidV7.generate()) {
                loginId = "auto_user_$ts"
                password = "password123!"
                email = "auto_$ts@example.com"
                phoneNumber = "01087654321"
                emailVerified = false
                status = MemberStatus.ACTIVE
                businessNumber = null
                memberName = "테스트"
            }

            assertThat(member.id.value).isNotNull
            assertThat(member.loginId).startsWith("auto_user_")
        }
    }

    @Test
    @DisplayName("existsByLoginId — 존재하는 loginId는 true를 반환해야 한다.")
    fun `existsByLoginId_existingId_returnsTrue`() {
        transaction {
            val ts = System.currentTimeMillis()
            MemberEntity.new(UuidV7.generate()) {
                loginId = "exists_user_$ts"
                password = "pw"
                email = "exists_$ts@example.com"
                phoneNumber = "01011112222"
                emailVerified = false
                status = MemberStatus.ACTIVE
                businessNumber = null
                memberName = "테스트"
            }

            assertThat(memberRepository.existsByLoginId("exists_user_$ts")).isTrue
            assertThat(memberRepository.existsByLoginId("nonexistent_$ts")).isFalse
        }
    }
    @Test
    fun `아이디 찾기는 활성과 비활성 회원만 조회한다`() {
        transaction {
            MemberStatus.entries.forEach { state ->
                val key = "lookup_${state.name}"
                MemberEntity.new {
                    loginId = key
                    password = "dummy-password"
                    email = "$key@example.test"
                    phoneNumber = "01000000000"
                    emailVerified = false
                    status = state
                    memberName = "찾기테스트"
                }
                val found = memberRepository.findMe(
                    com.media.bus.iam.member.dto.FindMeRequest("찾기테스트", null, "$key@example.test"),
                )
                assertThat(found != null).isEqualTo(state in listOf(MemberStatus.ACTIVE, MemberStatus.INACTIVE))
            }
        }
    }

}

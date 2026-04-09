package com.media.bus.stop.service

import com.media.bus.contract.entity.member.MemberType
import com.media.bus.contract.security.MemberPrincipal
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.guard.StopCommandGuard
import com.media.bus.stop.repository.StopRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.*

/**
 * `StopService` 단위 테스트.
 * 인가 처리는 인터셉터로 이전되어 `MemberPrincipal`을 직접 주입합니다.
 * `StopEntity` 팩토리 메서드는 `mockkObject`로 대체하여 DB 없이 검증합니다.
 */
@ExtendWith(MockKExtension::class)
class StopServiceTest {

    @MockK
    lateinit var stopRepository: StopRepository

    @MockK
    lateinit var stopCommandGuard: StopCommandGuard

    @InjectMockKs
    lateinit var stopService: StopService

    @Test
    fun `createOneStop_Guard_호출_후_엔티티_생성_확인`() {
        val memberId = UUID.randomUUID()
        val principal = MemberPrincipal(memberId, "admin", "admin@test.com", MemberType.ADMIN_USER, true, emptySet())
        val request = SimpleStopCreateRequest("123", "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE)

        justRun { stopCommandGuard.validateNotDuplicate("123") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(principal, request)

            verify { stopCommandGuard.validateNotDuplicate("123") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }

    @Test
    fun `createOneStop_Guard와_Repository_호출_순서_확인`() {
        val memberId = UUID.randomUUID()
        val principal = MemberPrincipal(memberId, "admin", "admin@test.com", MemberType.ADMIN_MASTER, true, emptySet())
        val request = SimpleStopCreateRequest("999", "마스터정류소", "128.0", "36.0", "NODE999", StopType.VILLAGE_BUS)

        justRun { stopCommandGuard.validateNotDuplicate("999") }
        mockkObject(StopEntity.Companion) {
            every { StopEntity.createFromRequest(request, memberId) } returns mockk()

            stopService.createOneStop(principal, request)

            // Guard가 먼저 호출된 후 엔티티 생성
            verify { stopCommandGuard.validateNotDuplicate("999") }
            verify { StopEntity.createFromRequest(request, memberId) }
        }
    }
}

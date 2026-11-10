package com.media.bus.reservation.reservation.service

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.reservation.contract.dto.request.CreateContractRequest
import com.media.bus.reservation.contract.entity.ContractTable
import com.media.bus.reservation.contract.entity.enums.PaymentCycle
import com.media.bus.reservation.contract.entity.enums.PaymentMethod
import com.media.bus.reservation.contract.repository.ContractRepository
import com.media.bus.reservation.contract.result.ContractResult
import com.media.bus.reservation.contract.service.ContractService
import com.media.bus.reservation.reservation.dto.request.CompleteToContractRequest
import com.media.bus.reservation.reservation.dto.request.CreateStopReservationRequest
import com.media.bus.reservation.reservation.dto.response.StopInfo
import com.media.bus.reservation.reservation.entity.ReservationConsultationTable
import com.media.bus.reservation.reservation.entity.ReservationEntity
import com.media.bus.reservation.reservation.entity.ReservationTable
import com.media.bus.reservation.reservation.entity.enums.ReservationStatus
import com.media.bus.reservation.reservation.hold.HoldProperties
import com.media.bus.reservation.reservation.repository.ReservationConsultationRepository
import com.media.bus.reservation.reservation.repository.ReservationRepository
import com.media.bus.reservation.reservation.result.ReservationResult
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.ClassLoaderResourceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * ## 선점·계약 독점의 DB 동시성 검증 (실제 PostgreSQL 필요, 기본 비활성)
 *
 * 이 정책의 정합성은 부분 유니크 인덱스, 제외 제약, 행 잠금이라는 **PostgreSQL 기능**에 기대므로
 * 목이나 H2 로는 검증할 수 없다. 비어 있는 일회용 PostgreSQL 에 Liquibase changelog 를 적용한 뒤
 * 실제 서비스 코드를 여러 스레드에서 동시에 호출한다.
 *
 * 실행:
 * ```bash
 * docker run -d --rm --name reservation-it-pg -e POSTGRES_PASSWORD=it -p 25433:5432 postgres:18.3
 * RESERVATION_IT_JDBC_URL=jdbc:postgresql://localhost:25433/postgres \
 *   ./gradlew :modules:reservation:test --tests "*StopHoldPostgresIT"
 * docker stop reservation-it-pg
 * ```
 * 접속 계정은 `RESERVATION_IT_DB_USER` / `RESERVATION_IT_DB_PASSWORD` 로 바꿀 수 있다(기본 postgres / it).
 * 스키마를 만들고 데이터를 쓰므로 **개발·운영 DB 를 가리키지 않는다.**
 *
 * Spring 컨텍스트 없이 서비스를 직접 생성하므로 `@Transactional` 은 동작하지 않는다.
 * 대신 호출마다 Exposed `transaction {}` 으로 감싸 요청 1건 = 트랜잭션 1개를 재현한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "RESERVATION_IT_JDBC_URL", matches = ".+")
@DisplayName("선점·계약 독점 PostgreSQL 동시성 검증")
class StopHoldPostgresIT {

    private lateinit var db: Database
    private lateinit var reservationService: ReservationService
    private lateinit var contractService: ContractService

    private val holdDuration: Duration = Duration.ofDays(7)

    @BeforeAll
    fun setUp() {
        val url = System.getenv("RESERVATION_IT_JDBC_URL")
        val user = System.getenv("RESERVATION_IT_DB_USER") ?: "postgres"
        val password = System.getenv("RESERVATION_IT_DB_PASSWORD") ?: "it"

        // 운영과 같은 경로(Liquibase changelog)로 스키마를 만든다 — 마이그레이션 자체도 검증 대상이다.
        DriverManager.getConnection(url, user, password).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            Liquibase("db/changelog/db.changelog-master.xml", ClassLoaderResourceAccessor(), database).update("")
        }

        db = Database.connect(url = url, driver = "org.postgresql.Driver", user = user, password = password)
        contractService = ContractService(ContractRepository())
        reservationService = ReservationService(
            reservationRepository = ReservationRepository(),
            consultationRepository = ReservationConsultationRepository(),
            contractService = contractService,
            holdProperties = HoldProperties(duration = holdDuration, maxActivePerMember = 100),
        )
    }

    private fun stop() = StopInfo(id = UUID.randomUUID(), stopId = "IT-STOP", stopName = "통합테스트 정류소")

    private fun reservationRequest(stop: StopInfo) = CreateStopReservationRequest(
        stopId = setOf(stop.id),
        consultationRequestedAt = OffsetDateTime.now(),
        desiredContractStartDate = OffsetDateTime.now().toLocalDate().plusDays(30),
    )

    private fun contractRequest(stop: StopInfo, start: String, end: String) = CreateContractRequest(
        stopId = stop.id,
        contractName = "통합테스트 계약",
        totalAmount = BigDecimal("1200000.00"),
        payAmount = BigDecimal("100000.00"),
        paymentCycle = PaymentCycle.MONTHLY,
        paymentMethod = PaymentMethod.BANK_TRANSFER,
        memberId = null,
        note = null,
        contractStartDate = OffsetDateTime.parse(start),
        contractEndDate = OffsetDateTime.parse(end),
    )

    private fun reserve(memberId: UUID, stop: StopInfo): UUID =
        transaction(db) { reservationService.createReservation(memberId, stop, reservationRequest(stop)).id.value }

    private fun statusOf(reservationId: UUID): ReservationStatus =
        transaction(db) { ReservationEntity[reservationId].status }

    private fun historyOf(reservationId: UUID): List<ReservationStatus> = transaction(db) {
        ReservationConsultationTable.selectAll()
            .where { ReservationConsultationTable.reservationId eq reservationId }
            .orderBy(ReservationConsultationTable.createdAt)
            .map { it[ReservationConsultationTable.status] }
    }

    /** 선점 기간이 이미 지난 것처럼 만든다. */
    private fun backdateHold(reservationId: UUID) = transaction(db) {
        ReservationTable.update({ ReservationTable.id eq reservationId }) {
            it[holdExpiresAt] = OffsetDateTime.now().minusMinutes(1)
        }
    }

    /** 같은 순간에 출발시킨 작업들의 결과(성공 값 또는 예외)를 모은다. */
    private fun <T> race(tasks: List<() -> T>): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(tasks.size)
        val ready = CountDownLatch(tasks.size)
        val start = CountDownLatch(1)
        try {
            val futures = tasks.map { task ->
                pool.submit(Callable {
                    ready.countDown()
                    start.await()
                    runCatching(task)
                })
            }
            ready.await(10, TimeUnit.SECONDS)
            start.countDown()
            return futures.map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun List<Result<*>>.failureCodes(): List<String> =
        mapNotNull { it.exceptionOrNull() }.map { (it as BusinessException).result.code }

    @Test
    fun `같은 정류소에 30명이 동시에 예약하면 1명만 선점한다`() {
        val stop = stop()

        val results = race(List(30) { { reserve(UUID.randomUUID(), stop) } })

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.failureCodes()).hasSize(29).containsOnly(ReservationResult.STOP_ALREADY_HELD.code)
    }

    @Test
    fun `선점 중인 정류소는 다른 회원도 본인도 다시 예약할 수 없다`() {
        val stop = stop()
        val holder = UUID.randomUUID()
        reserve(holder, stop)

        val other = runCatching { reserve(UUID.randomUUID(), stop) }
        val self = runCatching { reserve(holder, stop) }

        assertThat((other.exceptionOrNull() as BusinessException).result).isEqualTo(ReservationResult.STOP_ALREADY_HELD)
        assertThat((self.exceptionOrNull() as BusinessException).result).isEqualTo(ReservationResult.RESERVATION_ALREADY_EXISTS)
    }

    @Test
    fun `취소하면 선점이 풀려 다른 회원이 예약할 수 있다`() {
        val stop = stop()
        val holder = UUID.randomUUID()
        val first = reserve(holder, stop)

        transaction(db) { reservationService.cancelReservation(holder, first) }
        val second = reserve(UUID.randomUUID(), stop)

        assertThat(statusOf(first)).isEqualTo(ReservationStatus.CANCELLED)
        assertThat(statusOf(second)).isEqualTo(ReservationStatus.PENDING)
    }

    @Test
    fun `만료된 선점에 10명이 동시에 몰리면 기존 예약은 EXPIRED 가 되고 1명만 넘겨받는다`() {
        val stop = stop()
        val expired = reserve(UUID.randomUUID(), stop)
        backdateHold(expired)

        val results = race(List(10) { { reserve(UUID.randomUUID(), stop) } })

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.failureCodes()).hasSize(9).containsOnly(ReservationResult.STOP_ALREADY_HELD.code)
        assertThat(statusOf(expired)).isEqualTo(ReservationStatus.EXPIRED)
        // 만료 이력은 한 번만 남아야 한다
        assertThat(historyOf(expired)).containsExactly(ReservationStatus.PENDING, ReservationStatus.EXPIRED)
    }

    @Test
    fun `스케줄러 만료 처리는 기간이 지난 선점만 EXPIRED 로 바꾸고 동시에 돌아도 한 번만 처리한다`() {
        val overdueStop = stop()
        val liveStop = stop()
        val overdue = reserve(UUID.randomUUID(), overdueStop)
        val live = reserve(UUID.randomUUID(), liveStop)
        backdateHold(overdue)

        val results = race(List(4) { { transaction(db) { reservationService.expireOverdueHolds() } } })

        val released = results.flatMap { it.getOrThrow() }
        assertThat(released.count { it.stopId == overdueStop.id }).isEqualTo(1)
        assertThat(released.none { it.stopId == liveStop.id }).isTrue()
        assertThat(statusOf(overdue)).isEqualTo(ReservationStatus.EXPIRED)
        assertThat(statusOf(live)).isEqualTo(ReservationStatus.PENDING)
        assertThat(historyOf(overdue)).containsExactly(ReservationStatus.PENDING, ReservationStatus.EXPIRED)
    }

    @Test
    fun `회원 취소와 관리자 상담 시작이 동시에 오면 하나만 반영된다`() {
        repeat(10) {
            val stop = stop()
            val member = UUID.randomUUID()
            val reservationId = reserve(member, stop)

            val results = race(
                listOf(
                    { transaction(db) { reservationService.cancelReservation(member, reservationId) }; Unit },
                    {
                        transaction(db) {
                            reservationService.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, "상담 시작")
                        }
                        Unit
                    },
                ),
            )

            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            // 이력은 초기 PENDING + 이긴 전이 1건
            assertThat(historyOf(reservationId)).hasSize(2)
            assertThat(historyOf(reservationId).last()).isEqualTo(statusOf(reservationId))
        }
    }

    @Test
    fun `상담을 시작하면 선점 기간이 그 시점부터 다시 계산된다`() {
        val stop = stop()
        val reservationId = reserve(UUID.randomUUID(), stop)
        backdateHold(reservationId)

        val before = OffsetDateTime.now()
        transaction(db) { reservationService.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, null) }

        val holdExpiresAt = transaction(db) { ReservationEntity[reservationId].holdExpiresAt }
        assertThat(holdExpiresAt).isAfterOrEqualTo(before.plus(holdDuration).minusSeconds(1))
    }

    @Test
    fun `같은 정류소에 기간이 겹치는 계약 10건이 동시에 오면 1건만 저장된다`() {
        val stop = stop()

        val results = race(
            List(10) {
                {
                    transaction(db) {
                        contractService.createContract(
                            stop, contractRequest(stop, "2027-01-01T00:00:00+09:00", "2027-12-31T23:59:59+09:00"),
                        ).id.value
                    }
                }
            },
        )

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.failureCodes()).hasSize(9).containsOnly(ContractResult.CONTRACT_PERIOD_OVERLAP.code)
        val saved = transaction(db) { ContractTable.selectAll().where { ContractTable.stopId eq stop.id }.count() }
        assertThat(saved).isEqualTo(1)
    }

    @Test
    fun `앞 계약이 끝나는 시각에 시작하는 계약은 겹치지 않는다`() {
        val stop = stop()
        transaction(db) {
            contractService.createContract(stop, contractRequest(stop, "2027-01-01T00:00:00+09:00", "2028-01-01T00:00:00+09:00"))
        }

        val next = runCatching {
            transaction(db) {
                contractService.createContract(stop, contractRequest(stop, "2028-01-01T00:00:00+09:00", "2029-01-01T00:00:00+09:00"))
            }
        }

        assertThat(next.isSuccess).isTrue()
    }

    @Test
    fun `희망 시작일에 이미 계약 중인 정류소는 예약할 수 없다`() {
        val stop = stop()
        val today = OffsetDateTime.now()
        transaction(db) {
            contractService.createContract(
                stop,
                contractRequest(stop, today.minusDays(1).toString(), today.plusDays(365).toString()),
            )
        }

        val result = runCatching { reserve(UUID.randomUUID(), stop) }

        assertThat((result.exceptionOrNull() as BusinessException).result).isEqualTo(ReservationResult.STOP_UNDER_CONTRACT)
    }

    @Test
    fun `완료 전환에서 계약 기간이 겹치면 예약 상태 변경까지 함께 롤백된다`() {
        val stop = stop()
        // 2028년에 이미 다른 계약이 있다. 예약의 희망 시작일(30일 뒤)은 그 기간 밖이라 예약은 가능하다.
        transaction(db) {
            contractService.createContract(stop, contractRequest(stop, "2028-01-01T00:00:00+09:00", "2029-01-01T00:00:00+09:00"))
        }
        val reservationId = reserve(UUID.randomUUID(), stop)
        transaction(db) { reservationService.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, null) }

        val overlapping = CompleteToContractRequest(
            contractName = "겹치는 계약",
            totalAmount = BigDecimal("1000000.00"),
            payAmount = null,
            paymentCycle = PaymentCycle.MONTHLY,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            contractStartDate = OffsetDateTime.parse("2028-06-01T00:00:00+09:00"),
            contractEndDate = OffsetDateTime.parse("2028-12-01T00:00:00+09:00"),
            note = "완료 처리",
        )
        val result = runCatching {
            transaction(db) { reservationService.completeToContract(reservationId, stop, overlapping) }
        }

        assertThat((result.exceptionOrNull() as BusinessException).result).isEqualTo(ContractResult.CONTRACT_PERIOD_OVERLAP)
        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.CONSULTING)
        assertThat(historyOf(reservationId)).containsExactly(ReservationStatus.PENDING, ReservationStatus.CONSULTING)
    }

    @Test
    fun `완료 전환이 성공하면 예약은 COMPLETED 가 되고 계약이 생긴다`() {
        val stop = stop()
        val member = UUID.randomUUID()
        val reservationId = reserve(member, stop)
        transaction(db) { reservationService.updateReservationStatus(reservationId, ReservationStatus.CONSULTING, null) }

        val request = CompleteToContractRequest(
            contractName = "정상 계약",
            totalAmount = BigDecimal("1000000.00"),
            payAmount = null,
            paymentCycle = PaymentCycle.MONTHLY,
            paymentMethod = PaymentMethod.BANK_TRANSFER,
            contractStartDate = OffsetDateTime.parse("2027-03-01T00:00:00+09:00"),
            contractEndDate = OffsetDateTime.parse("2028-03-01T00:00:00+09:00"),
            note = null,
        )
        transaction(db) { reservationService.completeToContract(reservationId, stop, request) }

        assertThat(statusOf(reservationId)).isEqualTo(ReservationStatus.COMPLETED)
        val contractMember = transaction(db) {
            ContractTable.selectAll().where { ContractTable.stopId eq stop.id }.single()[ContractTable.memberId]
        }
        assertThat(contractMember).isEqualTo(member)
    }
}

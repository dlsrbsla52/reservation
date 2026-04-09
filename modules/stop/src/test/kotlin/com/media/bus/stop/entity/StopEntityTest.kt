package com.media.bus.stop.entity

import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.*

class StopEntityTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDb() {
            // H2 PostgreSQL 호환 모드 — timestampWithTimeZone 지원
            Database.connect(
                url = "jdbc:h2:mem:stopentitytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
            transaction {
                exec("CREATE SCHEMA IF NOT EXISTS stop")
                SchemaUtils.create(StopTable, StopUpdateHistoryTable)
            }
        }
    }

    @Test
    fun `createFromRequest_USER_소스와_등록자_UUID가_설정된다`() {
        val memberId = UUID.randomUUID()
        val stopId = UUID.randomUUID().toString().take(8)
        val request = SimpleStopCreateRequest(stopId, "테스트정류소", "127.0", "37.0", "NODE001", StopType.CENTER_LANE)

        transaction {
            val stop = StopEntity.createFromRequest(request, memberId)

            assertThat(stop.registeredById).isEqualTo(memberId)
            assertThat(stop.registeredBySource).isEqualTo(ChangeSource.USER)
            assertThat(stop.stopId).isEqualTo(stopId)
            assertThat(stop.stopName).isEqualTo("테스트정류소")
            assertThat(stop.stopsType).isEqualTo(StopType.CENTER_LANE)
        }
    }

    @Test
    fun `createFromPublicApi_SYSTEM_소스와_등록자_null이_설정된다`() {
        val stopId = UUID.randomUUID().toString().take(8)
        val row = SeoulBusStopRow(stopId, "공공API정류소", "126.9", "37.5", "NODE002", "중앙차로")

        transaction {
            val stop = StopEntity.createFromPublicApi(row)

            assertThat(stop.registeredById).isNull()
            assertThat(stop.registeredBySource).isEqualTo(ChangeSource.SYSTEM)
            assertThat(stop.stopId).isEqualTo(stopId)
        }
    }

    @Test
    fun `applyUpdate_변경_있으면_history_반환하고_필드_갱신`() {
        val stopId = UUID.randomUUID().toString().take(8)
        val row = SeoulBusStopRow(stopId, "구정류소", "127.0", "37.0", "NODE001", "중앙차로")

        transaction {
            val stop = StopEntity.createFromPublicApi(row)
            val updateRow = SeoulBusStopRow(stopId, "신정류소", "127.1", "37.1", "NODE002", "일반차로")

            val history = stop.applyUpdate(updateRow, ChangeSource.SYSTEM)

            assertThat(history).isNotNull()
            assertThat(stop.stopName).isEqualTo("신정류소")
            assertThat(stop.stopsType).isEqualTo(StopType.GENERAL_LANE)
        }
    }

    @Test
    fun `applyUpdate_변경_없으면_null_반환`() {
        val stopId = UUID.randomUUID().toString().take(8)
        val row = SeoulBusStopRow(stopId, "정류소", "127.0", "37.0", "NODE001", "중앙차로")

        transaction {
            val stop = StopEntity.createFromPublicApi(row)
            val sameRow = SeoulBusStopRow(stopId, "정류소", "127.0", "37.0", "NODE001", "중앙차로")

            val history = stop.applyUpdate(sameRow, ChangeSource.SYSTEM)

            assertThat(history).isNull()
        }
    }
}

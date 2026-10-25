package com.media.bus.stop.entity

import com.media.bus.common.exceptions.BusinessException
import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.support.Coordinate
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class StopUpdateHistoryEntityTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDb() {
            Database.connect(
                url = "jdbc:h2:mem:stophistorytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
            transaction {
                exec("CREATE SCHEMA IF NOT EXISTS stop")
                SchemaUtils.create(StopTable, StopUpdateHistoryTable)
            }
        }

        private fun createStop(stopId: String, name: String, xCrd: String, yCrd: String, nodeId: String, type: String): StopEntity =
            StopEntity.createFromPublicApi(SeoulBusStopRow(stopId, name, xCrd, yCrd, nodeId, type))
    }

    @Test
    fun `create_변경_전후_값이_올바르게_기록된다`() {
        val stopId = UUID.randomUUID().toString().take(8)

        transaction {
            val stop = createStop(stopId, "구정류소", "127.0", "37.0", "NODE001", "중앙차로")
            val updateRow = SeoulBusStopRow(stopId, "신정류소", "127.1", "37.1", "NODE002", "일반차로")

            val history = StopUpdateHistoryEntity.create(
                stop = stop,
                newRow = updateRow,
                newXCrd = Coordinate.parseLongitude(updateRow.xCrd),
                newYCrd = Coordinate.parseLatitude(updateRow.yCrd),
                newType = StopType.GENERAL_LANE,
                changeSource = ChangeSource.USER,
            )

            assertThat(history.oldStopName).isEqualTo("구정류소")
            assertThat(history.newStopName).isEqualTo("신정류소")
            assertThat(history.oldStopsType).isEqualTo(StopType.CENTER_LANE)
            assertThat(history.newStopsType).isEqualTo(StopType.GENERAL_LANE)
            assertThat(history.changeSource).isEqualTo(ChangeSource.USER)
            // 좌표는 scale이 7로 정규화되므로 값 비교는 compareTo 기반으로 한다
            assertThat(history.oldXCrd).isEqualByComparingTo(BigDecimal("127.0"))
            assertThat(history.newXCrd).isEqualByComparingTo(BigDecimal("127.1"))
            assertThat(history.oldYCrd).isEqualByComparingTo(BigDecimal("37.0"))
            assertThat(history.newYCrd).isEqualByComparingTo(BigDecimal("37.1"))
        }
    }

    @Test
    fun `applyUpdate_변경_있으면_history_반환하고_필드_갱신`() {
        val stopId = UUID.randomUUID().toString().take(8)

        transaction {
            val stop = createStop(stopId, "구정류소", "127.0", "37.0", "NODE001", "중앙차로")
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

        transaction {
            val stop = createStop(stopId, "정류소", "127.0", "37.0", "NODE001", "중앙차로")
            val sameRow = SeoulBusStopRow(stopId, "정류소", "127.0", "37.0", "NODE001", "중앙차로")

            val history = stop.applyUpdate(sameRow, ChangeSource.SYSTEM)

            assertThat(history).isNull()
        }
    }

    /**
     * 좌표 표기 형식만 다르고 값이 같은 경우를 변경으로 오탐지하면
     * 공공 API 재동기화마다 전체 정류소에 대해 불필요한 UPDATE와 이력이 쌓인다.
     */
    @Test
    fun `applyUpdate_좌표_표기만_다르고_값이_같으면_null_반환`() {
        val stopId = UUID.randomUUID().toString().take(8)

        transaction {
            val stop = createStop(stopId, "정류소", "127.0", "37.0", "NODE001", "중앙차로")
            val sameValueRow = SeoulBusStopRow(stopId, "정류소", "127.00000000", "037.0", "NODE001", "중앙차로")

            val history = stop.applyUpdate(sameValueRow, ChangeSource.SYSTEM)

            assertThat(history).isNull()
        }
    }

    @Test
    fun `createFromPublicApi_좌표가_범위를_벗어나면_예외`() {
        val stopId = UUID.randomUUID().toString().take(8)

        transaction {
            // 위도는 -90 ~ 90 범위를 벗어날 수 없다
            assertThatThrownBy { createStop(stopId, "정류소", "127.0", "91.0", "NODE001", "중앙차로") }
                .isInstanceOf(BusinessException::class.java)
        }
    }

    @Test
    fun `createFromPublicApi_좌표가_숫자가_아니면_예외`() {
        val stopId = UUID.randomUUID().toString().take(8)

        transaction {
            assertThatThrownBy { createStop(stopId, "정류소", "N/A", "37.0", "NODE001", "중앙차로") }
                .isInstanceOf(BusinessException::class.java)
        }
    }
}

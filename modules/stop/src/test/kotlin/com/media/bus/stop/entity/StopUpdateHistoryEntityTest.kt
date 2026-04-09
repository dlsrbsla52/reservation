package com.media.bus.stop.entity

import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.enums.ChangeSource
import com.media.bus.stop.entity.enums.StopType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
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
                newType = StopType.GENERAL_LANE,
                changeSource = ChangeSource.USER,
            )

            assertThat(history.oldStopName).isEqualTo("구정류소")
            assertThat(history.newStopName).isEqualTo("신정류소")
            assertThat(history.oldStopsType).isEqualTo(StopType.CENTER_LANE)
            assertThat(history.newStopsType).isEqualTo(StopType.GENERAL_LANE)
            assertThat(history.changeSource).isEqualTo(ChangeSource.USER)
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
}

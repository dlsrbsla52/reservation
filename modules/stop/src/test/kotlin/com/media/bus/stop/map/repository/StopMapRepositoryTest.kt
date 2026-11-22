package com.media.bus.stop.map.repository

import com.media.bus.stop.dto.external.SeoulBusStopRow
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.StopUpdateHistoryTable
import com.media.bus.stop.entity.enums.StopType
import com.media.bus.stop.map.dto.StopBoundsCriteria
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

/** `StopMapRepository` 의 bbox 조회를 H2(PostgreSQL 모드)로 검증한다. */
class StopMapRepositoryTest {

    private val stopMapRepository = StopMapRepository()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDb() {
            Database.connect(
                url = "jdbc:h2:mem:stopmaptest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
            transaction {
                exec("CREATE SCHEMA IF NOT EXISTS stop")
                SchemaUtils.create(StopTable, StopUpdateHistoryTable)
            }
        }
    }

    @BeforeEach
    fun clear() {
        transaction { StopTable.deleteAll() }
    }

    @Test
    fun `영역_안의_정류소만_반환한다`() {
        transaction {
            createStop("서울시청", "126.9780", "37.5665")
            createStop("남쪽바깥", "126.9780", "37.1000")
            createStop("동쪽바깥", "128.0000", "37.5665")

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", null),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].stopName).isEqualTo("서울시청")
        }
    }

    @Test
    fun `경계값은_포함한다`() {
        transaction {
            createStop("남서꼭짓점", "126.8", "37.4")
            createStop("북동꼭짓점", "127.2", "37.7")

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.4", "126.8", "37.7", "127.2", null),
            )

            assertThat(result).hasSize(2)
        }
    }

    @Test
    fun `자릿수가_다른_좌표도_숫자로_비교한다`() {
        transaction {
            createStop("한자리경도", "9.9", "37.5")
            createStop("두자리경도", "10.1", "37.5")

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "9.0", "38.0", "10.0", null),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].stopName).isEqualTo("한자리경도")
        }
    }

    @Test
    fun `상한_판정을_위해_limit_보다_한_건_더_읽는다`() {
        transaction {
            repeat(5) { createStop("정류소$it", "127.0", "37.5") }

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "126.0", "38.0", "128.0", 3),
            )

            assertThat(result).hasSize(4)
        }
    }

    @Test
    fun `좌표는_lat_lng_필드로_뒤바뀌지_않고_매핑된다`() {
        transaction {
            createStop("중계역", "127.0627647", "37.6472686")

            val marker = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "126.0", "38.0", "128.0", null),
            ).single()

            assertThat(marker.lat.toPlainString()).startsWith("37.6472686")
            assertThat(marker.lng.toPlainString()).startsWith("127.0627647")
        }
    }

    @Test
    fun `유형_필터가_있으면_해당_유형만_반환한다`() {
        transaction {
            createStop("마을버스정류장", "127.0", "37.5", "마을버스")
            createStop("중앙차로정류장", "127.0", "37.5", "중앙차로")

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "126.0", "38.0", "128.0", null, "VILLAGE_BUS"),
            )

            assertThat(result).hasSize(1)
            assertThat(result[0].stopName).isEqualTo("마을버스정류장")
        }
    }

    @Test
    fun `유형_필터가_없으면_모든_유형을_반환한다`() {
        transaction {
            createStop("마을버스정류장", "127.0", "37.5", "마을버스")
            createStop("중앙차로정류장", "127.0", "37.5", "중앙차로")

            val result = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "126.0", "38.0", "128.0", null),
            )

            assertThat(result).hasSize(2)
        }
    }

    /** 마커에 유형이 실려야 프론트가 유형별로 마커를 구분·필터링할 수 있다. */
    @Test
    fun `마커에_정류소_유형이_실린다`() {
        transaction {
            createStop("중계역", "127.0627647", "37.6472686", "가로변시간")

            val marker = stopMapRepository.findMarkersInBounds(
                StopBoundsCriteria.of("37.0", "126.0", "38.0", "128.0", null),
            ).single()

            assertThat(marker.stopsType).isEqualTo(StopType.ROADSIDE_TIMED)
        }
    }

    private fun createStop(
        name: String,
        xCrd: String,
        yCrd: String,
        stopsType: String = "중앙차로",
    ): StopEntity {
        val stopId = UUID.randomUUID().toString().take(8)
        return StopEntity.createFromPublicApi(
            SeoulBusStopRow(stopId, name, xCrd, yCrd, "NODE-$stopId", stopsType),
        )
    }
}

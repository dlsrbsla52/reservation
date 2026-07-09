package com.media.bus.stop.entity

import com.media.bus.stop.entity.enums.StopPriceChangeType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.*

class StopPriceEntityTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setUpDb() {
            Database.connect(
                url = "jdbc:h2:mem:stoppriceentitytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                driver = "org.h2.Driver",
            )
            transaction {
                exec("CREATE SCHEMA IF NOT EXISTS stop")
                SchemaUtils.create(StopPriceTable, StopPriceHistoryTable)
            }
        }
    }

    @Test
    fun `create_등록자와_단가가_설정된다`() {
        val stopId = UUID.randomUUID()
        val registeredById = UUID.randomUUID()

        transaction {
            val price = StopPriceEntity.create(stopId, BigDecimal("1200000.00"), registeredById)

            assertThat(price.stopId).isEqualTo(stopId)
            assertThat(price.unitPrice).isEqualByComparingTo(BigDecimal("1200000.00"))
            assertThat(price.registeredById).isEqualTo(registeredById)
        }
    }

    @Test
    fun `updatePrice_단가가_변경된다`() {
        val stopId = UUID.randomUUID()

        transaction {
            val price = StopPriceEntity.create(stopId, BigDecimal("1000000.00"), null)

            price.updatePrice(BigDecimal("1500000.00"))

            assertThat(price.unitPrice).isEqualByComparingTo(BigDecimal("1500000.00"))
        }
    }

    @Test
    fun `StopPriceHistoryEntity_create_변경_전후_단가가_기록된다`() {
        val stopId = UUID.randomUUID()
        val changedById = UUID.randomUUID()

        transaction {
            val history = StopPriceHistoryEntity.create(
                stopId, BigDecimal("1000000.00"), BigDecimal("1500000.00"), StopPriceChangeType.UPDATE, changedById,
            )

            assertThat(history.stopId).isEqualTo(stopId)
            assertThat(history.previousPrice).isEqualByComparingTo(BigDecimal("1000000.00"))
            assertThat(history.newPrice).isEqualByComparingTo(BigDecimal("1500000.00"))
            assertThat(history.changeType).isEqualTo(StopPriceChangeType.UPDATE)
            assertThat(history.changedById).isEqualTo(changedById)
        }
    }
}

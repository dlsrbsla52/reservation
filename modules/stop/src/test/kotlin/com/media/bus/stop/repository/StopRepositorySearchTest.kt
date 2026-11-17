package com.media.bus.stop.repository

import com.media.bus.stop.dto.request.SimpleStopCreateRequest
import com.media.bus.stop.entity.StopEntity
import com.media.bus.stop.entity.StopTable
import com.media.bus.stop.entity.enums.StopType
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StopRepositorySearchTest {
    private val repository = StopRepository()
    private lateinit var database: Database

    @BeforeAll
    fun setup() {
        database = Database.connect("jdbc:h2:mem:stop-search;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction(database) {
            exec("CREATE SCHEMA IF NOT EXISTS stop")
            SchemaUtils.create(StopTable)
        }
    }

    @BeforeEach
    fun clear() { transaction(database) { StopTable.deleteAll() } }

    @Test
    fun `와일드카드와 역슬래시는 접두 및 부분 검색에서 문자 그대로 조회한다`() {
        transaction(database) {
            listOf("%역", "_역", "\\역", "일반역").forEachIndexed { i, name -> create("id$i", name) }
            listOf("%", "_", "\\").forEach { keyword ->
                assertThat(repository.findByStopNameStartingWith(keyword).map { it.stopName }).containsExactly("${keyword}역")
                assertThat(repository.findListPaged(keyword, null, 0, 20).map { it.stopName }).containsExactly("${keyword}역")
                assertThat(repository.countList(keyword, null)).isEqualTo(1)
            }
        }
    }

    @Test
    fun `접두 검색은 초과 판정용 101건까지만 읽는다`() {
        transaction(database) {
            repeat(110) { create("id$it", "역$it") }
            assertThat(repository.findByStopNameStartingWith("역")).hasSize(101)
        }
    }

    private fun create(id: String, name: String) = StopEntity.createFromRequest(
        SimpleStopCreateRequest(id, name, "127.0", "37.5", "node$id", StopType.CENTER_LANE), UUID.randomUUID(),
    )
}

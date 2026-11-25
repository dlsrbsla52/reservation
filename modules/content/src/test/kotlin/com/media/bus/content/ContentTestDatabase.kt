package com.media.bus.content

import com.media.bus.content.entity.FaqTable
import com.media.bus.content.entity.InquiryTable
import com.media.bus.content.entity.NoticeImageTable
import com.media.bus.content.entity.NoticeTable
import com.media.bus.content.entity.PortfolioTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/** content 스키마를 만든 H2 메모리 DB */
object ContentTestDatabase {
    private val tables = arrayOf(PortfolioTable, NoticeTable, NoticeImageTable, FaqTable, InquiryTable)

    fun connect(name: String): Database {
        val database = Database.connect("jdbc:h2:mem:$name;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction(database) {
            exec("CREATE SCHEMA IF NOT EXISTS content")
            SchemaUtils.create(*tables)
        }
        return database
    }

    fun clear(database: Database) = transaction(database) { tables.reversed().forEach { it.deleteAll() } }
}

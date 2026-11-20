package com.media.bus.iam.audit.entity

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.core.vendors.currentDialect
import org.postgresql.util.PGobject

/** PostgreSQL `jsonb` 컬럼용 String 컬럼 타입 */
class JsonbStringColumnType : ColumnType<String>() {

    override fun sqlType(): String = when (currentDialect) {
        is H2Dialect -> "CLOB"
        else -> "JSONB"
    }

    /** PostgreSQL JDBC 는 `jsonb` 컬럼을 [PGobject] 로 반환한다. */
    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    /** PostgreSQL 에서는 [PGobject] 로 감싸 jsonb 로 명시 바인딩한다. */
    override fun notNullValueToDB(value: String): Any = when (currentDialect) {
        is H2Dialect -> value
        else -> PGobject().apply {
            type = "jsonb"
            this.value = value
        }
    }
}

/** PostgreSQL `jsonb` (또는 H2 `clob`) 컬럼에 매핑되는 [String] 컬럼을 등록한다. */
fun Table.jsonbString(name: String): Column<String> =
    registerColumn(name, JsonbStringColumnType())

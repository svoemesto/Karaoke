package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types

/**
 * Регрессия BR-176 (аудит #336, PKG-S2-11): `NULL` биндится как SQL NULL, а не
 * как строка `"null"`.
 *
 * До правки `RecordDiff` с `recordDiffValueNew == null` уходил в
 * `else -> ps.setString(index, null.toString())`, то есть в SQL писалась строка
 * `'null'`. Для `timestamp`/`boolean`-колонки PostgreSQL падал, исключение
 * гасилось — и терялся весь пакет правок одного `save()`.
 */
class KaraokeDbTableNullBindingTest {
    @Test
    fun `null timestamp binds typed NULL and never the string null`() {
        val ps = mock(PreparedStatement::class.java)
        bindRecordDiffValue(ps, 1, RecordDiff("publish_at", null, Timestamp(1_700_000_000_000L)))

        verify(ps).setNull(1, Types.TIMESTAMP)
        verify(ps, never()).setString(anyInt(), anyString())
    }

    @Test
    fun `null string binds VARCHAR NULL`() {
        val ps = mock(PreparedStatement::class.java)
        bindRecordDiffValue(ps, 2, RecordDiff("link", null, "https://example.org"))

        verify(ps).setNull(2, Types.VARCHAR)
    }

    @Test
    fun `non-null values still bind by type`() {
        val ps = mock(PreparedStatement::class.java)
        bindRecordDiffValue(ps, 3, RecordDiff("title", "new", "old"))

        verify(ps).setString(3, "new")
        assertEquals(Types.NULL, sqlTypeForNull(null))
    }
}

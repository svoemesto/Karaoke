package com.svoemesto.karaokeapp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 674: числовые свойства читаются без ClassCastException.
 *
 * Регресс: `getLong`/`getInt` кастовали значение (`it as Long`/`it as Int`), а
 * `defaultValue` у числовых свойств — `Int`. Первое же чтение Long-свойства
 * (`queue.tuple.temp_ttl_hours` в janitor'е) падало на старте воркера.
 */
class KaraokePropertiesNumericTest {
    @Test
    fun `getLong converts Int default to Long`() {
        assertEquals(24L, KaraokeProperties.getLong("queue.tuple.temp_ttl_hours"))
    }

    @Test
    fun `getInt reads Int default`() {
        assertEquals(24, KaraokeProperties.getInt("queue.tuple.temp_ttl_hours"))
    }

    @Test
    fun `getLong on unknown key is zero`() {
        assertEquals(0L, KaraokeProperties.getLong("674.no.such.property"))
    }
}

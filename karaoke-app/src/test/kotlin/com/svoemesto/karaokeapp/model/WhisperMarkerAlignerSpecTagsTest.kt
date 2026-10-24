package com.svoemesto.karaokeapp.model

import com.svoemesto.karaokeapp.services.APP_WORK_IN_CONTAINER
import com.svoemesto.karaokeapp.services.APP_WORK_ON_SERVER
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_LOCAL_POSTGRES_USER
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_PASSWORD
import com.svoemesto.karaokeapp.services.DB_SERVER_POSTGRES_USER
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Проверка совместимости "Точные маркеры" (buildMarkersFromSyllableTimes) со спецтегами — контракт
 * зафиксирован в `specs/010-lyrics-spec-tags/contracts/tag-registry.md`. Слова "да"/"но" выбраны как
 * односложные (простая, предсказуемая слоговая разбивка), чтобы фикстура syllableTimes была тривиальной.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WhisperMarkerAlignerSpecTagsTest {
    /**
     * Снимок глобальных настроек приложения, сделанный до подмены.
     *
     * Глобалы — общее состояние всего JVM: без восстановления значения
     * `false/false/"test"/"test"/"test"/"test"` оставались на всё время жизни
     * test-JVM, и любой тест, выполняемый после этого класса, получал другую
     * конфигурацию БД, чем ожидал.
     */
    private val globalsBefore = AppGlobalsSnapshot()

    // getSyllables()/haveVowel() (Utils.kt/Extentions.kt) транзитивно трогают ConstantsKt.<clinit>
    // (WORKING_DATABASE = Connection.local()), которому нужны эти globals, иначе
    // IllegalStateException при первом обращении к любой функции из Utils.kt в тесте без полного
    // Spring-контекста. Connection(...) - stateless wrapper (url/username/password как строки), не
    // открывает реальное соединение при конструировании - тестовые значения безопасны.
    @BeforeAll
    fun setUpGlobals() {
        globalsBefore.capture()
        globalsBefore.applyTestValues()
    }

    @AfterAll
    fun tearDownGlobals() {
        globalsBefore.restore()
    }

    /**
     * Контракт подмены: значения глобальных настроек — временные, они обязаны
     * возвращаться к прежним.
     *
     * Без этой проверки «восстановление» было бы декларацией в комментарии:
     * снять его из [AppGlobalsSnapshot.restore] — и тест остался бы зелёным.
     */
    @Test
    fun `глобальные настройки приложения восстанавливаются после подмены`() {
        val snapshot = AppGlobalsSnapshot()
        snapshot.capture()
        val before = readIfInitialized { DB_LOCAL_POSTGRES_USER }
        snapshot.applyTestValues()
        assertEquals("test", DB_LOCAL_POSTGRES_USER, "тестовое значение должно применяться")
        // Подменяем значение на «чужое» — восстановление обязано вернуть снимок.
        // (Без этой строки проверка была бы тавтологией: applyTestValues кладёт
        // ровно то же значение, что restore() и так вернул бы.)
        DB_LOCAL_POSTGRES_USER = "probe-value"
        snapshot.restore()
        assertEquals(
            before,
            readIfInitialized { DB_LOCAL_POSTGRES_USER },
            "после подмены значение глобальной настройки обязано быть восстановлено",
        )
    }

    // (0.0-0.5) - слог "да_"; большой зазор тишины (0.5..2.0, 1.5с >= NEWLINE_LEAD_IN_SECONDS=1.0)
    // перед (2.0-2.5) - слог "но_", чтобы время gap-маркера было детерминированным (nextLineStartTime - 1.0 = 1.0).
    private val syllableTimes = listOf(0.0 to 0.5, 2.0 to 2.5)

    @Test
    fun `обычная пустая строка без тегов даёт единственный NEWLINE-маркер (регрессия, FR-004)`() {
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n\nно", syllableTimes)
        assertNotNull(markers)
        assertEquals(5, markers!!.size)
        assertEquals(Markertype.NEWLINE.value, markers[2].markertype)
        assertEquals(1.0, markers[2].time)
    }

    @Test
    fun `две подряд идущие пустые строки без тегов всё равно дают только один NEWLINE (не N)`() {
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n\n\nно", syllableTimes)
        assertNotNull(markers)
        assertEquals(5, markers!!.size)
        assertEquals(Markertype.NEWLINE.value, markers[2].markertype)
    }

    @Test
    fun `~newline~ на отдельной строке даёт идентичный результат обычной пустой строке`() {
        val plain = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n\nно", syllableTimes)
        val tagged = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~newline~\nно", syllableTimes)
        assertEquals(plain, tagged)
    }

    @Test
    fun `~group N~ даёт SETTING-маркер с label GROUP N вместо NEWLINE`() {
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~group:1~\nно", syllableTimes)
        assertNotNull(markers)
        assertEquals(5, markers!!.size)
        assertEquals(Markertype.SETTING.value, markers[2].markertype)
        assertEquals("GROUP|1", markers[2].label)
        assertEquals(1.0, markers[2].time)
    }

    @Test
    fun `~comment текст~ даёт SETTING-маркер с label COMMENT текст`() {
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~comment:привет~\nно", syllableTimes)
        assertNotNull(markers)
        assertEquals(Markertype.SETTING.value, markers!![2].markertype)
        assertEquals("COMMENT|привет", markers[2].label)
    }

    @Test
    fun `алиас Куплет даёт идентичный маркер тегу group0`() {
        val viaGroup = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~group:0~\nно", syllableTimes)
        val viaAlias = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~Куплет~\nно", syllableTimes)
        assertEquals(viaGroup, viaAlias)
    }

    @Test
    fun `нераспознанный тег (невалидное значение) откатывается на дефолтный NEWLINE`() {
        val plain = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n\nно", syllableTimes)
        val invalidTag = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\n~group:abc~\nно", syllableTimes)
        assertEquals(plain, invalidTag)
    }

    @Test
    fun `текст без единого спецтега не меняет поведение вовсе (общая регрессия)`() {
        val markers = WhisperMarkerAligner.buildMarkersFromSyllableTimes("да\nно", listOf(0.0 to 0.5, 0.6 to 1.0))
        assertNotNull(markers)
        // Соседние строки без разрыва (lineIndex-гэп=1) - маркера NEWLINE между ними не должно быть вовсе.
        assertEquals(4, markers!!.size)
        assertEquals(
            listOf(Markertype.SYLLABLES.value, Markertype.ENDOFLINE.value, Markertype.SYLLABLES.value, Markertype.ENDOFLINE.value),
            markers.map { it.markertype },
        )
    }
}

/**
 * Снимок и восстановление глобальных настроек приложения
 * (`services/KaraokeAppService.kt`).
 *
 * Часть настроек объявлена как `lateinit`, часть — через `Delegates.notNull`:
 * прочитать их до первой инициализации нельзя (исключение), поэтому «снимок»
 * может быть пустым ([readIfInitialized] вернёт `null`). В таком случае
 * восстановить прежнее состояние невозможно (у `lateinit` нет операции
 * «расинициализировать») — и это не хуже прежнего поведения, когда значения
 * оставались подменёнными навсегда.
 */
private class AppGlobalsSnapshot {
    private var workInContainer: Boolean? = null
    private var workOnServer: Boolean? = null
    private var localUser: String? = null
    private var localPassword: String? = null
    private var serverUser: String? = null
    private var serverPassword: String? = null

    fun capture() {
        workInContainer = readIfInitialized { APP_WORK_IN_CONTAINER }
        workOnServer = readIfInitialized { APP_WORK_ON_SERVER }
        localUser = readIfInitialized { DB_LOCAL_POSTGRES_USER }
        localPassword = readIfInitialized { DB_LOCAL_POSTGRES_PASSWORD }
        serverUser = readIfInitialized { DB_SERVER_POSTGRES_USER }
        serverPassword = readIfInitialized { DB_SERVER_POSTGRES_PASSWORD }
    }

    fun applyTestValues() {
        APP_WORK_IN_CONTAINER = false
        APP_WORK_ON_SERVER = false
        DB_LOCAL_POSTGRES_USER = "test"
        DB_LOCAL_POSTGRES_PASSWORD = "test"
        DB_SERVER_POSTGRES_USER = "test"
        DB_SERVER_POSTGRES_PASSWORD = "test"
    }

    fun restore() {
        workInContainer?.let { APP_WORK_IN_CONTAINER = it }
        workOnServer?.let { APP_WORK_ON_SERVER = it }
        localUser?.let { DB_LOCAL_POSTGRES_USER = it }
        localPassword?.let { DB_LOCAL_POSTGRES_PASSWORD = it }
        serverUser?.let { DB_SERVER_POSTGRES_USER = it }
        serverPassword?.let { DB_SERVER_POSTGRES_PASSWORD = it }
    }
}

/** Значение глобальной настройки либо `null`, если она ещё не инициализирована. */
private fun <T> readIfInitialized(read: () -> T): T? =
    try {
        read()
    } catch (_: RuntimeException) {
        null
    }

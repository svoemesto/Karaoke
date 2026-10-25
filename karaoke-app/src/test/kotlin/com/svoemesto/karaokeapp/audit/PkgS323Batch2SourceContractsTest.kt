package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.services.StorageCircuitBreaker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-23` (аудит #336, OpenProject #395), партия 2 —
 * BR-144, BR-145, BR-146, BR-147, BR-149, BR-150, BR-151, BR-152, BR-153.
 *
 * Класс компилируется и на коде «до» правки, поэтому служит доказательством «до/после»:
 * на старом коде утверждения падают, на текущем — проходят.
 *
 * @see specs/395-pkg-s3-23/report.md
 */
class PkgS323Batch2SourceContractsTest {
    private fun repoFile(relativePath: String): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile ?: error("не найден корень репозитория (settings.gradle.kts)")
        }
        val file = File(dir, relativePath)
        assertTrue(file.isFile, "файл не найден: $relativePath")
        return file
    }

    private fun read(relativePath: String): String = repoFile(relativePath).readText()

    /**
     * Исходник БЕЗ комментариев — иначе утверждение вида «в файле нет `X`» ловило бы
     * само себя в тексте пояснения к правке.
     */
    private fun readCode(relativePath: String): String =
        read(relativePath)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")

    private val appMain = "karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp"

    // ---------------------------------------------------------------- BR-144

    @Test
    fun `BR-144 недостоверный ответ хранилища не кешируется`() {
        val cache = readCode("$appMain/services/StorageMetadataCache.kt")
        assertTrue(
            cache.contains("value == null"),
            "кеш должен отличать «хранилище не ответило» от «объекта нет»",
        )
        assertTrue(
            cache.contains("НЕ кешируем"),
            "недостоверный ответ не должен попадать в вечный кеш (BR-144)",
        )
        assertTrue(
            cache.contains("loader: () -> Boolean?"),
            "loader должен уметь вернуть null («ответ недостоверен»)",
        )

        val api = readCode("$appMain/services/StorageApiClient.kt")
        assertTrue(
            api.contains("fun fileExistsReliable("),
            "нужен метод fileExistsReliable с признаком достоверности",
        )
        val reliableBody =
            api.substringAfter("override fun fileExistsReliable(").substringBefore("\n    }")
        assertFalse(
            reliableBody.contains("?: false"),
            "fileExistsReliable не должен подменять «не ответили» на false (BR-144)",
        )
    }

    @Test
    fun `BR-144 health-report спрашивает надёжный ответ хранилища`() {
        val report = readCode("$appMain/HealthReport.kt")
        assertTrue(
            report.contains("fileExistsReliable("),
            "REMOTE-загрузчики health-report должны использовать fileExistsReliable",
        )
        assertFalse(
            report.contains("storageApiClient.fileExists("),
            "старая форма без признака достоверности не должна оставаться",
        )
    }

    // ---------------------------------------------------------------- BR-145

    @Test
    fun `BR-145 успех в CLOSED обнуляет счётчик сбоев`() {
        val c =
            StorageCircuitBreaker(
                timeoutSeconds = 5L,
                threshold = 3,
                cooldownSeconds = 30L,
                watchdogEnabled = false,
                watchdogBufferSeconds = 10L,
                checkIntervalSeconds = 1L,
            )
        // Две неудачи подряд…
        c.recordFailure(RuntimeException("boom-1"))
        c.recordFailure(RuntimeException("boom-2"))
        // …успех прерывает серию…
        c.recordSuccess()
        // …и две новые неудачи уже не должны открывать circuit (порог = 3).
        c.recordFailure(RuntimeException("boom-3"))
        c.recordFailure(RuntimeException("boom-4"))

        assertEquals(
            StorageCircuitBreaker.State.CLOSED,
            c.state(),
            "BR-145: успех должен обнулять счётчик — порог означает серию ПОДРЯД, " +
                "а не любые сбои за всё время",
        )
    }

    @Test
    fun `BR-145 серия сбоев подряд всё ещё открывает circuit`() {
        val c =
            StorageCircuitBreaker(
                timeoutSeconds = 5L,
                threshold = 3,
                cooldownSeconds = 30L,
                watchdogEnabled = false,
                watchdogBufferSeconds = 10L,
                checkIntervalSeconds = 1L,
            )
        c.recordFailure(RuntimeException("boom-1"))
        c.recordFailure(RuntimeException("boom-2"))
        c.recordFailure(RuntimeException("boom-3"))
        assertEquals(
            StorageCircuitBreaker.State.OPEN,
            c.state(),
            "BR-145: серия подряд из трёх сбоев обязана открывать circuit",
        )
    }

    // ---------------------------------------------------------------- BR-146

    @Test
    fun `BR-146 загрузчики кандидатов Telegram не подменяют сбой БД пустым списком`() {
        val source = readCode("$appMain/services/TelegramAutoPublishScheduler.kt")
        assertFalse(
            source.contains("catch (e: SQLException) {\n        }"),
            "пустой catch SQLException больше не допускается (BR-146)",
        )
        assertTrue(
            source.contains("sqlState="),
            "ошибка БД должна логироваться с SQLState (BR-146)",
        )
        assertFalse(
            source.contains("e.printStackTrace()"),
            "printStackTrace уходил в stdout мимо структурированных логов (BR-146)",
        )
        assertTrue(source.contains("infra.telegram.publish"))
    }

    // ---------------------------------------------------------------- BR-147

    @Test
    fun `BR-147 подписка SSE не разыменовывает список эмиттеров повторно`() {
        val source = readCode("$appMain/services/SseNotificationService.kt")
        assertTrue(
            source.contains("emitters.computeIfAbsent(key)"),
            "список эмиттеров берётся ОДИН раз через computeIfAbsent (BR-147)",
        )
        assertFalse(
            source.contains("emitters[key]!!"),
            "повторное разыменование давало NPE → 500 на /api/subscribe (BR-147)",
        )
    }

    // ---------------------------------------------------------------- BR-149

    @Test
    fun `BR-149 уже выполняющийся рендер не превращается в ошибку публикации`() {
        val source = readCode("$appMain/services/VkAutoPublishService.kt")
        assertTrue(
            source.contains("hasActiveRenderProcess("),
            "нужно отличать «рендер уже идёт» от «не удалось поставить задание» (BR-149)",
        )
        assertTrue(
            source.contains("if (hasActiveRenderProcess(song))"),
            "при активном рендере состояние остаётся RENDERING, ошибка не пишется (BR-149)",
        )
    }

    // ---------------------------------------------------------------- BR-150

    @Test
    fun `BR-150 флаг идемпотентности PREMIUM проверяется по свежей песне под локом`() {
        val source = readCode("$appMain/services/VkAutoPublishService.kt")
        assertTrue(
            source.contains("val premiumAlreadySent = freshSong?.newsPremiumVkSent"),
            "флаг newsPremiumVkSent должен читаться из свежей записи БД (BR-150)",
        )
        // Проверка stale-объекта вне лока — законные «быстрые выходы» (до входа в
        // synchronized). Под локом её быть не должно.
        val afterLock = source.substringAfter("synchronized(lock)")
        assertFalse(
            afterLock.contains("type == PublicationType.PREMIUM && song.newsPremiumVkSent"),
            "проверка stale-объекта под локом давала дублирующий пост (BR-150)",
        )
        assertTrue(
            afterLock.contains("premiumAlreadySent"),
            "под локом флаг должен проверяться по свежей записи БД (BR-150)",
        )
    }

    // ---------------------------------------------------------------- BR-151

    @Test
    fun `BR-151 тик VK-публикации логирует исключение и изолирует сбой одной новости`() {
        val source = readCode("$appMain/services/VkAutoPublishScheduler.kt")
        assertTrue(
            source.contains("VkAutoPublishScheduler.tick failed"),
            "исключение тика должно логироваться (BR-151)",
        )
        assertTrue(
            source.contains("runCatching"),
            "сбой одной новости не должен обрывать цикл тика (BR-151)",
        )
        assertFalse(
            source.contains("catch (e: Exception) {\n        }"),
            "пустой catch тика больше не допускается (BR-151)",
        )
    }

    // ---------------------------------------------------------------- BR-152

    @Test
    fun `BR-152 сбой БД в VK-публикации не выглядит как пустой результат`() {
        val scheduler = readCode("$appMain/services/VkAutoPublishScheduler.kt")
        assertFalse(
            scheduler.contains("catch (e: SQLException) {\n        }"),
            "сбой SELECT больше не подменяется пустым списком (BR-152)",
        )
        assertTrue(
            scheduler.contains("List<Long>?"),
            "загрузчик кандидатов должен различать «пусто» и «сбой» (BR-152)",
        )

        val service = readCode("$appMain/services/VkAutoPublishService.kt")
        assertTrue(
            service.contains("VkNewsLookupException"),
            "сбой чтения air-новости должен быть различим (BR-152)",
        )
        assertFalse(
            service.contains("catch (e: SQLException) {\n        }"),
            "пустой catch SQLException в поиске air-новости больше не допускается (BR-152)",
        )
    }

    // ---------------------------------------------------------------- BR-153

    @Test
    fun `BR-153 сбой прокси оборачивается в VkNetworkException`() {
        val source = readCode("$appMain/services/VkApiClient.kt")
        assertFalse(
            source.contains("return proxy.send(request, HttpResponse.BodyHandlers.ofString())"),
            "прямой вызов прокси вне try отдавал сырое IOException (BR-153)",
        )
        assertTrue(
            source.contains("VK недоступен ни через прокси, ни напрямую"),
            "сбой прокси должен приводить к типизированному VkNetworkException (BR-153)",
        )
    }
}

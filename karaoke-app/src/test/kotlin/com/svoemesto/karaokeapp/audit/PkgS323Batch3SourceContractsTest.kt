package com.svoemesto.karaokeapp.audit

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Регрессия пакета `PKG-S3-23` (аудит #336, OpenProject #395), партия 3 —
 * BR-155, BR-156, BR-157, BR-158, BR-159, BR-160, BR-163, BR-164, BR-165, BR-166.
 *
 * Класс компилируется и на коде «до» правки, поэтому служит доказательством «до/после»:
 * на старом коде утверждения падают, на текущем — проходят.
 *
 * @see specs/395-pkg-s3-23/report.md
 */
class PkgS323Batch3SourceContractsTest {
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

    // ---------------------------------------------------------------- BR-155

    @Test
    fun `BR-155 реестр per-song локов не растёт бесконечно`() {
        val source = readCode("$appMain/services/VkAutoPublishService.kt")
        assertFalse(
            source.contains("songLocks.computeIfAbsent(songId)"),
            "per-song локи в ConcurrentHashMap не удалялись никогда (BR-155)",
        )
        assertFalse(
            source.contains("ConcurrentHashMap<Long, Any>()"),
            "неограниченная карта локов больше не используется (BR-155)",
        )
        assertTrue(
            source.contains("SONG_LOCK_STRIPES"),
            "нужны striped-локи фиксированного размера (BR-155)",
        )
        assertTrue(
            source.contains("Math.floorMod(songId"),
            "индекс полосы должен быть неотрицательным (BR-155)",
        )
    }

    // ---------------------------------------------------------------- BR-156

    @Test
    fun `BR-156 кандидаты air-новостей отбираются одним SQL с JOIN и LIMIT`() {
        val source = readCode("$appMain/services/VkAutoPublishScheduler.kt")
        assertTrue(
            source.contains("LEFT JOIN tbl_songs s"),
            "нужен JOIN на tbl_songs, чтобы не грузить песни для уже опубликованных (BR-156)",
        )
        assertTrue(source.contains("LIMIT ?"), "выборка кандидатов должна быть ограничена (BR-156)")
        assertTrue(
            source.contains("newsCandidateLimit"),
            "предел числа кандидатов должен быть именованной константой (BR-156)",
        )
        assertTrue(
            source.contains("s.id_vk AS song_id_vk"),
            "признак «уже опубликована» должен приезжать из SQL (BR-156)",
        )
    }

    // ---------------------------------------------------------------- BR-157

    @Test
    fun `BR-157 слот rate-limit возвращается, если поста не было`() {
        val source = readCode("$appMain/services/VkAutoPublishScheduler.kt")
        assertTrue(
            source.contains("reserveRateLimitSlot()"),
            "слот должен резервироваться, а не тратиться (BR-157)",
        )
        assertTrue(
            source.contains("releaseRateLimitSlot(reservedAt)"),
            "слот должен возвращаться, если пост не состоялся (BR-157)",
        )
        assertFalse(
            source.contains("acquireRateLimitSlot()"),
            "старая схема «занял слот до попытки» больше не используется (BR-157)",
        )
    }

    // ---------------------------------------------------------------- BR-158

    @Test
    fun `BR-158 long-poll Telegram переживает сбой deleteWebhook`() {
        val source = readCode("$appMain/services/TelegramUpdatesConsumer.kt")
        assertTrue(
            source.contains("client.deleteWebhook()"),
            "вызов должен остаться (BR-158)",
        )
        val webhookIdx = source.indexOf("client.deleteWebhook()")
        val afterWebhook = source.substring(webhookIdx, webhookIdx + 400)
        assertTrue(
            afterWebhook.contains("catch (e: Exception)"),
            "deleteWebhook должен быть обёрнут в try/catch (BR-158)",
        )
        assertTrue(
            source.contains("isWork = false\n"),
            "в конце потока флаг isWork должен сбрасываться (BR-158)",
        )
        assertTrue(
            source.contains("started.compareAndSet(false, true)"),
            "start() должен быть атомарным (BR-158)",
        )
    }

    // ---------------------------------------------------------------- BR-159

    @Test
    fun `BR-159 сбой ASR различается с «сервис не настроен»`() {
        val service = readCode("$appMain/services/WhisperAsrService.kt")
        assertTrue(
            service.contains("sealed class Outcome"),
            "нужен типизированный результат ASR (BR-159)",
        )
        assertTrue(
            service.contains("asr.not_configured"),
            "«не настроено» должно быть отличимо от «ошибка» (BR-159)",
        )
        assertTrue(
            service.contains("asr.http_"),
            "HTTP-код ответа должен попадать в причину (BR-159)",
        )
        assertTrue(
            service.contains("InterruptedException"),
            "InterruptedException не должен глотаться (BR-159)",
        )
        assertFalse(
            service.contains("catch (_: Exception) {\n            null"),
            "сбой не должен сводиться к молчаливому null (BR-159)",
        )

        val controller = readCode("$appMain/controllers/SongEditorController.kt")
        assertTrue(
            controller.contains("transcribeDetailed"),
            "вызывающий должен получать причину сбоя (BR-159)",
        )
    }

    // ---------------------------------------------------------------- BR-160

    @Test
    fun `BR-160 тик обновления VK-токена чаще интервала порога`() {
        val source = readCode("$appMain/services/VkIdTokenRefreshScheduler.kt")
        assertTrue(
            source.contains("refreshThresholdMinutes = 30L"),
            "порог обновления — 30 минут (BR-160)",
        )
        assertTrue(
            source.contains("""@Scheduled(cron = "0 */10 * * * *")"""),
            "тик должен быть чаще порога — иначе токен успевает истечь (BR-160)",
        )
    }

    // ---------------------------------------------------------------- BR-163

    @Test
    fun `BR-163 поток загрузки файла закрывается гарантированно`() {
        val source = readCode("$appMain/services/KaraokeStorageService.kt")
        assertTrue(
            source.contains("file.inputStream().use { fileInputStream ->"),
            "поток загрузки должен закрываться через use { } (BR-163)",
        )
        assertFalse(
            source.contains("val fileInputStream = file.inputStream()"),
            "незакрытый FileInputStream больше не создаётся (BR-163)",
        )
    }

    // ---------------------------------------------------------------- BR-164

    @Test
    fun `BR-164 «нет файла» отличается от ошибки S3`() {
        val source = readCode("$appMain/services/KaraokeStorageService.kt")
        assertTrue(
            source.contains("NoSuchKey"),
            "только NoSuchKey/NoSuchBucket означают «объекта нет» (BR-164)",
        )
        assertFalse(
            source.contains("catch (_: ErrorResponseException) {\n                        false"),
            "любой ErrorResponseException больше не трактуется как «нет файла» (BR-164)",
        )
        assertTrue(
            source.contains("getFileStat: сбой MinIO"),
            "сбой MinIO должен быть ошибкой, а не «нет stat» (BR-164)",
        )
        assertTrue(source.contains("infra.cache.storage.local"))
    }

    // ---------------------------------------------------------------- BR-165

    @Test
    fun `BR-165 премиум-тик не превращает сбой БД в «очередь пуста»`() {
        val source = readCode("$appMain/services/PremiumAutoPublishScheduler.kt")
        assertFalse(
            source.contains("catch (e: SQLException) {\n        }"),
            "пустой catch SQLException больше не допускается (BR-165)",
        )
        assertFalse(
            source.contains("e.printStackTrace()"),
            "printStackTrace уходил в stderr мимо структурированных логов (BR-165)",
        )
        assertTrue(
            source.contains("List<Long>?"),
            "загрузчики очереди должны различать «пусто» и «сбой» (BR-165)",
        )
        assertTrue(source.contains("infra.premium.publish"))
    }

    // ---------------------------------------------------------------- BR-166

    @Test
    fun `BR-166 нетерминальный код публикации не остаётся безнаказанным`() {
        val source = readCode("$appMain/services/PremiumAutoPublishScheduler.kt")
        assertTrue(
            source.contains("handleChannelOutcome("),
            "результат публикации должен разбираться явно (BR-166)",
        )
        assertTrue(
            source.contains("\"scheduled\" -> handleFailure("),
            "SCHEDULED (контент не готов) тоже должен засчитываться как неудачная попытка (BR-166)",
        )
        assertFalse(
            source.contains("""if (tgResult.state.code == "send_failed") {"""),
            "реакция только на send_failed оставляла песню в вечной очереди (BR-166)",
        )
    }
}

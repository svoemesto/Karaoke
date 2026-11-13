package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Добор остатка для завершённого прогона уборки (KARAOKE-429, спека 739).
 *
 * **Дефект, найденный на живой приёмке 2026-11-12.** Если в момент удаления
 * песни серверная БД была недоступна (фаза B падает с `DB-CONNECT-001`), то
 * строка песни остаётся в серверной базе, а прогон уборки завершается как
 * `DONE` — уборка-то сделана. Ручной добор (`POST /api/song/cleanup/runs/{id}/
 * resume`) отказывал на завершённых прогонах с 409 `run_done`, поэтому остаток
 * нельзя было добрать вручную: оставался только штатный sync-движок, а он
 * отрабатывает раз в несколько часов.
 *
 * Тест — «страховка от возврата отказа»: завершённый прогон должен давать
 * добор строки в серверной БД, а не отказ.
 */
class SongCleanupResumeServerRetryTest {
    private val controllerSource =
        File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")

    @Test
    fun `завершённый прогон даёт добор строки в серверной БД`() {
        assertTrue(controllerSource.isFile, "исходник контроллера не найден: ${controllerSource.path}")
        val text = controllerSource.readText()
        val resume = resumeEndpointBody(text)
        assertTrue(
            resume.contains("retryServerDeletion(run.songId)"),
            "завершённый прогон должен давать добор строки песни в серверной БД",
        )
        assertFalse(
            resume.contains("\"run_done\""),
            "отказ run_done на завершённом прогоне больше не допускается: строка песни " +
                "в серверной БД — это остаток, который нужно уметь добирать",
        )
    }

    /**
     * Тело обработчика `POST /song/cleanup/runs/{runId}/resume`.
     *
     * @param text исходник контроллера.
     * @return фрагмент от объявления метода до следующей аннотации сопоставления.
     */
    private fun resumeEndpointBody(text: String): String {
        val start = text.indexOf("fun resumeSongCleanupRun")
        assertTrue(start > 0, "обработчик добора прогона не найден в контроллере")
        val rest = text.substring(start)
        val end = rest.indexOf("\n    @")
        return if (end > 0) rest.substring(0, end) else rest
    }
}

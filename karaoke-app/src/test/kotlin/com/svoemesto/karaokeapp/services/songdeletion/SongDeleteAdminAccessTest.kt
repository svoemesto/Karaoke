package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Тест доступности удаления администратору (KARAOKE-429, спека 739, FR-028).
 *
 * **FR-028:** удаление доступно любому администратору с текущими правами;
 * отдельная роль на удаление песен **не вводится**.
 *
 * Проверка статическая (по исходникам): отдельной роли не должно быть ни в
 * коде фичи, ни в конфигурации доступа.
 *
 * @see specs/739-song-delete-cascade/spec.md FR-028
 */
class SongDeleteAdminAccessTest {
    private val featureSources: List<File> =
        listOf(
            File("src/main/kotlin/com/svoemesto/karaokeapp/services/songdeletion"),
            File("src/main/kotlin/com/svoemesto/karaokeapp/model/SongDeleteResult.kt"),
            File("src/main/kotlin/com/svoemesto/karaokeapp/model/SongNameHistory.kt"),
        )

    @Test
    fun `в коде фичи нет упоминаний отдельной роли на удаление`() {
        val forbidden = listOf("ROLE_DELETE_SONG", "DELETE_SONG_ROLE", "songDeleteRole", "canDeleteSong")
        featureSources.forEach { source ->
            val files =
                if (source.isDirectory) {
                    source.listFiles()?.filter { it.extension == "kt" }.orEmpty()
                } else {
                    listOf(source)
                }
            files.forEach { file ->
                if (!file.isFile) return@forEach
                val text = file.readText()
                forbidden.forEach { marker ->
                    assertFalse(
                        text.contains(marker),
                        "отдельная роль «$marker» не вводится (FR-028): ${file.path}",
                    )
                }
            }
        }
    }

    @Test
    fun `эндпоинт удаления живёт в админском контуре karaoke-app`() {
        val apiController = File("src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt")
        assertTrue(apiController.isFile, "не найден ApiController")
        val text = apiController.readText()
        assertTrue(
            text.contains("\"/song/delete\""),
            "эндпоинт удаления песни — в админском ApiController (karaoke-app), не в публичной части",
        )
        assertFalse(
            File("src/main/kotlin/com/svoemesto/karaokeweb").exists(),
            "публичный модуль karaoke-web не должен содержать кода фичи удаления",
        )
    }
}

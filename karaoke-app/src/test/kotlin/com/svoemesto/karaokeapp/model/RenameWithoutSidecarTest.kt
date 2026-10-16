package com.svoemesto.karaokeapp.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты переименования песни без sidecar-файлов (specs/544-song-artifact-cleanup, T024).
 *
 * Покрывает FR-006 в части «нечего переименовывать»: сериализованное состояние песни
 * (`.song`/`.settings`) больше не создаётся, поэтому каскад переименования не должен ни
 * зависеть от него, ни воссоздавать его. Проверяется структурно — по составу членов класса
 * `Song`: писатели и читатель sidecar удалены вместе с путями.
 *
 * Сам каскад переименования (`Song.renameCascadeExtraArtifacts`) в юнит-тесте не
 * вызывается: он требует экземпляр `Song`, БД и оба хранилища. Его поведение без sidecar
 * покрывается приёмкой владельца (quickstart.md, сценарий 3, edge case «переименование
 * после удаления sidecar-файлов»).
 *
 * @see specs/544-song-artifact-cleanup/spec.md
 */
class RenameWithoutSidecarTest {
    private val memberNames: Set<String> =
        buildSet {
            val kotlinMemberNames = Song::class.members.map { it.name }
            kotlinMemberNames.forEach { add(it) }
            val javaClass = Song::class.java
            val javaFieldNames = javaClass.declaredFields.map { it.name }
            javaFieldNames.forEach { add(it) }
        }

    @Test
    fun `писатель sidecar-состояния удалён из Song`() {
        assertFalse(memberNames.contains("saveToFile"), "Song.saveToFile должен быть удалён (FR-006)")
    }

    @Test
    fun `читатель sidecar-состояния удалён из Song`() {
        assertFalse(memberNames.contains("loadFromFile"), "Song.loadFromFile должен быть удалён (FR-006)")
    }

    @Test
    fun `пути sidecar-файлов удалены из Song`() {
        listOf(
            "fileSongAbsolutePath",
            "fileSongAbsolutePathLegacy",
            "fileSongOrLegacySettingsAbsolutePath",
        ).forEach { name ->
            assertFalse(memberNames.contains(name), "Song.$name — осиротевший путь sidecar, должен быть удалён")
        }
    }

    @Test
    fun `писатели файлов проектов рендера удалены`() {
        // FR-009: .kdenlive/.kdenlive.srt больше не пишутся
        assertFalse(memberNames.contains("createKdenliveFiles"), "Song.createKdenliveFiles должен быть удалён (FR-009)")
    }

    @Test
    fun `пути, нужные переименованию, сохранены`() {
        // Обратная сторона: переименование продолжает работать с реальными путями
        assertTrue(memberNames.contains("kdenliveFileName"), "kdenliveFileName нужен каскаду переименования")
        assertTrue(memberNames.contains("fileAbsolutePath"), "исходный flac должен оставаться адресуемым")
        assertTrue(memberNames.contains("accompanimentNameFlac"), "flac-стемы — защищённый класс (FR-010)")
    }

    @Test
    fun `в Song нет вызовов удалённых писателей`() {
        // Ни один метод класса не должен ссылаться на удалённые писатели: если такой метод
        // появится снова, тест укажет на это раньше, чем прод.
        val removed = listOf("saveToFile", "loadFromFile", "createKdenliveFiles", "fileSongAbsolutePath")
        val stillPresent = Song::class.members.map { it.name }.filter { it in removed }
        assertEquals(emptyList<String>(), stillPresent, "в Song остались ссылки на удалённые члены")
    }
}

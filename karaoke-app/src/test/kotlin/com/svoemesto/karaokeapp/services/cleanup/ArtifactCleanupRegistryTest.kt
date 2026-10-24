package com.svoemesto.karaokeapp.services.cleanup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Юнит-тесты реестра классов артефактов песни (specs/544-song-artifact-cleanup, T006).
 *
 * Покрывает FR-010 (защищённые классы не удаляются), FR-013 (порядок чистки)
 * и инвариант 1 из `data-model.md` §2.
 *
 * @see specs/544-song-artifact-cleanup/data-model.md
 */
class ArtifactCleanupRegistryTest {
    @Test
    fun `каждый класс реестра объявлен полностью`() {
        // Инвариант, а не снимок инвентаризации: числа «17 классов»/«2 198 файлов»
        // описывали состояние ФС на конкретную дату и роняли тест при любом
        // обновлении инвентаризации, даже когда поведение кода не менялось.
        // Проверяем вместо этого полноту объявления каждого класса реестра.
        ArtifactCleanupClass.entries.forEach { cls ->
            assertTrue(cls.verdicts.isNotEmpty(), "${cls.name}: у класса должен быть хотя бы один вердикт")
            assertTrue(cls.title.isNotBlank(), "${cls.name}: у класса должно быть название")
            assertTrue(cls.note.isNotBlank(), "${cls.name}: у класса должно быть пояснение")
            if (cls != ArtifactCleanupClass.MANUAL_FILES) {
                // MANUAL_FILES — единственный класс без писателя в коде (см. data-model.md),
                // поэтому у него матчеров нет; остальные обязаны уметь что-то находить.
                assertTrue(cls.matchers.isNotEmpty(), "${cls.name}: у класса должен быть хотя бы один матчер")
            }
        }
    }

    @Test
    fun `каждый класс реестра находится по своему идентификатору`() {
        // Реестр используется из HTTP-контракта по id — класс, который нельзя найти
        // по своему же имени, недоступен для управления чисткой.
        ArtifactCleanupClass.entries.forEach { cls ->
            assertEquals(cls, ArtifactCleanupClass.byId(cls.name), "${cls.name} должен находиться по своему id")
            assertEquals(
                cls,
                ArtifactCleanupClass.byId(cls.name.lowercase()),
                "${cls.name} должен находиться по id в нижнем регистре",
            )
        }
    }

    @Test
    fun `MLT-проекты не удаляются - их читает живой melt-конвейер`() {
        // Решение владельца 2026-10-16 (вариант A): .mlt создаётся и НЕ удаляется —
        // аргументы MELT_*-процессов ссылаются на done_projects/*.mlt (KaraokeProcess.kt:1135-1147)
        val mlt = ArtifactCleanupClass.MLT_PROJECTS
        assertEquals(setOf(ArtifactCleanupVerdict.KEEP), mlt.verdicts)
        assertTrue(mlt.isProtected, "KEEP обязан отбиваться API так же, как PROTECTED")
        assertFalse(mlt.isDeletable)
        assertFalse(mlt.isDeletedAfterStage)
        assertEquals(0, mlt.cleanupOrder, "класс не участвует в разовой чистке")
        assertTrue(mlt.expectedFileCount > 0L, "у MLT-класса с известной раскладкой объявлен ожидаемый объём")
        assertTrue(mlt.expectedBytes > 0L, "у MLT-класса с известной раскладкой объявлен ожидаемый размер")
        assertTrue(mlt.matchers.any { ".mlt" in it.extensions })
    }

    @Test
    fun `ни один удаляемый класс не матчит файлы mlt`() {
        // Дефект, найденный на ревью: DONE_PROJECTS матчил done_projects/*.mlt — разовая
        // чистка класса 4 снесла бы проекты, нужные melt-конвейеру (нарушение SC-008)
        ArtifactCleanupClass.entries
            .filter { it.isDeletable || it.isDeletedAfterStage }
            .forEach { cls ->
                cls.matchers.forEach { matcher ->
                    assertFalse(
                        matcher.matchesPath("song.mlt", ArtifactCleanupClass.DONE_PROJECTS_FOLDER),
                        "${cls.name} не должен матчить .mlt",
                    )
                }
            }
    }

    @Test
    fun `классы с читателем не удаляются ни одним путём`() {
        ArtifactCleanupClass.entries
            .filter {
                ArtifactCleanupVerdict.KEEP in it.verdicts || ArtifactCleanupVerdict.NOT_TOUCH in it.verdicts
            }.forEach { cls ->
                assertTrue(cls.isProtected, "${cls.name}: класс с читателем должен отбиваться API")
                assertFalse(cls.isDeletable, "${cls.name}: не может быть DELETE_EXISTING")
                assertFalse(cls.isDeletedAfterStage, "${cls.name}: не может быть DELETE_AFTER_STAGE")
            }
    }

    @Test
    fun `файлы сохранённых классов не попадают ни в один удаляемый класс`() {
        // Сквозная проверка пересечений (пункт 4 ревью): представители KEEP/NOT_TOUCH
        // прогоняются через то же правило матчинга, что использует планировщик.
        val saved =
            listOf(
                Triple("song [lyrics].txt", ArtifactCleanupClass.DONE_FILES_FOLDER, ArtifactCleanupClass.SONG_TXT),
                Triple("song [sheetsage].pdf", ArtifactCleanupClass.SHEETSAGE_FOLDER, ArtifactCleanupClass.SHEETSAGE),
                Triple("song [key].json", "", ArtifactCleanupClass.KEY_JSON),
                Triple("song.mlt", ArtifactCleanupClass.DONE_PROJECTS_FOLDER, ArtifactCleanupClass.MLT_PROJECTS),
                Triple("key_bpm.csv", "", ArtifactCleanupClass.KEY_BPM_CSV),
            )
        val deletable = ArtifactCleanupClass.entries.filter { it.isDeletable || it.isDeletedAfterStage }
        saved.forEach { (fileName, parent, owner) ->
            assertTrue(
                owner.matchers.any { it.matchesPath(fileName, parent) },
                "${owner.name}: собственный матчер должен узнавать '$fileName'",
            )
            deletable.forEach { cls ->
                assertFalse(
                    cls.matchers.any { it.matchesPath(fileName, parent) },
                    "'$fileName' (${owner.name}) не должен попадать в удаляемый класс ${cls.name}",
                )
            }
        }
    }

    @Test
    fun `исходники и flac-стемы защищены от удаления`() {
        // FR-010: 768 ГБ исходников и 965 ГБ flac-стемов не удаляются ничем
        assertEquals(
            setOf(ArtifactCleanupClass.SOURCE_FLAC, ArtifactCleanupClass.STEMS_FLAC),
            ArtifactCleanupClass.entries.filter { ArtifactCleanupVerdict.PROTECTED in it.verdicts }.toSet(),
        )
    }

    @Test
    fun `классы с читателем помечены NOT_TOUCH`() {
        assertEquals(
            setOf(ArtifactCleanupClass.KEY_BPM_CSV, ArtifactCleanupClass.MANUAL_FILES),
            ArtifactCleanupClass.entries.filter { ArtifactCleanupVerdict.NOT_TOUCH in it.verdicts }.toSet(),
        )
    }

    @Test
    fun `инвариант 1 - ни один защищённый класс не удаляется`() {
        ArtifactCleanupClass.entries
            .filter { it.isProtected }
            .forEach { cls ->
                assertFalse(cls.isDeletable, "${cls.name}: защищённый класс не может быть DELETE_EXISTING")
                assertFalse(cls.isDeletedAfterStage, "${cls.name}: защищённый класс не может быть DELETE_AFTER_STAGE")
            }
    }

    @Test
    fun `порядок чистки неубывает и покрывает диапазон 1-7`() {
        // data-model §1: классы 3 (PUBLICATION_PNG/CHORDS_PNG) и 7 (HYGIENE_WAV/HYGIENE_JUNK)
        // делят номер — поэтому проверяем неубывание, а не строгое возрастание.
        val ordered = ArtifactCleanupClass.deletable()
        assertTrue(ordered.isNotEmpty(), "список классов разовой чистки не должен быть пуст")
        val orders = ordered.map { it.cleanupOrder }
        assertEquals(orders.sorted(), orders, "cleanupOrder должен быть неубывающим в порядке чистки")
        assertTrue(orders.all { it in 1..7 }, "cleanupOrder участвующих классов лежит в 1..7: $orders")
        // Набор различных значений строго возрастает и покрывает шаги 1..7 без пропусков:
        // внутри шага возможно несколько классов (3 — картинки, 7 — гигиена).
        assertEquals((1..7).toList(), orders.distinct())
    }

    @Test
    fun `демо-ролики чистятся первыми, локальные mp3 - после`() {
        val ordered = ArtifactCleanupClass.deletable()
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ordered.first())
        val demoIndex = ordered.indexOf(ArtifactCleanupClass.DEMO_MP4)
        val mp3Index = ordered.indexOf(ArtifactCleanupClass.STEMS_MP3_LOCAL)
        assertTrue(demoIndex < mp3Index, "DEMO_MP4 должен идти раньше STEMS_MP3_LOCAL (низкий риск первым)")
    }

    @Test
    fun `byId находит класс регистронезависимо и возвращает null на неизвестном`() {
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ArtifactCleanupClass.byId("demo_mp4"))
        assertEquals(ArtifactCleanupClass.DEMO_MP4, ArtifactCleanupClass.byId("  DEMO_MP4 "))
        assertNull(ArtifactCleanupClass.byId("NO_SUCH_CLASS"))
    }

    @Test
    fun `классы с ожидаемым числом файлов объявляют и ожидаемый объём`() {
        ArtifactCleanupClass.entries.forEach { cls ->
            if (cls.expectedFileCount > 0L) {
                assertTrue(cls.expectedBytes > 0L, "${cls.name}: при известном числе файлов должен быть известен объём")
            }
        }
    }

    @Test
    fun `matchers позволяют найти файлы только у классов с известной раскладкой`() {
        assertNotNull(ArtifactCleanupClass.DEMO_MP4.matchers.firstOrNull { it.nameContainsAny.contains("[demo]") })
        // MANUAL_FILES — класс без писателя в коде, раскладка неизвестна: матчеров нет
        assertTrue(ArtifactCleanupClass.MANUAL_FILES.matchers.isEmpty())
    }
}

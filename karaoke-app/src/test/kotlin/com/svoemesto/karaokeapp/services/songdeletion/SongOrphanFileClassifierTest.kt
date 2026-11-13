package com.svoemesto.karaokeapp.services.songdeletion

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Классификатор файлов на диске для разовой уборки (KARAOKE-429, спека 739).
 *
 * Примеры взяты из утверждённой инвентаризации (карта решений KARAOKE-409,
 * тикет KARAOKE-424, `T11-disk-orphans.md`): те же файлы и те же живые песни,
 * что были разобраны при замере 2026-11-12, с теми же вердиктами.
 */
class SongOrphanFileClassifierTest {
    private val albumFolder = "/sm-karaoke/done1/Песня/2024 - Альбом"

    private fun classifier(vararg liveNames: String): SongOrphanFileClassifier =
        SongOrphanFileClassifier(
            liveByFolder = mapOf(albumFolder to liveNames.toList()),
            allLiveNames = liveNames.toList(),
        )

    @Test
    fun `файл артефакта живой песни — не кандидат`() {
        val classifier = classifier("2000 (01) [Jane Air] - Akt Love")
        val result = classifier.classify("2000 (01) [Jane Air] - Akt Love.flac", albumFolder)
        assertEquals(SongOrphanFileClassifier.Verdict.LIVE, result.verdict)
    }

    @Test
    fun `имя живой песни с нестандартным хвостом — не кандидат`() {
        val classifier = classifier("2012 (02) [Машина Времени] - Эй, послушай, парень")
        val result =
            classifier.classify(
                "2012 (02) [Машина Времени] - Эй, послушай, парень_ [key].json",
                albumFolder,
            )
        assertEquals(SongOrphanFileClassifier.Verdict.LIVE_ODD_TAIL, result.verdict)
    }

    @Test
    fun `обложка альбома и служебные файлы — не кандидаты`() {
        val classifier = classifier("2000 (01) [Jane Air] - Akt Love")
        listOf(
            "LogoAlbum.png",
            "LogoAlbum.preview.png",
            "LogoAuthor.png",
            "key_bpm.csv",
            "[LYRICS].run",
            "folder.jpg",
            "R-7945333-1452184152-5919.jpeg.jpg",
            "null",
        ).forEach { name ->
            val result = classifier.classify(name, albumFolder)
            assertEquals(
                SongOrphanFileClassifier.Verdict.UNRECOGNIZED,
                result.verdict,
                "файл «$name» удалять нельзя: $result",
            )
        }
    }

    @Test
    fun `совпадение с живой песней после нормализации — не кандидат`() {
        val classifier = classifier("1989 (16) [Х.. забей] - Дед Мороз")
        val result = classifier.classify("1989 (16) [Х.. забей] - Дед мороз [lyrics].mlt", albumFolder)
        assertEquals(SongOrphanFileClassifier.Verdict.LIVE, result.verdict)
    }

    @Test
    fun `нечёткое совпадение с живой песней — не кандидат`() {
        val classifier = classifier("2000 (02) [Мумий Тролль] - Все будет (mix DZUMA _ AG)")
        val result =
            classifier.classify(
                "2000 (02) [Мумий Тролль] - Все будет (mix DZUMA & AG)-vocals.flac",
                albumFolder,
            )
        assertEquals(SongOrphanFileClassifier.Verdict.LIVE, result.verdict)
    }

    @Test
    fun `песни с таким именем нет в БД — кандидат`() {
        val classifier = classifier("2000 (01) [Jane Air] - Akt Love")
        val result = classifier.classify("2001 (03) [Jane Air] - Jazz [key].json", albumFolder)
        assertEquals(SongOrphanFileClassifier.Verdict.ORPHAN, result.verdict)
        assertEquals("2001 (03) [Jane Air] - Jazz", result.candidateName)
    }

    @Test
    fun `коэффициент подобия считается как у difflib`() {
        assertEquals(1.0, SongOrphanFileClassifier.similarity("abc", "abc"), 1e-9)
        assertEquals(0.0, SongOrphanFileClassifier.similarity("abc", "xyz"), 1e-9)
        // Совпадение 8 символов из 10: 2*8/10 = 0.8
        assertEquals(0.8, SongOrphanFileClassifier.similarity("abcdefghij", "abcdefghXY"), 1e-9)
    }

    @Test
    fun `нормализация имени как в инвентаризации`() {
        assertEquals(
            "1989 (16) [х забей] дед мороз",
            SongOrphanFileClassifier.normalize("1989 (16) [Х.. забей] - Дед Мороз"),
        )
        assertEquals(
            "2000 (02) [мумий тролль] все будет (mix dzuma ag)",
            SongOrphanFileClassifier.normalize("2000 (02) [Мумий Тролль] - «Всё будет» (mix DZUMA & AG)"),
        )
    }

    @Test
    fun `шаблон артефакта снимается с конца имени`() {
        val (cand, tail) = SongOrphanFileClassifier.stripTail("2012 (02) [МВ] - Эй [sheetsage].pdf")!!
        assertEquals("2012 (02) [МВ] - Эй", cand)
        assertEquals(" [sheetsage].pdf", tail)
        assertTrue(SongOrphanFileClassifier.stripTail("LogoAlbum.png") != null)
    }
}

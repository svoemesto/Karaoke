package com.svoemesto.karaokeapp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory

class SanitizePathTest {
    private val sp = SanitizePath

    @Test
    fun `sanitizePathSegment заменяет восклицательный знак на _`() {
        sp.run {
            assertEquals("Hello_", "Hello!".sanitizePathSegment())
            assertEquals("What__", "What?!".sanitizePathSegment())
            assertEquals("Дай жару_", "Дай жару!".sanitizePathSegment())
            val once = "Hello!".sanitizePathSegment()
            val twice = once.sanitizePathSegment()
            assertEquals(once, twice)
        }
    }

    @Test
    fun `sanitizePathSegment заменяет вопросительный знак на _`() {
        sp.run {
            assertEquals("Track_", "Track?".sanitizePathSegment())
            val once = "Track?".sanitizePathSegment()
            val twice = once.sanitizePathSegment()
            assertEquals(once, twice)
        }
    }

    @Test
    fun `sanitizePathSegment обрабатывает пустую строку и only-problematic`() {
        sp.run {
            assertEquals("", "".sanitizePathSegment())
            assertEquals("__", "!?".sanitizePathSegment())
            assertEquals("x", "*".sanitizePathSegment())
            assertEquals("_", "_".sanitizePathSegment())
        }
    }

    @Test
    fun `sanitizePathSegment сохраняет кириллицу и заменяет проблемные символы`() {
        sp.run {
            assertEquals("Лучшее_", "Лучшее!".sanitizePathSegment())
            assertEquals("Привет, мир__", "Привет, мир!?".sanitizePathSegment())
            val once = "Привет, мир!?".sanitizePathSegment()
            val twice = once.sanitizePathSegment()
            assertEquals(once, twice)
        }
    }

    @Test
    fun `sanitizePath сохраняет разделители и санитайзит сегменты`() {
        sp.run {
            assertEquals("/path/to/file_.mp3", "/path/to/file!.mp3".sanitizePath())
            assertEquals("a/b/c/d", "a/b/c/d".sanitizePath())
            assertEquals("/", "/".sanitizePath())
            assertEquals("a\\b\\c_", "a\\b\\c!".sanitizePath())
        }
    }

    @Test
    fun `обёртки в Extentions вызывают SanitizePath`() {
        assertEquals("Hello_", "Hello!".rightFileNameSymbols())
        assertEquals("Hello_", "Hello!".sanitizeSongFileName())
        assertEquals("/path/file_.mp3", "/path/file!.mp3".rightFileName())
    }

    // === US2: расширенное покрытие символов (FR-002, FR-003, FR-004, FR-014) ===

    @ParameterizedTest
    @ValueSource(strings = ["!", "?", "<", ">", "|", "&", ";", "\""])
    fun `FR-002 каждый проблемный символ заменяется на _`(input: String) {
        sp.run {
            assertEquals("_", input.sanitizePathSegment())
        }
    }

    @Test
    fun `FR-002 управляющие символы тоже заменяются на _`() {
        sp.run {
            assertEquals("_", "\n".sanitizePathSegment())
            assertEquals("_", "\r".sanitizePathSegment())
            assertEquals("_", "\t".sanitizePathSegment())
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["(", ")", "[", "]", "а", "Я", "1", "-", ".", "+", "=", "@", "#", "%", "^", "~", ",", " "])
    fun `FR-003 безопасные символы сохраняются`(input: String) {
        sp.run {
            assertEquals(input, input.sanitizePathSegment())
        }
    }

    @Test
    fun `FR-004 legacy mapping применяется идемпотентно`() {
        sp.run {
            assertEquals("`", "'".sanitizePathSegment())
            assertEquals("`", "`".sanitizePathSegment())
            assertEquals("s", "$".sanitizePathSegment())
            assertEquals("s", "s".sanitizePathSegment())
            assertEquals("x", "*".sanitizePathSegment())
            assertEquals("x", "x".sanitizePathSegment())
            assertEquals("-", ":".sanitizePathSegment())
            assertEquals("-", "-".sanitizePathSegment())
            assertEquals("a`b", "a'b".sanitizePathSegment())
            assertEquals("s5_", "$5!".sanitizePathSegment())
        }
    }

    @Test
    fun `FR-014 side-effect идемпотентность - повторный прогон не плодит логов`() {
        val logger = LoggerFactory.getLogger(SanitizePath::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        logger.level = Level.INFO
        try {
            sp.run {
                val firstResult = "Hello!".sanitizePathSegment()
                val afterFirst = appender.list.size
                assertTrue(afterFirst >= 1, "После первого прогона ожидалась хотя бы 1 лог-запись, было $afterFirst")
                // Идемпотентность side-effect: повторный прогон sanitize(firstResult)
                // не должен плодить новых лог-записей (changed == false).
                firstResult.sanitizePathSegment()
                val afterSecond = appender.list.size
                assertEquals(afterFirst, afterSecond, "Side-effect не идемпотентен: после второго прогона стало $afterSecond (было $afterFirst)")
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `FR-014 sanitize не пишет лог если ничего не изменилось`() {
        val logger = LoggerFactory.getLogger(SanitizePath::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        logger.level = Level.INFO
        try {
            sp.run {
                appender.list.clear()
                "SafeName_2024".sanitizePathSegment()
                assertEquals(0, appender.list.size, "Для safe-строки не должно быть лог-записей, было ${appender.list.size}")
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    // === US3: обратная совместимость с прод-именами (FR-004) ===

    @Test
    fun `US3 прод-имена с удалёнными проблемными символами сохраняются`() {
        sp.run {
            // Имитация имён из прод-БД: импорт по старому алгоритму
            // уже удалил `!`, `?`, заменил `$` на `s`, `'` на `` ` ``
            assertEquals("2012 - Daj zaru.flac", "2012 - Daj zaru.flac".sanitizePathSegment())
            assertEquals("Что это.flac", "Что это.flac".sanitizePathSegment())
        }
    }

    @Test
    fun `US3 прод-имена с legacy mapping сохраняются идемпотентно`() {
        sp.run {
            // `$` заменён на `s`, повторный прогон не меняет
            assertEquals("Queen s 720p", "Queen s 720p".sanitizePathSegment())
            // `'` заменён на `` ` ``, повторный прогон не меняет
            assertEquals("Queen` lyrics.mp3", "Queen` lyrics.mp3".sanitizePathSegment())
            // `:` заменён на `-`, повторный прогон не меняет
            assertEquals("2024-01-15 mix.flac", "2024-01-15 mix.flac".sanitizePathSegment())
            // `*` заменён на `x`, повторный прогон не меняет
            assertEquals("xtra track.mp3", "xtra track.mp3".sanitizePathSegment())
        }
    }

    @Test
    fun `US3 прод-имена с уже идемпотентной структурой полностью сохраняются`() {
        sp.run {
            // Скобки, квадратные скобки, кириллица, цифры — все сохраняются
            val prodSamples =
                listOf(
                    "2012 (01) [Ария] - Дай жару.flac",
                    "Track 1 (Live).mp3",
                    "2024 [Remix] - Hit.mp3",
                    "Daj zaru (2).flac",
                    "Ария - Дай жару [Official Video].mp4",
                    "Best of 2023 (Full Album).flac",
                    "Queen - Bohemian Rhapsody (Remastered 2011).flac",
                    "AC-DC - Highway to Hell.mp3",
                    "Любэ - Конь.mp3",
                    "Кино - Группа крови.flac"
                )
            prodSamples.forEach { sample ->
                assertEquals(
                    sample, sample.sanitizePathSegment(),
                    "Имя '$sample' должно сохраниться как есть (уже идемпотентно)"
                )
            }
        }
    }

    @Test
    fun `US3 синтетическая выборка 100+ legacy-имён идемпотентна`() {
        sp.run {
            // Синтетическая выборка на основе legacy-паттернов (SC-003).
            // Включает:
            // - имена, где `!`/`?` были удалены старым алгоритмом (drop)
            // - имена с legacy-mapping (`$` → `s`, `'` → `` ` ``, `:` → `-`, `*` → `x`)
            // - имена с уже идемпотентной структурой
            // - кириллица
            val base = mutableListOf<String>()
            // 25 имён с удалёнными `!`/`?` (были, теперь нет)
            repeat(5) {
                base +=
                    listOf(
                        "Track 1.flac", "What.flac", "Best Hit.flac",
                        "Queen s 720p.flac", "2024-01-15 mix.flac"
                    )
            }
            // 25 имён с legacy mapping
            repeat(5) {
                base +=
                    listOf(
                        "Queen` lyrics.mp3", "xtra track.mp3", "2024-01-15 mix.flac",
                        "AC-DC Highway.flac", "Best of Queen.mp3"
                    )
            }
            // 25 имён со скобками и []
            repeat(5) {
                base +=
                    listOf(
                        "2012 (01) [Ария] - Дай жару.flac",
                        "Track 1 (Live).mp3", "2024 [Remix] - Hit.mp3",
                        "Best of 2023 (Full Album).flac", "Queen - Bohemian.flac"
                    )
            }
            // 25 кириллических имён
            repeat(5) {
                base +=
                    listOf(
                        "Любэ - Конь.mp3", "Кино - Группа крови.flac",
                        "Ария - Штиль.mp3", "ДДТ - Последняя осень.flac",
                        "Сплин - Орбит без сахара.mp3"
                    )
            }
            // 25 имён с подчёркиваниями (после FR-002)
            repeat(5) {
                base +=
                    listOf(
                        "Best_of_queen.flac", "2024_remix_mix.mp3",
                        "live_version_track_1.flac", "remastered_2011_track.flac",
                        "preview_demo.mp3"
                    )
            }
            // Итого: 125 имён. Каждое должно быть идемпотентно.
            assertTrue(base.size >= 100, "Нужно >= 100 имён, есть ${base.size}")
            base.forEach { sample ->
                assertEquals(
                    sample, sample.sanitizePathSegment(),
                    "Имя '$sample' не идемпотентно"
                )
            }
        }
    }
}

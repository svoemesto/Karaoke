package com.svoemesto.karaokeapp.audit

import com.svoemesto.karaokeapp.sync.SyncRegistry
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KARAOKE-429 (инцидент 2026-11-14, прод): альбомы владельца («Мумий Тролль») появились
 * на проде, а новые песни — нет.
 *
 * Причина — порядок синхронизации, а не сами данные. Прод применяет тело
 * `POST /changerecords` ЦЕЛИКОМ в одной транзакции (`autoCommit=false`, `commit()` в конце,
 * `rollback()` при ошибке). Одна строка, нарушающая FK, откатывает ВЕСЬ батч → HTTP 500 →
 * ни одна строка батча не применена.
 *
 * Реестр `SyncRegistry.all` задаёт порядок обхода таргетов и в one-click
 * (`ApiController.postSyncOneClick`), и в планировщике (`AutoOneClickSyncScheduler`).
 * До правки порядок был `Song, Pictures, Authors, Albums, …` — песни синкались РАНЬШЕ
 * альбомов, поэтому новая песня с ещё не залитым альбомом роняла весь батч песен.
 *
 * Тест фиксирует инвариант: для каждой пары «родитель → ребёнок» по проверенному на проде
 * FK-графу родитель обязан идти РАНЬШЕ ребёнка. FK-граф — константа теста: он снят с прода
 * (`information_schema` + `pg_constraint`) 2026-11-14 и зафиксирован как контракт.
 *
 * Офлайн: живая БД не нужна — проверяется чистая структура реестра.
 */
class SyncFkOrderTest {
    /**
     * FK-граф синхронизируемых таблиц: ребёнок → родитель(и).
     * Снят с прода 2026-11-14 (`information_schema.referential_constraints`).
     */
    private val foreignKeyParents: Map<String, Set<String>> =
        mapOf(
            "albums" to setOf("authors"),
            "songs" to setOf("albums"),
            "songcoauthors" to setOf("songs", "authors"),
            "news" to setOf("songs"),
            "siteplaylists" to setOf("siteusers"),
            "siteplaylistitems" to setOf("siteplaylists"),
            "listeninghistory" to setOf("siteusers"),
            "songassignments" to setOf("siteusers"),
            "songassignmentdrafts" to setOf("siteusers"),
            "sharelinks" to setOf("siteusers"),
            "subscriptions" to setOf("siteusers", "pricetariffs"),
        )

    private fun order(): Map<String, Int> =
        SyncRegistry.all.mapIndexed { index, target -> target.key to index }.toMap()

    @Test
    fun `FK-родитель всегда раньше FK-ребёнка в порядке синхронизации`() {
        val positions = order()

        foreignKeyParents.forEach { (child, parents) ->
            parents.forEach { parent ->
                assertTrue(
                    positions.containsKey(parent),
                    "таргет $parent отсутствует в SyncRegistry.all — FK-граф теста устарел",
                )
                assertTrue(
                    positions.containsKey(child),
                    "таргет $child отсутствует в SyncRegistry.all — FK-граф теста устарел",
                )
                assertTrue(
                    positions.getValue(parent) < positions.getValue(child),
                    "FK-порядок нарушен: $parent (позиция ${positions[parent]}) " +
                        "идёт после $child (позиция ${positions[child]}). " +
                        "Прод применяет батч одной транзакцией — ребёнок без родителя откатит весь батч " +
                        "(KARAOKE-429: альбомы на проде есть, песен нет).",
                )
            }
        }
    }

    @Test
    fun `альбомы синкаются раньше песен`() {
        val positions = order()
        assertTrue(
            positions.getValue("albums") < positions.getValue("songs"),
            "альбомы обязаны синкаться раньше песен: tbl_songs.album_id → tbl_albums(id)",
        )
        assertTrue(
            positions.getValue("authors") < positions.getValue("albums"),
            "авторы обязаны синкаться раньше альбомов: tbl_albums.author_id → tbl_authors(id)",
        )
    }

    @Test
    fun `все таргеты реестра присутствуют и уникальны`() {
        val keys = SyncRegistry.all.map { it.key }
        assertTrue(keys.size == keys.toSet().size, "в SyncRegistry.all есть повторяющиеся ключи: $keys")
        assertTrue(
            keys.containsAll(foreignKeyParents.keys),
            "в SyncRegistry.all нет части синхронизируемых таблиц: " +
                "${foreignKeyParents.keys - keys.toSet()}",
        )
    }
}

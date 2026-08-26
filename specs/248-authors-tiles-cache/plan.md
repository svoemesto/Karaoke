# Implementation Plan: Кеш для /api/public/authors-tiles

**Branch**: `248-authors-tiles-cache` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)
**Parent**: [`specs/241-db-storage-perf-audit/spec.md`](../241-db-storage-perf-audit/spec.md) — Tier-2 / FR-105

**Input**: Feature specification from `/specs/248-authors-tiles-cache/spec.md`

## Summary

Добавить TTL-кеш (30 минут) + dirty-инвалидацию через `StatBySong.consumeDirty()` для endpoint `/api/public/authors-tiles`. Сейчас на каждый запрос — 2 тяжёлых full-scan к `tbl_songs` (DISTINCT + GROUP BY). После — 1 cold start + cache hits (0 SQL в течение 30 минут). Управление через `karaoke.public.authors-tiles-cache.enabled` (дефолт `true`).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17), Spring Boot 3.x (karaoke-web, prod)
**Primary Dependencies**: `StatBySong.consumeDirty()` (singleton, `karaoke-web/.../StatBySong.kt:60`), `KaraokeProperties.getBoolean(key)` (`karaoke-app/.../KaraokeProperties.kt:96`), `ConcurrentHashMap` (stdlib)
**Storage**: in-memory (`ConcurrentHashMap<String, CachedAuthorsTiles>`) — без БД-кеша, без Redis
**Testing**: ручное на проде + `pg_log`-замеры (см. Constitution § Тесты — `@Disabled`)
**Target Platform**: Linux server (prod karaoke-web)
**Project Type**: library/multi-module Gradle (`karaoke-app` + `karaoke-web` + `karaoke-db`)
**Performance Goals**: SC-001 <50 мс warm path, SC-003 ≤2 SQL на 100 повторных запросов
**Constraints**: сохранить Constitutional § II «Сырой JDBC»; не изменять публичный API/DTO контракт; TTL 30 мин; cross-module import `com.svoemesto.karaokeapp.KaraokeProperties` (allowed — `karaoke-web` depends on `karaoke-app`)
**Scale/Scope**: 1 endpoint, 1 helper пара, до ~125 авторов в кеше, key count = 6 (3 scope × 2 onlyPublished)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-verify after Phase 1 design.*

- ✅ **Principle I (Self-contained автопайплайн)**: не затрагивается — это in-memory cache, не вводит внешних SaaS-зависимостей.
- ✅ **Principle II (Сырой JDBC + дифф по хэшам)**: сохраняется. Существующая логика `Song.loadAuthorSongCounts` и `Song.loadListAuthors` (сырой JDBC) оборачивается в cache — НЕ изменяется.
- ✅ **Principle III (SyncRegistry)**: не затрагивается.
- ✅ **Principle IV (Async-очередь)**: не затрагивается.
- ✅ **Principle V (Двух-фронтенд)**: не затрагивается — endpoint прозрачен для клиента.
- ✅ **Principle VI (Code Standards)**: сохраняется. KDoc обязателен для новых helper'ов (FR-006 spec.md). Per-feature документ — `livedocs/features/248-authors-tiles-cache.md` будет создан.
- ✅ **Principle VII (Cross-Machine)**: не затрагивается.
- ✅ **Principle VIII (Секреты)**: не затрагивается — нет секрет-файлов.

**Constitution Check: PASS** — фича полностью соответствует всем принципам.

## Project Structure

### Documentation (this feature)

```text
specs/248-authors-tiles-cache/
├── plan.md                       # Этот файл
├── spec.md                       # Feature specification (FR-105 из parent)
├── checklists/
│   └── requirements.md          # 16/16 ✅
└── tasks.md                      # Phase 1-5 (через /speckit.tasks)
```

### Source Code (changes)

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
└── KaraokeProperties.kt          # ИЗМЕНЕНИЕ: register "karaoke.public.authors-tiles-cache.enabled"

karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── controllers/
│   └── PublicApiController.kt    # ИЗМЕНЕНИЕ: companion + helpers + wrap authorsTiles()
└── (StatBySong.kt                # БЕЗ изменений — consumeDirty уже есть)
```

**Structure Decision**: Single project (Option 1). Изменения точечные, в 2 файлах.

## Implementation Approach

### Phase 1: Регистрация property в `KaraokeProperties`

Добавить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` рядом с `karaoke.db.schema_cache.enabled` (строка ~322):

```kotlin
// Кеш для /api/public/authors-tiles (spec 248). Дефолт true — кеш прозрачен для всех
// вызовов endpoint'а, TTL=30 мин, инвалидация через StatBySong.consumeDirty().
// false — отключает кеш (полезно при отладке данных плашек авторов).
KaraokeProperty(
    key = "karaoke.public.authors-tiles-cache.enabled",
    defaultValue = true,
    description = "Кеш для /api/public/authors-tiles (TTL=30 мин). false = каждый запрос идёт в БД (отладка данных плашек).",
),
```

### Phase 2: Companion-объект + helpers в `PublicApiController`

Добавить в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` после объявления класса (строки 1-63):

```kotlin
import com.svoemesto.karaokeapp.KaraokeProperties
import java.util.concurrent.ConcurrentHashMap
```

И в companion object (новый блок после class):

```kotlin
/**
 * In-memory cache для /api/public/authors-tiles (FR-001, FR-105 parent).
 *
 * Hot endpoint на главной странице «Закромов» — без кеша делает 2 full-scan
 * к tbl_songs (DISTINCT + GROUP BY) на каждый запрос. С этим кешем — 1 cold
 * start + cache hits в течение TTL=30 мин.
 *
 * Инвалидация — через [StatBySong.consumeDirty]: если кто-то (save/sync песни)
 * взвёл dirty-флаг через [StatBySong.markDirty], следующий вызов сбрасывает
 * cache и пересчитывает данные.
 *
 * Не сохраняет пустые результаты (FR-007) — cache miss повторит попытку.
 *
 * Thread-safe через [ConcurrentHashMap] — два одновременных запроса в момент
 * cache miss могут сделать двойной loadFn, это допустимо (UI не блокируется).
 *
 * @see specs/248-authors-tiles-cache FR-001..FR-009
 * @see specs/241-db-storage-perf-audit FR-105
 * @see StatBySong.consumeDirty
 */
companion object {
    /** TTL кеша — 30 минут (FR-005). */
    private const val CACHE_TTL_MS = 30 * 60 * 1000L

    /** Ключ свойства в [KaraokeProperties] (FR-003). */
    private const val KARAOKE_PROPERTY_CACHE_ENABLED = "karaoke.public.authors-tiles-cache.enabled"

    /**
     * Запись кеша — пара (value, expiresAtMs). Immutable, чтобы не было гонок
     * при чтении в одном потоке и записи в другом.
     */
    private data class CachedAuthorsTiles(
        val value: List<AuthorTilePublicDto>,
        val expiresAtMs: Long,
    )

    /** Thread-safe хранилище кеша (FR-002). */
    private val authorsTilesCache = ConcurrentHashMap<String, CachedAuthorsTiles>()

    /**
     * Возвращает кешированный список `AuthorTilePublicDto` для ключа
     * `scope:onlyPublished` или выполняет `loadFn` и кладёт результат в кеш.
     *
     * Алгоритм (FR-001):
     * 1. Если кеш отключён через [KARAOKE_PROPERTY_CACHE_ENABLED] → `loadFn()`.
     * 2. Если [StatBySong.consumeDirty] вернул `true` → cache очищается
     *    (dirty-инвалидация имеет приоритет над TTL).
     * 3. Cache hit (ключ есть + `expiresAtMs > now`) → возврат из кеша.
     * 4. Cache miss → `loadFn()`. Если результат непустой (FR-007) — cache put.
     * 5. Если `loadFn()` бросил — cache не меняется, исключение пробрасывается.
     *
     * @param scope "main" / "special" / "all" / etc. — используется в cache key.
     * @param onlyPublished `true` для анонимов/обычных, `false` для редактора.
     * @param loadFn функция загрузки (выполняет 2 SQL: counts + authors).
     * @return список `AuthorTilePublicDto` (из кеша или свежий).
     *
     * @see specs/248-authors-tiles-cache FR-001..FR-009
     */
    private fun getCachedAuthorsTiles(
        scope: String,
        onlyPublished: Boolean,
        loadFn: () -> List<AuthorTilePublicDto>,
    ): List<AuthorTilePublicDto> {
        if (!isCacheEnabled()) {
            return loadFn()
        }
        // Dirty-инвалидация (FR-004): если кто-то взвёл флаг (save/sync),
        // сбрасываем весь cache. consumeDirty() атомарно читает и сбрасывает.
        try {
            if (StatBySong.consumeDirty()) {
                authorsTilesCache.clear()
                println("[authorsTilesCache] cache cleared by consumeDirty()")
            }
        } catch (_: Throwable) {
            // ignore — consumeDirty shouldn't throw, but defensive
        }

        val now = System.currentTimeMillis()
        val key = "$scope:$onlyPublished"
        val cached = authorsTilesCache[key]
        if (cached != null && cached.expiresAtMs > now) {
            return cached.value
        }
        println("[authorsTilesCache] cache miss scope=$scope onlyPublished=$onlyPublished")
        val fresh = loadFn()
        if (fresh.isNotEmpty()) {
            authorsTilesCache[key] = CachedAuthorsTiles(fresh, now + CACHE_TTL_MS)
        }
        return fresh
    }

    /**
     * Проверяет, разрешён ли cache свойством `karaoke.public.authors-tiles-cache.enabled`
     * в [KaraokeProperties] (дефолт `true`, зарегистрировано в `KaraokeProperties.kt`).
     *
     * Если `KaraokeProperties` по какой-то причине недоступен (ранняя инициализация,
     * проблемы с файлом) — функция возвращает `true` через `try/catch`. Безопасный
     * дефолт = кеш работает (минимизируем SQL round-trip'ы в типовом сценарии).
     *
     * @return `true` если кеш разрешён; `false` если явно отключён в свойствах.
     *
     * @see specs/248-authors-tiles-cache FR-003
     * @see KaraokeProperties.getBoolean
     */
    private fun isCacheEnabled(): Boolean =
        try {
            KaraokeProperties.getBoolean(KARAOKE_PROPERTY_CACHE_ENABLED)
        } catch (_: Throwable) {
            true
        }
}
```

### Phase 3: Wrap `authorsTiles()` в helper

Заменить тело `authorsTiles()` (строки 141-181) на:

```kotlin
@GetMapping("/authors-tiles")
fun authorsTiles(
    @RequestParam(required = false, defaultValue = "main") scope: String?,
    request: HttpServletRequest,
): List<AuthorTilePublicDto> {
    val isSpecialOrderFilter: Boolean? =
        when (scope) {
            "special" -> true
            "main" -> false
            "all" -> null
            else -> false
        }
    val onlyPublished = onlyPublishedFor(request)
    // Оборачиваем существующую логику в cache-helper (FR-001, FR-105 parent).
    // Cache key = "$scope:$onlyPublished" (FR-008). TTL=30 мин (FR-005).
    return getCachedAuthorsTiles(scope ?: "main", onlyPublished) {
        // Публичная поверхность прода — считаем и показываем только готовые песни
        // (specs/013-song-status-filter): плашка автора без готовых песен не отображается,
        // подпись плашки считает только их. Кроме "редактора" — для него фильтр по статусу снят,
        // подпись отражает полное количество песен автора (specs/017-editor-status-bypass).
        val counts =
            Song.loadAuthorSongCounts(
                isSpecialOrder = isSpecialOrderFilter,
                onlyPublished = onlyPublished,
                database = WORKING_DATABASE,
            )
        val loadedAuthors: List<String> =
            Song
                .loadListAuthors(
                    withSkiped = false,
                    isSpecialOrder = isSpecialOrderFilter,
                    database = WORKING_DATABASE,
                ).filter { (counts[it] ?: 0L) > 0L }
        val specialFlags: Map<String, Boolean> =
            loadedAuthors.associateWith {
                it in counts.keys && (isSpecialOrderFilter ?: false)
            }
        loadedAuthors.map {
            AuthorTilePublicDto.fromAuthorName(
                author = it,
                songCount = counts[it] ?: 0L,
                isSpecialOrder = it in specialFlags && specialFlags[it] == true,
            )
        }
    }
}
```

### Phase 4: Verify

1. `./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel` — без ошибок типов.
2. `./gradlew :karaoke-web:ktlintCheck` — без новых нарушений baseline.
3. `./gradlew :karaoke-web:bootJar --parallel` — PASS.

## Risks & Mitigations

| Риск | Вероятность | Митигация |
|------|-------------|-----------|
| `KaraokeProperties.getBoolean()` бросает на проде | Низкая | `try/catch` в `isCacheEnabled()` с fallback `true`. Кеш всегда работает. |
| Двойной loadFn при concurrent cache miss | Средняя | Допустимо — UI не блокируется, cache перезаписывается последним результатом. |
| `StatBySong.consumeDirty()` ломает существующую логику `StatsCacheScheduler` | Низкая | `consumeDirty()` атомарно сбрасывает флаг. `StatsCacheScheduler.refreshIfDirty` (вызывается раз в минуту) тоже использует `consumeDirty` — но порядок вызовов: scheduler → endpoint или endpoint → scheduler. В обоих случаях флаг корректно сбрасывается один раз. |
| Cross-module import `karaoke-app/KaraokeProperties` в `karaoke-web` | Низкая | Уже сделано в `KaraokeDbTable.kt` (для schema-cache), `karaoke-web` depends on `karaoke-app` через gradle. |
| `loadedAuthors.isEmpty()` (БД недоступна) → кеш не заполняется | Ожидаемо | FR-007: cache остаётся пустым, следующий вызов повторит. |
| `scope` принимает null (`?` тип) | Средняя | В cache key используем `scope ?: "main"` для безопасности. Spring `@RequestParam(required=false, defaultValue="main")` гарантирует non-null в runtime, но defensive. |

## Out-of-Scope (напоминание)

- Индекс `idx_songs_song_author` (FR-110) — отдельная фича.
- Кеш для `/api/public/zakroma` (FR-106) — отдельная фича.
- Tier-1 (FR-101, FR-102, FR-103, FR-104) и Tier-3 (FR-107, FR-108, FR-109) — отдельные фичи.
- Изменение SQL в `Song.loadListAuthors`/`Song.loadAuthorSongCounts` — НЕ затрагивается.
- Изменение `StatBySong.refreshCache` — НЕ затрагивается.
- Изменение публичного API или DTO-контрактов — cache прозрачен.

## Complexity Tracking

*Нет нарушений Constitution Check — таблица пуста.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет) | — | — |

## Verification Plan

### До деплоя (baseline)

1. Сделать `pg_log` снэпшот за 24 часа: `SELECT count(*), substring(query, 1, 80) FROM pg_log WHERE query LIKE '%tbl_songs%' GROUP BY substring(query, 1, 80) ORDER BY count(*) DESC LIMIT 20`.
2. Замерить p95 latency `/api/public/authors-tiles` через browser devtools (cold start 10 раз).

### После деплоя

1. Снять `pg_log` за 24 часа после деплоя, сравнить с baseline.
2. Сделать **искусственный сценарий**:
   - Открыть `/api/public/authors-tiles` 100 раз через curl в течение 30 мин.
   - `pg_log` должен показать ≤2 SQL к `tbl_songs` (cold start).
3. Проверить docker logs: должны быть `cache miss scope=...` (1 раз) + cache hits (без логов).
4. Симулировать save песни → следующий вызов `/api/public/authors-tiles` → cache cleared в логах.
5. Выставить `karaoke.public.authors-tiles-cache.enabled = false` через Karaoke.properties → loadFn() на каждый запрос, latency как раньше.

### Acceptance (mapping)

- **SC-001** (<50 мс warm path): замер через browser devtools.
- **SC-002** (<500 мс cold start): замер через browser devtools.
- **SC-003** (≤2 SQL на 100 запросов): подсчёт через `pg_log`.
- **SC-004** (≥80% снижение SQL): сравнение baseline/post-deploy.
- **SC-005** (cache disable работает): ручной тест с `enabled=false`.
- **SC-006** (≤50 строк нового кода, cyclomatic ≤5): code-review.

## Timeline Estimate

- Phase 1 (KaraokeProperties registration): 5 мин.
- Phase 2 (companion + helpers): 20–30 мин.
- Phase 3 (wrap authorsTiles): 10 мин.
- **Итого: ~1 час кодинга**.
- Тестирование на проде: 30 мин (deploy + замер + dirty-инвалидация).
- Deploy + 24 ч наблюдения + verification: 1 день.

## Definition of Done

- [ ] Все 9 FR из spec.md реализованы.
- [ ] Все 6 SC из spec.md измеримы и подтверждены (через `pg_log` + browser devtools).
- [ ] ktlintCheck + compile + bootJar проходит (см. AGENTS.md, «Обязательная проверка после правок»).
- [ ] Per-feature документ `livedocs/features/248-authors-tiles-cache.md` создан.
- [ ] PR создан через `gh pr create --base master` (см. AGENTS.md, «CI-gate для master»).
- [ ] CI (lint.yml) — 8/8 PASS.
- [ ] 1 PR → 1 merge в master → 1 деплой на прод (karaoke-web).

## Next Step

→ `/speckit.tasks specs/248-authors-tiles-cache` для генерации декомпозированных задач.
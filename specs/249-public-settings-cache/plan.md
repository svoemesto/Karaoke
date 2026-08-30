# Implementation Plan: TTL-кеш для PublicSettingsWebController.getProperty

**Branch**: `249-public-settings-cache` | **Date**: 2026-08-26 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/249-public-settings-cache/spec.md`

## Summary

Реализует Tier-2 P1 оптимизацию FR-006 из parent спеки [241-db-storage-perf-audit](../241-db-storage-perf-audit/spec.md):
вместо одного SQL `SELECT value FROM tbl_public_settings WHERE key = ?` на каждый HTTP-запрос
`/api/properties/getproperty` — in-memory `ConcurrentHashMap<String, CachedProperty>` с TTL 60 сек
+ dirty-инвалидация через **отдельный** `AtomicBoolean` (НЕ через `StatBySong.consumeDirty()` —
разные домены). Инвалидация — через `setProperty` (вызов `markDirty()` при успехе).

Паттерн повторяет проверенный подход из sister-фичи [248-authors-tiles-cache](../248-authors-tiles-cache/spec.md):
TTL-кеш в `companion object` + `consumeDirty()` инвалидация + `KaraokeProperties.getBoolean()` для
master-флага. Только domain-specific детали:
- TTL = 60 сек (не 30 мин как у authors-tiles).
- `NOT_FOUND_SENTINEL` для кеширования «ключ не найден» (отдельный object-маркер).
- `setProperty` → `markDirty()` (при успехе).

## Technical Context

**Язык/фреймворк**: Kotlin 2.x + Spring Boot 3.x (как родительский `PublicSettingsWebController`).
**Хранилище кеша**: `ConcurrentHashMap` в `companion object` (in-memory, single-instance).
**Дизаблинг**: `karaoke.public.public-settings-cache.enabled` через `KaraokeProperties.getBoolean`.

### Архитектурное решение (почему НЕ переиспользуем `StatBySong.consumeDirty()`)

`StatBySong.dirty` семантически про **free-флаги песен** (главная страница). `tbl_public_settings` —
про **kill-switches и публичные настройки** (разные админы, разные сценарии). Если переиспользовать
один dirty-флаг — то любое изменение песни сбрасывает кеш настроек (и наоборот), что неправильно.

Делаем **отдельный** `dirty: AtomicBoolean` в companion object `PublicSettingsWebController` —
single responsibility, предсказуемая инвалидация, ноль side-effect'ов между доменами.

## Constitution Check (NON-NEGOTIABLE принципы)

- **§ II Сырой JDBC + дифф по хэшам**: PASS. Никаких изменений в стеке доступа к БД — все правки
  внутри существующего `getProperty` через `WORKING_DATABASE.getConnection()`.
- **§ VI Code Standards**: PASS. KDoc 100% на новые методы/helper'ы (`getCachedProperty`,
  `markDirty`, `consumeDirty`, `isCacheEnabled`). Ссылки на parent спеку FR-106.
- **Git workflow**: PASS. Ветка `249-public-settings-cache`, PR через `gh pr create` →
  `gh pr checks` → merge.

## Project Structure

Изменения ТОЛЬКО в 1 файле + новый LiveDoc + 4 файла спеки.

```
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/
└── PublicSettingsWebController.kt   # +companion object (cache, dirty, helpers), KDoc на getProperty

livedocs/features/
└── 249-public-settings-cache.md    # NEW: per-feature документ (FR-009/FR-014)

specs/249-public-settings-cache/
├── spec.md                          # NEW
├── plan.md                          # NEW (этот файл)
├── tasks.md                         # NEW
└── checklists/requirements.md       # NEW
```

## Implementation Steps

### 1. `PublicSettingsWebController.kt` — добавить companion object + helper'ы

**Что добавить**:

```kotlin
companion object {
    private const val CACHE_TTL_MS = 60 * 1000L
    private const val KARAOKE_PROPERTY_CACHE_ENABLED = "karaoke.public.public-settings-cache.enabled"

    /** Маркер для «key не найден в БД» — отличаем от валидного value = "". */
    private val NOT_FOUND_SENTINEL = Any()

    private data class CachedProperty(
        val value: Any,         // String или NOT_FOUND_SENTINEL
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedProperty>()
    private val dirty = AtomicBoolean(false)

    /** Вызывается из setProperty при успехе (FR-009). */
    fun markDirty() = dirty.set(true)

    /** Вызывается из getCachedProperty (FR-004). */
    fun consumeDirty(): Boolean = dirty.getAndSet(false)

    private fun getCachedProperty(key: String, loadFn: () -> String): String {
        if (!isCacheEnabled()) return loadFn()

        try {
            if (consumeDirty()) {
                cache.clear()
            }
        } catch (_: Throwable) { /* defensive */ }

        val now = System.currentTimeMillis()
        val cached = cache[key]
        if (cached != null && cached.expiresAtMs > now) {
            return if (cached.value === NOT_FOUND_SENTINEL) "" else cached.value as String
        }

        val fresh = try {
            loadFn()
        } catch (e: Exception) {
            // FR-008: fail-open
            println("PublicSettingsWebController.getProperty error: ${e.message}")
            return ""
        }

        val valueForCache: Any = if (fresh.isEmpty()) NOT_FOUND_SENTINEL else fresh
        cache[key] = CachedProperty(valueForCache, now + CACHE_TTL_MS)
        return fresh
    }

    private fun isCacheEnabled(): Boolean =
        try {
            KaraokeProperties.getBoolean(KARAOKE_PROPERTY_CACHE_ENABLED)
        } catch (_: Throwable) {
            true
        }
}
```

**Что изменить в `getProperty`**:

```kotlin
@GetMapping("/getproperty")
@ResponseBody
fun getProperty(
    @RequestParam key: String,
): String = getCachedProperty(key) {
    val connection = WORKING_DATABASE.getConnection() ?: return@getCachedProperty ""
    connection.prepareStatement("SELECT value FROM tbl_public_settings WHERE key = ?").use { ps ->
        ps.setString(1, key)
        ps.executeQuery().use { rs ->
            if (rs.next()) rs.getString("value") ?: "" else ""
        }
    }
}
```

**Что добавить в `setProperty`** (после успешного UPDATE/INSERT, до `return true`):

```kotlin
// FR-009: инвалидация кеша для getProperty
markDirty()
```

### 2. `livedocs/features/249-public-settings-cache.md` — NEW

Per-feature документ (FR-009/FR-014). Содержит:
- Summary / Why (FR-106 parent спеки 241, Tier-2).
- Cache architecture (key, value, TTL, dirty).
- KDoc references на helper'ы.
- Cross-links: `../241-db-storage-perf-audit.md`, `../248-authors-tiles-cache.md`.

### 3. CI checks (последовательность по AGENTS.md § «Обязательная проверка после ЛЮБОГО изменения кода»)

```bash
./gradlew :karaoke-web:compileKotlin --parallel
./gradlew :karaoke-web:ktlintCheck
./gradlew :karaoke-web:bootJar --parallel
bash tools/check-kdoc-coverage.sh
pre-commit run --all-files
```

## Risks & Mitigations

| Риск | Митигация |
|------|-----------|
| `KaraokeProperties.getBoolean` может упасть (ранняя инициализация, файл недоступен) | `try/catch` в `isCacheEnabled()` → дефолт `true` (безопасный fallback) |
| Race condition: 2 одновременных запроса → двойной SELECT | Допустимо (UI не блокируется, последний writer выигрывает) — для админского UI при 1–5 RPS не критично |
| `setProperty` упал → cache не инвалидирован | `markDirty()` вызывается ТОЛЬКО при успехе (FR-009) — cache остаётся валидным (старое значение совпадает с реальным) |
| `NOT_FOUND_SENTINEL` путается с валидной пустой строкой | Отдельный `Any()`-объект, сравнение через `===` (referential equality) |
| TTL 60 сек слишком длинный для админа, ожидающего мгновенного эффекта | `markDirty()` в `setProperty` инвалидирует cache при изменении (FR-004) |

## Definition of Done

- [ ] `PublicSettingsWebController.kt` обновлён: companion object + helper'ы + KDoc 100%.
- [ ] `setProperty` вызывает `markDirty()` при успехе.
- [ ] `getProperty` обёрнут в `getCachedProperty`.
- [ ] LiveDoc создан в `livedocs/features/249-public-settings-cache.md` (FR-009).
- [ ] Все 5 спецификационных файлов созданы (spec/plan/tasks/checklist).
- [ ] ktlint PASS, KDoc coverage 100%, pre-commit PASS.
- [ ] PR создан, CI 7/7 PASS, merge в master.

## Next Steps

После мёрджа — обновить `specs/241-db-storage-perf-audit/tasks.md`:
- T012.2 → `[x] FR-106 реализован (PR #...)`.
- Обновить `livedocs/architecture-notes.md` §Pass 241 — отметить FR-106 как сделанный.
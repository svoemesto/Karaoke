# Research: Флаг «не снимать с эфира» (free_after_on_air)

> **Phase 0**: исследование технических деталей перед написанием кода.
> Разрешает 2 NEEDS CLARIFICATION из `plan.md`.

## Decision 1: где хранить поле

**Решение**: добавить колонку `free_after_on_air BOOLEAN NOT NULL DEFAULT false`
в таблицу `tbl_songs` (таблица с per-song метаданными — `free`, `exclusive`,
`tags`, `publish_date`, и т.п.; см. `Song.kt` companion и `SongStateResolver.kt`).

**Rationale**:

- `tbl_songs` — единственная таблица с per-song метаданными, которая
  синхронизируется между LOCAL и SERVER через `SyncRegistry.all` (Constitution
  III). Добавлять отдельную таблицу — лишний JOIN + отдельный sync.
- Поле `free` уже там — это естественный «сосед» для `free_after_on_air`.
- Поле сохраняется через тот же `KaraokeDbTable.save()` (reflection-diff),
  что и остальные — никакого нового механизма.

**Alternatives considered**:

- Хранить в `player_readiness_flags` (JSON-флаг, как `stemAccompanimentReady`).
  **Отклонено**: `player_readiness_flags` — для **read-only** инфры
  (`HealthReport`), не для бизнес-флагов редактора. Семантика другая.
- Хранить в отдельной таблице `tbl_song_publishing_flags`. **Отклонено**:
  лишний sync-target, лишний JOIN на каждый `Song` access (горячий путь).

## Decision 2: обновление recordhash-триггера

**Решение**: при добавлении колонки `free_after_on_air` в `tbl_songs` —
пересоздать оба триггера `update_tbl_songs_recordhash()` и
`update_tbl_songs_sync_recordhash()`, добавив `COALESCE(NEW.free_after_on_air::TEXT, 'false') ||`
в цепочку `md5(...)`.

**Rationale**:

- Constitution III: «При добавлении/изменении колонок таблицы, участвующей
  в sync, **обязательно** пересоздаётся `recordhash`-триггер для затронутых
  таблиц (LOCAL и PROD) — иначе md5 разойдётся и sync сломается».
- Текущие триггеры в `deploy/recordhash_settings.sql` и
  `deploy/recordhash_settings_sync.sql` уже включают `free` и `exclusive`
  — добавление `free_after_on_air` идёт по тому же паттерну.

**Alternatives considered**:

- Не обновлять триггер. **Отклонено**: прямое нарушение Constitution III +
  sync md5 разойдётся → «Синхронизация в 1 клик» сломается на любой песне
  с изменённым `free_after_on_air`.

## Decision 3: имя в Kotlin и в БД

**Решение**: в Kotlin — `freeAfterOnAir` (camelCase), в БД — `free_after_on_air`
(snake_case), в JS/JSON — `freeAfterOnAir` (camelCase, как и `free`).

**Rationale**:

- Соответствует существующему паттерну: `free` (Kotlin/JS) ↔ `free` (БД),
  `exclusive` (Kotlin) ↔ `exclusive` (БД). Для нового поля — тот же стиль.
- JS/JSON camelCase используется во всех существующих DTO (`SongDTO`,
  `song.free`, `song.dateTimePublish`).

**Alternatives considered**:

- `never_off_air` (английский эквивалент). **Отклонено**: пользователь
  явно предложил `free_after_on_air` в задаче #81 — это и есть
  канонический термин.

## Decision 4: реакция на установку флага до наступления эфира

**Решение**: флаг сохраняется в БД как обычно, но **не действует** до
наступления `dateTimePublish ≤ now()`. После эфира — `SongState.ON_AIR`
(приоритет над `TODAY`/`DONE`/`EXCLUSIVE`, но не над `free=true`).

**Rationale**:

- Clarification 2026-09-11 (Q2): «Только после эфира». Соответствует
  формулировке задачи #81 («после выхода в эфир песня остаётся в эфире
  навсегда»).
- До эфира песня и так premium-only (по `dateTimePublish` в будущем —
  см. `accessModeFor` в `knowledge/domains/publishing/components/dictionaries.md`).

## Decision 5: интеграция с `SongStateResolver.resolve()`

**Решение**: добавить ветку между `free=true → ON_AIR` и `dt == null → EXCLUSIVE`:

```kotlin
if (free) return SongState.ON_AIR
// NEW: если эфир уже наступил и флаг "не снимать" — считаем ON_AIR
if (freeAfterOnAir) {
    val dt = dateTimePublish ?: return SongState.EXCLUSIVE
    if (!dt.after(now)) return SongState.ON_AIR
}
val dt = dateTimePublish ?: return SongState.EXCLUSIVE
if (!dt.after(now) && isFreelyAvailableNowAt(dt, now, freeAccessWindowMonths)) {
    return SongState.ON_AIR
}
// ... остальная логика (TODAY / DONE)
```

**Rationale**: минимальное изменение, приоритеты сохраняются:

1. `idStatus < 6` → `IN_WORK` (как было);
2. `free=true` → `ON_AIR` (как было, имеет приоритет);
3. `freeAfterOnAir=true && onAir` → `ON_AIR` (новое правило);
4. (старая логика) стандартное окно эфира → `ON_AIR`;
5. `dt == null` → `EXCLUSIVE`;
6. today + dt > now → `TODAY`;
7. иначе → `DONE`.

## Decision 6: интеграция с публичным API (`accessModeFor`)

**Решение**: расширить `StatsService.accessModeFor(song, user)` (или
эквивалентный метод в publishing) — после проверки `isExclusive` добавить:

```kotlin
if (song.isExclusive) return AccessMode.PREMIUM_ONLY
if (song.freeAfterOnAir && song.dateTimePublish?.isAfter(Instant.now()) == false) {
    return AccessMode.OPEN
}
if (song.publishDate?.isAfter(Instant.now()) == true) {
    return AccessMode.PREMIUM_ONLY  // ещё не вышла в эфир
}
return AccessMode.OPEN
```

**Rationale**: минимум изменений в публичном пути; новая ветка имеет
приоритет над «`publishDate` в прошлом, но эфирный период истёк».

## Decision 7: UX в `SongEdit.vue`

**Решение**: добавить новый блок «label-and-input» ниже блока «Всегда
бесплатно (вечный эфир)» с тем же паттерном (две кнопки ДА/НЕТ) и
обработчиком `setFreeAfterOnAir(bool)`.

**Rationale**:

- Соответствует существующему UX (`setFree(true|false)` на строках
  2189–2204).
- Редактору проще — те же клики, тот же стиль.
- Поле сохраняется через тот же `SongService.save()` без изменений в API.

## Decision 8: per-feature документ

**Решение**: обновить (или создать) `docs/features/song-air-access.md` —
включить секцию «Не снимать с эфира: флаг `free_after_on_air`» с
описанием контракта, приоритетов и правил комбинации с `free`.

**Rationale**:

- FR-009 (Constitution VI): «При правке кода одной из 9 ключевых
  подсистем разработчик MUST в том же PR обновить соответствующий
  per-feature документ».
- Логика эфира уже частично описана в существующих документах
  (`specs/143-song-free-access-window/spec.md`), но не в виде
  per-feature документа.

## Open questions / deferred

Нет открытых NEEDS CLARIFICATION после Stage 2. Все решения
зафиксированы и подтверждены пользователем (clarification Q1: «независимы,
флаг хранится»; Q2: «только после эфира»).
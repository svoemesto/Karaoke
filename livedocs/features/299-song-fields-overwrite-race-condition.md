---
status: Active
slug: 299-song-fields-overwrite-race-condition
related:
  - ../../specs/299-song-fields-overwrite-race-condition/spec.md
  - ../../specs/281-find-lyrics-overwrites-key-bpm/spec.md
  - ../../specs/278-fix-key-loss-on-lyrics-search/spec.md
  - ../../docs/ops/log-correlation.md
---

# 299 — Перезатирание полей песни при фоновой обработке

> Drill-down — [specs/299-song-fields-overwrite-race-condition/spec.md](../../specs/299-song-fields-overwrite-race-condition/spec.md).
> Предыдущие итерации фикса той же проблемы: [specs/281-find-lyrics-overwrites-key-bpm/spec.md](../../specs/281-find-lyrics-overwrites-key-bpm/spec.md) (Pass 281, reload-from-db-before-save) и [specs/278-fix-key-loss-on-lyrics-search/spec.md](../../specs/278-fix-key-loss-on-lyrics-search/spec.md) (Pass 278, локальный фикс в `doCreateFromFolder`).
> Карта WARN-маркеров: [docs/ops/log-correlation.md](../../docs/ops/log-correlation.md#32-spring-boot-docker-logs-karaoke-web-или-karaoke-app).

## Что делает

Защищает ~31 «горячий» путь `Song.saveToDb()` от race condition, при которой параллельная транзакция (ручная правка через `SongEdit.vue`) успевает обновить поля песни между `loadFromDbById(...)` и `ps.executeUpdate(...)` фонового процесса (импорт папки, поиск текстов, демус и т.д.). Паттерн **Pass 281 `reload-from-db-before-save` не атомарен** — между reload и save остаётся окно гонки.

Решение: **pessimistic `SELECT ... FOR NO KEY UPDATE`** в явной JDBC-транзакции (`Connection.setAutoCommit(false)` + `SET LOCAL lock_timeout = '5s'` + reload + UPDATE + commit).

## Где в коде

### Новые методы (`Song.kt`)

| Метод | Сигнатура | Назначение |
|---|---|---|
| `Song.saveToDbLocked()` | `(): Boolean` | Обёртка над `saveToDb()` с блокировкой строки |
| `Song.Companion.loadFromDbByIdForUpdate(...)` | `(id, database, storageService, storageApiClient, connection): Song?` | Загружает песню под блокировкой на уже открытой транзакции |

### Новое поле `KaraokeProperties`

- `songSaveLockedTimeoutMs` (Long, default 5000) — таймаут для `SET LOCAL lock_timeout` в `saveToDbLocked()`.

### Защищённые hot paths (~31)

**Pass 281 hot paths (8 мест):**
- `UtilsAI.kt:applyFoundLyricsIfMissing` (FR-010)
- `Utils.kt:applyDuplicateOriginal` (Pass 278, FR-011)
- `Utils.kt:applyAudioParentMarkers` (Pass 278, FR-012)
- `Utils.kt:applyFamilySongSelection` (Pass 281, FR-013)
- `Utils.kt:autoAssignOriginalByWaveform` (Pass 281, FR-014)
- `Utils.kt:findAudioParentByWaveform` (Pass 281, все 4 reload'а, FR-015)
- `Song.kt:setSourceMarkers` (Pass 281, оба reload'а, FR-016)
- `Song.kt:setSourceText` (Pass 281, оба reload'а, FR-016)

**FR-020 hot paths (19+ мест, HTTP/рендер):**
- `services/TelegramAutoPublishService.kt`: 4 места
- `services/VkAutoPublishService.kt`: 6 мест
- `services/SongReleaseAnnouncementService.kt`: 2 места
- `services/PremiumAutoPublishScheduler.kt`: 2 места
- `KaraokeProcess.kt`: 5 мест (статусы рендера MP4)
- `Utils.kt`: 1 место (`fillFormattedFields` — добавлен KDoc-обоснование, не hot path)
- + остальные 15+ мест из FR-020 — KDoc-обоснование «объект живёт < 100мс, race не воспроизводится» (FR-021) — добавляется в code review.

## Как тестировать

[`contracts/manual-test-checklist.md`](../../specs/299-song-fields-overwrite-race-condition/contracts/manual-test-checklist.md) — 5 шагов на dev-машине:

1. **Шаг 1**: компиляция + ktlint.
2. **Шаг 2** (опц.): unit-проверка через мок (race-эмуляция).
3. **Шаг 3** (ОСНОВНОЙ): импорт папки + мгновенная правка `songName` через SongEdit + дождаться поиска текстов.
4. **Шаг 4**: SQL-проверка + grep `infra.prod.ping` на WARN-маркеры + SSE-проверка.
5. **Шаг 5** (при провале): rollback-план (`git revert` + incident-report).

## Что мониторить на проде

После деплоя 24-48 часов:

```bash
docker logs karaoke-app 2>&1 | grep -E 'WARN.*song\.(locked_save_fallback|locked_save_failed|lock_timeout|locked_save_skipped)' | head -50
```

Ожидаемые частоты (SC-007):
- `song.locked_save_fallback`: < 1 / час (норма при удалении песен во время фоновой обработки).
- `song.locked_save_failed`: 0 / час.
- `song.lock_timeout`: < 1 / час.
- `song.locked_save_skipped`: редко (только для readonly-песен).

Если `lock_timeout` > 1/час → рассматривать переход на optimistic-подход (FR-030-bis в spec.md, не реализовано).

## Архитектурная сводка

- **PostgreSQL lock semantics**: `FOR NO KEY UPDATE` блокирует `FOR UPDATE`/`DELETE`, но НЕ блокирует `FOR SHARE`/`FOR KEY SHARE`/простые `SELECT` (PG 9.3+, на проде 15+).
- **Транзакция per-row**: `lock_timeout = 5s` защищает от deadlock-зависаний; `connection.autoCommit` восстанавливается в `finally`.
- **Backward compat**: `Song.saveToDb()` НЕ изменяется (FR-003) — 70+ мест вызова продолжают работать в автокоммите без блокировки.
- **Constitution compliance**: §II «сырой JDBC» (никаких JPA/Hibernate/Spring-Tx), §III «SyncRegistry» (per-row lock не мешает sync, который читает после commit).

## История

- **Pass 278** (PR #??, 2026-08-??): первый фикс race condition в `doCreateFromFolder` через локальный reload-from-db-before-save. Защитил 1 путь (импорт из папки).
- **Pass 281** (PR #395, 2026-08-31): расширение фикса Pass 278 на 6 горячих путей (`applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`, `setSourceMarkers`, `setSourceText`). Паттерн тот же — reload-from-db-before-save. **НЕ атомарен** — между reload и save остаётся окно гонки.
- **Pass 299** (этот фикс, OpenProject WP #49): замена reload-from-db-before-save на `SELECT FOR NO KEY UPDATE` + `UPDATE` в одной транзакции. Покрывает 31+ место. Без миграции БД. Без изменения `Song.saveToDb()`.

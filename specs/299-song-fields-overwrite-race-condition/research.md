# Research: 299 — Перезатирание полей песни при фоновой обработке

> **Phase 0 output для `/speckit-plan`.** Резолвит все `NEEDS CLARIFICATION` из `plan.md` и подготавливает данные для `data-model.md` и `tasks.md`.
>
> **Дата:** 2026-09-03

## Резюме

Подтверждены все технические решения, выбранные на стадии `/speckit.clarify`:
1. **Pessimistic `SELECT FOR NO KEY UPDATE`** в JDBC-транзакции (см. Clarifications Q1).
2. **Fallback `?: song` + WARN** при null от `loadFromDbByIdForUpdate` (см. Clarifications Q2).
3. **Scope = все 25+ мест FR-020 в этом PR** (см. Clarifications Q3, Q5 — manual test checklist создан).
4. **`SET LOCAL lock_timeout = '5s'`** через `KaraokeProperties.songSaveLockedTimeoutMs` (см. Clarifications Q4).
5. **`Song.saveToDbLocked()`** — новый метод в `Song.kt`, без изменения существующего `Song.saveToDb()` (FR-003, обратная совместимость).

`KaraokeConnection` уже поддерживает нужный API (`getConnection(): Connection?`), `KaraokeProperties` уже поддерживает `getLong()` / `set()`. Никаких изменений в инфраструктурных слоях не требуется.

## R1. PostgreSQL `FOR NO KEY UPDATE` semantics

### Решение
Используем `SELECT ... FOR NO KEY UPDATE` вместо полного `SELECT ... FOR UPDATE`.

### Обоснование
- **Что блокирует `FOR NO KEY UPDATE`** (PostgreSQL 9.3+):
  - `FOR UPDATE` других транзакций
  - `DELETE` других транзакций
  - `FOR NO KEY UPDATE` других транзакций (только в PG 9.3+, в более поздних — не блокирует `FOR NO KEY UPDATE` параллельно)
- **Что НЕ блокирует**:
  - `FOR SHARE` (чтение с блокировкой от записи)
  - `FOR KEY SHARE` (чтение с блокировкой только PK)
  - Простые `SELECT` (без lock-клаузул)
  - `UPDATE` других колонок, **кроме** если они касаются FK (но `tbl_songs.id` — PK, мы его не меняем)
- **Подходит для нашего случая**:
  - `tbl_songs.id` — PK, никогда не меняется (см. Pass 281 KDoc, Constitution §II «сырой JDBC»)
  - Нам нужно блокировать `UPDATE` других транзакций на эту строку, не блокируя чтение
  - FK constraints есть (`album_id`, но песня — child, ссылается на `tbl_albums`): `FOR NO KEY UPDATE` совместим с FK-проверками в parent (см. PG docs)

### Альтернативы рассмотрены
- **`FOR UPDATE`** — слишком сильная блокировка, запрещает `FOR SHARE` чтения (например, репорты/health-check, которые читают `tbl_songs` под `FOR SHARE`).
- **`FOR SHARE`** — слишком слабая, блокирует только `FOR UPDATE`, но не блокирует другие `FOR SHARE` — не подходит для нашего use-case (нам нужно mutual exclusion).
- **`SELECT ... FOR KEY SHARE`** — самая слабая, блокирует только `FOR UPDATE`/DELETE; НЕ блокирует `FOR NO KEY UPDATE` от других потоков → не подходит (два `applyFoundLyricsIfMissing` от разных фонов могли бы одновременно обновить строку).

### Ссылки
- PostgreSQL 15+ docs: [Locking Clause](https://www.postgresql.org/docs/15/sql-select.html#SQL-FOR-UPDATE-SHARE) — раздел 13.3 «Explicit Locking».
- Karaoke prod: PostgreSQL 15+ (см. `deploy/karaoke-db/`, `AGENTS.md`).

## R2. `SET LOCAL lock_timeout` + `Connection.setAutoCommit(false)` взаимодействие

### Решение
```kotlin
connection.autoCommit = false
connection.createStatement().use { st ->
    st.execute("SET LOCAL lock_timeout = '${timeoutMs}ms'")
}
// затем loadFromDbByIdForUpdate + ps.executeUpdate + commit
```

### Обоснование
- `SET LOCAL` действует только внутри текущей транзакции — после `commit`/`rollback` сбрасывается автоматически.
- `lock_timeout` срабатывает на любые `SELECT FOR UPDATE`/`UPDATE`/`INSERT`/`DELETE`, которые ждут блокировки дольше указанного времени.
- При таймауте PostgreSQL бросает `PSQLException` с SQL state `55P03` (`canceling statement due to lock timeout`).
- При deadlock — `PSQLException` с SQL state `40P01` (`deadlock detected`).

### Альтернативы рассмотрены
- **ALTER DATABASE/ROLE** — глобально, влияет на все сессии (включая те, что НЕ должны иметь таймаут). Слишком грубо.
- **`Statement.setQueryTimeout(seconds)`** (JDBC API) — действует на конкретный `Statement`, но не ловит блокировку во время `SELECT FOR UPDATE` (lock acquisition происходит до statement execution). Не подходит.
- **Application-level timeout через `Future.get(timeout)`** — требует обёртки всех saveToDb в `ExecutorService.submit(...).get(timeout)`, значительно усложняет код.

### Best practices
- `SET LOCAL` — стандартный PG-механизм, без миграций, без ALTER.
- `lock_timeout = '5s'` — оптимальный баланс (типичная транзакция ~5мс; 5 сек достаточно для «долгих» deadlock-resolving, но не позволяет зависнуть).
- В тестах / dev-машине можно override через `KaraokeProperties.songSaveLockedTimeoutMs = 1000` для быстрого обнаружения проблем.

### Ссылки
- PostgreSQL docs: [SET LOCAL](https://www.postgresql.org/docs/15/sql-set.html), [lock_timeout](https://www.postgresql.org/docs/15/runtime-config-client.html#GUC-LOCK-TIMEOUT).
- JDBC docs: [Connection.setAutoCommit](https://docs.oracle.com/javase/8/docs/api/java/sql/Connection.html#setAutoCommit-boolean-).

## R3. `KaraokeConnection` API — транзакции

### Решение
Используем существующее `database.getConnection()` для получения JDBC `Connection` и ручное управление транзакциями через `setAutoCommit(false)` + `commit()` + `rollback()`.

### Обоснование
- `KaraokeConnection.getConnection()` (см. `KaraokeConnection.kt:54`) возвращает `java.sql.Connection?`. Уже используется в `Song.saveToDb()` (строка 5433 спеки 299, текущая строка `Song.kt:5433`).
- `KaraokeConnection` хранит **по одному физическому соединению на поток** (ThreadLocal) — спека 087. Это означает:
  - Транзакция, открытая в `saveToDbLocked`, держит блокировку **до `commit`/`rollback`** на том же потоке.
  - Другие потоки используют свои ThreadLocal-соединения → не блокируются.
  - Внутри одного потока (например, HTTP-request thread Tomcat) транзакция сериализует все операции с БД в этом потоке — это нормально, транзакция короткая (~5-10мс).
- Self-healing (спека 236): если ThreadLocal соединение закрыто/невалидно — пересоздаётся прозрачно.

### Альтернативы рассмотрены
- **Spring `@Transactional`** — Constitution §II запрещает (только raw JDBC). Не рассматривается.
- **Выделенный connection из pool (HikariCP)** — потребует изменений в `KaraokeConnection.getConnection()`, может сломать 174+ существующих вызывающих мест (см. KDoc `KaraokeConnection.kt:31`). Слишком инвазивно.
- **Дополнительный параметр `useLock` в `saveToDb`** (франкенштейн) — смешивает два режима в одном методе, сложнее тестировать. Против FR-003 (обратная совместимость).

### Caveats (подводные камни)
- **Если `commit` не вызван** (например, забыли в `try` или exception вылетел до `commit`) — `connection` остаётся в состоянии `autoCommit = false`. Следующий `getConnection()` на этом потоке вернёт то же соединение в состоянии «транзакция не завершена». PG будет накапливать изменения в pending-транзакции; новые `executeUpdate` будут в той же транзакции. **КРИТИЧНО**: код должен использовать `try/finally` с гарантированным `rollback` в catch + `autoCommit = true` в finally.
- **Если `autoCommit = false` уже выставлен** другим кодом (например, при вложенных вызовах) — `saveToDbLocked` ДОЛЖЕН запомнить предыдущее значение и восстановить его в `finally`.
- **`rollback()` после `commit()`** — безопасен (PG игнорирует), JDBC API это допускает.

## R4. Pass 281 — фактические места reload-from-db-before-save

### Решение
Pass 281 уже защитил 6 мест (см. спеку 281 FR-001, FR-011, FR-012, FR-013, FR-014). Эти места **остаются на месте** (FR-040) и **дополнительно** переводятся на `saveToDbLocked` (FR-010..FR-016):

| Спека 281 FR | Файл:строка | Что делает |
|---|---|---|
| FR-001 | `UtilsAI.kt:144` `applyFoundLyricsIfMissing` | Сохраняет найденный текст из поиска |
| FR-011 | `Utils.kt:4939` `applyFamilySongSelection` | Копирует данные «похожей версии» |
| FR-012 | `Utils.kt:5104` `autoAssignOriginalByWaveform` | Финальный `saveToDb` после автопривязки |
| FR-013 | `Utils.kt:5248` `findAudioParentByWaveform` | 4 reload'а перед `saveToDb` |
| FR-014 | `Song.kt:3626` `setSourceMarkers` | Сохранение маркеров (цикл по голосам) |
| FR-014 | `Song.kt:3690` `setSourceText` | Сохранение текста (цикл по голосам) |
| (Pass 278) | `Utils.kt:4847` `applyDuplicateOriginal` | Копирование оригинала при импорте |
| (Pass 278) | `Utils.kt:4897` `applyAudioParentMarkers` | Копирование маркеров аудио-родителя |

### Что конкретно нужно изменить в этих местах
Заменить существующий `loadFromDbById(...) ?: song` (старый pattern) на `saveToDbLocked()` (новый метод). Pass 281 KDoc-комментарии сохраняются как обоснование, но технически `loadFromDbById` уже не нужен — `saveToDbLocked` сам делает reload под блокировкой.

**Деталь для FR-011/FR-012/FR-013**: после `applyFamilySongSelection` / `autoAssignOriginalByWaveform` / `findAudioParentByWaveform` есть блок синхронизации `song.X = songToSave.X` (т.н. «sync», спека 279). С переходом на `saveToDbLocked` этот блок можно **упростить** — `songToSave` возвращается из `saveToDbLocked` (если нужно, изменить сигнатуру) и `song` синхронизируется один раз в конце. Альтернатива — оставить sync как есть для безопасности.

**Решение для Phase 1**: оставить существующий sync-блок (минимальные изменения, выше безопасность). `saveToDbLocked` возвращает `Boolean` (success/fail), а не `Song` (т.к. reload уже синхронизирован через `Song` reference, переданный в `loadFromDbByIdForUpdate`).

## R5. FR-020 — hot/not-hot analysis (25+ мест)

### Решение
Для каждого места из FR-020 определить: hot (объект живёт > 100мс, нужна блокировка) или not-hot (объект живёт < 100мс, можно оставить `saveToDb` без lock + KDoc-обоснование).

### Методология
- **Hot path** = между `loadFromDbById(...)` и `saveToDb()` есть I/O-bound операция (HTTP, ffmpeg, ML), которая занимает > 100мс.
- **Not-hot** = объект `song` загружается и сохраняется в одном коротком методе, без значимых I/O между ними.

### Per-place analysis (preliminary)

| Файл:строка | Контекст | Hot? | Решение |
|---|---|---|---|
| `Utils.kt:666` | TBD — нужна проверка | TBD | TBD в tasks.md |
| `Utils.kt:1594, 1624, 1705, 1734, 1737` | `doCreateFromFolder` или `findParentAndAudioParentForAll` | **Likely hot** (импорт папки — десятки секунд ffmpeg/ML) | `saveToDbLocked` + KDoc |
| `Utils.kt:4141, 4201` | Поиск родителей / фоновые задачи | **Likely hot** | `saveToDbLocked` |
| `Utils.kt:4654` | `newSong.saveToDb()` в `doCreateFromFolder` сразу после `createFromPath()` | **Not-hot** (< 100мс, createFromPath синхронный) | KDoc: «сразу после createFromPath, race не воспроизводится, см. FR-021» |
| `KaraokeProcess.kt:408, 415, 422, 429, 436` | `createKaraoke()` — рендер MLT/MP4 (минуты) | **HOT** (минуты на песню) | `saveToDbLocked` |
| `Song.kt:455, 738, 776` | TBD — внутренние `saveToDb()` | TBD | TBD в tasks.md |
| `Song.kt:5951, 6577` | TBD | TBD | TBD в tasks.md |
| `Song.kt:8186, 8357, 8374, 8544` | TBD (вероятно `markers/result/formatted` обновления) | **Likely hot** (рендер длится минуты) | `saveToDbLocked` |
| `ApiController.kt:883, 904, 7014, 7043, 7099, 7125, 7907, 7912` | Endpoints с HTTP-вызовами или MLT-запуском | **Mixed** — некоторые hot, некоторые not | TBD в tasks.md |
| `MainController.kt:1631, 1804, 1993` | Endpoints | **Mixed** | TBD в tasks.md |
| `services/TelegramAutoPublishService.kt:257, 293, 310, 327` | HTTP к Telegram Bot API (10-60 сек) | **HOT** | `saveToDbLocked` |
| `services/VkAutoPublishService.kt:252, 358, 372, 468, 480, 535` | HTTP к VK API (10-60 сек) | **HOT** | `saveToDbLocked` |
| `services/SongReleaseAnnouncementService.kt:290, 437` | Новости + премиум-публикация | **Likely hot** | `saveToDbLocked` |
| `services/PremiumAutoPublishScheduler.kt:289, 311` | Премиум-публикация (HTTP) | **HOT** | `saveToDbLocked` |

### Что нужно для финального вердикта
Полный анализ каждого места в `tasks.md` Phase 1.5 (Read-Only inspection). На этой стадии достаточно conservative verdict: **если есть I/O или цикл > 100мс → hot → `saveToDbLocked`**.

## R6. Lock contention benchmarks (preliminary, без бенчмарка на проде)

### Решение
Полагаемся на **теоретический** анализ: на проде 18k+ песен, типичный сценарий — 1-2 потока на песню (1 ручная правка + 1 фоновый процесс). `FOR NO KEY UPDATE` на уровне строки не блокирует другие песни.

### Обоснование
- **Per-row lock** в PG: блокирует только конкретный rowid, не всю таблицу.
- **`FOR NO KEY UPDATE`** совместим с `FOR SHARE` / `FOR KEY SHARE` / `SELECT` без lock → не блокирует чтение других потоков (репорты, health-check).
- **lock_timeout = 5s** — если что-то пошло не так (deadlock), транзакция прервётся через 5 сек, а не зависнет на час.
- **Типичное окно блокировки**: 5-10мс (1 round-trip SELECT + 1 round-trip UPDATE + commit) — за это время пользователь через UI точно не успеет сделать save (минимум 100мс RTT + UI render).

### Что мониторить на проде (после деплоя)
- **WARN `song.locked_save_fallback`** в `infra.prod.ping` логе (см. `docs/ops/log-correlation.md`) — НЕ должно быть (признак удаления песни во время фоновой обработки).
- **WARN `song.locked_save_failed`** — НЕ должно быть (признак SQL-ошибки, lock timeout, deadlock).
- **WARN `song.lock_timeout`** — допустимо < 1 раза в час (SC-007).
- **ERROR** с trace от `Song.saveToDbLocked` — НЕ должно быть.

Если lock-wait начнёт срабатывать > 1 раза в час → пересмотреть подход (FR-030-bis: optimistic lock через колонку `version`).

## R7. Backward compatibility — `Song.saveToDb()` остаётся без изменений

### Решение
`Song.saveToDb()` (FR-030: «поведение НЕ изменяется, Pass 278 FR-004») остаётся как есть. Существующие 70+ мест вызова продолжают работать в автокоммите без блокировки. Только hot paths из FR-010..FR-016 и FR-020 явно вызывают `saveToDbLocked()`.

### Обоснование
- 70+ мест вызова — слишком много для одновременной миграции; риск регрессий высок.
- Not-hot места (объект живёт < 100мс) реально НЕ имеют race condition — зачем тратить ресурсы на lock?
- Hot paths из Pass 281 уже выделены в спецификации (FR-010..FR-016) — миграция точечная.
- FR-020 даёт явный список из ~25 мест; для каждого — либо lock, либо KDoc-обоснование.

## Summary таблица решений

| # | Решение | Обоснование | Альтернативы |
|---|---------|-------------|--------------|
| R1 | `FOR NO KEY UPDATE` | PK не меняется, не блокирует чтение | `FOR UPDATE` (слишком сильно), `FOR SHARE` (слишком слабо) |
| R2 | `SET LOCAL lock_timeout = '5s'` | Per-transaction, не ALTER | `Statement.setQueryTimeout` (не ловит SELECT FOR UPDATE lock) |
| R3 | `database.getConnection()` + manual autoCommit | Уже используется, не ломает API | Spring `@Transactional` (запрещено), HikariCP direct (инвазивно) |
| R4 | Заменить Pass 281 reload на `saveToDbLocked` | Совместимо, добавляет блокировку | Оставить reload (race не атомарен) |
| R5 | Hot/not-hot по принципу «I/O или цикл > 100мс» | Conservative, низкий риск пропустить hot | Только Pass 281 hot paths (не выполняет FR-020) |
| R6 | Per-row lock, lock_timeout=5s, мониторинг WARN | Теоретический анализ + WARN метрика | Бенчмарк перед merge (требует прод-окружения) |
| R7 | `saveToDb()` без изменений, новый `saveToDbLocked()` | Backward compat, точечная миграция | Изменить `saveToDb()` под оба режима (сложнее тестировать) |

## Phase 1 input (для data-model.md)

Из research.md следующие данные нужны в `data-model.md`:
1. Сигнатура `Song.saveToDbLocked(): Boolean` — обёртка над `saveToDb()` с блокировкой.
2. Сигнатура `Song.loadFromDbByIdForUpdate(id, database, storageService, storageApiClient, connection): Song?` — НЕ открывает транзакцию, требует уже открытую.
3. Новое поле `KaraokeProperties.songSaveLockedTimeoutMs: Long = 5000`.
4. Новые log-маркеры: `song.locked_save_fallback`, `song.locked_save_failed`, `song.lock_timeout` (для `docs/ops/log-correlation.md`).

## См. также

- [`spec.md`](./spec.md) — спецификация (FR-001..FR-060).
- [`plan.md`](./plan.md) — Implementation Plan (Constitution Check, Project Structure).
- [`../../281-find-lyrics-overwrites-key-bpm/spec.md`](../../281-find-lyrics-overwrites-key-bpm/spec.md) — Pass 281.
- [`../specs/087-fix-shared-db-connection`](../../087-fix-shared-db-connection/) — ThreadLocal connection model.
- [`../specs/236-fix-karaoke-connection-self-healing`](../../236-fix-karaoke-connection-self-healing/) — self-healing.
- [`../../../docs/ops/log-correlation.md`](../../../docs/ops/log-correlation.md) — log markers.
- [`../../../.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) — Constitution (8 principles).

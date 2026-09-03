# Implementation Plan: 299 — Перезатирание полей песни при фоновой обработке

**Branch**: `299-song-fields-overwrite-race-condition` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/299-song-fields-overwrite-race-condition/spec.md`

## Summary

Защитить 25+ «горячих» мест `Song.saveToDb()` от race condition, при которой параллельная транзакция (ручная правка через `SongEdit.vue`) успевает обновить поля песни между `loadFromDbById(...)` и `ps.executeUpdate(...)` фонового процесса (импорт папки, поиск текстов, демус и т.д.). Паттерн **Pass 281 `reload-from-db-before-save` не атомарен** (между reload и save остаётся окно гонки). Решение: **pessimistic `SELECT FOR NO KEY UPDATE`** в явной JDBC-транзакции (см. [Clarifications Session 2026-09-03, Q1](spec.md#clarifications)).

Покрытие: 6 функций из Pass 281 (`applyFoundLyricsIfMissing`, `applyDuplicateOriginal`, `applyAudioParentMarkers`, `applyFamilySongSelection`, `autoAssignOriginalByWaveform`, `findAudioParentByWaveform`, `Song.setSourceMarkers`, `Song.setSourceText`) + 25+ новых мест из FR-020 (`Utils.kt`, `KaraokeProcess.kt`, `Song.kt`, `ApiController.kt`, `MainController.kt`, `TelegramAutoPublishService.kt`, `VkAutoPublishService.kt`). Каждое место получает либо новый метод `saveToDbLocked()`, либо явное KDoc-обоснование «объект живёт < 100мс, race не воспроизводится».

Механизм:
1. Новый метод `Song.loadFromDbByIdForUpdate(id, database, storageService, storageApiClient, connection)` — НЕ открывает свою транзакцию (получает её из `saveToDbLocked`), НЕ делает свой autoCommit-flip.
2. Новый метод `Song.saveToDbLocked()` — обёртка над `saveToDb()` с блокировкой: открывает транзакцию → `SET LOCAL lock_timeout = '5s'` → `loadFromDbByIdForUpdate` → `getDiff(this, savedSong)` → `ps.executeUpdate` → `commit`. При `null` от `loadFromDbByIdForUpdate` (песня удалена) — fallback на `saveToDb()` без блокировки + WARN.
3. PASS 281 паттерн `reload-from-db-before-save` сохраняется как страховка поверх `FOR NO KEY UPDATE` (FR-040).
4. Existing `Song.saveToDb()` НЕ меняется (FR-003) — обратная совместимость с 70+ мест вызова.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17 (см. `gradle/libs.versions.toml`, `AGENTS.md` — рабочая версия).

**Primary Dependencies**:
- Spring Boot 3.x (DI, нет Spring-Tx — manual transaction management).
- PostgreSQL JDBC driver (`org.postgresql:postgresql`) — поддержка `FOR NO KEY UPDATE` (PostgreSQL 9.3+, в проде 15+).
- НЕ используется: JPA/Hibernate, Spring `@Transactional` (запрещены Constitution §II).
- Существующий `KaraokeConnection` (`Karaoke.kt`) — обёртка над JDBC `Connection`.

**Storage**: PostgreSQL `tbl_songs` (raw JDBC, 18k+ записей на проде). Без миграции схемы — `FOR NO KEY UPDATE` не требует изменений таблиц.

**Testing**: 
- Нет автотестов для `Song.saveToDb()` в проекте (`karaoke-app/src/test` — `@Disabled`, см. Constitution §II).
- Manual-test checklist: [`contracts/manual-test-checklist.md`](contracts/manual-test-checklist.md) — 5 шагов на dev-машине.

**Target Platform**: Linux server (prod `<PROD_SERVER_IP>`), dev-machine `nsa-i9` под `nsa` (см. AGENTS.md «Машинно-специфичные исключения (Pass 282)» — разрешено пересобирать `karaoke-app` без согласия). Backend Karaoke runs in `eclipse-temurin:22-jre-jammy` Docker-контейнере.

**Project Type**: backend library + Spring Boot app (Kotlin multi-module Gradle: `karaoke-app`, `karaoke-web`, `karaoke-db`).

**Performance Goals**: 
- `saveToDbLocked()` overhead: < 10мс на горячий путь (1 round-trip `loadFromDbByIdForUpdate` + 1 round-trip `ps.executeUpdate` + commit). Сейчас `saveToDb()` — ~5мс (без lock), +5мс на `FOR NO KEY UPDATE` = ~10мс.
- Lock contention на одну песню: 1-2 потока (ручная правка + 1 фон), конкуренция редкая.
- Global throughput: 100 песен × ~10сек поиска текстов = 1000 сек = 17 мин. Lock-wait < 5мс на песню не блокирует другие песни.

**Constraints**: 
- НЕ используем JPA/Hibernate (Constitution §II «сырой JDBC»).
- НЕ используем Spring-Tx / `@Transactional` (нет DI-контейнера для `Song`, manual `Connection.setAutoCommit(false)`).
- НЕ меняем схему БД (никаких миграций).
- `lock_timeout = '5s'` через `SET LOCAL` (см. Clarifications Q4) — не ALTER DATABASE/ROLE.
- KDoc coverage ≥ 50% (CI gate, FR-041, FR-021).

**Scale/Scope**: 
- `tbl_songs` — 18k+ записей на проде.
- 70+ мест вызова `Song.saveToDb()` в проекте.
- 6 hot paths из Pass 281 + 25+ мест из FR-020 = 31+ мест для перевода на `saveToDbLocked()` или обоснования «не горячее».
- Hot paths вызовов (типичные сценарии):
  - Импорт папки: 100 песен × ~10-60 сек каждая = 17-100 мин на импорт.
  - Поиск текстов для всех песен: аналогично.
  - Рендер MP4: ~5 мин на песню, параллельно — обновление `result_text`/`formatted_text_*` через `setSourceMarkers`/`setSourceText`.
  - Публикация на площадки: 10-60 сек HTTP-вызов к VK/Telegram API.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|---|---|---|
| **§I. Self-contained автопайплайн** | ✅ Pass | Не добавляем внешних зависимостей (только JDBC API). |
| **§II. Сырой JDBC + дифф по хэшам** | ✅ Pass | `Connection.setAutoCommit(false)` + `SELECT FOR NO KEY UPDATE` + `commit/rollback` — нативный JDBC API, без JPA/Hibernate. Diff по recordhash не затрагивается (обновляются только гонки в saveToDb). |
| **§III. Двух-БД синхронизация через SyncRegistry** | ✅ Pass | `tbl_songs` уже в SyncRegistry (`sync/SyncTarget.kt:262`), recordhash-триггер активен. Lock на уровне строки совместим с sync (sync читает после commit — увидит актуальные данные). |
| **§IV. Async-очередь задач с парсингом stdout** | ✅ Pass | `redirectErrorStream(true)` соблюдается (Pass 281). Lock не блокирует парсинг stdout. |
| **§V. Двух-фронтенд** | ✅ Pass | Lock на backend, фронт (webvue3) не затрагивается. UI `SongEdit.vue` уже поддерживает параллельные правки через SSE. |
| **§VI. Code Standards (FR-006/007/009)** | ✅ Pass | KDoc coverage ≥ 50% — все новые методы (`Song.saveToDbLocked`, `Song.loadFromDbByIdForUpdate`) сопровождаются KDoc с `@see specs/299`. Lint: ktlint baseline не растёт (FR-041). Per-feature документ: обновляем `livedocs/features/299-...` если создаём новый (см. tasks.md Phase 5). |
| **§VII. Cross-Machine Setup** | ✅ Pass | Не меняем `AGENTS.md`/`CLAUDE.md`/`.gitignore`/`.pre-commit-config.yaml`. |
| **§VIII. Секреты и git-гигиена** | ✅ Pass | Не добавляем секретов. `KaraokeProperties.songSaveLockedTimeoutMs` — настройка без секретов. |

**Verdict**: ✅ Constitution Check passes. Никаких нарушений не требует обоснования в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/299-song-fields-overwrite-race-condition/
├── plan.md                                  # Этот файл
├── research.md                              # Phase 0 output (см. ниже)
├── data-model.md                            # Phase 1 output (см. ниже)
├── quickstart.md                            # Phase 1 output (см. ниже)
├── contracts/
│   └── manual-test-checklist.md             # Уже создан в clarify стадии (5 шагов)
└── tasks.md                                 # Phase 2 output (после /speckit.tasks)
```

### Source Code (repository root)

Изменения локализованы в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`:

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                              # +Song.saveToDbLocked(), +Song.loadFromDbByIdForUpdate()
│   └── KaraokeDbTable.kt                    # Без изменений (recordhash не затрагивается)
├── controllers/
│   ├── ApiController.kt                     # 6-8 мест saveToDb() → saveToDbLocked() (FR-020)
│   ├── MainController.kt                    # 2-3 места saveToDb() → saveToDbLocked() (FR-020)
│   ├── SongEditorController.kt              # Без изменений (saveToDb вызывается из коротких эндпоинтов, <100мс)
│   ├── HealthReport.kt                      # Без изменений (периодический, объект свежий)
│   └── PromoController.kt                   # Без изменений
├── services/
│   ├── TelegramAutoPublishService.kt        # 4 места saveToDb() → saveToDbLocked() (FR-020)
│   ├── VkAutoPublishService.kt              # 5 мест saveToDb() → saveToDbLocked() (FR-020)
│   ├── SongReleaseAnnouncementService.kt    # 2 места saveToDb() → saveToDbLocked() (FR-020)
│   └── PremiumAutoPublishScheduler.kt       # 2 места saveToDb() → saveToDbLocked() (FR-020)
├── Utils.kt                                 # 8-10 мест: applyDuplicateOriginal, applyAudioParentMarkers,
│                                            #   applyFamilySongSelection, autoAssignOriginalByWaveform,
│                                            #   findAudioParentByWaveform, doCreateFromFolder, +
│                                            #   другие из FR-020
├── UtilsAI.kt                               # 1 место: applyFoundLyricsIfMissing (FR-010)
├── KaraokeProcess.kt                        # 5 мест saveToDb() → saveToDbLocked() (FR-020)
└── KaraokeProcessWorker.kt                  # Без изменений (applyFoundLyricsIfMissing уже под защитой)

KaraokeProperties.kt                         # +1 поле songSaveLockedTimeoutMs (default 5000)
```

**Structure Decision**: Модуль `karaoke-app` — монолитный backend на Kotlin/Spring Boot. Изменения локализованы в `model/Song.kt` (новые методы), `Utils.kt`/`UtilsAI.kt` (горячие пути Pass 281), `controllers/`, `services/`, `KaraokeProcess.kt` (FR-020), и `KaraokeProperties.kt` (lock_timeout конфиг). Без новых модулей, без миграций БД, без новых зависимостей.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution — таблица пустая.

## Phase 0: Research (см. [research.md](./research.md))

Phase 0 резолвит все `NEEDS CLARIFICATION` из Technical Context. Поскольку на стадии `/speckit.clarify` 5 вопросов уже заданы и resolved, Phase 0 фокусируется на:
1. **PostgreSQL `FOR NO KEY UPDATE`** семантика (что блокирует, что нет, deadlock detection).
2. **`SET LOCAL lock_timeout`** взаимодействие с JDBC `Connection.setAutoCommit(false)`.
3. **Существующий `KaraokeConnection`** API — поддерживает ли он `getConnection()`, можно ли управлять транзакциями через него.
4. **Pass 281 reload-from-db-before-save** — какие конкретно места Pass 281 покрыл (FR-001..FR-014 спеки 281), чтобы не дублировать.
5. **25+ мест из FR-020** — какие из них реально hot (объект живёт > 1 сек), какие — нет (объект живёт < 100мс, race не воспроизводится).

## Phase 1: Design (см. [data-model.md](./data-model.md), [quickstart.md](./quickstart.md))

Phase 1 дизайнит:
1. **`Song.saveToDbLocked()`** — паттерн try/finally с autoCommit flip + lock_timeout + load + diff + update + commit.
2. **`Song.loadFromDbByIdForUpdate(connection, ...)`** — принимает уже открытую транзакцию, делает `SELECT ... FOR NO KEY UPDATE`, возвращает `Song?`.
3. **`KaraokeProperties.songSaveLockedTimeoutMs`** — новое поле, default 5000.
4. **25+ мест в FR-020** — для каждого место ресёрч (Phase 0) даёт вердикт «hot» или «not-hot». Hot → `saveToDbLocked()`. Not-hot → KDoc-обоснование.

## Phase 2: Tasks (см. [tasks.md](./tasks.md), создаётся в `/speckit.tasks`)

Phase 2 декомпозирует реализацию на конкретные задачи с зависимостями и DoD. Будет создана на следующей стадии.

## См. также

- [`spec.md`](./spec.md) — основная спецификация (US1-US4, FR-001..FR-060, SC-001..SC-008, Clarifications).
- [`research.md`](./research.md) — Phase 0 research output (PostgreSQL FOR NO KEY UPDATE семантика, `KaraokeConnection` API, FR-020 ресёрч).
- [`data-model.md`](./data-model.md) — Phase 1 data-model (новые методы `Song.saveToDbLocked`/`loadFromDbByIdForUpdate`, поле `songSaveLockedTimeoutMs`).
- [`quickstart.md`](./quickstart.md) — Phase 1 quickstart (dev-машина запуск + smoke test).
- [`contracts/manual-test-checklist.md`](./contracts/manual-test-checklist.md) — manual test checklist (5 шагов).
- [`../../281-find-lyrics-overwrites-key-bpm/spec.md`](../../281-find-lyrics-overwrites-key-bpm/spec.md) — Pass 281 спека (на которой основан этот).
- [`../../../.specify/memory/constitution.md`](../../../.specify/memory/constitution.md) — Constitution (8 Core Principles).
- [`../../../docs/ops/log-correlation.md`](../../../docs/ops/log-correlation.md) — карта логов прода.

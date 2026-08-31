# Implementation Plan: Кэш счётчиков песен автора в `tbl_authors`

**Branch**: `286-author-song-counts-cache` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/home/nsa/Karaoke/specs/286-author-song-counts-cache/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Добавить в `tbl_authors` две денормализованные колонки — `ready_songs_count` и `total_songs_count` — которые обновляются атомарно DB-триггером на `tbl_songs` (INSERT/UPDATE/DELETE) на LOCAL-БД. Эндпоинт `/api/public/authors-tiles` начинает читать счётчики напрямую из `tbl_authors` (одна точечная выборка) вместо GROUP BY по `tbl_songs`. Sync LOCAL → SERVER прокатывает счётчики и `recordhash` на SERVER-БД; L2-кеш `authorsTilesCache` сбрасывается через существующий `consumeDirty()` + новый вызов `notifyStatsDirty()` при изменении `id_status` песни.

**Primary research finding**: `tbl_songs` живёт ТОЛЬКО на LOCAL-БД (нет `recordhash_songs.sql`, нет `SongSyncTarget` в `SyncRegistry.all`); триггер нужен только на LOCAL. На SERVER колонки `ready_songs_count`/`total_songs_count` появляются через миграцию + backfill + sync.

## Technical Context

**Language/Version**: Kotlin 1.x + Spring Boot + JDK (см. `.specify/memory/constitution.md` раздел «Технологический стек»)
**Primary Dependencies**: PostgreSQL (сырой JDBC через `KaraokeConnection`), Spring Boot, нет JPA/Hibernate (Constitution Principle II)
**Storage**: PostgreSQL — таблицы `tbl_authors` (LOCAL+SERVER, sync через `AuthorsSyncTarget`), `tbl_songs` (только LOCAL)
**Testing**: ручное (Constitution — в CI тестов нет, существующие помечены `@Disabled`); проверка через `quickstart.md`
**Target Platform**: Linux server (karaoke-app на admin-машине, karaoke-web на проде; см. AGENTS.md «Технологический стек» → «Runtime»)
**Project Type**: Web application — backend (Kotlin/JVM), frontend (Vue 3 + Vite, в данной фиче не затрагивается)
**Performance Goals**: убрать GROUP BY по `tbl_songs` с горячего пути `/zakroma`; ожидаемая латентность cold-cache для `/api/public/authors-tiles` < 100 мс для 100 авторов (SC-003)
**Constraints**: Конституция запрещает JPA/Hibernate, требует сырой JDBC + sync через `SyncRegistry`, фиксирует стек образов (JRE не JDK, не `alpine`)
**Scale/Scope**: ~18k+ песен на проде, ~100 авторов (оценка по спекам 248/270/282); одна точечная выборка из `tbl_authors` масштабируется линейно по числу авторов

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Проверка | Статус |
|---------|----------|--------|
| **I. Self-contained автопайплайн** | Фича не затрагивает медиа-пайплайн, только DB + API. | ✅ PASS |
| **II. Сырой JDBC + дифф по хэшам** | Новые колонки читаются/пишутся через сырой JDBC (`Statement.executeQuery` / `executeUpdate`). `recordhash` для `tbl_authors` пересоздаётся. | ✅ PASS |
| **III. Двух-БД синхронизация через SyncRegistry** | `AuthorsSyncTarget` уже зарегистрирован. `sync_authors_*_update_allowed` остаются `true`. `recordhash` пересоздаётся с включением новых колонок. | ✅ PASS |
| **IV. Async-очередь задач** | Не затрагивается (фича не про процессы). | ✅ PASS |
| **V. Двух-фронтенд** | `/api/public/authors-tiles` уже существует; фронт не меняется (`AuthorTilePublicDto` без изменений). | ✅ PASS |
| **VI. Code Standards (FR-006/007/009)** | Новый `Author.loadAuthorTilesWithCounts` — публичный API, потребует KDoc с `@see docs/features/author-song-counts-cache.md`. Per-feature документ создаётся в том же PR. Линтеры пройдут через `./gradlew :karaoke-web:ktlintCheck` + baseline-проверки. | ⚠ TODO — добавить KDoc + per-feature документ |
| **VII. Cross-Machine Setup** | Не затрагивается (только код + миграция). | ✅ PASS |
| **VIII. Секреты и git-гигиена** | Никаких секретов в коде; `deploy/do.env` не трогается. | ✅ PASS |

**Gates result**: PASS после добавления KDoc и per-feature документа (это в `tasks.md`).

## Project Structure

### Documentation (this feature)

```text
specs/286-author-song-counts-cache/
├── plan.md              # This file
├── research.md          # Phase 0 output — done
├── data-model.md        # Phase 1 output — done
├── quickstart.md        # Phase 1 output — done
├── contracts/
│   └── authors-tiles-api.md  # Phase 1 output — done
├── checklists/
│   └── requirements.md  # Spec Quality Checklist — done (12/12 passing)
├── spec.md              # Updated with Clarifications (Q1/Q2/Q3 resolved)
└── tasks.md             # Phase 2 output — NOT created by /speckit.plan
```

### Source Code (repository root)

```text
deploy/
└── karaoke-db/
    └── 44_author_song_counts.sql    # NEW — миграция

docs/
└── features/
    └── author-song-counts-cache.md  # NEW — per-feature документ

karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    ├── controllers/
    │   └── ApiController.kt         # EDIT — добавить notifyStatsDirty() для id_status
    └── model/
        └── Author.kt                # EDIT — добавить AuthorTileRow + loadAuthorTilesWithCounts()

karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    └── controllers/
        └── PublicApiController.kt   # EDIT — заменить 3 SQL на 1 через Author.loadAuthorTilesWithCounts
```

**Structure Decision**: Backend-only изменение. Миграция + правки 3 Kotlin-файлов + новый per-feature документ. Фронтенд (`webvue3`, `karaoke-public`) не затрагивается — `AuthorTilePublicDto` остаётся без изменений, формат `/api/public/authors-tiles` сохраняется.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений Constitution. Все gates проходят (VI — после добавления KDoc/per-feature документа, что уже зафиксировано в tasks).

---

## Phase Artifacts

### Phase 0 — `research.md`

Подтверждено:
1. `tbl_songs` живёт только на LOCAL → триггер только на LOCAL; колонки + recordhash — на обеих БД.
2. Миграция в `deploy/karaoke-db/44_author_song_counts.sql` (по паттерну `27_author_special_order.sql`).
3. Существующий `consumeDirty()` + добавить `notifyStatsDirty()` при изменении `id_status`.
4. Новый `Author.loadAuthorTilesWithCounts()` — 1 SQL-запрос вместо 3 в `PublicApiController.authorsTiles()`.
5. Per-feature документ `docs/features/author-song-counts-cache.md` обязателен (Constitution VI FR-009).
6. Ручное тестирование по `quickstart.md`.
7. Миграция через psql без перезапуска контейнера (машинно-специфичное исключение `nsa-i9`/`nsa`).

### Phase 1 — `data-model.md` + `contracts/` + `quickstart.md`

- `data-model.md` — две новые колонки `tbl_authors`, триггер `trg_tbl_songs_update_author_counts` (PL/pgSQL скелет с 8 ветками для INSERT/UPDATE/DELETE × id-status/song-author), миграция с backfill, изменения в `Author.kt`, `PublicApiController.kt`, `ApiController.kt`.
- `contracts/authors-tiles-api.md` — HTTP API `/api/public/authors-tiles` без breaking changes; формат ответа идентичен.
- `quickstart.md` — пошаговое руководство для ручной валидации: миграция → триггер (8 тестов) → API (5 тестов) → sync (2 теста) → cleanup.

### Re-evaluation of Constitution Check post-Phase 1

| Принцип | После Phase 1 |
|---------|----------------|
| II. Сырой JDBC | ✅ Подтверждено: `Author.loadAuthorTilesWithCounts` использует `Statement.executeQuery`, не JPA. |
| III. Sync через SyncRegistry | ✅ `recordhash` для `tbl_authors` пересоздаётся в миграции. `AuthorsSyncTarget` уже зарегистрирован. |
| VI. Code Standards | ⚠ KDoc + per-feature документ запланированы в `tasks.md` (Phase 2). |
| Остальные | ✅ Без изменений. |

**Все gates по-прежнему PASS. Готово к Phase 2 (`/speckit.tasks`).**
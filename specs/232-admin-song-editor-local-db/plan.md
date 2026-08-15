# Implementation Plan: Облегчённый редактор песен в админке → локальная БД admin-машины

**Branch**: `232-admin-song-editor-local-db` | **Date**: 2026-08-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/232-admin-song-editor-local-db/spec.md`

## Summary

Облегчённый редактор песен в админке (webvue3, `SongKaraokeEditorModal`, `mode='song'`) при сохранении пишет Song в серверную БД (`Connection.remote()` через `target='remote'`), а должен — в локальную БД admin-машины (`Connection.local()`). Sync LOCAL → SERVER — отдельная, явная операция пользователя, а не побочный эффект редактирования.

**Технический подход**: точечная правка runtime-логики выбора БД в **двух методах** `SongEditorController` (`editById` и `editSave`, ветка `mode='song'`): `WORKING_DATABASE` / `withDb(target)` → `Connection.local()` напрямую. Параметр `target` остаётся в сигнатуре для обратной совместимости, но игнорируется серверной стороной для `mode='song'`. `mode='assignment'` и все остальные эндпоинты — без изменений.

Без миграций SQL, без новых сущностей, без новых эндпоинтов. Объём правки — ~10–20 строк в одном файле.

## Technical Context

**Language/Version**: Kotlin 1.x, JDK 17, Spring Boot 2.x/3.x (см. `Constitution § Технологический стек`)

**Primary Dependencies**: Spring MVC (`@PostMapping`, `@RequestParam`), kotlinx-serialization (`SourceMarker.serializer()` уже импортирован в `SongEditorController.kt:31`). Никаких новых зависимостей.

**Storage**: PostgreSQL 16 (через сырой JDBC, `KaraokeConnection` + фабрики `Connection.local()/remote()`). Без изменений схемы, индексов, триггеров.

**Testing**: в CI нет автоматических тестов на эту логику (см. `Constitution § Рабочий процесс → Тесты`). Существующие тесты в `karaoke-app/src/test` помечены `@Disabled` или требуют сеть/credentials. Проверка — пользователем на admin-машине по `quickstart.md`.

**Target Platform**: Linux-сервер admin-машины (`karaoke-app` в Docker) + webvue3 в браузере админа. На прод-сервере поведение остаётся корректным (там `Connection.local()` = `karaoke-db:5432`, единственная локальная БД окружения).

**Project Type**: Backend (изменение в одном Spring-контроллере). Тонкая зависимость на фронтенд: `webvue3` шлёт `target=local|remote`, но сервер теперь игнорирует это для `mode='song'` — фронт править не нужно.

**Performance Goals**: не критично. Два параметра в SQL-запросе не меняются, две строки логики. Время отклика не ухудшается.

**Constraints**:
- Обратная совместимость HTTP-контракта (структура JSON-ответа, имена полей, HTTP-200 для всех ответов контроллера — см. `contracts/api-contracts.md`).
- Никаких новых SQL-миграций.
- Параметр `target` остаётся в сигнатуре (`@RequestParam(required = false)`), но его значение игнорируется для `mode='song'`.

**Scale/Scope**: 2 метода в 1 файле (`SongEditorController.kt`), ~10–20 строк кода, без миграций, без новых сущностей, без новых эндпоинтов. Тонкая зависимость на фронт: 0 строк изменений в `webvue3`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Прошёл в `research.md` (Phase 0) и подтверждён после дизайна (Phase 1). Сводка:

| Принцип | Статус | Обоснование |
|---------|--------|-------------|
| I. Self-contained автопайплайн | ✅ pass | Не затрагивает пайплайн рендера/обработки медиа |
| II. Сырой JDBC + дифф по хэшам | ✅ pass | Доступ к БД остаётся через `Connection.local()` (сырой JDBC). Sync по recordhash не трогается |
| III. Двух-БД синхронизация через SyncRegistry | ✅ pass | Не меняет SyncRegistry, не меняет флаги `sync_*` |
| IV. Async-очередь задач | ✅ pass | Не запускает ffmpeg/melt/Demucs; только чтение/запись Song |
| V. Двух-фронтенд: админка и публичный сайт | ✅ pass | Меняется бэкенд-эндпоинт, который дёргает только админка (webvue3). karaoke-public не задействована |
| VI. Code Standards | ✅ pass | KDoc с `@see` будет добавлен на новый LiveDoc. Линтеры ktlint/ESLint не должны зацепиться (минимальная правка существующих методов) |
| VII. Cross-Machine Setup | ✅ pass | Не затрагивает .git-blame-ignore-revs, .gitattributes, локальные AI-конфиги |
| VIII. Секреты и git-гигиена | ✅ pass | Не затрагивает секрет-файлы, env-переменные, credentials |

**Все Gates проходят**. Никаких нарушений Constitution нет, секция `Complexity Tracking` пуста.

## Project Structure

### Documentation (this feature)

```text
specs/232-admin-song-editor-local-db/
├── plan.md              # Этот файл (output /speckit.plan)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/           # Phase 1 output (/speckit.plan)
│   └── api-contracts.md
├── checklists/
│   └── requirements.md  # quality checklist
└── spec.md              # /speckit.specify output
```

### Source Code (repository root)

Фича затрагивает **только один файл** в backend:

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    └── controllers/
        └── SongEditorController.kt   # ← editById (732–830), editSave (837–901)

livedocs/                              # ← после реализации добавится LiveDoc (FR-014)
└── features/
    └── 232-admin-song-editor-local-db.md   # новый файл, cross-link на эту спеку
```

**Structure Decision**: backend-only. Webvue3 (фронт) не требует изменений — параметр `target` остаётся в API, но сервер его игнорирует для `mode='song'`. Это согласуется с минимальным, точечным характером фичи.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

Секция пуста — все Gates проходят, нарушений нет.

## Phase 0 (Research)

См. [`research.md`](./research.md). Все 3 [NEEDS CLARIFICATION] из спеки разрешены в секции `Clarifications` спеки до начала Phase 0.

## Phase 1 (Design & Contracts)

См.:
- [`data-model.md`](./data-model.md) — описание изменений в поведении (без изменений схемы).
- [`contracts/api-contracts.md`](./contracts/api-contracts.md) — фиксация контракта HTTP-эндпоинтов.
- [`quickstart.md`](./quickstart.md) — ручные сценарии проверки на admin-машине.

## Связь с другими фичами

- **Спека 163** (`163-fix-song-editor-regressions`) — фикс регрессий редакторов после внедрения спецтегов. Текущая фича — **не** повторение 163 и **не** её расширение, а отдельный баг про выбор БД (см. Assumptions в спеке).
- **Спека 182** (`182-editor-self-assign-tasks`) — вводит `editorAssignmentDefaultTarget` и `assignmentsTarget` (через webvue3 store). Текущая фича использует эти механизмы для диагностики, но не меняет их — только фиксирует, что для `mode='song'` Song всегда в LOCAL, независимо от `assignmentsTarget`.
- **LiveDoc `editorial`** (`livedocs/domain/editorial.md`) — описывает контекст заданий. Текущая фича не противоречит ему: задания остаются target-aware.

## Что нужно сделать после реализации (по FR-014)

При изменении bounded context / C4 уровня — в том же PR обновить LiveDoc. Эта фича **формально** не меняет `editorial` (BC остаётся target-aware), но добавляет явное правило «`mode='song'` всегда LOCAL». Поэтому рекомендуется в том же PR добавить LiveDoc `livedocs/features/232-admin-song-editor-local-db.md` с cross-link на:
- эту спеку (`specs/232-admin-song-editor-local-db/spec.md`);
- домен `editorial` (упоминание, что Song ≠ assignment по части target);
- архитектуру `dual-db-access.md` (явный контракт: `edit/{byId,save}` для `mode='song'` всегда LOCAL).

Это попадёт в `tasks.md` как отдельная задача на этапе `/speckit.tasks`.

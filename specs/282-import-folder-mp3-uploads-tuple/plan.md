# Implementation Plan: 282 — Кортеж заданий при «Добавить файлы из папки» (mp3 голоса/аккомпанимента → локальное + удалённое хранилище)

**Branch**: `282-import-folder-mp3-uploads-tuple` | **Date**: 2026-08-31 | **Spec**: [specs/282-import-folder-mp3-uploads-tuple/spec.md](spec.md)

**Input**: Feature specification from `/specs/282-import-folder-mp3-uploads-tuple/spec.md` (после `/speckit.clarify` 2026-08-31: Q1 → добавляем и `FF_MP3_*`, и `UPLOAD_*` в кортеж; Q2 → порядок «сначала все `FF_MP3_*`, потом все `UPLOAD_*`»).

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Расширить кортеж заданий в `Song.createFromPath()` (`karaoke-app/.../model/Song.kt:8072-8228`) шестью новыми вызовами `KaraokeProcess.createProcess(...)` после существующего `DEMUCS2`: `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE` (×2), `UPLOAD_TO_REMOTE_STORE` (×2). Все шесть — в одном `threadId = 1` (`THREAD_LANE_HEALTH_REPORT`) с приоритетами `-1` для создания и `-2` для загрузки. Существующий вызов `HealthReport.startRepairAll(newSong, ...)` в `ApiController.doCreateFromFolder` (`ApiController.kt:5511`) сохраняется как fallback; дедупликация в `KaraokeProcess.createProcess` (`KaraokeProcess.kt:1001-1007` для `UPLOAD_*`) предотвращает задвоение. Изменения локальны для одного метода, не требуют миграций БД, новых зависимостей или изменений фронтенда. Соответствует LiveDoc 082 «demucs → mp3 → upload в одном lane».

## Technical Context

**Language/Version**: Kotlin 1.x (проект использует Kotlin), JDK 17 (см. `constitution.md` § Технологический стек). Конкретная версия Kotlin — текущая в репозитории (`gradle/libs.versions.toml` / `build.gradle.kts`); никаких новых языковых фич не требуется.

**Primary Dependencies**: Spring Boot 2.x/3.x (для DI — `WORKING_DATABASE`, `storageService`, `storageApiClient`), существующие модули проекта:
- `com.svoemesto.karaokeapp.model.Song` (изменяется, метод `createFromPath`),
- `com.svoemesto.karaokeapp.KaraokeProcess` (`createProcess`, без изменений — переиспользуется),
- `com.svoemesto.karaokeapp.KaraokeProcessTypes` (enum — `DEMUCS2`, `FF_MP3_ACCOMPANIMENT`, `FF_MP3_VOCAL`, `UPLOAD_TO_LOCAL_STORE`, `UPLOAD_TO_REMOTE_STORE` уже определены),
- `com.svoemesto.karaokeapp.KaraokeStorageService` / `StorageApiClient` (используются только внутри `KaraokeProcess.createProcess` для UPLOAD — мы их не трогаем, передаём `bucketName`/`storageFileName`/`pathToFile` через `context`),
- `com.svoemesto.karaokeapp.HealthReport` (без изменений; сохраняется `startRepairAll` как fallback).

**Storage**: PostgreSQL (через сырой JDBC, `KaraokeConnection`/`WORKING_DATABASE` — никаких миграций); MinIO локальный (`storageService` / `KSS_APP`) + MinIO удалённый (`storageApiClient` / `SAC_APP`) с бакетом `karaoke` (см. `Song.kt:78`). Никаких новых таблиц/колонок/бакетов.

**Testing**: автоматические тесты в CI отсутствуют (`constitution.md` § «Тесты»); существующие тесты в `karaoke-app/src/test` — `@Disabled`. Проверка — пользователем на admin-машине через UI `/utils/createfromfolder` после сборки `bootJar`. Подробные сценарии — в `quickstart.md` (Phase 1).

**Target Platform**: Linux-сервер (admin-машина + прод-сервер — см. `constitution.md` § «Деплой-окружения»). Образ `eclipse-temurin:22-jre-jammy` для `karaoke-app`/`karaoke-web`. Изменение компилируется и упаковывается через `./gradlew :karaoke-app:bootJar` (см. `AGENTS.md` § «Сборка / деплой / тесты»).

**Project Type**: web-service с двумя SPA (admin `webvue3` + public `karaoke-public`) поверх Kotlin/Spring-Boot бэкенда. Фича затрагивает **только бэкенд** (`karaoke-app`), фронтенд не меняется.

**Performance Goals**:
- Импорт папки с N новыми песнями добавляет ровно `N × 7` записей в `tbl_processes` (1 × DEMUCS2 уже было + 6 новых) с `thread_id = 1`; параллельность по лейну сохраняется (как у `DEMUCS2` сегодня).
- Никакой дополнительной синхронной работы на HTTP-потоке `doCreateFromFolder` — все 6 вызовов — `doWait = true` (создают запись в `tbl_processes` со статусом `WAITING`, отрабатываются воркером асинхронно).
- LiveDoc 082 указывает на необходимость отработки папок из 1000+ файлов без OOM; это уже обеспечено спецификацией 082 (streaming подход в `createFromPath`) и не зависит от этой фичи.

**Constraints**:
- CPU ограничен тремя слоями (docker `--cpus`, `MLT_CPU_LIMIT`, `docker update`) — новые FF_MP3_* и UPLOAD_* не добавляют дополнительных слоёв, переиспользуют уже существующие правила (см. `Utils.kt:3596-3613` — FF_MP3_* явно в списке без-лимитного слоя 1; UPLOAD_* — через существующий `KaraokeProcess.createProcess`).
- Никаких новых зависимостей, env-переменных, миграций БД.
- `processBuilder.redirectErrorStream(true)` обязателен для всех KaraokeProcess — этот код уже соответствует.

**Scale/Scope**: типичная папка альбома — 5–20 треков; кортеж удлиняется на 6 заданий × N песен. На 18k+ песен проде это ~108k новых записей в `tbl_processes` для одной полной перепрокрутки — допустимо (таблица уже сейчас значительно больше).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип (Constitution v2.1.0) | Статус | Комментарий |
|---|-------------------------------|--------|-------------|
| I | Self-contained автопайплайн | ✅ PASS | Не вводим внешних SaaS; всё на существующих локальных компонентах (`DEMUCS2`, `FF_MP3_*` через `ffmpeg`, UPLOAD через MinIO локальный/удалённый). |
| II | Сырой JDBC + дифф по хэшам | ✅ PASS | Никаких изменений в доступе к БД; `KaraokeProcess.createProcess` уже использует сырой JDBC через `loadList`/`delete`/`saveToDb`. |
| III | Двух-БД синхронизация через SyncRegistry | ✅ PASS | `tbl_processes` уже участвует в sync (если участвует — нам не нужно добавлять/удалять sync-флаги для новых записей, т.к. мы только создаём новые записи с уже синхронизируемыми полями). Проверить `sync/SyncTarget.kt` — `TblProcesses` либо уже там, либо записи `tbl_processes` обрабатываются как append-only лог и не требуют отдельной регистрации. **Action item для реализации**: верифицировать наличие `tbl_processes` в `SyncRegistry.all`; если нет — НЕ добавлять в этой фиче (требует отдельного обсуждения, см. ADR backlog в Constitution v2.1.0 Sync Impact Report). |
| IV | Async-очередь задач с парсингом stdout | ✅ PASS | Используем существующий `KaraokeProcessWorker.run` + парсер stdout для ffmpeg-команд (см. `KaraokeProcess.kt:1692-1833`); `redirectErrorStream(true)` уже соблюдён в существующем коде для FF_MP3_*/UPLOAD_*. |
| V | Двух-фронтенд: админка и публичный — разные приложения | ✅ PASS | Фронтенд (`webvue3`, `karaoke-public`) не меняется; только `karaoke-app` бэкенд. |
| VI | Code Standards (FR-006 KDoc, FR-007 линтеры, FR-009 per-feature docs) | ✅ PASS | Изменения локальны для одного метода (`Song.createFromPath`); KDoc на этом методе **уже есть** (`Song.kt:8072-8071`) и обновляется в `## Что делает` секции по факту. Линтеры — ktlint + ESLint — должны быть прогнаны (см. `AGENTS.md` § «Обязательная проверка после ЛЮБОГО изменения кода»). Per-feature doc: LiveDoc `082-fix-import-folder-oom.md` уже описывает желаемое поведение кортежа — после реализации **обязательно** обновить его в том же PR (FR-014 Constitution Principle + AGENTS.md § «Обновление LiveDocs»). |
| VII | Cross-Machine Setup | ✅ PASS | Изменения не затрагивают локальные AI-конфиги, `.git-blame-ignore-revs`, `.gitattributes`, кросс-машин документацию. |
| VIII | Секреты и git-гигиена | ✅ PASS | Не вводим секреты, не трогаем `deploy/.env`/`do.env`/`.key`/`.pem`. Pre-commit проверка `git ls-files | grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$'` остаётся пустой. |

**Итог**: 8/8 PASS. Никаких нарушений, не требующих justification в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/282-import-folder-mp3-uploads-tuple/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/           # Phase 1 output (/speckit.plan)
│   └── process-context.md  # контракт context-map для новых KaraokeProcess.createProcess вызовов
├── checklists/
│   └── requirements.md  # (уже создан в /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся этим планом)
```

### Source Code (repository root)

```text
karaoke-app/
└── src/main/kotlin/com/svoemesto/karaokeapp/
    └── model/
        └── Song.kt                      ← ИЗМЕНЯЕТСЯ (метод createFromPath, ~строка 8072-8228)

# Остальные модули/файлы НЕ затрагиваются:
# - KaraokeProcess.kt — без изменений (createProcess переиспользуется)
# - KaraokeProcessTypes.kt — без изменений (все типы уже enum-значения)
# - HealthReport.kt — без изменений (startRepairAll сохраняется)
# - ApiController.kt — без изменений (doCreateFromFolder уже вызывает HealthReport.startRepairAll)
# - MainController.kt — без изменений (legacy-эндпоинт идёт через те же общие функции)
# - webvue3/**, karaoke-public/** — без изменений (UI не меняется)
```

**Structure Decision**: **Option 1 — single backend module** (только `karaoke-app`); фронтенд и другие модули — без изменений. Изменение локально в одном методе `Song.createFromPath()` (добавление 6 вызовов `KaraokeProcess.createProcess` после существующего `DEMUCS2`). Никаких новых файлов в исходниках. Документация обновляется в LiveDoc `livedocs/features/082-fix-import-folder-oom.md` (в том же PR).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Нет нарушений. Секция пустая.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

## Re-evaluation: Constitution Check after Phase 1 design

**Дата**: 2026-08-31

После генерации `research.md`, `data-model.md`, `contracts/process-context.md` и `quickstart.md` — переоценка Constitution Check:

| # | Принцип | Статус | Изменение относительно первичной оценки |
|---|---------|--------|------------------------------------------|
| I | Self-contained автопайплайн | ✅ PASS | Без изменений — design не вводит внешних зависимостей |
| II | Сырой JDBC + дифф по хэшам | ✅ PASS | Без изменений — design не трогает доступ к БД |
| III | Двух-БД синхронизация | ✅ PASS | R-005 в research.md зафиксировал: НЕ добавляем `tbl_processes` в `SyncRegistry.all` в этой фиче; только верифицируем существующее поведение. ADR backlog остаётся за пределами скоупа. |
| IV | Async-очередь задач | ✅ PASS | R-002 в research.md подтвердил: используем существующий `KaraokeProcessWorker`, приоритеты `-1`/`-2`, единый `threadId=1`. Никаких новых lanes. |
| V | Двух-фронтенд | ✅ PASS | Design явно не затрагивает `webvue3`/`karaoke-public` (см. plan.md Project Structure и data-model.md) |
| VI | Code Standards | ✅ PASS | R-004 в research.md: удалить закомментированные `FF_MP3_KAR`/`FF_MP3_LYR` как cleanup; R-006: обновить LiveDoc 082 в том же PR; KDoc на `Song.createFromPath` уже есть и будет обновлён. |
| VII | Cross-Machine Setup | ✅ PASS | Без изменений |
| VIII | Секреты и git-гигиена | ✅ PASS | Без изменений |

**Итог**: 8/8 PASS. Никаких новых нарушений, добавленных design-фазой. Фича готова к `/speckit.tasks`.

# Implementation Plan: 280 — AssignModal: фильтр по rootId и audioRootId

**Branch**: `280-assign-modal-root-audio-id` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/280-assign-modal-root-audio-id/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Расширение админ-модалки «Назначить песню на разметку» (`webvue3/src/components/SongEditor/AssignModal.vue`) — добавить в строку поиска кандидатов два новых числовых фильтра: `root ID` и `A-root ID` (соответствуют `root_id` / `audio_parent_id` в БД), которые комбинируются AND с уже существующими `Автор`, `Альбом`, `Название песни` и чекбоксом «Только кандидаты на разметку». Реализация только на фронте (`webvue3`): правки HTML-разметки `.se-search-row`, добавление локального `data()`-поля и прокидывание в store-action `searchCandidateSongs` параметров `filterRootId` / `filterAudioParentId` в HTTP-POST `/api/songsdigests`. Бэкенд (`ApiController.apisSongsDigests`) уже принимает эти параметры и маппит в SQL `filter_root_id` / `filter_audio_parent_id` (точное совпадение по `=`). DTO `SongDTOdigest` уже содержит `rootId` и `audioParentId` — изменений схемы БД, миграций и DTO не требуется.

## Technical Context

**Language/Version**: Vue 3 + Vite (JavaScript), Node 22 LTS, фронтенд-пакет `webvue3`. Бэкенд Kotlin/Spring Boot не затрагивается.

**Primary Dependencies**: Vue 3 (`v-model`, `computed`), Vuex 4 (action `searchCandidateSongs` в `webvue3/src/components/SongEditor/store.js`), `promisedXMLHttpRequest` (уже используется). Никаких новых npm-зависимостей.

**Storage**: N/A (фильтрация на стороне SQL — `Song.loadListFromDb` уже умеет `filter_root_id` / `filter_audio_parent_id`).

**Testing**: в проекте нет unit-тестов для webvue3 (как и для большинства фич согласно AGENTS.md / конституции § «Тесты»). Верификация — ручная пользователем в админке + чек-лист в `checklists/requirements.md` + линтеры (`webvue3/.eslint-baseline.json` через `./tools/check-eslint-baseline.sh <pkg>`).

**Target Platform**: web-браузер, Linux-окружение admin-машины. Vite-сборка в Docker-образ через `deploy/do.sh build_webvue3`.

**Project Type**: web (frontend-only правка в существующем multi-module проекте: `karaoke-app` + `karaoke-web` + `webvue3` + `karaoke-public`).

**Performance Goals**: действия пользователя — отклик UI <100мс (v-model — синхронный); HTTP-запрос `/api/songsdigests` <1с при текущих объёмах (≤N₁∩N₂∩… записей после AND-фильтрации). Никаких новых горячих путей, никаких debounce/throttle — поведение совпадает с существующим «нажал Найти → запрос».

**Constraints**:
- никаких изменений бэкенда, БД, DTO, миграций;
- никаких новых npm-зависимостей;
- ESLint baseline (`webvue3/.eslint-baseline.json`) — никаких НОВЫХ нарушений;
- JSDoc на изменяемых публичных API (action `searchCandidateSongs`, computed `canSubmit` — не меняется; data-поля — JSDoc опциональны);
- Docker-сборка образа `webvue3` должна проходить (`bash do.sh build_webvue3`).

**Scale/Scope**: 2 файла во фронте (`AssignModal.vue`, `store.js`); 1 добавленная ветка фильтрации; 2 новых input-поля; 0 изменений на бэкенде.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Justification |
|-----------|--------|---------------|
| **I. Self-contained автопайплайн** | ✅ Pass | фича только UI-фильтр, не затрагивает media-pipeline, ffmpeg/melt/Demucs/Sheetsage. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ Pass | фича не трогает БД-доступ и sync-сравнения. |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ Pass | `Song` уже в sync, но мы не меняем схему и не добавляем сущности. |
| **IV. Async-очередь задач с парсингом stdout** | ✅ Pass | не затрагивает `KaraokeProcess*` и subprocess-инфраструктуру. |
| **V. Двух-фронтенд: админка и публичный сайт** | ✅ Pass | правка только в `webvue3` (админка). `karaoke-public` не затрагивается. |
| **VI. Code Standards (JSDoc, ESLint baseline)** | ⚠️ Verify | после правки — обязателен `npm run lint` + `tools/check-eslint-baseline.sh webvue3`. Новых нарушений быть не должно. JSDoc на новых data-полях — опционально (это внутреннее состояние компонента), но action `searchCandidateSongs` получает JSDoc-комментарий о расширении сигнатуры. |
| **VII. Cross-Machine Setup** | ✅ Pass | не трогаем AI-конфиги, `.git-blame-ignore-revs`, `.gitattributes`. |
| **VIII. Секреты и git-гигиена** | ✅ Pass | никаких секрет-файлов; только frontend-исходники. |

**Gates passed.** Никаких нарушений, требующих обоснования в Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/280-assign-modal-root-audio-id/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command) — пусто: внешние контракты не меняются
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

**Затрагиваемые файлы:**

```text
webvue3/src/components/SongEditor/
├── AssignModal.vue          # ПРАВКА: +2 input + 2 clear-кнопки в .se-search-row, +2 поля в data()
└── store.js                 # ПРАВКА: action searchCandidateSongs — добавить filterRootId/filterAudioParentId

livedocs/features/
└── 280-assign-modal-root-audio-id.md   # NEW (по FR-014 из AGENTS.md — обновление LiveDoc в том же PR)
```

**Структура решения:** только фронтенд. Никаких новых модулей, никаких новых тестов-каталогов.

**Structure Decision**: правка in-place в существующих файлах `webvue3/src/components/SongEditor/{AssignModal.vue, store.js}`. Новых директорий не создаётся. LiveDoc создаётся как новый файл в `livedocs/features/` (конвенция проекта).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Не требуется: все gates passed.

## Phase 0: Research

Главный открытый вопрос на момент старта — **терминология `audioRootId` (из ТЗ) vs `audioParentId` (каноническое имя в API/UI/БД)**. Это разрешено в спеке Assumption A-1: используем `audioParentId` + метка «A-root ID:» (UI-конвенция из `SongsFilterModal.vue`). Дополнительных NEEDS CLARIFICATION в Technical Context нет — стек, зависимости и поведение полностью определены существующими контрактами `SongsFilterModal.vue` + `ApiController.apisSongsDigests` + `SongDTOdigest`.

См. [research.md](research.md) — подтверждение канонического имени, обзор 3 аналогичных точек расширения фильтра в проекте, оценка рисков.

## Phase 1: Design & Contracts

См. [data-model.md](data-model.md) — изменение состояния компонента (`data()`), без новых сущностей.
См. [contracts/contracts.md](contracts/contracts.md) — HTTP-контракт `/api/songsdigests` (уже существующий, без изменений; фича только консьюмер).
См. [quickstart.md](quickstart.md) — сценарии ручной валидации в админке.

Re-evaluate Constitution Check после Phase 1: **все gates остаются PASS**, дополнительных нарушений не появилось.

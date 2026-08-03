# Implementation Plan: Доступность плеера в таблице «Песни» при статусе ≥4

**Branch**: `125-player-status-gate` | **Date**: 2026-08-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/125-player-status-gate/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Снизить порог доступности иконки основного плеера в таблице «Песни» (`webvue3/src/components/Songs/SongsTable.vue`) с `idStatus >= 6` (READY) до `idStatus >= 4` (MARKERS_CREATED), включая согласованное обновление текста disabled-подсказки (title). Иконка DEMO-плеера и кнопка «Открыть плеер» в SongEdit не меняются (см. Assumptions в spec.md). Чисто фронтенд-изменение в одном Vue-компоненте: condition-выражение в двух местах (`v-if`/`v-else` для `#cell(player)`) плюс текст `title`.

## Technical Context

**Language/Version**: TypeScript/JavaScript (Vue 3 Options API), Node 22

**Primary Dependencies**: Vue 3 + Vite, Bootstrap-vue-next (admin SPA `webvue3`)

**Storage**: N/A (не меняется — читает уже загруженное поле `idStatus` сущности Song, никаких новых запросов/миграций)

**Testing**: Ручная проверка в браузере (в CI юнит/e2e тестов для `webvue3` нет, см. constitution.md «Рабочий процесс»); `npm run lint:check` + `npx prettier --check` как gate перед коммитом

**Target Platform**: Admin SPA (`webvue3`), браузер

**Project Type**: web (frontend-only изменение, backend не затрагивается)

**Performance Goals**: N/A — точечное изменение условия рендера в существующей таблице, без влияния на производительность

**Constraints**: Изменение MUST быть ограничено `SongsTable.vue` (иконка `player`), не задевать `playerDemo` и SongEdit (см. FR-005 spec.md)

**Scale/Scope**: 1 файл (`webvue3/src/components/Songs/SongsTable.vue`), 2 условия (`v-if`/`v-else` в блоке `#cell(player)`, строки ~236-267) + текст `title` disabled-состояния

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Применимые Core Principles:

- **Principle V (Двух-фронтенд: admin/public — разные приложения)** — PASS. Изменение локализовано в `webvue3` (admin SPA), `karaoke-public` не затрагивается. Смешивания ответственностей нет.
- **Principle VI (Code Standards, NON-NEGOTIABLE)** — PASS (с обязательством). `SongsTable.vue` — существующий `export default` Vue-компонент, уже имеет JSDoc-заголовок; точечное изменение условия не требует нового компонента/новой публичной функции, но перед коммитом обязательны `npm run lint:check`, `npx prettier --check`, `bash tools/check-jsdoc-coverage.sh webvue3` (не должны регрессировать — покрытие уже 100%/не ниже baseline).
- **Principle VII (Cross-Machine Setup)** — N/A, изменение не касается конфигов/окружения.
- **Principle VIII (Секреты и git-гигиена)** — N/A, секретов не затрагивает.
- **Principle I-IV (self-contained pipeline, raw JDBC, sync registry, async-очередь)** — N/A, backend/БД/очереди не меняются; `idStatus` — уже существующее, синхронизируемое поле, схема не меняется.

Никаких нарушений, Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/125-player-status-gate/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md  # Spec quality checklist (/speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

Контрактов (`contracts/`) нет — фича не добавляет и не меняет ни один backend API/эндпоинт, только условие рендера во фронтенд-компоненте существующей admin SPA.

### Source Code (repository root)

```text
webvue3/                                   # admin SPA (Vue 3 + Vite)
└── src/
    └── components/
        └── Songs/
            └── SongsTable.vue             # единственный изменяемый файл:
                                            #  - #cell(player) v-if/v-else (idStatus >= 6 → >= 4)
                                            #  - title неактивной иконки ("статус < 6" → "статус < 4")
```

**Structure Decision**: Единственный проект — существующая admin SPA `webvue3`. Backend (`karaoke-app`/`karaoke-web`), `karaoke-public` и БД не затрагиваются. Новых директорий/модулей не создаётся.

## Complexity Tracking

Не заполняется — нарушений Constitution Check нет (см. раздел выше).

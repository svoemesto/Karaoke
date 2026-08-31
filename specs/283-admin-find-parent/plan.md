# Implementation Plan: Админка webvue3 — кнопка «Поиск родителя» для автора

**Branch**: `283-admin-find-parent` | **Date**: 2026-08-31 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/283-admin-find-parent/spec.md`

## Summary

Новая кнопка «Поиск родителя» на главной странице админки `webvue3` (`HomeView.vue`) — даёт куратору точечно прогнать **только фазу 1** текстового поиска родителя (`findParentCandidateId`) для песен одного автора с `root_id = 0`. По умолчанию поиск ограничен «только своим автором» (`crossAuthor=false`); опционально — разрешает подбор среди других авторов. Аналог существующего `customFunction`, но с тремя ключевыми отличиями: (1) фильтр по автору обязателен; (2) только фаза 1, без аудио; (3) контролируемый флаг `crossAuthor`. Реализация — новый эндпоинт `POST /api/utils/findparentforauthor`, новая top-level функция `Utils.findParentForAuthor`, новый Vuex-action; минимальные изменения в существующем `Utils.findParentCandidateId` (1 параметр с дефолтом, 1 строка логики).

## Technical Context

- **Language/Version**: Kotlin 1.x (JDK 17) для бэкенда, JavaScript/Vue 3 для фронта.
- **Primary Dependencies**:
  - Backend: Spring Boot (уже инжектится в `ApiController.kt`: `storageService`, `storageApiClient`, `lyricsFinderService`), PostgreSQL JDBC (`WORKING_DATABASE.getConnection()`), SSE-нотификации через `SNS.send(SseNotification.message(...))`.
  - Frontend: Vue 3 + Vuex 4 + `CustomConfirm` (local component `webvue3/src/components/Common/CustomConfirm.vue`), HTTP через `promisedXMLHttpRequest` (см. `store.js:2416`).
- **Storage**: PostgreSQL (через сырой JDBC, без ORM — Constitution § II). Таблица `tbl_songs`, колонка `root_id`. Новых таблиц/колонок **нет**.
- **Testing**: ручная валидация админом по `quickstart.md` (8 сценариев). В CI тестов нет (Constitution § «Рабочий процесс»). Существующие `karaoke-app/src/test` — `@Disabled`.
- **Target Platform**: `karaoke-app` (admin-машина, single-instance JVM-контейнер) + `karaoke-web` (прод) — оба на Linux.
- **Project Type**: web-service (Kotlin backend) + SPA (Vue 3).
- **Performance Goals**: фоновый поток (как `customFunction`/`autoAssignOriginalAll`), HTTP-ответ возвращается сразу, итог — SSE-тост. Без синхронного ожидания на UI.
- **Constraints**:
  - single-instance JVM → `@Volatile`-флаг достаточен для защиты от гонок (Constitution § IV).
  - Без JPA/Hibernate (Constitution § II) — только сырой JDBC.
  - Без ktlint/ESLint-нарушений сверх baseline (Constitution § VI).
  - Docker-образ `webvue3` нужно пересобрать через `deploy/do.sh build_webvue3` (AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»).
- **Scale/Scope**: одна новая кнопка, один новый эндпоинт, одна новая top-level функция, ~80 строк кода всего (фронт + бэк).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Статус | Комментарий |
|---|---------|--------|-------------|
| I  | Self-contained автопайплайн | ✅ | Фича работает на локальных данных (`tbl_songs`, in-memory) — никаких внешних SaaS в горячем пути. |
| II | Сырой JDBC + дифф по хэшам | ✅ | Только сырой JDBC через `WORKING_DATABASE.getConnection()`. Выборка — один `SELECT id … WHERE root_id = 0 AND LOWER(song_author) = LOWER(?)`, итерация по списку `ids` (не N+1). |
| III | Двух-БД синхронизация через SyncRegistry | ✅ | Не затрагивается: `tbl_songs` и так в `SyncRegistry`; фича только пишет `root_id`, что уже учтено в `recordhash`-триггере. **Проверить в `/speckit.tasks`**: при изменении способа записи `root_id` — пересоздать `recordhash`-триггер для `tbl_songs` (но мы пишем через существующий `Song.saveToDb()` — триггер уже учитывает `root_id`). |
| IV | Async-очередь задач с парсингом stdout | ✅ | Фоновая задача через `thread { … }`, логи в stdout (как `customFunction`/`autoAssignOriginalAll`). Без `ProcessBuilder`. SSE-уведомление по завершении — тот же паттерн. |
| V  | Двух-фронтенд: админка и публичный сайт | ✅ | Фича только в админке `webvue3` (`HomeView.vue`). Публичный сайт не затрагивается. |
| VI | Code Standards | ✅ | Новые публичные API будут с KDoc/JSDoc + `@see specs/283-admin-find-parent/spec.md` (per-feature документ не создаём — R-008 в research.md). Линтеры (`ktlintCheck`, ESLint, prettier) и Docker-сборка — обязательные шаги после правок (AGENTS.md). |
| VII | Cross-Machine Setup | ✅ | Не затрагивается: фича не меняет `.gitignore`, `.gitattributes`, `.git-blame-ignore-revs`. |
| VIII | Секреты и git-гигиена | ✅ | Не затрагивается: фича не вводит/не меняет секрет-файлы, не трогает `.env`. |

**GATE: PASS** — нарушений нет, дополнительных обоснований не требуется. Complexity Tracking — пусто (см. ниже).

## Project Structure

### Documentation (this feature)

```text
specs/283-admin-find-parent/
├── plan.md              # этот файл
├── spec.md              # Feature Spec
├── research.md          # Phase 0 (R-001…R-010)
├── data-model.md        # Phase 1 (Song + ParentCandidate + isFindParentInProgress)
├── contracts/
│   └── http-endpoint.md # Phase 1 (POST /api/utils/findparentforauthor)
├── quickstart.md        # Phase 1 (8 сценариев ручной валидации)
├── checklists/
│   └── requirements.md  # Spec Quality Checklist (✅ 17/17)
└── tasks.md             # Phase 2 — будет создан /speckit.tasks
```

### Source Code (repository root)

**Структура не меняется** — фича встраивается в существующие файлы. Никаких новых директорий в исходниках.

Затрагиваемые файлы:

| Файл                                                                                            | Изменение                                                                                          |
|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt`             | +1 endpoint `doFindParentForAuthor` (рядом с `doCustomFunction`, ~5890).                            |
| `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt`                                  | +1 top-level `findParentForAuthor` (по образцу `customFunction`); +1 параметр в `findParentCandidateId` (с дефолтом, см. R-002); +1 `@Volatile` флаг `isFindParentInProgress`. |
| `webvue3/src/views/HomeView.vue`                                                                | +1 кнопка «Поиск родителя» (над «Автопривязать оригинал по аудио…»); +2 метода `findParentForAuthor`/`doFindParentForAuthor`. |
| `webvue3/src/components/Songs/store.js`                                                         | +1 Vuex-action `findParentForAuthorPromise` (рядом с `autoAssignOriginalAllPromise`, ~2416).       |

**Structure Decision**: фича затрагивает 4 существующих файла в рамках уже сложившейся структуры `karaoke-app`/`karaoke-web`/`webvue3` (см. Constitution § «Технологический стек»). Никаких новых модулей/директорий. Тесты в скоупе фичи не появляются (валидация — ручная по `quickstart.md`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _(нет)_   | —          | —                                   |

Constitution Check — без нарушений. Complexity Tracking пуст по форме.

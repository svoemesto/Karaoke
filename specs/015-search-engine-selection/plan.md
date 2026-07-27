# Implementation Plan: Выбор поискового движка для текстов песен и обложек альбомов

**Branch**: `015-search-engine-selection` | **Date**: 2026-07-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/015-search-engine-selection/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command; its definition describes the execution workflow.

## Summary

Сегодня поиск текста песни (`getSearchSongTextAll` → жёстко закодированный
`getSearXNGSearch`, который после фичи 014 реально ходит в fourget) и поиск
обложки альбома (`AlbumCoverService.search`, только SearXNG) используют
единственный, зашитый в код движок — сменить его можно только правкой кода.
Решение (см. `research.md`): ввести два независимых `KaraokeProperty`
(`lyricsSearchEngine` — 4 варианта: Yandex sync/async, SearXNG, fourget;
`albumCoverSearchEngine` — 2 варианта: SearXNG, fourget) через уже
существующий механизм настроек проекта (без нового UI-экрана). Функция
`getSearXNGSearch` переименовывается в движок-нейтральный диспетчер
`getLyricsSearch(engine, forceResearch)`; `forceResearch` предварительно
удаляет старые `SearchAsync`/`SearchResult` для песни (новые
`deleteBySongId`-методы), позволяя «переискать». В окне результатов поиска
текста (`SearchText.vue`) добавляются две кнопки — «Искать заново» (диалог с
выбором движка через уже существующий `CustomConfirm.fields[fldIsSelect]`) и
«Удалить результаты поиска» (простое подтверждение). Дополнительно: как
только статус песни пересекает порог готовности (≥3 — уже существующий в
проекте порог для публичного плеера), результаты поиска для неё удаляются
автоматически (переиспользуется уже существующая точка `crossedReadyThreshold`
в `Song.saveToDb()`); для уже готовых песен — кнопка «Удалить результаты
поиска готовых песен» на главной странице админки (по образцу уже
существующей фоновой массовой операции `doRecalcPlayerReadiness`).

## Technical Context

**Language/Version**: Kotlin/Spring Boot (backend, `karaoke-app`) + Vue 3
Options API (frontend, `webvue3`) — оба уже используемые в проекте стеки, без
изменений.

**Primary Dependencies**: Никаких новых библиотек. Backend — существующие
`SearchTool`, `AlbumCoverService`, `KaraokeProperties`, `KaraokeDbTable`.
Frontend — существующий `CustomConfirm.vue` (уже поддерживает
`fields[].fldIsSelect` — ровно то, что нужно для выбора движка, новый
UI-компонент не создаётся).

**Storage**: PostgreSQL, без изменений схемы — новые companion-методы
удаления по `songId` для уже существующих таблиц `tbl_search_async`/
`tbl_search_results` (см. `data-model.md`); настройки движков — через уже
существующий файл `Karaoke.properties` (механизм `KaraokeProperties`), не БД.

**Testing**: Ручная/интеграционная проверка через `quickstart.md` (5
сценариев) — соответствует принятой в проекте практике (см. `constitution.md`
→ «Рабочий процесс» → «Тесты», а также `plan.md` фичи 014).

**Target Platform**: То же окружение, что и фича 014 (admin-машина, Docker) —
новых контейнеров не добавляется: `SEARXNG`-вариант поиска текста и
`FOURGET`-вариант поиска обложек обращаются к уже существующим сервисам
`searxng`/`fourget` по новым HTTP-путям, не новым сервисам.

**Project Type**: Backend-интеграция (`karaoke-app`) + точечные UI-правки в
admin SPA (`webvue3`, `SearchText.vue`) — single-project, без изменений в
`karaoke-public`/`karaoke-web` (Principle V — не трогаем).

**Performance Goals**: Без изменений относительно сегодняшнего поведения по
задержке отдельных запросов — цель фичи в переключаемости и повторяемости
поиска, не в скорости.

**Constraints**: FR-009/FR-010 (spec.md) — смена дефолтной настройки НЕ
должна менять уже сохранённые результаты других песен; удаление (FR-007,
FR-008) — только по конкретному `songId`, не массово; Yandex-варианты — не
предлагаются для обложек альбомов (FR-002, решение пользователя).

**Scale/Scope**: Один админ/оператор одновременно работает с одной песней в
редакторе — конкурентный доступ нескольких операторов к одной и той же песне
вне рамок (см. Edge Cases `spec.md`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| I. Self-contained автопайплайн (NON-NEGOTIABLE) | **PASS** | `SEARXNG`/`FOURGET` — self-hosted, без изменений архитектуры. `YANDEX_SYNC`/`YANDEX_ASYNC` — внешний Yandex Cloud Search API, но это НЕ новая зависимость (существовала в коде до фичи 014/015), а её осознанный возврат как явно выбираемой опции — одобрение пользователя на это получено прямым текстом в постановке задачи (см. `research.md`, Вопрос 5). |
| II. Сырой JDBC + дифф по хэшам (NON-NEGOTIABLE) | **PASS** | Новые методы `deleteBySongId` используют существующий `KaraokeDbTable.delete()` по одному `id` за раз (по образцу `CartItem.deleteByUserAndSongs`) — без новых SQL-конструкций, без O(n²) сравнений (нет сравнения LOCAL↔SERVER в этой задаче). |
| III. Двух-БД синхронизация через SyncRegistry | **N/A** | `tbl_search_async`/`tbl_search_results` не участвуют в `SyncRegistry` сегодня и не добавляются в этой задаче; `KaraokeProperties` — не БД-сущность, синхронизации не подлежит. |
| IV. Async-очередь задач с парсингом stdout | **N/A** | Ни один из 4 движков не запускается через `ProcessBuilder`/`KaraokeProcess*` — все являются синхронными/асинхронными HTTP-вызовами (как и было в `getYandexSearch`/`getSearXNGSearch` до этой задачи). |
| V. Двух-фронтенд: админка и публичный сайт | **PASS** | Изменения только в `webvue3` (`SearchText.vue`) — `karaoke-public`/`karaoke-web` не затрагиваются. |
| VI. Code Standards (NON-NEGOTIABLE) | **ACTION REQUIRED, не блокирует** | Новый/переименованный публичный код (`getLyricsSearch`, `deleteBySongId`×2, `AlbumCoverSearchEngine`/`LyricsSearchEngine`, `searchFourgetImages`, `searchUrlsViaSearxng`) обязан получить KDoc с `@see` на `docs/features/llm-lyrics-search.md`; JSDoc для новых методов `SearchText.vue`/vuex-действий. FR-009 конституции требует обновить `docs/features/llm-lyrics-search.md` в том же PR (уже частично обновлён фичей 014, требует дополнения про 4 движка). ktlint/ESLint — без новых нарушений baseline. |
| VII. Cross-Machine Setup | **N/A** | Не затрагивает персональные AI-конфиги/onboarding. |

Нарушений, требующих секции «Complexity Tracking», нет.

*Re-check после Phase 1* (см. `research.md`, `data-model.md`, `contracts/`,
`quickstart.md`): решения Phase 0/1 не меняют статусы выше — Yandex-варианты
остаются осознанным, одобренным пользователем возвратом уже существовавшей
зависимости (I), удаление по `songId` — O(n) без сравнения БД (II), новых
sync-сущностей/async-очередей/фронтендов не появилось (III/IV/V). Требуется
только KDoc/JSDoc + обновление per-feature документа (VI) — учтено в Project
Structure ниже. Constitution Check **повторно пройден без замечаний**.

## Project Structure

### Documentation (this feature)

```text
specs/015-search-engine-selection/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/
│   ├── api-endpoints.md # Phase 1 output (/speckit.plan command)
│   └── ui-modal.md      # Phase 1 output (/speckit.plan command)
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

Single project (backend `karaoke-app` + точечные правки admin SPA `webvue3`),
без изменений в `karaoke-public`/`karaoke-web` (Principle V):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── UtilsAI.kt                    # getSearXNGSearch → переименовать/расширить в
│                                  # getLyricsSearch(engine, forceResearch) — диспетчер
│                                  # по LyricsSearchEngine; getYandexSearch — без изменений
├── llm/Tools.kt                  # SearchTool: + searchUrlsViaSearxng(query) (прямой
│                                  # SearXNG для текста), searchUrls (fourget) — без изменений
├── AlbumCoverFinder.kt            # AlbumCoverService.search(..., engine) — новый параметр;
│                                  # + searchFourgetImages(query) рядом с searchSearxngImages
├── model/SearchAsync.kt          # + deleteBySongId(songId, ...)
├── model/SearchResult.kt         # + deleteBySongId(songId, ...)
├── model/Song.kt                 # saveToDb(): рядом с существующим crossedReadyThreshold →
│                                  # + SearchResult/SearchAsync.deleteBySongId(id, ...) (FR-011)
├── HealthReport.kt (или новый файл) # + deleteSearchResultsForReadySongs(...): Int — по образцу
│                                  # recalculatePlayerReadiness (FR-012/FR-013)
├── KaraokeProperties.kt           # + lyricsSearchEngine, albumCoverSearchEngine в listKaraokeProperties
└── controllers/ApiController.kt   # getSearchSongTextAll: + engine/forceResearch параметры;
                                    # searchAlbumCover: + engine параметр;
                                    # + POST /api/song/deletesearchresults
                                    # + POST /api/utils/deletesearchresultsforreadysongs
                                    #   (фоновый thread + SSE-тост, по образцу doRecalcPlayerReadiness)

webvue3/src/components/Songs/
├── store.js                       # searchTextForSong: + {engine, forceResearch} payload;
│                                    # + deleteSearchResults(songId) action;
│                                    # + deleteSearchResultsForReadySongsPromise action
└── edit/SearchText.vue            # + кнопки «Искать заново» / «Удалить результаты поиска»
                                     # (CustomConfirm.fields[fldIsSelect] для выбора движка —
                                     # компонент уже поддерживает, новый UI не создаётся)

webvue3/src/views/HomeView.vue      # + кнопка «Удалить результаты поиска готовых песен»,
                                     # рядом с уже существующей recalcPlayerReadiness

docs/features/llm-lyrics-search.md  # ОБЯЗАТЕЛЬНО дополнить (FR-009 конституции) — описать
                                     # 4 движка, forceResearch, deleteBySongId, автоочистку
                                     # по статусу готовности
```

**Structure Decision**: Изменение целиком внутри `karaoke-app` (backend) +
точечные правки `webvue3` (admin SPA) — single-project. Новый UI-компонент не
создаётся (переиспользуется `CustomConfirm.fields`), новых docker-сервисов не
требуется (переиспользуются `searxng`/`fourget` из фичи 014), схема БД не
меняется (только новые companion-методы удаления по `songId`).

## Complexity Tracking

*Нарушений Constitution Check нет (см. таблицу выше) — секция не заполняется.*

# Implementation Plan: Пагинация / динамическая подгрузка результатов поиска

**Branch**: `262-search-pagination` | **Date**: 2026-08-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/262-search-pagination/spec.md`

## Summary

Расширяем публичный эндпоинт `/api/public/songs` опциональными параметрами
`page` / `pageSize` и оборачиваем ответ в `PagedSongsDto` (с `totalCount`,
`hasMore`). На фронте (`SearchView.vue` + Vuex `songs`) добавляем состояние
`searchPagination`, кнопку «Загрузить ещё», синхронизацию `?page=N` в URL
для shareable-ссылок и F5-устойчивости. Сортировка стабильна
(`Song.id ASC`, уже используется в `Song.loadListFromDb`). Минимальный
дифф бэка (один контроллер + один DTO), фронта — расширение существующего
Vuex-модуля без новых зависимостей.

## Technical Context

**Language/Version**:
- Backend: Kotlin (Spring Boot, версия как у `karaoke-web/build.gradle.kts`)
- Frontend: Vue 3 + Vuex 4 + Bootstrap 5 (`karaoke-public/package.json`)

**Primary Dependencies** (только существующие):
- Backend: Spring Boot Web MVC, Jackson (Kotlin-module), JDBC через
  `KaraokeConnection` (см. Constitution II), `KaraokeStorageService`,
  `StorageApiClient`.
- Frontend: Vue 3, Vue Router 4, Vuex 4, Bootstrap 5, axios
  (через `apiGet('/api/public/songs', params)` в `store/modules/songs.js`).

**Storage**: существующая PostgreSQL через `KaraokeConnection` (constitution II —
сырой JDBC, без JPA/Hibernate); таблица `tbl_songs`. Никаких миграций БД.

**Testing**: проект не имеет автоматизированных тестов в CI (см.
`AGENTS.md` «Тесты: в CI нет»); проверка — ручной сценарий пользователем
плюс curl-проверки, описанные в `quickstart.md`. Существующие тесты
`karaoke-app/src/test` помечены `@Disabled`.

**Target Platform**:
- Backend: Linux server (production), JDK 17+.
- Frontend: современные браузеры (Chrome/Firefox/Safari актуальных версий;
  mobile Safari + Chrome Android). Никаких IE/legacy.

**Project Type**: web (frontend + backend), multi-module Gradle — два
Spring Boot модуля (`karaoke-app`, `karaoke-web`) + две SPA
(`webvue3` admin, `karaoke-public` public). Эта фича затрагивает
`karaoke-web` (бэкенд) и `karaoke-public` (фронтенд).

**Performance Goals**:
- Первая порция результатов (≤35 песен) возвращается за <1s для запросов
  с totalCount ≥500 (SC-002: ≥50% улучшение по сравнению с baseline
  — полным возвратом всех 500+ песен разом).
- COUNT(*) по `tbl_songs WHERE <filter>` — за <500ms (с тем же
  фильтром, что и `items`); используется тот же `getWhereList(...)`
  helper, что и `Song.loadListFromDb`.
- Подгрузка следующей порции (35 песен) — за <1s (аналогично
  первой, но инкрементальный OFFSET; на больших страницах >2-3s — допустимо
  для сценария «Загрузить ещё», не для первой порции).

**Constraints**:
- Минимальный дифф (FR-015 спеки, SC-009): один контроллер
  (`PublicApiController.songs`) + один новый DTO (`PagedSongsDto`).
  Никаких изменений в SQL-миграциях, других контроллерах, других DTO.
- Обратная совместимость (FR-003, FR-016): отсутствие параметров
  `page`/`pageSize` → старый формат ответа `List<SongPublicDto>`
  (без обёртки). Это позволяет катить фронт и бэк независимо.
- Сырой JDBC (Constitution II): без JPA/Hibernate. Все SQL-запросы —
  через `KaraokeConnection.getConnection()` + `prepareStatement` +
  `executeQuery`, паттерн как у `Author.countWithNewAlbum`
  (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Author.kt:384`).
- Никаких новых frontend-зависимостей: используется существующий
  Vuex + Bootstrap 5 + axios; auto-load при scroll-near-bottom
  реализуется через `@scroll` listener (vanilla JS).
- Никаких коммитов секретов (Constitution VIII) — фича не затрагивает
  деплой/конфиги.

**Scale/Scope**:
- Production dataset: ~18 000+ песен в `tbl_songs` (см. SC-001 спеки
  261 «200 песен в `/search`» и `livedocs/features/093-news-pagination-top-35.md`
  «19 000+ новостей»). Типичный запрос с фильтром возвращает
  0–5000 песен; реже — 10 000+.
- UI: одна страница `/search` + один Vuex-модуль `songs` +
  один компонент `SearchView.vue`. Никаких новых экранов.
- Бэк: один контроллер (`PublicApiController.songs`) + один новый
  DTO (`PagedSongsDto`) + один новый helper-метод
  `Song.countMatchingAttr(...)` (companion object).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Status | Notes |
|---|-----------|--------|-------|
| I | Self-contained автопайплайн | N/A | Фича не затрагивает пайплайн производства караоке; только публичный поиск. |
| II | Сырой JDBC + дифф по хэшам | ✅ PASS | Используем `KaraokeConnection.getConnection()` + `prepareStatement` (паттерн `Author.countWithNewAlbum`). Никаких JPA/Hibernate. `Song.loadListFromDb` уже поддерживает `args["limit"]`/`args["offset"]` (см. `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7650-7657`) — используем существующую инфраструктуру, новые SQL-фильтры НЕ нужны. |
| III | Двух-БД синхронизация | N/A | `tbl_songs` уже синхронизируется (см. `SyncRegistry.all`). Никаких изменений в схеме/триггерах. |
| IV | Async-очередь задач | N/A | Фича не затрагивает длительные операции. |
| V | Двух-фронтенд | ✅ PASS | Изменения только в публичном SPA (`karaoke-public/src/views/SearchView.vue` + `store/modules/songs.js`). Админка `webvue3` НЕ затрагивается. CSS-переменные `--km-*` остаются источником цветов. |
| VI | Code Standards | ✅ PASS | Новый публичный API (`PagedSongsDto`) сопровождается KDoc с `@see` на `specs/262-search-pagination/contracts/api-songs.md`. Существующие компоненты строки (`PlayerIcon`, `FavoriteIcon`, `PlaylistIcon`, `PremiumIcon`, `CartIcon`) НЕ изменяются → регресс по baseline ktlint/ESLint невозможен. Линтеры — через `./gradlew :karaoke-web:ktlintCheck` + `tools/check-eslint-baseline.sh karaoke-public` (см. AGENTS.md «Обязательная проверка после ЛЮБОГО изменения кода»). |
| VII | Cross-Machine Setup | ✅ PASS | Никаких изменений в личных AI-конфигах; только код в общих модулях. |
| VIII | Секреты и git-гигиена | ✅ PASS | Фича не затрагивает секрет-файлы. Pre-commit check `git ls-files | grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$'` MUST вернуть пусто (правило уже соблюдается). |

**Вердикт**: Constitution Check PASSED. Все релевантные принципы соблюдены.

## Project Structure

### Documentation (this feature)

```text
specs/262-search-pagination/
├── plan.md              # Этот файл
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api-songs.md     # Расширенный контракт GET /api/public/songs
├── checklists/
│   └── requirements.md  # Quality checklist (уже есть)
└── tasks.md             # Phase 2 output (NOT created by /speckit.plan)
```

### Source Code (repository root)

Эта фича модифицирует **существующие** файлы и добавляет **один новый** DTO.
Никаких новых модулей или директорий.

**Backend** (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`):

```text
controllers/
└── PublicApiController.kt          # MODIFY: добавить page/pageSize, вернуть PagedSongsDto при их наличии
dto/
└── PagedSongsDto.kt                # NEW: обёртка items + totalCount + page + pageSize + hasMore
```

Дополнительно (опционально, упрощает тестирование и единообразие):

```text
../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/
└── Song.kt                         # MODIFY (companion): добавить countMatchingAttr(...) helper
```

**Frontend** (`karaoke-public/src/`):

```text
store/modules/
└── songs.js                        # MODIFY: добавить searchPagination state, appendSearchResults mutation,
                                    #         loadMoreSearchResults action
views/
└── SearchView.vue                  # MODIFY: добавить счётчик "Показано X из Y", кнопку "Загрузить ещё",
                                    #         обработку ошибок, URL-sync для page/pageSize
```

**Структура (выбранная)**: модификация существующих файлов в рамках уже
существующей структуры проекта (`karaoke-web` + `karaoke-public` +
опциональный helper в `karaoke-app`). Новые модули, директории или
зависимости **не добавляются**.

## Complexity Tracking

> **Не заполняется** — Constitution Check PASSED без нарушений, упрощать
> дальше нечего. Все решения в пользу минимального диффа (SC-009).

## Phase 0 (research.md)

См. [`research.md`](./research.md). Phase 0 закрывает следующие вопросы:

1. Поддерживает ли существующий `Song.loadListFromDb` параметры `limit`/`offset`?
   → **ДА** (см. `karaoke-app/.../Song.kt:7650-7657`).
2. Есть ли pattern для `COUNT(*)` в проекте? → **ДА** (см.
   `karaoke-app/.../Author.kt:384` — `Author.countWithNewAlbum`).
3. Поддерживает ли Vuex-store в проекте паттерн `requestId` для race-conditions?
   → **ДА** (см. `karaoke-public/src/store/modules/songs.js:50-55` — уже
   используется в `search` action; расширяется на `loadMoreSearchResults`).
4. Есть ли в проекте готовый компонент «Load more» / пагинация? → НЕТ,
   реализуется inline в `SearchView.vue` (минимальный дифф).
5. Какой подход к синхронизации URL ↔ Vuex-state в проекте? → router
   `query` напрямую через `$route.query` (стандарт Vue Router 4);
   `useRoute()` composable или `this.$route` (в Vue 2-style Options API).

## Phase 1 (data-model.md, contracts/, quickstart.md)

См.:

- [`data-model.md`](./data-model.md) — расширение `SongPublicDto`
  контракта (не требуется для бэка — оборачивается в `PagedSongsDto`),
  новая сущность `PagedSongsDto`, новые поля в Vuex `songs`.
- [`contracts/api-songs.md`](./contracts/api-songs.md) — расширенный
  контракт `GET /api/public/songs` (с параметрами `page`/`pageSize`
  и обёрткой `PagedSongsDto` при их наличии).
- [`quickstart.md`](./quickstart.md) — ручной сценарий проверки:
  curl на `/api/public/songs?page=1`, `?page=2`, проверка
  непересечения `id`, стабильности порядка, totalCount; в браузере —
  F5 на `?page=3`, нажатие «Загрузить ещё», проверка счётчика.

### Re-evaluation Constitution Check after design

Все 8 пунктов Constitution Check остаются в статусе PASS/N/A. Phase 1
**не** вносит новых SQL-фильтров, миграций, секретов или новых
зависимостей — регресс невозможен.

## Связанные документы

- [spec.md](./spec.md) — фичеспека (4 User Stories, 18 FR, 10 SC).
- [research.md](./research.md) — Phase 0 research output.
- [data-model.md](./data-model.md) — Phase 1 data model.
- [contracts/api-songs.md](./contracts/api-songs.md) — Phase 1 contract.
- [quickstart.md](./quickstart.md) — Phase 1 validation guide.
- Спека-предшественник: [261-search-results-ui](../261-search-results-ui/spec.md)
  (UI строки + редизайн — должна остаться без регрессий).
- Проектные стандарты: [AGENTS.md](../../AGENTS.md), раздел
  «Обязательная проверка после ЛЮБОГО изменения кода» (compile + lint +
  bootJar + frontend build) — **применяется к этому PR полностью**.
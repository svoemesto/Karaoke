# Implementation Plan: 261 — Исправление иконки плеера и редизайн строк результатов поиска

**Branch**: `261-search-results-ui` | **Date**: 2026-08-28 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/261-search-results-ui/spec.md`

## Summary

Два изменения, объединённые в одну спеку:

1. **Bug**: иконка плеера в результатах поиска всегда серая — `SongPublicDto` (DTO ответа `/api/public/songs`) не содержит поле `contentReady`, поэтому `<PlayerIcon :content-ready-state>` всегда получает `'notready'`. Песни «в эфире» ошибочно показываются как недоступные.
2. **UI**: результаты поиска должны визуально совпадать с плейлистом/избранным — `PlaylistEditView` — единой row-структурой, применяемой и на десктопе, и на мобилке (Clarification Q1 → A, 2026-08-28).

Минимальный backend-diff: расширение `SongPublicDto` тремя новыми полями (`contentReady`, `albumPictureUrl`, `authorPictureUrl`). Frontend-diff: перерисовка `SearchView.vue` по образцу `PlaylistEditView.vue:95-189` + scoped-копия его CSS-стилей.

Артефакты Phase 0/Phase 1: [research.md](research.md), [data-model.md](data-model.md), [contracts/api-songs.md](contracts/api-songs.md), [quickstart.md](quickstart.md).

## Technical Context

**Language/Version**:
- Backend: Kotlin 1.x + Spring Boot 3.x + JDK 17 (`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`).
- Frontend: Vue 3 (Options API как в существующих файлах) + Vue Router + Vuex (Options Stores), Bootstrap 5 (`karaoke-public/src/views/SearchView.vue`).

**Primary Dependencies**:
- Backend: только Jackson (Spring Web) — никаких новых deps.
- Frontend: `vue`, `vue-router`, `vuex`, `vuedraggable` (уже в проекте, используется `PlaylistEditView`), `vue3-svg-loader`/`SvgIcon`. Никаких новых deps.

**Storage**: N/A для изменений — данные не пишутся в БД, только читаются. Существующие таблицы `tbl_songs`, `tbl_albums`, `tbl_authors` без миграций.

**Testing**: в CI нет (см. AGENTS.md, раздел «Тесты»). Существующие тесты в `karaoke-app/src/test/` интеграционные, `@Disabled`. Валидация — вручную по [quickstart.md](quickstart.md).

**Target Platform**:
- Backend: `karaoke-web` контейнер на Linux (eclipse-temurin:22-jre-jammy, см. Constitution §Runtime).
- Frontend: `karaoke-public` SPA — современные браузеры (Chrome/Firefox/Safari актуальные версии, iOS Safari, Android Chrome), оба вьюпорта: десктоп ≥1024px и мобильный ≤768px.

**Project Type**: web-service + SPA; для этой спеки задействованы только:
- `karaoke-web` (бэк, kotlin) — расширение DTO.
- `karaoke-public` (фронт, Vue 3) — перерисовка `SearchView.vue`.
- `karaoke-app` (admin) — НЕ задействован (никаких правок).
- `webvue3` (admin SPA) — НЕ задействован.

**Performance Goals**:
- Запрос `/api/public/songs` отрабатывает за ≤500мс для запросов с до 200 песен в результате.
- Время до интерактивности `/search` ≤1с.
- Размер JSON ответа поиска: ≤+5% (3 новых строковых поля на песню; batch-резолв URL контроллера — ≤2 доп. SQL-запросов).

**Constraints**:
- **Минимальный backend-diff**: только `SongPublicDto.kt` + 1 batch-резолв в `PublicApiController.songs()`. Никаких миграций БД, никаких изменений `ZakromaPublicDto`/`SitePlaylistItemDto`/прочих DTO, никаких изменений `KaraokeProperties`/SQL.
- **Никаких новых deps** на фронте (см. «Primary Dependencies»).
- **Никаких новых стилей «с нуля»**: копия стилей `PlaylistEditView.vue:801-995` scoped в `SearchView.vue` (CSS-переменные `--km-*`, без хардкода цветов).
- **Никаких регрессий**: все 9 Edge Cases из спеки должны продолжать работать. См. [quickstart.md](quickstart.md) §Сценарии D/E.

**Scale/Scope**:
- Касается `SearchView.vue` (1 файл шаблона + 1 `<style scoped>`).
- Касается `SongPublicDto.kt` (1 файл DTO).
- ~18k+ песен в продакшен-БД; средний результат поиска — десятки песен; запросы с 200+ песен тестируются (SC-007).
- Затрагивает клиентов только `SearchView.vue`; никаких других потребителей `/api/public/songs` в проекте сейчас нет.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-validated after Phase 1 design.*

Сверяемся с `.specify/memory/constitution.md` (v2.1.0, ratified 2026-07-20, last amended 2026-08-03):

| Принцип | Соответствие | Комментарий |
|---|---|---|
| **I. Self-contained пайплайн** | ✅ N/A | Эта спека не затрагивает пайплайн (ffmpeg/melt/Demucs/Sheetsage) — только публичный поиск. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ N/A | Миграций нет, записи в БД нет, batch-резолв через существующие `Album.getAlbumsByIds`/`Author.loadIdsByNames` (сырой JDBC). |
| **III. Двух-БД синхронизация через SyncRegistry** | ✅ N/A | Изменяемое DTO (`SongPublicDto`) не входит в `SyncRegistry.all` — используется только публичным `karaoke-web` (без sync). |
| **IV. Async-очередь с парсингом stdout** | ✅ N/A | Длительных операций нет; batch-резолв `Album`/`Author` — синхронный SQL (≤2 запроса). |
| **V. Двух-фронтенд (admin + public)** | ✅ Compliance | Все правки в `karaoke-public` (публичный SPA). Никаких изменений в `webvue3` (admin). CSS-переменные `--km-*` (Состав V: «CSS-переменные `--km-*», инвариант). Bootstrap 5 (не `Bootstrap-vue-next`). |
| **VI. Code Standards (FR-006, FR-007, FR-009)** | ✅ Compliance | - FR-006 (KDoc/JSDoc): новые поля `SongPublicDto` и `albumPictureUrl`/`authorPictureUrl` будут с KDoc `@see` на `ZakromaPublicDto` (по образцу существующих `authorId`). `SearchView.vue` <PlayerIcon> уже прокомментирован (Pass 239). <br/>- FR-007 (линтеры): стандартные ktlintCheck (для kotlin-изменений в `SongPublicDto.kt`) + `tools/check-eslint-baseline.sh karaoke-public` (для Vue-изменений) выполняются в CI. <br/>- FR-009 (per-feature документы): `SearchView.vue` — часть подсистемы `docs/features/search.md` (если есть). Если такого нет, при изменении его можно создать/обновить в этом же PR. |
| **VII. Cross-Machine Setup** | ✅ Compliance | Никаких локальных AI-конфигов, ничего для `.git-blame-ignore-revs` или `.gitattributes`. |
| **VIII. Секреты и git-гигиена** | ✅ Compliance | Никаких секрет-файлов; `git ls-files | grep -iE '\.env$\|do\.env$\|\.key$\|\.pem$\|\.p12$\|\.pfx$'` пусто после правок. |

**Пре-conditions из «Ограничения и доступы агента»**:
- (1) Пересборка `karaoke-app`: N/A (эта спека только в `karaoke-web` + фронт).
- (2) Деплой на сервер: пользователь делает вручную по согласованию.
- (4) `deploy/do.env`: не трогаем.
- (5) Pre-commit check секретов — должен пройти.
- (6) Не печатать секреты в вывод — N/A (никаких секретов).

**Re-evaluation after Phase 1 design**: всё ещё ✅ — нарушений нет, обоснований для Complexity Tracking не требуется.

## Project Structure

### Documentation (this feature)

```text
specs/261-search-results-ui/
├── plan.md              # Этот файл (/speckit.plan output)
├── research.md          # Phase 0 output — D1-D6 решения, [research.md](research.md)
├── data-model.md        # Phase 1 output — структура searchResults/SongPublicDto, [data-model.md](data-model.md)
├── quickstart.md        # Phase 1 output — 9 ручных сценариев, [quickstart.md](quickstart.md)
├── contracts/
│   └── api-songs.md     # Phase 1 output — контракт /api/public/songs, [contracts/api-songs.md](contracts/api-songs.md)
└── tasks.md             # Phase 2 output (/speckit.tasks — НЕ создаётся этим command)
```

### Source Code (затрагиваемые файлы)

```text
karaoke-web/
└── src/main/kotlin/com/svoemesto/karaokeweb/
    ├── dto/
    │   └── SongPublicDto.kt          # +3 поля (contentReady, albumPictureUrl, authorPictureUrl), +KDoc, +param в fromSong (или helper)
    └── controllers/
        └── PublicApiController.kt    # +batch-resolve (Album/Author) внутри метода songs()

karaoke-public/
└── src/
    ├── views/
    │   └── SearchView.vue            # ПОЛНАЯ ПЕРЕРИСОВКА: <table>+<km-cards> → div-row (по образцу PlaylistEditView)
    └── (прочие файлы не меняются: store/modules/songs.js, components/*.vue, composables/*.js — без изменений)
```

**Не затрагиваются** (явно):
- `karaoke-app/` — admin-конфиг, не относится к публичному поиску.
- `webvue3/` — admin SPA.
- `deploy/`, `livedocs/` — без правок (только проверка LiveDocs-CI, как обычно).
- `tools/specify-bootstrap.sh`, `tools/reserve-branch-number.sh` — без правок.
- Контракты/миграции БД — нет.

**Structure Decision**: один бэк-DTO-файл + одна перерисованная Vue-вьюха. Никаких новых модулей/компонентов/таблиц. Diff остаётся в рамках «node_modules-style» (минимальное расширение существующих контрактов и UI).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (нет нарушений) | — | — |

Complexity Tracking — пуст, поскольку Constitution Check проходит без нарушений.

## Implementation Notes (не блокирующие, для исполнителя)

1. **Сигнатура `SongPublicDto.fromSong`**: либо добавить 2 параметра `albumPictureUrl, authorPictureUrl`, либо вынести URL-сборку в helper. Оба варианта валидны; первый минимальнее. Принять решение по месту в `tasks.md` (Implementation Notes).
2. **Helper на стороне контроллера**: пакет `Album.getAlbumsByIds(ids, db, ...)` уже есть. `Author.loadIdsByNames([names], db, ...)` уже есть. Доп. helper `Author.getAuthorsByNames(names, db)` может потребоваться — посмотреть при реализации (если нет, использовать `loadIdsByNames` + then `Authors.getByIds(...)`).
3. **Вынос row в общий компонент vs копия**: Implementation Notes. Копия проще и не плодит параметров; общий компонент DRY. Рекомендую начать с копии (мини-дифф), рефакторинг — отдельной задачей позже.
4. **CSS**: scoped-копия безопасна и остаётся в одном PR; вынос в `karaoke-public/src/assets/css/km-rows.css` — Implementation Notes (если решение DRY нужно).
5. **Nullable `albumId` на `Song`**: учитывать `song.albumId == null` → `""`. У `songs()` контроллера есть фильтр по `id_status`/`tag SKIP` — превью для таких песен НЕ строятся (`""`).
6. **Track analytics `OPENED`**: уже работает автоматически через `PublicPlayerController.access()` при `source=list` для клика по `<PlayerIcon>`; никаких frontend-правок для event-tracking не требуется.
7. **Готовность backend'а**: после деплоя `karaoke-web` — проверить `curl http://localhost:8080/api/public/songs?songName=тест | jq '.[0] | {contentReady, albumPictureUrl, authorPictureUrl}'` (или аналогичная проверка), чтобы убедиться, что 3 поля приходят.

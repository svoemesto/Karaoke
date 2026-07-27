# Implementation Plan: Альбомы — клик по ячейке открывает модалку обложки альбома

**Branch**: `014-album-cell-album-cover-modal` | **Date**: 2026-07-27 | **Spec**: [./spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-album-cell-album-cover-modal/spec.md`

## Summary

Повторить функционал кнопки «Изменить обложку альбома» из `SongEdit.vue` (модалка `AlbumCoverModal`) в админском компоненте `AlbumsTable.vue`. Клик по preview-обложке (колонка `(альбом)`) или по названию альбома (колонка `Название`) открывает ту же модалку с тем же поведением (поиск в интернете / загрузка с диска / кроппинг 1:1 / сохранение в `LogoAlbum.png`).

**Технический подход (см. [research.md](./research.md)):**
- Backend: новый `POST /api/albums/firstsongid?albumId=X` (helper `Album.getFirstSongId` в `Album.kt`).
- Frontend: новый Vuex action `getFirstSongIdByAlbumIdPromise` + `setCurrentSongIdOnly` для временной подмены `currentSongId` + `loadOneRecord` для обновления превью.
- `AlbumCoverModal.vue` **не меняется** (требование пользователя: «такая же модалка»).

## Technical Context

**Language/Version**: Kotlin 1.x (JDK 17, Gradle multi-module) для backend; Vue 3 + Vite + Node 22 для `webvue3`.
**Primary Dependencies**: Spring Boot (Karaoke), Vuex 4, bootstrap-vue-next, vue-advanced-cropper (уже есть в `AlbumCoverModal.vue`).
**Storage**: PostgreSQL через сырой JDBC (`KaraokeConnection`); MinIO для preview `Pictures`. Без изменений в схеме БД.
**Testing**: Тесты в CI не запускаются (см. `AGENTS.md`). Проверка — пользователем вручную по сценариям в [quickstart.md](./quickstart.md).
**Target Platform**: Admin SPA `webvue3` (браузер, `permitAll()` в `SecurityConfig.kt`).
**Project Type**: Web application (backend `karaoke-app` + frontend `webvue3`).
**Performance Goals**: O(1) SQL для нового endpoint; UX — открытие модалки ≤ 2 секунд (одна сеть round-trip + `setCurrentSongIdOnly` без загрузки).
**Constraints**: Без изменений DTO/sync/БД-миграций. Без изменений в `AlbumCoverModal.vue`, `Song.kt`, `Picture.kt`, `SyncRegistry`.
**Scale/Scope**: 5k+ альбомов в проде; фича затрагивает ровно 1 строку в таблице при клике (1 round-trip к `/api/albums/firstsongid` + 1 round-trip к `/api/albums/albumsdigests` при `@saved`).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Проверка по 7 Core Principles из `.specify/memory/constitution.md` (v1.2.0):

| # | Принцип | Как соблюдается | Пройден? |
|---|---|---|---|
| I | Self-contained автопайплайн | Не затрагивается: фича — UI-обёртка над существующим поиском/сохранением обложки. Никаких внешних SaaS в горячем пути. | ✅ |
| II | Сырой JDBC + дифф по хэшам | Не затрагивается: DTO `AlbumDTO` и `tbl_albums`/`tbl_songs` не меняются. Новый endpoint использует существующий `KaraokeConnection` (helper `getFirstSongId`). | ✅ |
| III | Двух-БД синхронизация через SyncRegistry | Не затрагивается: новый endpoint `/api/albums/firstsongid` НЕ участвует в sync (не добавлен в `SyncRegistry.all`). | ✅ |
| IV | Async-очередь + `redirectErrorStream(true)` | Не затрагивается: фича не запускает подпроцессов. | ✅ |
| V | Двух-фронтенд: admin / public разделены | Соблюдается: фича только в `webvue3` (admin). `karaoke-public` и `karaoke-web` не затрагиваются. `AlbumCoverModal.vue` живёт в `webvue3/src/components/Songs/edit/`, импорт в `AlbumsTable.vue` (тоже `webvue3`) — корректно. | ✅ |
| VI | Code Standards (FR-006/007/009) | При реализации (в `tasks.md`): KDoc на новых публичных API (`fun getFirstSongId`, `fun apisGetFirstSongIdByAlbumId`); JSDoc на новых Vuex actions; per-feature документ НЕ создаётся (фича не входит в 9 ключевых подсистем `docs/features/`) — но запись в `docs/architecture-notes.md` обязательна. | ✅ (после реализации) |
| VII | Cross-Machine Setup | Не затрагивается: личные AI-конфиги и `.git-blame-ignore-revs` не редактируются. | ✅ |

**Дополнительные MUST из конституции:**
- Без `redirectErrorStream(false)` — неприменимо (нет подпроцессов).
- Без `nginx:alpine` / `node:latest` — неприменимо (нет изменений в Docker-образах).
- KDoc/JSDoc обязательны — отражено в `tasks.md` (после `/speckit.tasks`).
- ESLint/ktlint не должны падать на новом коде — отражено в `tasks.md`.

**Нарушений нет.** GATE пройден.

## Project Structure

### Documentation (this feature)

```text
specs/014-album-cell-album-cover-modal/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── api.md           # Новый endpoint + UI-контракты
├── checklists/
│   └── requirements.md  # (создан на /speckit.specify)
└── spec.md              # (создан на /speckit.specify)
```

### Source Code (repository root)

Затрагиваемые файлы (для `tasks.md`):

```text
karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/Album.kt                       # + fun getFirstSongId(albumId, database): Long?
└── controllers/ApiController.kt         # + @PostMapping("/albums/firstsongid")

webvue3/src/components/Albums/
├── AlbumsTable.vue                      # + cell(albumPicture)/cell(name) handlers,
│                                        #   + isAlbumCoverModalVisible, prevCurrentSongId,
│                                        #   + <AlbumCoverModal>, + openAlbumCoverModal(),
│                                        #   + closeAlbumCoverModal(), + onAlbumCoverSaved(),
│                                        #   + import AlbumCoverModal, + canEditCover
└── store.js                             # + action getFirstSongIdByAlbumIdPromise

webvue3/src/components/Songs/store.js    # БЕЗ изменений (переиспользуем setCurrentSongIdOnly)
webvue3/src/components/Songs/edit/AlbumCoverModal.vue  # БЕЗ изменений
```

**Structure Decision**: Web application (Option 2) — фича распределена между `karaoke-app` (новый endpoint) и `webvue3` (UI). `karaoke-web` и `karaoke-public` не затрагиваются. `karaoke-db` — legacy, не используется.

## Complexity Tracking

> **Не заполняется** — Constitution Check не выявил нарушений, упрощения не требуются.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| (нет) | — | — |

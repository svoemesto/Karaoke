# Implementation Plan: 190-playlist-play-button-and-stems-cancel

**Branch**: `190-playlist-play-button-and-stems-cancel` | **Date**: 2026-08-14 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/190-playlist-play-button-and-stems-cancel/spec.md`
**Phase 0**: [research.md](research.md)
**Phase 1**: [data-model.md](data-model.md), [contracts/api-public-playlist-detail.md](contracts/api-public-playlist-detail.md), [quickstart.md](quickstart.md)

## Summary

Три фичи в одном релизе:
1. **P1 Запуск с любой песни** — кнопка ▶ в каждой строке плейлиста, вызывает `playid` postMessage (handler уже есть в `PlayerView.vue:139`).
2. **P2 Превью альбома и автора** — два новых поля в `SitePlaylistItemDto` (`albumPictureUrl`, `authorPictureUrl`); оба формируются на бэкенде по предсказуемым storage-ключам MinIO (паттерн `AuthorTilePublicDto`, Pass 50).
3. **P1 Фикс задвоения вейвформ** — `AbortController` в `KaraokePlayer` отменяет все in-flight fetch предыдущей песни в начале `playSong()`; защитный destroy в `_buildWaveforms()`.

Технический подход — минимум инвазии: 2 поля в DTO (без миграции БД), 1 контроллер расширен, 1 Vue-компонент дополнен, плеер получает `_activeAbortController`.

## Technical Context

| Поле | Значение | Источник |
|---|---|---|
| **Language/Version (backend)** | Kotlin 1.x, JDK 17, Spring Boot 2.x/3.x | `.specify/memory/constitution.md` |
| **Language/Version (frontend)** | Vue 3 + Vite, Node 22, Bootstrap 5 | То же |
| **Primary Dependencies (backend)** | Сырой JDBC через `KaraokeConnection`, Jackson, Spring Web | Constitution §Core Principles II |
| **Primary Dependencies (frontend, плеер)** | `KaraokePlayer.js` (vanilla JS, Web Audio API), `wavesurfer.js` (динамический import), `vuedraggable` (уже есть) | `karaoke-public/src/player/KaraokePlayer.js:1433` |
| **Storage** | PostgreSQL (сырой JDBC), MinIO (S3-compatible) | Constitution §Технологический стек |
| **Testing** | В CI нет; `@Disabled` интеграционные; проверка — пользователем | Constitution §Тесты |
| **Target Platform** | Linux server (karaoke-web) + node 22-alpine (karaoke-public) | Constitution §Runtime |
| **Project Type** | Multi-module Gradle (karaoke-app, karaoke-web) + Vue 3 SPA (karaoke-public) | Constitution §Технологический стек |
| **Performance Goals** | SC-001..006 — UI-фича; sub-second не требуется | spec.md |
| **Constraints** | (a) karaoke-web — read-only к MinIO (через nginx); (b) karaoke-app НЕ разворачивается на проде; (c) FR-012 — поведение вне плейлиста MUST остаться неизменным | Constitution + spec.md |
| **Scale/Scope** | ≤200 песен в плейлисте; 18k+ песен в БД | Constitution + `PublicPlaylistController.kt:51-60` |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Принцип | Статус | Обоснование |
|---|---|---|
| **I. Self-contained автопайплайн** | ✅ Pass | Фича UI/data, не затрагивает пайплайн обработки медиа. |
| **II. Сырой JDBC + дифф по хэшам** | ✅ Pass | Новые поля НЕ аннотированы `@KaraokeDbTableField` → не пишутся в БД → не входят в `recordhash` → sync не затрагивается (см. [data-model.md §Sync implications](data-model.md)). |
| **III. Двух-БД синхронизация** | ✅ Pass | `tbl_site_playlist_items` уже в `SyncRegistry.all` (см. `SyncTarget.kt`); новые поля не требуют sync-флагов (transient/runtime). |
| **IV. Async-очередь задач** | ✅ N/A | UI-фича, нет OS-процессов. |
| **V. Двух-фронтенд (admin/public)** | ✅ Pass | Только `karaoke-public` трогаем (плеер + страница плейлиста); `webvue3` — нет. |
| **VI. Code Standards** | ⚠️ Требует проверки | KDoc для публичных API + JSDoc для Vue-компонентов (см. FR-006 Constitution). ESLint/ktlint — без новых baseline-нарушений. |
| **VII. Cross-Machine Setup** | ✅ Pass | Не правим AI-конфиги, `.git-blame-ignore-revs`, `.gitattributes`. |
| **VIII. Секреты и git-гигиена** | ✅ Pass | Не добавляем секрет-файлы; `deploy/.env` и т.п. — не трогаем. |

**Re-check после Phase 1**: подтверждено — `data-model.md` явно указывает, что новые поля **НЕ аннотированы** `@KaraokeDbTableField`, не пишутся в БД, не попадают в `recordhash`. Sync-инвариант Constitution III соблюдён.

## Project Structure

### Documentation (this feature)

```text
specs/190-playlist-play-button-and-stems-cancel/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — Technical decisions
├── data-model.md        # Phase 1 — SitePlaylistItemDto + UI state
├── contracts/
│   └── api-public-playlist-detail.md   # Phase 1 — JSON schema
├── quickstart.md        # Phase 1 — ручные сценарии проверки
├── spec.md              # Исходная спека (уже есть)
├── checklists/
│   └── requirements.md  # Quality checklist (уже есть)
└── tasks.md             # Phase 2 — /speckit.tasks (НЕ создаётся этим планом)
```

### Source Code (repository root)

**Структура — multi-module Gradle + Vue 3 SPA (уже существующая, см. [AGENTS.md §Где правила](../../AGENTS.md))**:

```text
karaoke-app/
├── src/main/kotlin/com/svoemesto/karaokeapp/
│   ├── model/
│   │   ├── SitePlaylistItem.kt        # +2 transient поля
│   │   └── SitePlaylistItemDto.kt     # +2 поля с дефолтом ""
│   └── controllers/
│       └── (без изменений)

karaoke-web/
├── src/main/kotlin/com/svoemesto/karaokeweb/
│   ├── controllers/
│   │   └── PublicPlaylistController.kt   # +заполнение 2 полей в playlistDetail()
│   └── dto/
│       └── (без изменений — SitePlaylistItemDto живёт в karaoke-app)

karaoke-public/
├── src/views/
│   └── PlaylistEditView.vue    # +▶ кнопка в строке, +2 превью, +playSongFromIndex
├── src/player/
│   └── KaraokePlayer.js        # +_activeAbortController, +signal в fetch, +_abortActive()
└── src/services/
    └── playlistApi.js          # без изменений (поля приходят в JSON)
```

**Structure Decision**: существующая структура (karaoke-app / karaoke-web / karaoke-public) полностью подходит; никаких новых модулей или под-проектов не требуется. Минимальная инвазия в 4 файла: 1 entity, 1 DTO, 1 контроллер, 1 Vue-view, 1 плеер.

## Complexity Tracking

> **Не заполнено** — Constitution Check пройден без нарушений.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| — | — | — |

## Phase 0 → Phase 1 → Phase 2 handoff

Phase 0 завершён — все технические решения зафиксированы в [research.md](research.md) (10 решений: D1..D10).

Phase 1 завершён:
- [data-model.md](data-model.md) — `SitePlaylistItem` + `SitePlaylistItemDto` с 2 новыми полями; UI state с 2 fallback-флагами; формулы URL.
- [contracts/api-public-playlist-detail.md](contracts/api-public-playlist-detail.md) — JSON-схема ответа `/api/public/account/playlists/{id}`, формат URL, backward compatibility.
- [quickstart.md](quickstart.md) — 7 ручных сценариев + чек-лист отчёта.

**Готовность к Phase 2** (`/speckit.tasks`):
- Все технические решения однозначны → tasks можно декомпозировать на конкретные файлы/функции.
- Никаких NEEDS CLARIFICATION → план полон.
- 5 рисков (R1..R4 + R4 в research.md) уже митигированы в D-решениях.

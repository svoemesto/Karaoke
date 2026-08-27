# Implementation Plan: 259-playlist-clickable-links

**Branch**: `259-playlist-clickable-links` | **Date**: 2026-08-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/259-playlist-clickable-links/spec.md`

## Summary

Сделать название песни и имя автора в строках плейлиста/избранного
(`PlaylistEditView.vue`) интерактивными ссылками: клик по названию песни →
страница этой песни (`/song?id=<id>`), клик по имени автора → «Закрома»
этого автора (`/zakroma/<authorId>`).

Дополнительно (после рефакторинга по замечанию пользователя): back-link в
шапке `SongView` теперь читает `authorId` прямо из `SongPublicDto`
(заполняется в `PublicApiController.song()` через `Author.loadIdsByNames`),
а не из Vuex-store `lastSongReferrer`. Это устраняет race в `mounted`
(Vue Router 4 переиспользует инстанс `ZakromaView` при `/zakroma → /zakroma/<id>`)
и всю категорию связанных багов. Старый механизм `lastSongReferrer` удалён.

## Technical Context

**Language/Version**: Kotlin 1.x (бэк), JavaScript ES2022 (Vue 3 фронт)

**Primary Dependencies**:
- Vue 3 (Composition + Options API mix), `vue-router` 4, `vuex`
- Kotlin + Spring Boot, `KaraokeConnection`, `JdbcTemplate`
- Bootstrap 5 + CSS-переменные `--km-*`

**Storage**: N/A (никаких изменений в БД; `SongPublicDto.authorId` — derived field)

**Testing**: ручная проверка в браузере

**Target Platform**: SPA `karaoke-public` + Spring-boot `karaoke-web`

**Project Type**: full-stack фича — 2 файла на бэке (`SongPublicDto`, `PublicApiController`), 5 файлов на фронте (3 view + 1 store).

**Performance Goals**: +1 batch-SELECT (`Author.loadIdsByNames`) на загрузку страницы песни. Никаких новых HTTP-запросов на клик в плейлисте/поиске.

**Constraints**:
- FR-011: страница песни `/song?id=<id>` без `authorId` в URL (контракт спеки 258).
- FR-013: `SongPublicDto.authorId` nullable — fallback на общий `/zakroma` если автор удалён.
- FR-014: Vuex `zakroma.lastSongReferrer` НЕ используется (state/getter/mutation удалены).

**Scale/Scope**: 7 файлов изменено (2 бэк + 5 фронт).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Принцип | Статус | Комментарий |
|---|---------|--------|-------------|
| I  | Self-contained автопайплайн | ✅ N/A | Не касается медиа-пайплайна. |
| II | Сырой JDBC + дифф по хэшам | ✅ N/A | `Author.loadIdsByNames` уже использует сырой JDBC (см. Author.kt:302). |
| III | Двух-БД синхронизация через SyncRegistry | ✅ N/A | Не вводим новых сущностей. |
| IV | Async-очередь задач с парсингом stdout | ✅ N/A | Не используем ProcessBuilder. |
| V  | Двух-фронтенд (admin / public) | ✅ PASS | Меняем ТОЛЬКО `karaoke-public` (`PlaylistEditView`, `SongView`, `ZakromaView`, `SearchView`, `zakroma` store) и `karaoke-web` (`PublicApiController`, `SongPublicDto`). Не трогаем `webvue3`. |
| VI | Code Standards | ✅ PASS | Линтеры ktlint/ESLint. Новые строки добавлены в KDoc/JSDoc стиле. Baseline 0. |
| VII | Cross-Machine Setup | ✅ N/A | Никаких изменений в `.gitignore`, `.gitattributes`, `.git-blame-ignore-revs`. |
| VIII | Секреты и git-гигиена | ✅ N/A | Не вводим секретов. |

**GATE: PASS**. Никаких нарушений Constitution.

## Project Structure

### Documentation (this feature)

```text
specs/259-playlist-clickable-links/
├── spec.md              # /speckit.specify output (✅ обновлён после рефакторинга)
├── plan.md              # This file (/speckit.plan output)
└── tasks.md             # /speckit.tasks output (✅ обновлён)
```

### Source Code (repository root)

```text
karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/
├── dto/SongPublicDto.kt                # EDIT — добавлено authorId
└── controllers/PublicApiController.kt  # EDIT — резолв authorId через Author.loadIdsByNames

karaoke-public/src/
├── store/modules/zakroma.js            # EDIT — удалён lastSongReferrer
├── views/SongView.vue                  # EDIT — songHeaderBack читает currentSong.authorId
├── views/PlaylistEditView.vue          # EDIT — кликабельные ссылки + authorId через authorTiles
├── views/SearchView.vue                # EDIT — кликабельные ссылки (без lastSongReferrer)
└── views/ZakromaView.vue               # EDIT — удалена логика lastSongReferrer
```

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

Не требуется — нарушений нет.

## Implementation Notes

### Бэк (минимально-инвазивно)

1. **`SongPublicDto.kt`**: + nullable `authorId: Long? = null`. Поле не заполняется в `fromSong(s)` — резолв делается в контроллере (там есть `db`).

2. **`PublicApiController.song(id)`**: после `Song.loadFromDbById`:
   ```kotlin
   val authorIds = Author.loadIdsByNames(listOf(s.author), WORKING_DATABASE)
   SongPublicDto.fromSong(s).copy(assignment = assignmentDto, authorId = authorIds[s.author])
   ```
   `Author.loadIdsByNames` уже существует (Author.kt:302), используется в `/authors-tiles`. Делает 1 batch-SELECT: `SELECT id, author FROM tbl_authors WHERE author IN (?)`.

### Фронт

1. **`SongView.songHeaderBack()`**: читает `this.currentSong.authorId` → если есть, возвращает `{ name: 'zakroma-author', params: { authorId: String(song.authorId) } }`. Если `authorId = null` (автор удалён из БД) — fallback на `/zakroma`.

2. **`PlaylistEditView`**: имя автора — `<router-link :to="{ name: 'zakroma-author', params: { authorId: authorIdFor(item.author) } }">` через кэш `state.zakroma.authorTiles`. Никакого `lastSongReferrer`.

3. **`SearchView`** и **`ZakromaView`**: аналогично — никаких `@click` на `<RouterLink>` названия песни. Просто SPA-навигация на `/song?id=X`.

4. **`store/modules/zakroma.js`**: удалены `lastSongReferrer` state/getter/mutation (dead code).

### Почему НЕ через referrer (предыдущий подход)

Предыдущая итерация спеки 259 использовала Vuex `lastSongReferrer`, который ZakromaView/PlaylistEditView/SearchView устанавливали при клике или в mounted. Это работало, но:

- **Race в `mounted`**: `loadAuthorTiles` в ZakromaView.mounted() запускается без `await`. На первом заходе `authorTiles` пустой → `find()` возвращает undefined → `referrer = null`.
- **Vue Router 4 переиспользует инстанс компонента**: при `/zakroma → /zakroma/<id>` компонент НЕ пересоздаётся → `mounted` НЕ вызывается → referrer не ставится.
- **Сложность на ровном месте**: 3 view пишут в Vuex, SongView читает — много мест, где можно ошибиться.

Текущий подход — `authorId` из DTO песни — устраняет все три проблемы. `Song.author` однозначно резолвится в `tbl_authors.id`, бэк передаёт его в DTO, фронт использует напрямую. Никакой логики «откуда пришёл пользователь».

## Acceptance Check (маппинг FR → план)

| FR | Как покрывается |
|----|------------------|
| FR-001 | `<router-link>` на `item.songName` → `{ name: 'song', query: { id: item.songId } }` (PlaylistEditView) |
| FR-002 | `<router-link>` на `item.author` → `{ name: 'zakroma-author', params: { authorId: authorIdFor(item.author) } }` (PlaylistEditView) |
| FR-003 | `<router-link>` на `sett.songName` в SearchView, без изменений в обработчиках |
| FR-004 | `<router-link>` на `sett.songName` в ZakromaView, без `@click` |
| FR-005 | Back-link в SongView → `currentSong.authorId` из DTO |
| FR-006 | `<router-link>` через `authorTiles` Vuex-кэш для имени автора |
| FR-007 | CSS `var(--km-accent)` + hover/focus подчёркивание |
| FR-008 | Маршрут `/song?id=<id>` без authorId (контракт спеки 258) |
| FR-009 | Маршрут `/zakroma/:authorId` (по числовому ID) |
| FR-010 | CSS-переменные `--km-*` в обоих дизайнах |
| FR-011 | Не ломаем drag-drop/mute/play (`<router-link>` не запускает плеер) |
| FR-012 | `SongPublicDto.authorId` в бэке, nullable fallback |
| FR-013 | `lastSongReferrer` удалён из стора |

## Risks & Mitigations

- **R1**: имя автора в `tbl_songs` не совпадает с записью в `tbl_authors` (орфография, удаление). → Mitigation: `authorId = null` → SongView fallback на `/zakroma` (мягко, без падения).
- **R2**: `Author.loadIdsByNames` — новый SQL-запрос на каждую загрузку `/song?id=X`. → Mitigation: один batch-SELECT (не N+1), типичная страница песни грузится за 1 запрос дополнительно. Пренебрежимо.
- **R3**: `SongPublicDto.authorId` — breaking change для старого фронта (без этой фичи). → Mitigation: поле `Long? = null` (default), старый фронт просто не читает поле. Совместимо.

## Next Steps

1. ✅ Все задачи T001-T008 (предыдущая итерация) + удаление `lastSongReferrer` (текущая).
2. ✅ Backend compile + bootJar.
3. ✅ Frontend lint + build.
4. ⏳ Commit + push + PR через `gh pr create --base master`.
# Tasks: 259-playlist-clickable-links

**Input**: Design documents from `/specs/259-playlist-clickable-links/`
- spec.md (✅)
- plan.md (✅)

**Prerequisites**: plan.md (✅), spec.md (✅)

**Tests**: тесты в CI отсутствуют (`karaoke-app/src/test` — `@Disabled`, см. Constitution). Фича проверяется пользователем вручную.

**Organization**: задачи сгруппированы по слоям (бэк / фронт) — T001-T004 (бэк + базовый фронт из первой итерации), T005-T009 (рефакторинг через `authorId` из DTO после замечания пользователя), T010-T011 (проверки). Все в одной feature-ветке, scope одного PR.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: параллельно (если файлы разные и нет зависимостей)
- **[Story]**: US1 / US1.b / US2 / US3

## Phase 1: Бэкенд — `SongPublicDto.authorId`

**Цель**: back-link в SongView получает `authorId` прямо из DTO песни (FR-012).

- [x] T001 [US1.b] Добавить `val authorId: Long? = null` в `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt`.
- [x] T002 [US1.b] В `PublicApiController.song(id)` после `Song.loadFromDbById` резолвить `Author.loadIdsByNames([s.author], db)` и передать в `SongPublicDto.copy(authorId = ...)`.

## Phase 2: Фронт — `SongView.songHeaderBack` через `authorId`

**Цель**: back-link ведёт на `/zakroma/<authorId>` без referrer'ов через Vuex (FR-005, FR-013).

- [x] T003 [US1.b] В `karaoke-public/src/views/SongView.vue` переписать `songHeaderBack()`: читать `this.currentSong.authorId`, возвращать `{ name: 'zakroma-author', params: { authorId: ... } }`. Fallback на `/zakroma` если `authorId = null`.

## Phase 3: Фронт — кликабельные ссылки в плейлисте/поиске/закромах

**Цель**: US1 + US2 — клик по названию песни → `/song`, клик по имени автора → `/zakroma/<authorId>`.

- [x] T004 [US1] [US2] В `karaoke-public/src/views/PlaylistEditView.vue` обернуть `<div class="km-song-title">` в `<router-link :to="{ name: 'song', query: { id: item.songId } }">` (FR-001).
- [x] T005 [US2] В том же файле обернуть `<span>{{ item.author }}</span>` в `<router-link :to="{ name: 'zakroma-author', params: { authorId: authorIdFor(item.author) } }">` (FR-002, FR-006). Добавить хелпер `authorIdFor(name)` через `state.zakroma.authorTiles`.
- [x] T006 [US1] [US2] В `karaoke-public/src/views/SearchView.vue` — `<RouterLink :to="{ path: '/song', query: { id: sett.id } }">` уже есть, без `@click` (избегаем referrer-логики).
- [x] T007 [US1] В `karaoke-public/src/views/ZakromaView.vue` — то же самое: `<RouterLink :to="{ path: '/song', query: { id: sett.id } }">` без `@click`.

## Phase 4: Фронт — CSS + утилиты

- [x] T008 [US1] [US2] В `<style scoped>` `PlaylistEditView.vue` добавить `.km-song-title-link` / `.km-song-author-link` с `var(--km-accent)`, `display: block`, underline на hover/focus (FR-007, FR-010).

## Phase 5: Удаление dead code — `lastSongReferrer`

**Цель**: вся старая логика `lastSongReferrer` (state, getter, mutation, watcher'ы, `@click` handler'ы, `onSongTitleClick` методы) удалена — после Phase 2 никто её не читает.

- [x] T009 [US1.b] Удалить `lastSongReferrer` из `state`/`getters`/`mutations` в `karaoke-public/src/store/modules/zakroma.js`. Удалить все `setLastSongReferrer(...)` вызовы в `ZakromaView.mounted`, `ZakromaView.$route.fullPath` watcher, `ZakromaView.onAuthorSelect`, `PlaylistEditView.onSongTitleClick`, `SearchView.onSongTitleClick`. Удалить `@click` обработчики на `<router-link>` / `<RouterLink>` названия песни в трёх view. Удалить `onSongTitleClick` методы и связанные computed/methods (authorTiles, authorIdFor остаются — нужны для кликабельной ссылки на имя автора).

## Phase 6: Проверки (NON-NEGOTIABLE per AGENTS.md)

- [x] T010 [P] Backend compile + bootJar: `./gradlew :karaoke-web:compileKotlin :karaoke-web:bootJar --parallel` — успешно.
- [x] T011 [P] Frontend: `cd karaoke-public && npm run lint && npm run build` — 0 ошибок, 0 новых нарушений baseline.

## Phase 7: Документация

- [x] T12 Обновить `specs/259-playlist-clickable-links/spec.md` (US1.b, FR-012, FR-013, SC-005, SC-006, обновлённые Assumptions).
- [x] T13 Обновить `specs/259-playlist-clickable-links/plan.md` (новый подход через `authorId`).

---

## Dependencies & Execution Order

- **Phase 1** (T001-T002) → независимо (бэк отдельно от фронта).
- **Phase 2** (T003) → зависит от T001-T002 (читает `authorId` из DTO).
- **Phase 3-5** (T004-T009) → независимо от Phase 1-2 (можно делать параллельно, но на одной ветке).
- **Phase 6** (T010-T011) → после Phase 1-5.
- **Phase 7** (T12-T13) → параллельно с реализацией.

## Parallel Opportunities

- T001-T002 параллельно с T003 (но T003 требует результата T001-T002 для type-checking).
- T004-T009 последовательно (все правят `*.vue` / `*.js`, пересекающиеся файлы).

## Implementation Strategy

### Финальная архитектура

```
GET /api/public/song/{id}
  ↓
  Song.loadFromDbById(id) → Song{author: "Кино", ...}
  Author.loadIdsByNames(["Кино"], db) → {"Кино": 123}
  SongPublicDto.fromSong(s).copy(authorId = 123)
  ↓
  JSON: { id, songName, author: "Кино", authorId: 123, ... }

Vue SongView:
  currentSong = { author: "Кино", authorId: 123, ... }
  songHeaderBack() → { name: 'zakroma-author', params: { authorId: '123' }, label: '← К песням «Кино»' }
```

Никаких `lastSongReferrer`, `commit('zakroma/setLastSongReferrer')`, watcher'ов, race-условий.

## Notes

- Все правки изолированы: 2 файла на бэке (`SongPublicDto`, `PublicApiController`) + 5 файлов на фронте (3 view, 1 store, 1 DTO на фронте не меняется).
- Старая логика спеки 258 (`lastSongReferrer`) — удалена полностью, потому что `authorId` из DTO её заменяет на 100%.
- Commit-сообщение: `259: clickable song/author links + authorId в DTO песни`.
- Push и PR через `gh pr create --base master`.
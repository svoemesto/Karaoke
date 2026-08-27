---
status: Active
slug: 259-playlist-clickable-links
related:
  - ../../specs/259-playlist-clickable-links/spec.md
  - ../../specs/259-playlist-clickable-links/plan.md
  - ../../specs/259-playlist-clickable-links/tasks.md
  - 258-zakroma-routing-refactor
  - 250-unify-site-header
---

# 259 — Кликабельные название песни и автор в плейлисте/избранном (LiveDoc)

> Drill-down — [specs/259-playlist-clickable-links/spec.md](../../specs/259-playlist-clickable-links/spec.md),
> [plan.md](../../specs/259-playlist-clickable-links/plan.md),
> [tasks.md](../../specs/259-playlist-clickable-links/tasks.md).

## Что делает

Делает название песни и имя автора в строках плейлиста/избранного
(`PlaylistEditView.vue`) интерактивными ссылками:

| Элемент строки | Куда ведёт |
|----------------|-------------|
| Название песни | `/song?id=<songId>` — страница этой песни |
| Имя автора | `/zakroma/<authorId>` — «Закрома» этого автора |

Дополнительно (US1.b): back-link в шапке `SongView` ведёт на страницу песен
автора (`/zakroma/<authorId>`), а не на общий `/zakroma`. Это работает для
любого сценария входа на `/song`: плейлист, поиск, закрома, прямой URL,
share-сессия.

## Ключевое решение: `SongPublicDto.authorId`

**Источник истины для back-link — DTO песни, а не Vuex `lastSongReferrer`.**

`Song.author` (свободный текст) однозначно резолвится в `tbl_authors.id`
через `Author.loadIdsByNames([s.author], db)` — один batch-SELECT в
`PublicApiController.song()`. `SongPublicDto.authorId: Long?` передаётся
фронту, `SongView.songHeaderBack()` читает его напрямую.

### Почему не через Vuex `lastSongReferrer` (предыдущая итерация)

1. **Race в `mounted`**: `loadAuthorTiles('main')` в `ZakromaView.mounted()` —
   fire-and-forget, без `await`. На первом заходе `authorTiles` пустой →
   `find()` возвращает undefined → referrer = null.
2. **Vue Router 4 переиспользует инстанс компонента**: при `/zakroma → /zakroma/<id>`
   компонент НЕ пересоздаётся → `mounted` НЕ вызывается → referrer не ставится.
3. **Сложность на ровном месте**: 3 view пишут в Vuex, SongView читает —
   много мест, где можно ошибиться (спец-корзина vs обычный автор, тайтлы vs
   выбранный автор).

Текущий подход устраняет все три проблемы. `Song.author` всегда известен
по самой песне — никакого «откуда пришёл пользователь».

## Изменённые файлы

### Backend

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` —
  добавлено `val authorId: Long? = null`. Nullable: `null` если автор удалён
  из `tbl_authors` (мягкий fallback в SongView на общий `/zakroma`).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt`
  — в `/api/public/song/{id}` после `Song.loadFromDbById` резолвит
  `Author.loadIdsByNames([s.author], db)` и передаёт в DTO.
  Используется существующий helper `Author.loadIdsByNames` (см. спеку 258, RT-1.A1).

### Frontend

- `karaoke-public/src/views/SongView.vue` — `songHeaderBack()` читает
  `currentSong.authorId`. Fallback на общий `/zakroma` если `authorId = null`.
- `karaoke-public/src/views/PlaylistEditView.vue` — `<router-link>` на название
  песни (`/song`) и имя автора (`/zakroma/<authorId>` через
  `state.zakroma.authorTiles`). CSS-классы `.km-song-title-link` /
  `.km-song-author-link` (цвет `var(--km-accent)`, underline на hover/focus).
- `karaoke-public/src/views/SearchView.vue` — `<RouterLink>` названия песни
  уже был; оставлен как есть (без `@click` — клик = SPA-навигация).
- `karaoke-public/src/views/ZakromaView.vue` — `<RouterLink>` названия песни
  уже был; оставлен как есть. Удалена логика `lastSongReferrer` (mount,
  watcher, `onSongTitleClick`, `onAuthorSelect`).
- `karaoke-public/src/store/modules/zakroma.js` — удалены `lastSongReferrer`
  state/getter/mutation (dead code после введения `authorId` в DTO).

## Что НЕ делает

- Не меняет `SitePlaylistItemDto` (бэк уже возвращает `author` — имя).
- Не добавляет `authorId` в DTO для других списков (Zakroma, Search,
  Playlist items) — SongView получает данные через `/api/public/song/{id}`
  отдельным запросом, и там уже есть `authorId`.
- Не меняет URL `/song?id=X` (без `authorId` — контракт спеки 258, FR-A8).
- Не делает кликабельные картинки (cover/author pic) в строке плейлиста —
  out of scope.

## Backward compatibility

- `SongPublicDto.authorId = null` по умолчанию — старый фронт просто не
  читает поле. Совместимо.
- Удаление `lastSongReferrer` из стора — breaking для любого кода, который
  читал `state.zakroma.lastSongReferrer`. В проекте таких мест больше нет
  (grep чисто).

## Связь с другими фичами

- **Спека 258 (`zakroma-routing-refactor`)** — back-link в SongView РАНЬШЕ
  строил по Vuex `lastSongReferrer`, который ZakromaView устанавливал в
  `mounted`. Эта зависимость устранена: SongView читает `authorId` из DTO.
  Удаление `lastSongReferrer` — финальный шаг отхода от спеки 258.
- **Спека 250 (`unify-site-header`)** — без изменений. `AppHeader.back`
  уже поддерживает форму `{ name, params, label }` для named routes.
- **FR-014 (`site-traffic-resilience`)** — без изменений. `loadAuthorTiles`
  остаётся в сторе с дедупом 30 c; используется только для кликабельной
  ссылки имени автора в плейлисте.
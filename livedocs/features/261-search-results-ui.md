---
status: Active
slug: 261-search-results-ui
related:
  - ../../specs/261-search-results-ui/spec.md
  - ../../specs/261-search-results-ui/plan.md
  - ../../specs/261-search-results-ui/tasks.md
  - 259-playlist-clickable-links
  - 239-zakroma-author-songs-batch-render
---

# 261 — Иконка плеера и редизайн строк результатов поиска (LiveDoc)

> Drill-down — [specs/261-search-results-ui/spec.md](../../specs/261-search-results-ui/spec.md),
> [plan.md](../../specs/261-search-results-ui/plan.md),
> [tasks.md](../../specs/261-search-results-ui/tasks.md).

## Что делает

Два изменения для публичного поиска `/search`:

1. **Bug-фикс иконки плеера.** Раньше `<PlayerIcon :content-ready-state>`
   в строке поиска всегда получал `'notready'` → иконка всегда серая,
   независимо от реального статуса песни (в эфире / готовая не в эфире /
   неготовая). Причина — `SongPublicDto` не содержал поле `contentReady`.
   Теперь поле есть → иконка зелёная для «в эфире», золотая (демо) для
   готовой не в эфире, серая для неготовой — как в Закромах и на странице
   песни.

2. **Визуальное соответствие плейлисту.** Строка результата поиска теперь
   попиксельно совпадает со строкой `PlaylistEditView`: чёрная плашка
   с превью обложки альбома (48×48) и превью автора (120×48, аспект 5:2),
   название песни кликабельной ссылкой, под ним подпись «Автор - год,
   альбом», иконки действий справа. Применяется одинаково на десктопе
   и мобиле — старая ветка `<div class="km-cards">` для мобильного
   удалена (Clarification Q1 → A).

## Ключевое решение: единая row-разметка, никакой card-ветки

`PlaylistEditView.vue` (эталонная страница) НЕ имеет отдельной
мобильной ветки: один `<div class="km-song-row">` рендерится через
адаптивный flex на любом вьюпорте. Search-результаты раньше имели
две ветки рендера (`<table>` + `<div class="km-cards">` через media-query
768px) — это расхождение устранено: одна разметка, один CSS.

### Почему не выделено в общий `<SongRow>` компонент

- Diff остаётся минимальным: 1 DTO + 1 controller-helper вставка +
  1 вьюха перерисовывается, без новых компонентов.
- `PlaylistEditView.vue` сам НЕ извлекает строку — для одной вьюхи
  лишний `<SongRow>`-компонент преждевременная абстракция.
- Если в будущем понадобится 3+ потребителя — отдельной задачей.
  Сейчас копия стилей и шаблона работает и DRY-acceptable.

## Изменённые файлы

### Backend

- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/dto/SongPublicDto.kt` —
  3 новых поля: `contentReady: Boolean`, `albumPictureUrl: String`, `authorPictureUrl: String`.
  Сигнатура `fromSong(s, includeDetails, albumPictureUrl="", authorPictureUrl="")`
  расширена двумя trailing-args (defaults `""` для обратной совместимости
  с другими call-site'ами).
- `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt` —
  в методе `songs(...)` два private-helper'а (`albumPreviewUrlForSong` /
  `authorPreviewUrlForName`) дублируют паттерн `PublicPlaylistController.albumPreviewUrl` /
  `.authorPreviewUrl` (тот же шаблон ключа MinIO: `${author}/${author}.preview.author.png`).
  Никаких batch-lookup'ов в `tbl_pictures` — превью строятся из полей
  песни, верификация существования в MinIO отдаётся на фронт через
  `@error` фолбэк (FR-005).

### Frontend

- `karaoke-public/src/views/SearchView.vue` — полная перерисовка:
  - удалены `<table class="km-table">` и `<div class="km-cards">`,
    вместо них `<div class="km-song-list">` + `<div class="km-song-row">`;
  - добавлены `km-song-pictures` (чёрная плашка с двумя `<img>` +
    плейсхолдеры `♪`/`👤`), `km-song-info` (router-link название +
    `km-song-sub` «Автор - год, альбом»), `km-song-actions` (иконки
    плеер/корзина/премиум/избранное/плейлист + подпись эфира);
  - `<style scoped>` с CSS, скопированным из PlaylistEditView.vue:801-995;
  - `<img>` с `loading="lazy" decoding="async"` (D5, важно для
    mobile viewport и больших результатов);
  - `@error` handler с transient-state `imageErrors` (Map по
    `${song.id}:${kind}`), GC по новому списку;
  - `authorTiles` + `authorIdFor(name)` из Vuex-стора `zakroma` для
    кликабельных ссылок имени автора на «Закрома»;
  - `setup()` async-mount для догрузки `authorTiles` (mirror
    PlaylistEditView.vue:575-582).

## Что НЕ делает

- Не меняет другие страницы (ZakromaView, AuthorPlaylistView) —
  иконка плеера там работала корректно (Pass 239), спека 261
  закрывает только Search.
- Не выносит row в общий `<SongRow>`-компонент (см. «Ключевое
  решение» выше).
- Не делает кликабельными превью альбома/автора (cover/author pic) —
  out of scope (FR-004 говорит только про текстовые ссылки).
- Не добавляет `contentReady`/`albumPictureUrl`/`authorPictureUrl`
  в другие DTO (ZakromaPublicDto, SitePlaylistItemDto) — Scope 261
  строго `/api/public/songs`.

## Связь с другими фичами

- **Спека 259 (`playlist-clickable-links`)** — кликабельные название
  песни и имя автора в строке плейлиста/избранного; эта спека
  расширяет контракт 259 (FR-007/FR-008 спеки 261 полностью
  идентичны FR-001/FR-002 спеки 259) на страницу поиска. Code-share
  минимален: только `authorIdFor` + `authorTiles` boilerplate.
  Картинки превью скопированы из строк PlaylistEditView, никаких
  новых компонентов.
- **Pass 239 (`zakroma-author-songs-batch-render`)** — иконка плеера
  без per-row readiness, передача готовности через props. До спеки
  261 search-results получал `contentReady === undefined` из-за
  отсутствия поля в `SongPublicDto`; теперь поле доставляется тем же
  путём, что и в ZakromaAlbumSongPublicDto.
- **Спека 250 (`unify-site-header`)** — без изменений. `AppHeader`
  используется как раньше.
- **PublicPlayerController.stemsReady** — единственный источник
  истины для `isContentReady`; `SongPublicDto.contentReady` — это
  зеркало, не дубликат логики.

## Производительность

- Бэк: batch-lookup НЕ добавлен. URL-construction для 200 песен
  тривиальна (простая конкатенация строк на CPU, O(1) памяти). 2 SQL
  запроса уже сделаны (Song.loadListFromDb + mainController.doRegisterEvent).
- Фронт: `loading="lazy"` + `decoding="async"` на превью-картинках;
  для 200-песенного результата грузятся только видимые, остальные —
  по мере скролла. CSS — адаптивный flex (как PlaylistEditView).
- Размер JSON ответа `/api/public/songs` +5% (3 новых коротких поля
  на песню).

## Обратная совместимость

- Три новых поля `SongPublicDto` имеют default-значения (`false`/`""`),
  старые клиенты их просто игнорируют.
- Существующий call-site `SongPublicDto.fromSong(it, includeDetails = false)`
  в `PublicApiController.song(id)` (страница одной песни, не поиск)
  продолжает работать без изменений — defaults `albumPictureUrl=""`,
  `authorPictureUrl=""`.
- `Pass 239`-логика PlayerIcon не изменена.

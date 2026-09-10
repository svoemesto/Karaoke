# Vuex filter stores (паттерн)

> **Домен**: system (frontend)
> **Компонента**: детальный каталог filter stores.

## Назначение

`webvue3/src/components/<Entity>/filter/store.js` — **отдельный
Vuex store для фильтров таблицы** (по образцу [vuex-patterns.md](vuex-patterns.md)).

**Зачем отдельный store**: чтобы пикер «Альбом (ссылка)» в
`SongEdit.vue` не толкался в один кеш с "тяжёлым" дайджестом
`AlbumsTable.vue` (иначе кто первый загрузился — того и кеш).

## Каталог filter stores

22 filter stores — по одному на каждую entity table.

## Пример: Albums filter (6 полей)

```javascript
state: {
    albumsFilterId: '',
    albumsFilterAuthorName: '',
    albumsFilterYear: '',
    albumsFilterName: '',
    albumsFilterAlbumType: '',
    albumsFilterSongsCountMin: '',
}
```

## Пример: Songs filter (20+ полей!)

```javascript
state: {
    songsFilterId: '',
    songsFilterSongName: '',
    songsFilterSongAuthor: '',
    songsFilterSongAlbum: '',
    songsFilterPublishDate: '',
    songsFilterPublishTime: '',
    songsFilterIdStatus: '',
    songsFilterCountVoices: '',
    songsFilterTags: '',
    songsFilterResultVersion: '',
    songsFilterVersionBoosty: '',
    songsFilterVersionBoostyFiles: '',
    songsFilterVersionSponsr: '',
    songsFilterVersionDzenKaraoke: '',
    songsFilterVersionVkKaraoke: '',
    songsFilterVersionTelegramKaraoke: '',
    songsFilterVersionPlKaraoke: '',
    songsFilterVersionMaxKaraoke: '',
    songsFilterRate: '',
    songsFilterIsSync: '',
    // ... ещё 5+ полей
}
```

**Songs filter — самый большой** (~25 полей) — отражает
сложность сущности Song.

## Persistence

```javascript
setWebvueProp('songsFilterId', value)  // на сервер через nginx-proxy
```

Фильтры **переживают F5** через `setWebvueProp`/`getWebvueProp` (см.
[vuex-patterns.md](vuex-patterns.md)). **НЕ** через `localStorage`
— чтобы синхронизировались между устройствами.

## Hot paths

- **Каждое открытие таблицы** — `loadFilter()` из server-side key/value.
- **Каждое изменение фильтра** — `saveFilter()` обратно.

## Архитектурные решения

### Решение 1: server-side key/value (НЕ localStorage)

Если бы `localStorage` — фильтры привязаны к браузеру. `setWebvueProp`
— server-side key/value, синхронизируется между устройствами.

### Решение 2: Отдельный store (НЕ в основном)

Основной store (`Songs/store.js`) — для `digest`, `current`, etc.
Filter store — **отдельно**, чтобы не загрязнять основной кеш.

### Решение 3: Публичный модуль (`karaoke-public`) — localStorage + transient auto-reset

В **публичном модуле** (`karaoke-public/src/views/ZakromaView.vue`)
паттерн другой: быстрый фильтр категорий альбомов хранится в
`localStorage` (`km-zakroma-hidden-album-types`), потому что это
**per-visitor** настройка, а не per-user (анонимные посетители не
имеют server-side key/value).

При открытии конкретного альбома через `?albumId=` (Pass 362, ADR
[local-0008](../../adr/local-0008-effective-hidden-album-types.md)):
- `effectiveHiddenAlbumTypes(zak)` (transient computed) исключает тип
  открытого альбома из фильтра для текущего рендера.
- `hiddenAlbumTypes` Set и `localStorage` НЕ мутируются.
- Вся панель `.km-album-controls-bar` (переключатель «Сквозной/По
  типам» + фильтр категорий) скрывается через `v-if` при
  `selectedAlbumId != null`.

Это «transient auto-reset»: пользователь явно кликнул на альбом —
фильтр временно не блокирует его просмотр; после возврата фильтр в
исходном состоянии.

## Связь

- [vuex-patterns.md](vuex-patterns.md) — общий паттерн.
- [store-songs.md](store-songs.md) — основной store.

## Changelog

- **Pass 455-457** (2026-09-09): Initial. Автор: agent (Karaoke).
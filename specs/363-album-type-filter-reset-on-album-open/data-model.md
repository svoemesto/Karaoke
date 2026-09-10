# Data Model: Сброс фильтра категории альбома при открытии песен конкретного альбома

**Feature**: [spec.md](./spec.md)
**Date**: 2026-09-10
**Branch**: `363-album-type-filter-reset-on-album-open`

> Фича НЕ вводит новых сущностей и НЕ меняет структуру БД. Модель данных фронт-енда
> (в компоненте `ZakromaView.vue`) уже существует — фича только ДОБАВЛЯЕТ новое
> computed-свойство. Этот документ фиксирует текущее + добавляемое состояние.

## Existing entities (не меняются)

### `ZakromaAlbum` (frontend DTO)

Представляет один альбом автора на странице Закромов. Приходит в payload `/api/public/zakroma/stream` (Pass 357).

| Поле | Тип | Описание |
|---|---|---|
| `albumId` | number \| null | `tbl_albums.id`, FK на `tbl_songs.album_id` (Pass 357). |
| `albumName` | string | Название альбома. |
| `year` | number \| null | Год выпуска (NULL → в конец списка). |
| `albumType` | string | **dbValue** — одно из: `studio` / `live` / `compilation` / `bootleg` / `single` / `archive` / `tribute` (см. `AlbumType.kt`). |
| `albumTypeLabel` | string | Display-подпись (например, «Студийные»). |
| `albumTypeEnum` | string \| null | Типизированный enum (см. `album-entity.md`). |
| `albumSettings` | Song[] | Песни альбома. |
| `sortOrder` | number | Порядок отображения. |

**Связи**:
- `ZakromaAlbum.albumId === selectedAlbumId` — условие, по которому фильтруется `displayedZakroma`.
- `ZakromaAlbum.albumType` — то, что мы добавляем/удаляем из `hiddenAlbumTypes` (в computed, не в Set-е).

### `hiddenAlbumTypes` (Set<string>, in-component)

Уже существует (Pass 012, FR-025/027). Инициализируется из `localStorage.km-zakroma-hidden-album-types`. Персистится при изменении через `toggleAlbumType(dbValue)`.

**Метод**:
- `toggleAlbumType(dbValue)` — вкл/выкл типа альбома, мутирует Set и `localStorage`.

**Эта фича НЕ МЕНЯЕТ** `hiddenAlbumTypes`. Только читает через новый computed.

## New entity (в рамках этой фичи)

### `effectiveHiddenAlbumTypes` (computed, Set<string>)

**Файл**: `karaoke-public/src/views/ZakromaView.vue`.

**Тип**: Vue computed property.

**Описание**: возвращает `hiddenAlbumTypes` за вычетом `albumType` текущего открытого альбома (если он там есть).

**Inputs (зависимости)**:
- `hiddenAlbumTypes` — Set из data().
- `selectedAlbumId` — computed из `$route.query.albumId`.
- `zakroma` — getter из Vuex (`mapGetters('zakroma', ['zakroma'])`).

**Output**:
- `Set<string>` — для использования в `visibleAlbums(zak)`.

**Алгоритм**:
```
const opened = (zakroma || []).flatMap(z => z.albums || []).find(a => Number(a.albumId) === Number(selectedAlbumId))
if (!opened || !hiddenAlbumTypes.has(opened.albumType)) return hiddenAlbumTypes
const next = new Set(hiddenAlbumTypes)
next.delete(opened.albumType)
return next
```

**Реактивность**: Vue пересчитает автоматически при изменении любого из 3-х входов.

## State transitions

Нет — фига stateless для персистентного состояния. `localStorage` остаётся источником правды; `effectiveHiddenAlbumTypes` — чисто производное.

## Миграции БД

Не требуются (фига чисто-фронтовая).

## Changelog

- **2026-09-10**: Initial. Автор: agent (Karaoke).
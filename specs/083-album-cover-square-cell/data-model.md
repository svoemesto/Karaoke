# Data Model: Альбомы — квадратная ячейка обложки альбома

**Phase 1 output for**: `083-album-cover-square-cell`
**Date**: 2026-07-29
**Spec**: [spec.md](./spec.md)

## Резюме

Фича **не затрагивает модель данных**. Это чисто визуальная правка CSS
одной колонки в одном Vue-компоненте. Никакие сущности, поля, типы,
отношения, стейт-машины или миграции не изменяются.

Этот файл создаётся ради полноты пакета артефактов `/speckit.plan` и
фиксирует, что **data model остаётся стабильной**.

## Сущности (без изменений)

| Сущность | Расположение | Изменения |
|----------|--------------|-----------|
| `Album` (Kotlin-модель) | `karaoke-app/.../model/Album.kt` | нет |
| `AlbumDTO` (DTO бэкенда) | `karaoke-app/.../dto/AlbumDTO.kt` | нет |
| `AlbumType` (enum) | `karaoke-app/.../model/AlbumType.kt` | нет |
| `tbl_albums` (SQL-таблица) | `deploy/karaoke-db/*` | нет |
| `AlbumsStore` (Vuex) | `webvue3/.../Albums/store.js` | нет |
| `AlbumsFilterStore` (Vuex) | `webvue3/.../Albums/filter/store.js` | нет |
| `AlbumDigest` (Vuex-стейт) | `webvue3/.../Albums/store.js` | нет — поля `albumPicturePreviewUrl`, `albumPictureId` и т.п. остаются как есть |

## Внутренний «контракт» (UI-уровень)

Это не data model, а визуальный инвариант, который фича вводит/поддерживает:

| Параметр | Значение | Источник |
|----------|----------|----------|
| Высота строки таблицы | 54px | `.fld-picture-preview { height: 54px }` в `AlbumsTable.vue:724` |
| Ширина колонки `(альбом)` | 54px (= высоте строки) | `albumDigestFields[i=1].style.minWidth/maxWidth` в `AlbumsTable.vue:269-273` |
| Площадь preview-изображения | 50×50px (с 2px зазором до границ ячейки) | `.preview-image { max-width: 50px; max-height: 50px }` (после правки) |
| Площадь плейсхолдера «Нет изображения» | 54×54px | `.fld-picture-preview` (54×54 ячейка с `display: flex; align-items: center; justify-content: center`) |

## Что НЕ меняется (важно для регрессий)

- `Album.albumPicturePreviewUrl` (String) — продолжает приходить с бэкенда
  как было, `object-fit: contain` вписывает изображение в квадрат.
- `Album.albumPictureId` (Long?) — без изменений; используется
  модалкой `AlbumCoverModal` при клике (логика A-003 сохранена).
- `AlbumsStore.getAlbumsDigest` — возвращает тот же `AlbumDigest[]`,
  никаких новых полей.
- Filter `albumsFilterAlbumType` — без изменений.

## Если бы data model всё-таки менялся

В данной фиче это не нужно, но для полноты — в будущем, если потребуется
ввести «рекомендуемый размер обложки 54×54px» как поле схемы:

- Потребовалось бы поле `Album.albumPictureAspect: String` или
  `albumPictureWidth/albumPictureHeight: Int` — НЕ вводим в этой фиче
  (YAGNI: текущих полей `albumPicturePreviewUrl` достаточно; UI сам
  подгоняет под квадрат через `object-fit: contain`).

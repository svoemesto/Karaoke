# API Contracts: Альбомы — клик по ячейке открывает модалку обложки альбома

**Date**: 2026-07-27
**Spec**: [./spec.md](./spec.md)
**Research**: [./research.md](./research.md)
**Data Model**: [./data-model.md](./data-model.md)

## Сводка

| Endpoint | Где живёт | Назначение | Изменяется ли |
|---|---|---|---|
| `POST /api/albums/firstsongid` | `karaoke-app` (новый) | Получить `id` первой песни альбома (для контекста модалки) | **новый** |
| `POST /api/song/picturealbum` | `karaoke-app` (существующий) | Получить base64 текущей обложки альбома по `id` песни | переиспользуется как есть |
| `POST /api/song/searchalbumcover` | `karaoke-app` (существующий) | Поиск кандидатов обложки в интернете | переиспользуется как есть |
| `POST /api/song/savealbumcover` | `karaoke-app` (существующий) | Сохранить выбранную/скадрированную обложку | переиспользуется как есть |
| `POST /api/albums/albumsdigests` | `karaoke-app` (существующий) | Список/одна запись альбома (используется `loadOneRecord(albumId)` для обновления превью) | переиспользуется как есть |

## Новый endpoint: `POST /api/albums/firstsongid`

**Назначение:** вернуть `id` песни, которую можно использовать как «контекст» для модалки `AlbumCoverModal` (модалка привязана к конкретной песне через `currentSongId`).

**Request:**

| Параметр | Тип | Обязательный | Описание |
|---|---|---|---|
| `albumId` | `Long` | да | `id` альбома из `albumsDigest` |

**Response:**

| HTTP | Content-Type | Тело | Описание |
|---|---|---|---|
| 200 | `application/json` или `text/plain` | `Long` (id песни) | Если у альбома есть песни |
| 200 | `application/json` или `text/plain` | `0` | Если у альбома нет песен (UI должен блокировать клик, см. INV-1 в data-model.md, но бэк всё равно защищается) |

**Алгоритм (псевдокод, реализация в `ApiController.kt`):**

```kotlin
@PostMapping("/albums/firstsongid")
@ResponseBody
fun apisGetFirstSongIdByAlbumId(@RequestParam albumId: Long): Long {
    return Album.getFirstSongId(albumId, WORKING_DATABASE) ?: 0L
}
```

**Helper в `Album.kt`:**

```kotlin
/**
 * Возвращает id «репрезентативной» песни альбома — песни с минимальным id в этом альбоме.
 * null, если у альбома нет песен.
 *
 * Используется в AlbumsTable.vue для контекста AlbumCoverModal (модалка привязана к
 * конкретной песне через currentSongId — см. specs/014-album-cell-album-cover-modal).
 *
 * ВНИМАНИЕ: в Song есть in-memory поле Song.firstSongInAlbum, но оно НЕ сохраняется
 * в БД (нет колонки в tbl_songs, нет упоминания в Song.getSqlToInsert). Поэтому
 * «семантический» вариант first_song_in_album = TRUE невозможен — используем MIN(id).
 */
fun getFirstSongId(albumId: Long, database: KaraokeConnection): Long? {
    return database.query("""
        SELECT id FROM tbl_songs
        WHERE album_id = ?
        ORDER BY id
        LIMIT 1
    """, albumId).firstOrNull()?.getLong("id")
}
```

> **Заметка по реализации:** конкретный API `KaraokeConnection` (метод `query`/`firstOrNull`/`getLong`) — посмотреть соседние helper'ы в `Album.kt:227` (`countSongsByAlbumIds`), стиль должен совпадать. Это для `tasks.md` / реализации.

**Контракт для Vuex action:**

```javascript
// webvue3/src/components/Albums/store.js (новый action)
getFirstSongIdByAlbumIdPromise(ctx, albumId) {
  let request = {
    method: 'POST',
    url: '/api/albums/firstsongid',
    params: { albumId },
  }
  return promisedXMLHttpRequest(request).then((data) => Number(data))
}
```

## Контракт UI-использования (AlbumsTable.vue)

### Computed: `canEditCover(item)`

```javascript
canEditCover(item) {
  return item && item.songsCount > 0
}
```

### Template: `cell(albumPicture)`

```vue
<template #cell(albumPicture)="data">
  <div
    class="fld-picture-preview"
    :class="{ 'is-clickable': canEditCover(data.item) }"
    :title="canEditCover(data.item) ? data.item.name : 'У альбома нет песен — обложка недоступна'"
    @click.left="canEditCover(data.item) && openAlbumCoverModal(data.item)"
  >
    <img v-if="data.item.albumPicturePreviewUrl" :src="data.item.albumPicturePreviewUrl" ... />
    <div v-else class="no-image-placeholder">Нет изображения</div>
  </div>
</template>
```

### Template: `cell(name)`

```vue
<template #cell(name)="data">
  <div
    class="fld-album-name"
    :class="{ 'is-clickable': canEditCover(data.item) }"
    :title="canEditCover(data.item) ? 'Изменить обложку альбома' : 'У альбома нет песен — обложка недоступна'"
    @click.left="canEditCover(data.item) && openAlbumCoverModal(data.item)"
    v-text="data.value"
  />
</template>
```

### Template: добавление модалки (рядом с `PictureEditModal`)

```vue
<AlbumCoverModal
  v-if="isAlbumCoverModalVisible"
  @saved="onAlbumCoverSaved"
  @close="closeAlbumCoverModal"
/>
```

### Methods

```javascript
async openAlbumCoverModal(item) {
  if (!this.canEditCover(item)) return
  // Запоминаем текущий currentSongId (может быть null, если админ впервые открыл /Albums)
  this.prevCurrentSongId = this.$store.getters.getCurrentSongId
  this.isBusy = true
  try {
    const firstSongId = await this.$store.dispatch('getFirstSongIdByAlbumIdPromise', item.id)
    if (!firstSongId) {
      // Бэк вернул 0 — песен нет (race condition с UI-блокировкой)
      console.warn('Альбом без песен, модалка не открыта:', item.id)
      return
    }
    this.currentAlbumCoverFirstSongId = firstSongId
    // Устанавливаем currentSongId БЕЗ сетевого запроса — модалка сама дёрнет getAlbumPictureBase64Promise
    this.$store.commit('setCurrentSongIdOnly', firstSongId)
    this.isAlbumCoverModalVisible = true
  } finally {
    this.isBusy = false
  }
},
closeAlbumCoverModal() {
  this.isAlbumCoverModalVisible = false
  // Восстанавливаем прежний currentSongId
  this.$store.commit('setCurrentSongIdOnly', this.prevCurrentSongId || null)
  this.prevCurrentSongId = null
  this.currentAlbumCoverFirstSongId = null
},
onAlbumCoverSaved(/* url */) {
  // Модалка уже эмитнула close после успешного save
  // Обновляем превью в строке таблицы
  this.$store.dispatch('loadOneRecord', this.currentAlbumCoverAlbumId)
  this.currentAlbumCoverAlbumId = null
},
```

### Imports

```javascript
import AlbumCoverModal from '../Songs/edit/AlbumCoverModal.vue'
```

### Components

```javascript
components: {
  // ... существующие
  AlbumCoverModal,
},
```

## Контракт безопасности

- Все эндпоинты `/api/*` живут в `karaoke-app` (admin-only, `permitAll()` в `SecurityConfig.kt` согласно constitution V).
- Никаких новых прав/ролей не требуется.
- Никаких изменений в публичной части (`karaoke-web`, `karaoke-public`) — фича только в админке.

## Что НЕ меняется (контракт-стабильность)

- `AlbumCoverModal.vue` — никаких изменений.
- `SongDTO`, `AlbumDTO`, `PictureDTO` — никаких изменений.
- Endpoint'ы `/api/song/picturealbum`, `/api/song/searchalbumcover`, `/api/song/savealbumcover` — никаких изменений.
- `SyncRegistry` — без изменений (новый endpoint не участвует в LOCAL↔SERVER sync).

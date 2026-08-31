# Contracts: 280 — AssignModal: фильтр по rootId и audioRootId

**Date**: 2026-08-31
**Spec**: [spec.md](spec.md)

> Фича НЕ вводит новых HTTP-контрактов и НЕ меняет существующие. Этот документ фиксирует **форму потребления** уже существующего эндпоинта `POST /api/songsdigests` со стороны новых фильтров.

## 1. HTTP-контракт `/api/songsdigests` (потребление)

### 1.1. Запрос

```
POST /api/songsdigests
Content-Type: application/x-www-form-urlencoded

filterRootId=42&filterAudioParentId=17&filterSongName=&filterAuthor=Петров&filterAlbum=&filterStatus=1
```

### 1.2. Параметры (затрагиваемые фичей)

| Параметр HTTP | Тип | Required | Описание | Контракт бэкенда |
|---------------|-----|----------|----------|-------------------|
| `filterRootId` | `String` (BIGINT-as-string) | нет | Точное совпадение по `song.root_id` | `@RequestParam(required=false) filterRootId: String?` (ApiController.kt:2386) → `args["filter_root_id"]` (2463) |
| `filterAudioParentId` | `String` (BIGINT-as-string) | нет | Точное совпадение по `song.audio_parent_id` | `@RequestParam(required=false) filterAudioParentId: String?` (2387) → `args["filter_audio_parent_id"]` (2464) |

### 1.3. Логика бэкенда (без изменений)

```kotlin
// ApiController.apisSongsDigests (Kotlin, фрагмент, БЕЗ ИЗМЕНЕНИЙ)
@RequestParam(required = false) filterRootId: String?,
@RequestParam(required = false) filterAudioParentId: String?,
...
filterRootId?.let { if (filterRootId != "") args["filter_root_id"] = filterRootId }
filterAudioParentId?.let { if (filterAudioParentId != "") args["filter_audio_parent_id"] = filterAudioParentId }

// Song.loadListFromDb фильтрует по точному совпадению "=" в SQL
```

### 1.4. Ответ (без изменений)

```json
{
  "workInContainer": true,
  "songsDigests": [
    {
      "id": 12345,
      "rootId": 42,
      "audioParentId": 17,
      "songName": "...",
      "songAuthor": "...",
      "songAlbum": "...",
      "idStatus": 1,
      "status": "Создание текста",
      ...
    }
  ],
  "statuses": {...}
}
```

> Поля `rootId` и `audioParentId` присутствуют в DTO всегда (`SongDTOdigest.kt:96-97`), не зависят от параметров фильтра.

## 2. Vuex-action контракт (`searchCandidateSongs`)

### 2.1. Текущая сигнатура (ДО фичи)

```js
// webvue3/src/components/SongEditor/store.js:173-185
searchCandidateSongs(ctx, { query, author, album, onlyStatus1 }) {
  const params = {}
  if (query) params.filterSongName = query
  if (author) params.filterAuthor = author
  if (album) params.filterAlbum = album
  if (onlyStatus1) params.filterStatus = '1'
  return promisedXMLHttpRequest({ method: 'POST', url: '/api/songsdigests', params })
    .then((data) => {
      const result = JSON.parse(data)
      return result.songsDigests || []
    })
}
```

### 2.2. Целевая сигнатура (ПОСЛЕ фичи)

```js
/**
 * Поиск песен-кандидатов для назначения на разметку.
 *
 * @param {object} ctx - Vuex context
 * @param {object} payload
 * @param {string} [payload.query] - фильтр по song_name (LIKE)
 * @param {string} [payload.author] - фильтр по song_author (LIKE)
 * @param {string} [payload.album] - фильтр по song_album (LIKE)
 * @param {boolean} [payload.onlyStatus1=true] - только кандидаты на разметку (id_status=1)
 * @param {string} [payload.rootId] - точное совпадение по song.root_id
 * @param {string} [payload.audioRootId] - точное совпадение по song.audio_parent_id
 * @returns {Promise<Array<SongDTOdigest>>} массив песен-кандидатов
 *
 * @see spec.md FR-005, FR-007 — проброс filterRootId/filterAudioParentId в /api/songsdigests
 * @see spec.md Assumption A-1 — audioRootId в payload маппится в filterAudioParentId
 */
searchCandidateSongs(ctx, { query, author, album, onlyStatus1, rootId, audioRootId }) {
  const params = {}
  if (query) params.filterSongName = query
  if (author) params.filterAuthor = author
  if (album) params.filterAlbum = album
  if (onlyStatus1) params.filterStatus = '1'
  if (rootId) params.filterRootId = rootId
  if (audioRootId) params.filterAudioParentId = audioRootId
  return promisedXMLHttpRequest({ method: 'POST', url: '/api/songsdigests', params })
    .then((data) => {
      const result = JSON.parse(data)
      return result.songsDigests || []
    })
}
```

### 2.3. Обратная совместимость

- Все существующие вызовы (`AssignModal.doSearch`) не передают `rootId`/`audioRootId` → параметры остаются `undefined` → HTTP-параметры НЕ добавляются → бэкенд фильтрует как раньше.
- JSDoc-комментарий добавлен в соответствии с FR-006 Конституции.

## 3. UI-контракт `AssignModal` (template)

### 3.1. Изменения в `.se-search-row`

**ДО фичи:**

```html
<div class="se-search-row">
  <input v-model="authorQuery" list="se-authors-list" placeholder="Автор..." class="se-search-author" @keyup.enter="doSearch" />
  <input v-model="albumQuery" placeholder="Альбом..." class="se-search-album" @keyup.enter="doSearch" />
  <input v-model="searchQuery" placeholder="Название песни..." class="se-search-name" @keyup.enter="doSearch" />
  <button type="button" class="se-btn" @click="doSearch">Найти</button>
</div>
```

**ПОСЛЕ фичи (упрощённо — без бутстрап-адаптаций, см. `quickstart.md`):**

```html
<div class="se-search-row">
  <input v-model="authorQuery" list="se-authors-list" placeholder="Автор..." class="se-search-author" @keyup.enter="doSearch" />
  <input v-model="albumQuery" placeholder="Альбом..." class="se-search-album" @keyup.enter="doSearch" />
  <input v-model="searchQuery" placeholder="Название песни..." class="se-search-name" @keyup.enter="doSearch" />
  <input
    v-model="rootIdQuery"
    type="text"
    inputmode="numeric"
    pattern="[0-9]*"
    placeholder="root ID…"
    class="se-search-root-id"
    @keyup.enter="doSearch"
  />
  <input
    v-model="audioRootIdQuery"
    type="text"
    inputmode="numeric"
    pattern="[0-9]*"
    placeholder="A-root ID…"
    class="se-search-audio-root-id"
    @keyup.enter="doSearch"
  />
  <button type="button" class="se-btn" @click="doSearch">Найти</button>
</div>
```

### 3.2. CSS-классы

Переиспользуются существующие (`.se-search-author`, `.se-search-album`, `.se-search-name`) для консистентности. Для новых полей добавляются локальные классы `.se-search-root-id` (≈14% ширины) и `.se-search-audio-root-id` (≈14%) — общая ширина строки остаётся в пределах 920px модалки.

### 3.3. Кнопки очистки (FR-002)

По правому краю каждого числового поля — кнопка «✕», аналогичная `.sfm-button-clear-field` из `SongsFilterModal.vue`:

```html
<div class="se-search-root-wrap">
  <input v-model="rootIdQuery" ... />
  <button type="button" class="se-btn-clear" :disabled="!rootIdQuery" @click="rootIdQuery = ''" title="Очистить">✕</button>
</div>
```

## 4. Совместимость с существующими фичами

| Фича / модуль | Влияние |
|---------------|---------|
| `apisSongsDigests` (бэкенд) | Без изменений. Новые параметры — существующие. |
| `SongsFilterModal.vue` | Без изменений (фильтр в общей таблице песен; использует те же `filterRootId`/`filterAudioParentId`, но через свой store). |
| `SongEditorTable.vue` | Без изменений (AssignModal открывается из таблицы — `frSongId`/`hrSongId` не затрагиваются). |
| `ReviewModal.vue` | Без изменений. |
| Health-report (`HealthReportTable.vue`) | Без изменений. |
| Sync LOCAL↔SERVER | Без изменений (схема не меняется). |

## 5. Перечень артефактов, которые НЕ создаются в этой фиче

- ❌ Новый HTTP-эндпоинт
- ❌ Новая SQL-миграция
- ❌ Новый DTO-класс / поле DTO
- ❌ Новый Vuex-store / модуль
- ❌ Новый Vue-компонент
- ❌ Новые npm-зависимости
- ❌ Новые gradle-зависимости
- ❌ Новые docker-образы
- ❌ Новые ENV-переменные
- ❌ Новые LiveDoc-принципы (только новый файл `livedocs/features/280-assign-modal-root-audio-id.md` по FR-014 AGENTS.md)

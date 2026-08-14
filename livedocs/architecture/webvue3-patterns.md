---
status: Active
slug: webvue3-patterns
type: topic
related:
  - ../architecture/L2-containers.md
  - ../features/190-playlist-play-button-and-stems-cancel.md
  - ../features/164-complete-guest-share-link.md
---

# webvue3 — паттерны

> Drill-down из `AGENTS.md` секции «Важные паттерны».
> Этот LiveDoc — полная версия. В AGENTS.md осталась только короткая ссылка.

## Персистентность страницы пагинации

**Проблема**: `currentPage` живёт в `data()` компонента; при уходе с роута
Vue выгружает компонент и значение сбрасывается. Чтобы при уходе
«Песни → Публикации → Песни» открывалась та же страница — храним номер в Vuex.

**Решение** (3 вещи в сторе + 2 вещи в компоненте):

### В сторе (модуль с таблицей)

```js
// components/Songs/store.js — пример
export default {
  state: {
    songsTableCurrentPage: 1,
    // ...
  },
  getters: {
    getSongsTableCurrentPage: (state) => state.songsTableCurrentPage,
  },
  mutations: {
    setSongsTableCurrentPage(state, page) {
      state.songsTableCurrentPage = page;
    },
  },
};
```

### В компоненте таблицы

```vue
<script>
export default {
  data() {
    return {
      currentPage: this.$store.getters.getSongsTableCurrentPage || 1,
      // ...
    };
  },
  watch: {
    currentPage(newPage) {
      this.$store.commit('setSongsTableCurrentPage', newPage);
    },
    countRows(newCount) {
      // Ослабленная логика: сбрасывать currentPage только если вышла за пределы
      const totalPages = Math.ceil(newCount / this.perPage);
      if (this.currentPage > totalPages) {
        this.currentPage = 1;
      }
    },
  },
};
</script>
```

### Применено в

`SongsTable` (с расширенной логикой `countRows`), `ProcessesTable`,
`AuthorsTable`, `PicturesTable`, `SiteUsersTable`, `SitePlaylistsTable`,
`PropertiesTable`, `DictionariesTable`. В `StatsView` — две отдельные пагинации
(`statsBySongPage` / `webEventsPage`) в сторе `stats`.

### Правило для новых таблиц

Применять тот же шаблон к **любой новой таблице в `webvue3`** сразу, иначе
пользователь потеряет позицию при переключении пунктов меню.

## Таблицы `karaoke-public` — `table-layout: fixed` + явная `width`

`<table class="table" style="table-layout: fixed;">` требует явной `width: Npx`
для каждой колонки. Без этого ширины «прыгают» при скролле.

**Колонки платформ**: 16 иконок × 22px = 352px (точная ширина).

**Ловушка**: `display: flex` на `<td>` ломает высоту строки — использовать
`text-align: center; vertical-align: middle`.

## Bootstrap 5 в `karaoke-public`

`<select>` → класс `form-select` (НЕ `form-control`). Это частая ошибка при
миграции с Bootstrap 4 на 5.

## Картинки — только MinIO, НЕ БД

Поле `picture_full` в БД всегда `""`. Картинки только в MinIO. `PicturesDTO`
содержит `previewUrl`/`fullUrl`.

При загрузке картинки **всегда** использовать `ignoreUseInList = false`
(см. оптимизацию в фиче 186).

## Тег SKIP — заглушка

Если в `tags` есть `SKIP`, показывается заглушка «удалено по требованию
правообладателя». Теги наружу не утекают (фильтруются на сервере).

## Табулатура (ASCII-only)

При отображении ASCII-табулатур:
- `-` вместо `⎼` (U+23BC).
- `||` вместо `‖` (U+2016).

Unicode ломает выравнивание через font fallback. ASCII-only даёт стабильный
рендер во всех браузерах.

## HealthReport — видео проверяются только при idStatus >= 6

Не трогать логику для видео при статусе < 6 (видео ещё нет). Это правило
предотвращает ложные «видео отсутствует» в HealthReport.

## postMessage-мост между родителем и iframe-плеером

**Проблема**: `karaoke-public` показывает плеер в `<iframe>` (через
`/player/:id`), но родительский компонент (`PlaylistEditView.vue`,
`PlayerView.vue`, `SongView.vue`) должен:
- Управлять плеером (play/pause/seek, переключение трека, pushQueue).
- Получать события от плеера (ended, positionUpdate, displayMode).

Эти два контекста — **разные `window`-объекты**, поэтому прямое
обращение к методам `KaraokePlayer` невозможно.

**Решение**: **postMessage** API с маркером `source` для фильтрации.

### Из iframe-плеера → родитель

```js
// karaoke-public/src/player/KaraokePlayer.js
window.parent.postMessage({
  source: 'karaoke-player',
  type: 'display-mode',
  mode: 'karaoke'
}, '*')

// Примеры событий:
//   source: 'karaoke-player'
//   type: 'display-mode' | 'ended' | 'progress' | 'share-session-revoked'
//   mode: 'karaoke' | 'lyrics' | 'demo'
//   currentTime, duration — для progress
```

### Из родителя → iframe-плеер

```js
// karaoke-public/src/composables/usePlaylistPlayer.js
function sendToPlayer(type, extra = {}) {
  const win = document.querySelector('iframe#kp-player')?.contentWindow
  if (win) {
    win.postMessage({
      source: 'kp-playlist',  // другой source!
      type,
      ...extra
    }, '*')
  }
}

// Примеры команд:
//   source: 'kp-playlist'
//   type: 'play' | 'pause' | 'play-song' | 'push-queue' | 'abort-stems'
//   songId, positionMs — для play-song / seek
```

### Фильтрация входящих сообщений

```js
// karaoke-public/src/views/PlaylistEditView.vue
window.addEventListener('message', (e) => {
  if (e.data?.source !== 'karaoke-player') return  // чужие события игнорируем
  if (e.data.type === 'ended') {
    // обновить состояние плейлиста
  }
})

// karaoke-public/src/player/KaraokePlayer.js
window.addEventListener('message', (e) => {
  if (e.data?.source !== 'kp-playlist') return
  if (e.data.type === 'play-song') {
    this.playSong(e.data.songId)
  }
})
```

### Ловушки

- **`'*'` targetOrigin** — для прода должно быть `'https://karaoke.example'`,
  чтобы третий сайт не мог слать команды. Для локальной разработки —
  `'*'` (иначе cross-origin на `localhost:8080` → `localhost:8081`).
- **`e.source !== window.parent`** — iframe может быть вложен в чужой
  iframe; проверять `e.source === window.parent`.
- **Без `try/catch`** — `postMessage` может бросить `DOMException` если
  iframe уже закрыт (например, юзер ушёл со страницы).

### Применено в

- `karaoke-public/src/views/PlayerView.vue` — одиночная песня, без плейлиста
- `karaoke-public/src/views/PlaylistEditView.vue` — плейлист (спека 190)
- `karaoke-public/src/views/SongView.vue` — карточка песни
- `karaoke-public/src/composables/usePlaylistPlayer.js` — composable-обёртка
- `karaoke-public/src/player/KaraokePlayer.js` — iframe-плеер

### Edge cases

- **Share-link revoked в iframe** → плеер шлёт событие
  `share-session-revoked` → родитель показывает overlay + кнопку «Закрыть».
- **Backpressure при spam-click по ▶** → родитель через `pushQueueDeferred`
  (50 мс `setTimeout`) сериализует команды (спека 190, FR-010).
- **Back/forward в браузере** → iframe остаётся в DOM (не перезагружается),
  родитель реинициализирует состояние через `loadFromStorage()`.

## Связанные LiveDocs

- Architecture: [L2-containers.md](../architecture/L2-containers.md) (Vue 3 SPA)
- Feature: [190-playlist-play-button-and-stems-cancel.md](../features/190-playlist-play-button-and-stems-cancel.md) (плейлисты + postMessage)
- Feature: [164-complete-guest-share-link.md](../features/164-complete-guest-share-link.md) (share-link в iframe)

## Код

- `webvue3/src/components/Songs/SongsTable.vue` — образец паттерна пагинации
- `webvue3/src/store/modules/Songs/store.js` — образец стора
- `karaoke-public/src/components/Songs/SongsTable.vue` — public-аналог
- `karaoke-public/src/composables/usePlaylistPlayer.js` — postMessage-мост (родитель)
- `karaoke-public/src/player/KaraokePlayer.js` — postMessage-мост (iframe)

## История

- Создан: 2026-08-14 (мигрировано из `AGENTS.md` v1.7.1)
- Последнее обновление: 2026-08-14
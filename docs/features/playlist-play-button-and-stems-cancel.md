# Плейлисты — запуск с любой песни, превью альбома/автора и фикс задвоения вейвформ

> **Status**: active
> **Feature Key**: playlist-play-button-and-stems-cancel
> **Last Updated**: 2026-08-14
> **Spec**: `specs/190-playlist-play-button-and-stems-cancel/spec.md`
> **Branch**: `190-playlist-play-button-and-stems-cancel`

## Что делает

Три фичи в одном релизе для публичного кабинета пользователя (`karaoke-public`):

1. **Запуск воспроизведения с любой песни плейлиста** — в каждой строке плейлиста (включая
   «Избранное») теперь есть кнопка `▶`, которая запускает воспроизведение именно этой песни как
   текущего трека, без необходимости сначала нажимать «▶ Запустить плейлист» или перетаскивать
   песню в начало списка.
2. **Превью картинок альбома И автора в каждой строке** — рядом с названием отображаются две
   миниатюры 48×48 px (альбом слева, автор справа), с CSS-плейсхолдером при отсутствии файла
   или сетевой ошибке.
3. **Фикс задвоения вейвформ при быстром переключении треков** — `AbortController` отменяет
   in-flight HTTP-запросы предыдущей песни при переключении (раньше fetch предыдущей песни
   мог дописать `accBuffer`/`vocBuffer` уже после старта нового трека, что вызывало задвоение
   вейвформ «Музыка»/«Голос»).

## Зачем

Прод-репорт от 2026-08-14: пользователи не могли запустить воспроизведение с произвольной песни
в плейлисте (только с первой), а в плеере периодически задваивались вейвформы при быстром
переключении треков — особенно неприятно в больших «Избранных». Превью картинок в строках —
явное пожелание пользователя для узнаваемости.

## Как работает

### Запуск с любой песни (`PlaylistEditView.vue`)

Каждая строка песни в `<draggable>` теперь содержит:

```html
<button class="km-song-play"
        :disabled="isPlayDisabled(item)"
        :title="playTitle(item)"
        @click="onSongPlay(item)">▶</button>
```

Логика `onSongPlay(item)`:

1. **muted/locked** → ничего (кнопка `disabled`, `title="Эта песня пропускается — сначала
   включите её"`).
2. **плеер ещё не запущен** (`!started.value`) → `startPlaylist(item.songId)`. Это создаёт
   iframe реактивно (`started.value = true`) и кладёт `kp_pl_queue` в sessionStorage с
   выбранной песней как `first`. PlayerView в `onMounted` сам подхватит и стартанёт плеер.
   Альтернативный postMessage здесь не работает: до `started.value === true` iframe не
   существует, `send()` молча игнорирует сообщения.
3. **клик по ▶ на текущей играющей** → `send('toggle')` (FR-003a, toggle pause/resume).
4. **иначе** (плеер запущен, выбрана другая или та же на паузе) →
   `send('setqueue', { ids: playableIds() })` + `send('playid', { songId })`.

Handler `playid` уже существует в `PlayerView.vue:139-141` — он вызывает `playPos(p)`,
который запрашивает токен, дёргает `player.playSong(...)` и шлёт обратно `track`/`state`.

### Race-condition pushQueue ↔ playPos (FR-010)

`onSongPlay` НЕ вызывает `pushQueue()` синхронно после `playid` — очередь формируется плеером
внутри `playPos`. Если пользователь делает drag-drop / mute / удаление сразу после клика ▶
(`onReorder` / `toggleMute` / `removeItem`), новый `pushQueueDeferred()` шлёт `setqueue` через
`setTimeout(50 мс)` — этого достаточно, чтобы `playPos()` завершил init() и применил свою очередь
до нашего `setqueue`.

**Подзаголовок** (уточнено пользователем 2026-08-14): формат **«Автор - год, альбом»**.
Год подставляется только если `> 0`; альбом — только если непустой; «Автор» опускается, если
пуст; разделитель ` - ` показывается только если есть хотя бы год или альбом.

### Превью картинок альбома и автора

**Вёрстка** (уточнено пользователем 2026-08-14): оба превью обёрнуты в один
`<div class="km-song-pictures">` — контейнер с **чёрным фоном** и **скруглением углов**
(`background: #000`, `border-radius: 8px`), `margin: 5px` (по краям/сверху/снизу) и
`gap: 5px` между картинками. Сами картинки и плейсхолдеры — **без своего фона**
(`background: transparent`), но **со скруглением** (`border-radius: 6px`). Превью **автора** —
горизонтальное с аспектом **5:2** (width 120px × height 48px, т.е. 5:2.5 ≈ 5:2 при округлении
до 5px сетки). Превью **альбома** — квадрат 48×48.

Backend (`PublicPlaylistController.playlistDetail()`) теперь возвращает для каждого `items[i]`
два дополнительных поля:

```json
{
  "albumPictureUrl": "/minio/karaoke/Король%20и%20Шут/1996%20-%20Камнем%20по%20голове/Король%20и%20Шут%20-%201996%20-%20Камнем%20по%20голове.preview.album.png",
  "authorPictureUrl": "/minio/karaoke/Король%20и%20Шут/Король%20и%20Шут.preview.author.png"
}
```

Формулы ключей — **предсказуемые строки**, без SQL-lookup:

- **Альбом**: `${author}/${year} - ${album}/${author} - ${year} - ${album}.preview.album.png`
  (формула из `Song.pictureNameAlbum` и `PublicPlayerController.pictureAlbumStorageKey`).
- **Автор**: `${author}/${author}.preview.author.png` (формула из `Pictures.storageFileNamePreview`,
  паттерн `AuthorTilePublicDto.fromAuthorName` после миграции Pass 50 на прямой nginx-прокси).

Если файла в MinIO нет — фронт по `error` event на `<img>` (`@error="item._albumPictureFailed = true"`)
переключает на CSS-плейсхолдер (иконка ♪ для альбома, 👤 для автора). Тот же плейсхолдер
и при пустом URL от бэкенда (FR-005).

URL — **прямой на MinIO через nginx-прокси** `/minio/karaoke/<encoded_path>` (Pass 50):
- Минует Spring-контроллер `/api/public/picture?file=...` (200+ редиректов через nginx).
- Кэшируется nginx с `Cache-Control: public, max-age=86400`.

**НЕ делаем** `existsInMinIO` HEAD-проверку (как в `PublicPlayerController`) — это удваивает
HTTP-запросы на каждый рендер плейлиста (premium до 200 песен = 400 лишних HEAD).

### Фикс задвоения вейвформ (FR-007)

В `KaraokePlayer` (karaoke-public/src/player/KaraokePlayer.js) добавлено:

1. **Поле `_activeAbortController: AbortController | null`** — создаётся в `init()` после
   `_buildUI()`, передаётся как `signal` во все `fetch()` текущего трека.
2. **Метод `_abortActive()`** — прерывает текущий controller, обнуляет его. Вызывается из:
   - **`playSong()` ПЕРВОЙ строкой** (T022) — отменяет запросы предыдущей песни до уничтожения
     `wsAcc`/`wsVoc` и старта нового `init()`.
   - **`destroy()` ПЕРВОЙ строкой** (T023) — страховка от утечки на закрытие вкладки.
3. **`signal: this._activeAbortController.signal`** во всех `fetch()`:
   - `/playerdata` (init()).
   - `_fetchAudio(accompaniment)` и `_fetchAudio(vocals)` через `Promise.all` в `_loadAudio()`.
4. **Guard `if (signal?.aborted) return null`** в `_fetchAudio`:
   - В начале функции (если уже aborted).
   - **После `decodeAudioData(...)`** — `decodeAudioData` НЕ поддерживает `AbortController`,
     Promise всё равно зарезолвится; но если уже aborted, возвращаем `null`, и `_loadAudio`
     игнорирует результат (не перезаписывает `accBuffer`/`vocBuffer`). Это **закрывает
     первопричину** бага (research.md §R1).
5. **Защитный destroy в `_buildWaveforms()`** (T021): при входе в функцию явно уничтожаются
   старые `wsAcc`/`wsVoc` (если есть) и очищаются `innerHTML` контейнеров `#kp-ws-acc` /
   `#kp-ws-voc` — страховка от race, когда `_loadAudio()` старого трека ещё в полёте.

### Что НЕ затронуто

- **Одиночная песня вне плейлиста** (`/player/:id` без `?pl=1`) — поведение плеера идентично
  версии до фикса (FR-012). `_activeAbortController` создаётся в каждом `init()`, но abort
  вызывается только при `playSong()` (которого в single-song нет) или `destroy()`.
- **Blob/inlineData режим** — `_activeAbortController` создаётся, но `signal` никогда не
  отменяется (нет `playSong()`).

## Технические решения

См. `specs/190-playlist-play-button-and-stems-cancel/research.md` (D1..D10) и
`specs/190-playlist-play-button-and-stems-cancel/data-model.md`.

## Затронутые файлы

| Файл | Изменения |
|---|---|
| `karaoke-app/.../model/SitePlaylistItem.kt` | +2 transient-поля (без `@KaraokeDbTableField`, не пишутся в БД) |
| `karaoke-app/.../model/SitePlaylistItemDto.kt` | +2 поля с дефолтом `""` |
| `karaoke-web/.../controllers/PublicPlaylistController.kt` | +`authorPreviewUrl()` / `albumPreviewUrl()`; заполнение 2 полей в `playlistDetail()` |
| `karaoke-public/src/views/PlaylistEditView.vue` | +кнопка ▶ в строке, +onSongPlay, +два `<img>` с @error-fallback, +pushQueueDeferred для FR-010 |
| `karaoke-public/src/player/KaraokePlayer.js` | +`_activeAbortController`, +`_abortActive()`, +signal во все fetch, +guard после decode, +защита в _buildWaveforms |

## Регрессии

- FR-012 явно требует, чтобы одиночная песня без плейлиста работала идентично. Проверено:
  `_activeAbortController` создаётся в каждом `init()`, но `playSong()` вызывается только
  в плейлист-режиме; в single-song — никогда. `_abortActive()` сработает только при
  `destroy()` (закрытие вкладки).
- Sync LOCAL ↔ SERVER: новые поля не аннотированы `@KaraokeDbTableField` → не пишутся в БД →
  не входят в `recordhash` → sync не затрагивается (Constitution III, data-model.md
  §Sync implications).

## Сценарии ручной проверки

См. `specs/190-playlist-play-button-and-stems-cancel/quickstart.md` — 7 сценариев + чек-лист.

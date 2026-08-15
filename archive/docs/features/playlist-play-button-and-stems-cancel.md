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

**Высота строки** (уточнено пользователем 2026-08-14): `.km-song-row` имеет
`padding: 5px` сверху/снизу (10px суммарно) для более плотного списка плейлиста.
Горизонтальный padding `0.7rem` (~11px) оставлен без изменений.

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

## Инварианты

- postMessage-канал между `PlaylistEditView` и `PlayerView` (iframe) открывается **только**
  после `started.value === true` (реактивное условие рендера iframe). До этого `send()`
  молча игнорирует сообщения (`playerIframe.value === null`). Клик ▶ в строке **до** запуска
  плеера должен пройти через `startPlaylist(item.songId)` — он реактивно создаёт iframe.
- `decodeAudioData` (Web Audio API) **не** поддерживает `AbortController`. Promise всё равно
  зарезолвится; поэтому после decode явный guard `if (signal?.aborted) return null`,
  и `_loadAudio` не перезаписывает `accBuffer`/`vocBuffer` отменённой загрузкой.
- Формулы URL превью (`/minio/karaoke/<encoded>` по storage-ключу) — **предсказуемые строки**,
  никаких SQL-lookup; если файла нет в MinIO — фронт по `@error` показывает CSS-плейсхолдер
  (иконка ♪/👤). Тот же плейсхолдер при пустом URL от бэкенда (FR-005).
- `_activeAbortController` создаётся в **каждом** `init()` (включая single-song) — но в
  single-song режиме `playSong()` НЕ вызывается, поэтому abort фактически никогда не
  срабатывает (кроме `destroy()`). Нет регрессии (FR-012).
- Новые поля DTO (`albumPictureUrl`, `authorPictureUrl`) — **transient** (без
  `@KaraokeDbTableField`), не пишутся в БД → не участвуют в `recordhash` sync (Constitution III).
- Drag-drop сразу после клика ▶ использует `setTimeout(50ms)` для `setqueue`, чтобы не
  перетереть свежую очередь, поставленную плеером в `playPos()` (FR-010).

## Известные ловушки

- **Не использовать `@KaraokeDbTableField` для новых полей** — иначе они попадут в
  `recordhash` и сломают синхронизацию `tbl_site_playlist_items` (sync пойдёт по несуществующим
  колонкам БД и провалится с SQL-ошибкой).
- **Не проверять `existsInMinIO` на бэкенде** (HEAD через nginx-прокси) для превью —
  удваивает HTTP-запросы на каждый рендер плейлиста (premium до 200 песен = до 400 лишних
  HEAD). Полагаемся на `@error` fallback во фронте.
- **Не использовать `songPictureUrl` из `SongPublicDto`** для превью альбома в плейлисте —
  он ходит через Spring-контроллер (`/api/public/song-picture/{id}`) с лишним 302-redirect
  и не использует предсказуемый storage-ключ альбома. Прямой URL на MinIO (Pass 50)
  короче и кэшируется nginx.
- **В `init()`** при обработке картинок (`_loadImage` через `new Image()`) — НЕ пробрасывать
  `signal` (Image API его не поддерживает). Вместо этого — guard
  `if (signal.aborted) return` **до** `_loadImage()`; результат `_loadImage` (Promise) сам
  отменится при переходе на следующий трек (не критично).
- **При spam-click по ▶** в `onSongPlay` — НЕ вызывать `pushQueue()` синхронно после `send('playid')`,
  очередь формируется плеером внутри `playPos()`. Иначе старая `setqueue` перетрёт новую.
- **`fetchPlayerToken`** вызывается в `startPlaylist` — только ОДИН раз (на стартовую песню).
  Токены на остальные песни в очереди плеер запрашивает сам через `need-token` postMessage
  (handler `PlaylistEditView.vue:onMessage`).

## Ссылки

- Спека: [`specs/190-playlist-play-button-and-stems-cancel/spec.md`](../../specs/190-playlist-play-button-and-stems-cancel/spec.md)
- План: [`specs/190-playlist-play-button-and-stems-cancel/plan.md`](../../specs/190-playlist-play-button-and-stems-cancel/plan.md)
- Research: [`specs/190-playlist-play-button-and-stems-cancel/research.md`](../../specs/190-playlist-play-button-and-stems-cancel/research.md)
- Data model: [`specs/190-playlist-play-button-and-stems-cancel/data-model.md`](../../specs/190-playlist-play-button-and-stems-cancel/data-model.md)
- API-контракт: [`specs/190-playlist-play-button-and-stems-cancel/contracts/api-public-playlist-detail.md`](../../specs/190-playlist-play-button-and-stems-cancel/contracts/api-public-playlist-detail.md)
- Quickstart (ручные сценарии): [`specs/190-playlist-play-button-and-stems-cancel/quickstart.md`](../../specs/190-playlist-play-button-and-stems-cancel/quickstart.md)
- LiveDoc: [`livedocs/features/190-playlist-play-button-and-stems-cancel.md`](../../livedocs/features/190-playlist-play-button-and-stems-cancel.md)
- Domain: [`livedocs/domain/publishing.md`](../../livedocs/domain/publishing.md), [`livedocs/domain/rendering.md`](../../livedocs/domain/rendering.md)
- Architecture: [`livedocs/architecture/webvue3-patterns.md`](../../livedocs/architecture/webvue3-patterns.md)
- Связанный LiveDoc (тот же плеер): [`livedocs/features/101-audio-transpose-player.md`](../../livedocs/features/101-audio-transpose-player.md)

## Сценарии ручной проверки

См. `specs/190-playlist-play-button-and-stems-cancel/quickstart.md` — 7 сценариев + чек-лист.

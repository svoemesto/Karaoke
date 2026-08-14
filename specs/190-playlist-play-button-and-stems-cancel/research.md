# Research: 190-playlist-play-button-and-stems-cancel

**Branch**: `190-playlist-play-button-and-stems-cancel` | **Date**: 2026-08-14
**Spec**: [spec.md](spec.md)
**Status**: Phase 0 complete — all NEEDS CLARIFICATION resolved (5/5 вопросов закрыты в `/speckit.clarify` 2026-08-14)

## Technical Context (финальный)

| Поле | Значение | Источник |
|---|---|---|
| Language/Version (backend) | Kotlin 1.x, JDK 17, Spring Boot 2.x/3.x | `.specify/memory/constitution.md` §Технологический стек |
| Language/Version (frontend) | Vue 3 + Vite, Node 22 (LTS), Bootstrap 5 / Bootstrap-vue-next | То же |
| Primary Dependencies (backend) | Сырой JDBC через `KaraokeConnection`, Jackson, Spring Web | То же |
| Primary Dependencies (frontend, плеер) | `KaraokePlayer.js` (vanilla JS, Web Audio API), `wavesurfer.js` (через `import('wavesurfer.js')`), Vue 3 SPA (`karaoke-public`) | `karaoke-public/src/player/KaraokePlayer.js:1433` |
| Storage | PostgreSQL (сырой JDBC), MinIO (S3-compatible) | Constitution |
| Testing | В CI нет; `@Disabled` интеграционные; проверка — пользователем | Constitution |
| Target Platform | Linux server (karaoke-web) + node 22-alpine (karaoke-public) | Constitution §Runtime |
| Project Type | Web-service (multi-module Gradle: karaoke-app, karaoke-web + Vue 3 SPA karaoke-public) | Constitution §Технологический стек |
| Performance Goals | SC-001..006 (UI-фича; sub-second latency на запуск воспроизведения не требуется) | spec.md |
| Constraints | (а) `karaoke-web` НЕ имеет write-доступа к MinIO; только Spring API (только чтение через nginx-прокси); (б) `karaoke-app` пишет в MinIO и не разворачивается на проде; (в) существующее поведение плеера вне плейлиста (`/player/:id` без `?pl=1`) MUST остаться неизменным (FR-012) | Constitution §Технологический стек + spec.md FR-012 |
| Scale/Scope | 18k+ песен на проде; плейлист пользователя ≤ лимита (premium: 200; free: меньше — см. `PREMIUM_ITEMS_LIMIT`/`FREE_FAVORITES_LIMIT`); UI-фича, не масштабируется по нагрузке | Constitution §Рациональ + `PublicPlaylistController.kt:51-60` |

## Phase 0: Decisions

### D1 — Источник `authorPictureUrl` на бэкенде

- **Decision**: URL формируется из **storage-key автора** в MinIO — формат `${author}/${author}.preview.author.png` (тот же шаблон, что в `Pictures.storageFileNamePreview` для автора, см. `AuthorTilePublicDto.kt:10`). Возвращается как **прямой URL на MinIO через nginx-прокси** `/minio/karaoke/<encoded-path>` (паттерн Pass 50, см. `AuthorTilePublicDto.kt:55-57`), а не через Spring-эндпоинт `/api/public/picture?file=...`.
- **Rationale**:
  1. **Consistency** с недавно мигрированной `AuthorTilePublicDto` (Pass 50 / 2026-07-29, см. комментарии `AuthorTilePublicDto.kt:12-15`) — ушли от `/api/public/picture?file=` (200+ редиректов через Spring) к прямому nginx-прокси.
  2. **Предсказуемость ключа** — не нужно ходить в `tbl_pictures` или `Author.picture` entity (которая требует доступа к rootFolder, см. предупреждение в `PublicPlayerController.kt:262-264`); ключ детерминирован формулой.
  3. **Кэшируемость** — nginx отдаёт `Cache-Control: public, max-age=86400` (Pass 50, `AuthorTilePublicDto.kt:18`).
  4. **Fallback** — если MinIO вернёт 404, фронт по `@error` спрячет `<img>` (тот же подход, что в `AuthorTilePublicDto.kt:21-22`).
- **Alternatives**:
  - `/api/public/picture?file=...` (через Spring с 302-redirect на MinIO): рабочий, но лишний Spring-redirect — отказались ради consistency.
  - `Author.picturePreviewUrl` поле entity (karaoke-app): требует JOIN к `tbl_pictures` для каждой песни; при том, что storage-key предсказуем, это лишний SQL без выгоды.
  - Возврат **только имени файла** и сборка URL на фронте: размазывает логику формирования URL между бэком и фронтом; конституция «Jackson conventions» предполагает готовые URL в DTO.

### D2 — Источник `albumPictureUrl` на бэкенде

- **Decision**: URL формируется по тому же принципу, что D1, но для альбома: ключ `${author}/${year} - ${album}/${author} - ${year} - ${album}.preview.album.png` (формула из `Song.pictureNameAlbum` и `PictureAlbumStorageKey` в `PublicPlayerController.kt:265-267`).
- **Rationale**: тот же предсказуемый ключ, та же стратегия `/minio/karaoke/...`, тот же fallback на `@error`.
- **Alternative**: использовать `songPictureUrl` из `SongPublicDto` (`/api/public/song-picture/${s.id}`). Это эндпоинт **по ID песни**, а не по ключу альбома, и:
  - ходит через Spring-контроллер (тот же «лишний redirect» что в D1);
  - возвращает обложку конкретной песни, а не альбома; для сборника, где у всех песен одна обложка альбома — это ОК, но для одиночной песни без альбома — она вернёт превью самой песни (если оно есть);
  - **требует SQL-lookup по `tbl_pictures`** по `songId` (см. `PublicApiController.song-picture` если есть) — лишний запрос.
- Решили НЕ использовать `songPictureUrl` и вернуть прямую ссылку на MinIO.

### D3 — Расположение двух превью в строке

- **Decision**: **альбом слева, автор справа от него**, обе перед song-info (см. spec.md §Clarifications Q2 → A).
- **Rationale**: альбом обычно более семантически важен для плейлиста, чем фото автора (песня ассоциируется с альбомом); визуально «обложка + портрет» — стандартный паттерн для музыкальных интерфейсов.
- **Alternative** (отвергнуто в clarifications): автор слева → альбом справа — обратный порядок.

### D4 — Поведение ▶ на строке текущей играющей песни

- **Decision**: **toggle pause/resume**, не `playSong()` заново (см. spec.md §Clarifications Q3 → A, FR-003a).
- **Rationale**: совпадает с основной кнопкой ⏯ плеера (`_togglePlay()` в `KaraokePlayer.js:1476`); не запускает init() повторно (что могло бы вызвать race с уже идущим `playSong()` и перезагрузку стемов без необходимости).
- **Implementation hint** (для plan/tasks): `PlaylistEditView` при клике ▶ сравнивает `item.songId === currentSongId.value && isPlaying.value` → шлёт `toggle` postMessage (уже есть в `PlayerView.vue:151` — handler `d.type === 'toggle'`), а не `playid`.

### D5 — Поведение ▶ для muted/locked

- **Decision**: кнопка рендерится с `disabled` и `title="Эта песня пропускается — сначала включите её"` (см. spec.md §Clarifications Q4 → A, FR-004).
- **Rationale**:
  - единая высота строк (важно для SC-005);
  - тактильная обратная связь через нативный tooltip;
  - снять mute можно через существующую кнопку 🔇/🔊 — не теряется функциональность.
- **Alternative** (отвергнуто в clarifications): скрыть кнопку полностью (вариант A).

### D6 — Обработка ошибки загрузки превью

- **Decision**: Vue `@error` на `<img>` переключает на CSS-плейсхолдер (см. spec.md §Clarifications Q5 → A, SC-005).
- **Rationale**: тот же плейсхолдер, что при пустом URL на бэкенде; никаких битых изображений в DOM; единый код-путь для обоих превью.
- **Implementation hint**: `@error="onImgError(item, 'album' | 'author')"` → `item.coverFailed = true` / `item.authorFailed = true` → v-if переключает на `<div class="km-song-cover-fallback">`.

### D7 — Фикс задвоения вейвформ: архитектура отмены

- **Decision**: в `KaraokePlayer` (karaoke-public/src/player/KaraokePlayer.js) добавляется поле `_activeAbortController: AbortController | null`. Создаётся в начале `init()` (после `_buildUI()`). Передаётся как `signal` во все `fetch()`:
  - `/playerdata` (строка 191)
  - `_loadAudio()` → два `_fetchAudio()` для accompaniment и vocals (строки 1315-1324)
  - `_loadImage()` для album/artist (строки 239, 242)
- В начале `playSong()` (PlayerView.vue:1900, перед `await this.init()`) старый controller из karaoke-player **отменяется**, и `KaraokePlayer.playSong()` создаёт новый. Реализация — `_abortActive()` метод в `KaraokePlayer`, который вызывается в **первой строке** `playSong()` и в `destroy()`.
- **Rationale**: гарантирует SC-003 (≤100 мс после playSong все запросы предыдущей песни = aborted). Минимальное инвазивное изменение (одно поле + один параметр `signal` в каждом fetch).
- **WaveSurfer fix**: текущий `wsAcc.destroy()`/`wsVoc.destroy()` (PlayerView.vue:1913-1920) сохраняется, но **дополнительно** `_buildWaveforms()` в начале проверяет `if (this.wsAcc) { this.wsAcc.destroy(); this.wsAcc = null }` (страховка от race, когда `_loadAudio()` ещё в полёте, а `playSong()` уже вызван). Также очищается контейнер `ac.innerHTML = ''` / `vc.innerHTML = ''` перед `WaveSurfer.create()`.

### D8 — Передача `playid` от PlaylistEditView к плееру

- **Decision**: новая функция `playSongFromIndex(idx)` в `PlaylistEditView.vue`, которая вычисляет `playableIds()`, находит индекс выбранной песни, шлёт `setqueue` (для обновления очереди), затем `playid` postMessage с `songId` (handler уже есть в PlayerView.vue:139 — `d.type === 'playid'` → `playPos(p)`). Если песня уже играет → шлёт `toggle`.
- **Rationale**: handler `playid` уже реализован в PlayerView.vue:139-141; нужно только правильно послать его из PlaylistEditView при клике ▶.
- **Edge**: если `currentSongId` совпадает с выбранным и `isPlaying` → шлём `toggle` вместо `playid` (см. D4).

### D9 — Race-condition `pushQueue` vs `playSong` (FR-010)

- **Decision**: при `playid` плеер сам пересчитает `playPos(p)` и внутри `playSong()` синхронно очистит `queue` перед установкой новой. Со стороны `PlaylistEditView` после `playid` **не слать** `setqueue` ещё раз (избежать race). Если же пользователь сделал drag-drop/mute после клика ▶ — `pushQueue` шлёт **новую** очередь после `playid` с микро-задержкой (50 мс через `setTimeout`).
- **Rationale**: текущий `pushQueue` (PlaylistEditView.vue:312) шлёт `setqueue` всегда при изменении `items.value`; нужно избежать ситуации, когда старый `setqueue` от drag-drop перезаписывает очередь, которую плеер только что установил в `playPos()`.

### D10 — Минимальные изменения `SitePlaylistItemDto`

- **Decision**: добавить два `val` поля в `SitePlaylistItemDto`:
  ```kotlin
  val albumPictureUrl: String = "",
  val authorPictureUrl: String = "",
  ```
  И заполнить их в `PublicPlaylistController.playlistDetail()` (см. controller строки 121-130) для каждого `itemsDto`. Логика построения URL переиспользует формулу D1/D2.
- **Rationale**: минимум инвазии — DTO не требует миграции БД (поля опциональные, дефолт `""`), обратная совместимость для других потребителей DTO.

## Integration points (что трогаем в репо)

| Файл | Что | Зачем |
|---|---|---|
| `karaoke-app/.../model/SitePlaylistItemDto.kt` | +2 поля | D10 |
| `karaoke-app/.../model/SitePlaylistItem.kt` | +2 поля (entity-зеркало) | Чтобы DTO можно было собрать из БД-row (для sync/CLI) |
| `karaoke-web/.../controllers/PublicPlaylistController.kt` | `playlistDetail()` формирует `albumPictureUrl`/`authorPictureUrl` | D1/D2/D10 |
| `karaoke-public/src/services/playlistApi.js` | ничего не меняется (поля приходят в `body.items[i].albumPictureUrl`/`authorPictureUrl`) | — |
| `karaoke-public/src/views/PlaylistEditView.vue` | +кнопка `▶` в строке; +два `<img>` (cover/author) с `@error`; `playSongFromIndex(idx)` | D3/D4/D6/D8 |
| `karaoke-public/src/views/PlayerView.vue` | без изменений (handlers уже есть) | — |
| `karaoke-public/src/player/KaraokePlayer.js` | +`_activeAbortController`; +`signal` во все fetch; +`_abortActive()` в начале `playSong()`/`destroy()`; +защита от race в `_buildWaveforms()` | D7 |

## Risks

- **R1**: `playSong()` в `KaraokePlayer` уже вызывает `_stopSources()` и уничтожает `wsAcc/wsVoc` — но **между** этим и `init()` нового трека есть окно, в котором старый fetch ещё может дописать `accBuffer/vocBuffer`. Решается через `AbortController` (D7) + доп. защиту в `_buildWaveforms()`.
- **R2**: drag-drop сразу после клика ▶ может прислать старый `setqueue` (до того, как `playSong` завершил init). Решается через 50 мс `setTimeout` для post-init `setqueue` (D9).
- **R3**: `Album.pictureFileName` формула в PublicPlayerController использует `song.year` и `song.album`. Если у песни `year=0` или пустой `album` — ключ будет содержать `0` или пустую строку, MinIO вернёт 404. Решается `@error` → плейсхолдер (D6).
- **R4**: `Author.picturePreview` зависит от имени автора (строка). Если в `tbl_authors` есть дубликаты имён с разным написанием — превью может показываться неконсистентно. Это вне scope фичи; предполагаем, что имена нормализованы (см. `Author.kt`).

## Out of scope (явно)

- Миграция БД, новые таблицы, новые индексы (поля в DTO опциональные, дефолт `""`).
- Новый REST-эндпоинт для превью плейлиста (используем существующий `/api/public/account/playlists/{id}`).
- Кэширование превью на клиенте (полагаемся на браузерный кэш по URL MinIO + `Cache-Control: public, max-age=86400` от nginx).
- Поддержка `.smkaraoke` режима в плейлисте (он работает только в single-song; FR-012 не затрагивает playlist-flow).
- Изменение архитектуры плеера вне playlist-flow (FR-012).

## Phase 0 → Phase 1 handoff

Все технические решения зафиксированы. Phase 1:
- `data-model.md` — описать `SitePlaylistItem` с новыми полями.
- `contracts/` — `api-public-playlist-detail.md` (JSON-схема ответа).
- `quickstart.md` — пошаговый сценарий проверки фичи (без полного импл. кода).

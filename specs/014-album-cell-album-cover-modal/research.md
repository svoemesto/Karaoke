# Research: Альбомы — клик по ячейке открывает модалку обложки альбома

**Date**: 2026-07-27
**Spec**: [./spec.md](./spec.md)

## Нерешённые вопросы на старте

В `spec.md` нет блоков `[NEEDS CLARIFICATION]`, но есть три точки, где нужно принять техническое решение
**до** написания `tasks.md`. Эти решения зафиксированы ниже как **Decision** с обоснованием.

---

## Decision 1: Как получить `id` песни альбома для контекста модалки

**Контекст:** `AlbumCoverModal.vue` работает через `getCurrentSong` (Vuex `SongsStore`) → `Song.loadFromDbById(id=currentSongId)` на бэке.
В `albumsDigest` (ответ `/api/albums/albumsdigests`) нет `firstSongId` или любого указателя на песни альбома.

**Альтернативы:**

| Вариант | Плюсы | Минусы |
|---|---|---|
| **A. Расширить `AlbumDTO` полем `firstSongId: Long`** | Универсально для любого будущего использования | Ломает DTO → задевает sync (`recordhash` для `albums` + `albums_sync`? нет, `albumsDigests` это DTO, не tbl_albums, но DTO сейчас `Comparable<AlbumDTO>` и его equals/hashCode меняются). Лишний JOIN при каждом `apisAlbumsDigest` (5000+ альбомов). |
| **B. Отдельный эндпоинт `POST /api/albums/firstsongid?albumId=X`** | Минимальная инвазивность, O(1) SQL, не ломает DTO, легко кешируется на клиенте | +1 эндпоинт |
| **C. Переиспользовать существующий `loadOneRecord` (только album) + локальный поиск** | Без нового бэка | Не работает — в `albumsDigest` нет песен вообще |

**Решение: B (отдельный эндпоинт).**

**Обоснование:**
- Минимальный риск: DTO не трогаем, sync не задеваем, существующие потребители `albumsDigest` не меняются.
- SQL — тривиальный, O(1) с индексом по `tbl_songs.album_id`: `SELECT id FROM tbl_songs WHERE album_id = ? AND first_song_in_album = TRUE LIMIT 1` с fallback `SELECT id FROM tbl_songs WHERE album_id = ? ORDER BY id LIMIT 1`.
- Endpoint логически живёт рядом с `apisAlbumsDigest`/`apisCreateAlbum`/`apisUpdateAlbum` в `ApiController.kt` (раздел альбомов).
- Возвращает `Long` (id песни) или `0L` (нет песен) — согласуется с convention `setAlbumValuePromise` в `AlbumsTable.vue:461` (проверяет `result !== 0`).

**Ссылка на источник:** `Album.countSongsByAlbumIds` уже есть как образец батч-SQL (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt:227`); для единичного запроса нужен только новый helper в этом же файле.

---

## Decision 2: Как обрабатывать побочный эффект на `currentSongId`

**Контекст:** `AlbumCoverModal` использует `getCurrentSong` из `SongsStore`. Чтобы открыть её из `AlbumsTable`, нужно установить `currentSongId = <id песни альбома>`. Это побочный эффект на глобальное состояние админки.

**Альтернативы:**

| Вариант | Плюсы | Минусы |
|---|---|---|
| **A. Подменить `currentSongId` без восстановления** | Просто | Теряется рабочий контекст: если админ был в `/Songs` с `currentSongId = 42`, перешёл в `/Albums`, открыл модалку, закрыл — теперь `currentSongId = <id песни альбома>`, возврат в `/Songs` откроет чужую песню. Ломает SC-004. |
| **B. Запомнить `prevCurrentSongId` и восстановить после `@close`/`@saved`** | Сохраняет контекст, выполняет SC-004 | +1 пара строк в `AlbumsTable.vue`. |
| **C. Рефакторить `AlbumCoverModal`, чтобы принимал `id` песни через prop** | Идеологически чисто | Модалка перестаёт быть «такой же» (требование пользователя: «такую же модалку, как в SongEdit»); модалка общая, изменения зацепят `SongEdit.vue`. Больше работы, нет выигрыша. |

**Решение: B (запомнить и восстановить).**

**Обоснование:**
- Минимальные изменения — добавить `data().prevCurrentSongId` + `methods().openAlbumCoverModal()`/`closeAlbumCoverModal()` в `AlbumsTable.vue`.
- Используем существующий `setCurrentSongIdOnly` (`webvue3/src/components/Songs/store.js:1665`) для установки id **без** сетевого запроса (модалка всё равно дёрнет `getAlbumPictureBase64Promise`, который сам подтянет данные). На закрытии — `setCurrentSongIdOnly(prevCurrentSongId || null)`.
- `null` (а не `0`) согласуется с тем, что в store `currentSongId` может быть не задан — `setCurrentSongIdOnly(null)` безопасен.

---

## Decision 3: Какой endpoint выбрать для получения `firstSongId`

**Контекст:** на бэке уже есть `Song.firstSongInAlbum: Boolean` (`Song.kt:335`) — у каждого альбома есть «главная» песня.

**Альтернативы:**

| Вариант | Плюсы | Минусы |
|---|---|---|
| **A. Искать `first_song_in_album = TRUE`** | Семантически правильно | Может вернуть null, если ни одна песня альбома не помечена как `first` (старая БД / edge cases) |
| **B. `MIN(id)` среди песен альбома** | Всегда есть результат | Не использует семантику `firstSongInAlbum`, может вернуть «не ту» песню, если у альбома есть песня до `firstSongInAlbum` |
| **C. Комбинация: сначала `first_song_in_album = TRUE`, иначе `MIN(id)`** | Устойчиво ко всем edge cases | Чуть больше кода в helper'е |

**Решение: C (комбинация).**

**Обоснование:**
- Семантика `firstSongInAlbum` заведена в `Song.kt:335` именно для таких случаев (см. комментарии в `Publication.kt:91-200`, где `firstSongInAlbum` используется как маркер «главной» песни альбома).
- Fallback на `MIN(id)` — страховка от неполных данных, цена одного дополнительного `SELECT` на edge case.
- Реализация: helper `Album.getFirstSongId(albumId, database): Long?` в `Album.kt`, вызывается из нового эндпоинта `apisGetFirstSongIdByAlbumId` в `ApiController.kt`.

---

## Зависимости

- **Backend:** `karaoke-app` (Kotlin/Spring Boot), `Album.kt` (helper), `ApiController.kt` (новый endpoint).
- **Frontend:** `webvue3` (Vue 3), `AlbumsTable.vue` (UI), `Albums/store.js` (новый action), `Songs/store.js` (без изменений, переиспользуем `setCurrentSongIdOnly`).
- **НЕ требуется:** миграция БД, изменения DTO, изменения `SyncRegistry` (новый эндпоинт `/api/albums/*` — не часть sync), изменения `AlbumCoverModal.vue`, изменения backend-модулей `Picture`/`Pictures`.

## Что НЕ меняется

- `AlbumCoverModal.vue` — никаких правок (требование: «такая же модалка»).
- `Song.kt`, `Picture.kt`, `Pictures.kt` — никаких правок.
- `albumsDigest` (DTO + endpoint `/api/albums/albumsdigests`) — никаких правок (Decision 1: B).
- `SyncRegistry.all` — без изменений.
- Миграции БД — без изменений.

## Риски

| Риск | Митигация |
|---|---|
| Если `prevCurrentSongId` не был установлен (админ впервые открыл `/Albums`), а `setCurrentSongIdOnly(null)` окажется неожиданным | Восстановление `null` безопасно — store уже умеет работать с `currentSongId = null` (см. `setCurrentSongIdOnly(state, null)` в `store.js:1665`). |
| Новый endpoint `/api/albums/firstsongid` не покрыт тестами | Согласно `AGENTS.md` и `constitution.md` тесты в CI не запускаются; проверка — пользователем вручную. Документируем в `quickstart.md`. |
| Кеширование не нужно | Endpoint O(1) SQL, вызывается только при клике (не на каждый рендер таблицы) — кеш избыточен. |
| Race condition: пользователь дважды быстро кликает по разным альбомам | Простое решение: `isAlbumCoverModalVisible` блокирует повторное открытие, пока модалка открыта. Уже работает через `data().isAlbumCoverModalVisible`. |

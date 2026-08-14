# Data Model: Ускорение загрузки песен в Закромах

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-08-14

## Замечание

Эта фича — **performance-оптимизация** существующего стрима `GET /api/public/zakroma/stream`. **Новые сущности НЕ вводятся**, схема БД НЕ меняется. Существующие DTO (`ZakromaStreamMessageDto`, `ZakromaAlbumMetaPublicDto`, `ZakromaAlbumSongPublicDto`) остаются без изменений полей.

Этот документ фиксирует:
1. **Существующие сущности, задействованные в фиче** (для контекста).
2. **Новые методы на существующих сущностях** (batch lookup'ы).
3. **Границы изменений** — что НЕ меняется.

---

## Существующие сущности (задействованные)

### Backend (Kotlin)

#### `Picture` (entity)
- **Модуль**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Picture.kt` (далее по тексту — `Pictures` — фабрика).
- **Назначение**: представляет картинку (портрет автора, обложку альбома) в MinIO + БД.
- **Используется в фиче**: для lookup'ов портрета автора и обложек альбомов.
- **Изменения**: + новый статический метод `getPicturesByNames(names: List<String>, database, storageService, storageApiClient, ignoreUseInList: Boolean = false): Map<String, Picture>`.

#### `Album` (entity)
- **Модуль**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Album.kt`.
- **Назначение**: каноническая сущность альбома (с albumType, sortOrder, описанием).
- **Используется в фиче**: для подгрузки метаданных альбома по `albumId` (если песни привязаны к реальному Album).
- **Изменения**: + новый статический метод `getAlbumsByIds(ids: List<Long>, database, storageService, storageApiClient): Map<Long, Album>`.

#### `Zakroma` (entity)
- **Модуль**: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Zakroma.kt`.
- **Назначение**: view-модель для страницы «Закрома»: `author → albums → albumSongs`.
- **Используется в фиче**: как основная структура, которую строит `buildFromSongs()`.
- **Изменения**: переписать `private fun buildFromSongs(...)` так, чтобы использовать batch lookup'ы вместо N+1.

### Frontend (Vue 3 / Vuex)

#### `useZakromaStreamProgress` (composable)
- **Модуль**: `karaoke-public/src/composables/useZakromaStreamProgress.js`.
- **Назначение**: NDJSON-стрим-парсер для endpoint'а `/api/public/zakroma/stream`.
- **Используется в фиче**: точка истины для прогрессометра и заполнения `state.zakroma`.
- **Изменения**:
  - Заменить `setTimeout(resolve, 0)` на `queueMicrotask` (или `Promise.resolve()`).
  - Добавить `visibilitychange` listener — при возврате на вкладку протолкнуть накопленные данные через `nextTick()`.
  - Yield **пачками по 50 сообщений** (вместо каждого).

#### `zakroma` (Vuex module)
- **Модуль**: `karaoke-public/src/store/modules/zakroma.js`.
- **Назначение**: хранилище `authors`, `authorTiles`, `zakroma`, `specialBucket`, `isStreaming`, `streamProgress`, `streamError`.
- **Используется в фиче**: action `loadZakromaStream` запускает стрим.
- **Изменения**: нет (только `composable` обновляется).

### Backend endpoint (read-only)

#### `GET /api/public/zakroma/stream`
- **Модуль**: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicApiController.kt:254-403`.
- **Контракт** (НЕ меняется): NDJSON, 5 типов сообщений — `meta`/`album`/`song`/`done`/`error`.
- **Изменения**: батч-flush по 50 песен (вместо flush после каждой). Сообщения те же самые.

---

## Новые методы (детализация)

### `Pictures.getPicturesByNames`

```kotlin
/**
 * Batch-загрузка картинок по списку имён. Аналог getPictureByName,
 * но одним SQL-запросом вместо N.
 *
 * Используется в Zakroma.buildFromSongs() для устранения N+1 при загрузке
 * страницы автора с большим числом альбомов.
 *
 * @param names список имён картинок (например, портреты авторов
 *             или обложки альбомов в формате `"Author - Year - AlbumName"`)
 * @param database соединение с БД (см. Constitution II: только KaraokeConnection)
 * @param storageService сервис MinIO (lazy-инициализируется в Pictures)
 * @param storageApiClient клиент MinIO API (lazy-инициализируется в Pictures)
 * @param ignoreUseInList если true — игнорировать флаг `use_in_list` (для preview).
 *                        В Закромах всегда false (см. Zakroma.kt:119, 163).
 * @return Map<name, Picture>. Если картинки с таким именем нет — отсутствует в Map.
 *         Порядок НЕ сохраняется.
 * @see docs/features/zakroma-stream-progress.md
 */
fun getPicturesByNames(
    names: List<String>,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
    ignoreUseInList: Boolean = false,
): Map<String, Picture>
```

**Реализация** (псевдокод):
```kotlin
if (names.isEmpty()) return emptyMap()
val placeholders = names.joinToString(",") { "?" }
val sql = """
    SELECT * FROM tbl_pictures
    WHERE name IN ($placeholders)
      ${if (ignoreUseInList) "" else "AND use_in_list = true"}
"""
val result = mutableMapOf<String, Picture>()
database.prepareStatement(sql).use { stmt ->
    names.forEachIndexed { i, name -> stmt.setString(i + 1, name) }
    val rs = stmt.executeQuery()
    while (rs.next()) {
        val pic = Picture.fromResultSet(rs, database, storageService, storageApiClient)
        result[pic.name] = pic  // последний выигрывает (как в getPictureByName)
    }
}
return result
```

**Ловушка**: 
- SQL-инъекция — параметризованные плейсхолдеры обязательны (Constitution II).
- Размер списка: PostgreSQL `IN` поддерживает до 32767 параметров в одном запросе. Для 2500 песен / 30 альбомов это 60 имён — безопасно.
- Дедупликация `names` на стороне caller (если один автор упомянут 2 раза — лучше дедупнуть ДО запроса).

**Альтернативная сигнатура**: возвращать `Map<String, List<Picture>>` если по одному имени может быть несколько картинок. Для Закромов имя уникально (формат `"Author - Year - AlbumName"` или просто `"Author"`), так что `Map<String, Picture>` достаточно.

---

### `Album.getAlbumsByIds`

```kotlin
/**
 * Batch-загрузка альбомов по списку id. Аналог getAlbumById,
 * но одним SQL-запросом вместо N.
 *
 * Используется в Zakroma.buildFromSongs() для устранения N+1 при загрузке
 * страницы автора с большим числом альбомов.
 *
 * @param ids список ID альбомов (из `tbl_songs.album_id`)
 * @param database соединение с БД (см. Constitution II: только KaraokeConnection)
 * @param storageService сервис MinIO
 * @param storageApiClient клиент MinIO API
 * @return Map<id, Album>. Если альбома с таким id нет — отсутствует в Map.
 * @see docs/features/zakroma-stream-progress.md
 */
fun getAlbumsByIds(
    ids: List<Long>,
    database: KaraokeConnection,
    storageService: KaraokeStorageService,
    storageApiClient: StorageApiClient,
): Map<Long, Album>
```

**Реализация** — аналогично `getPicturesByNames`, через `WHERE id IN (...)`.

---

### `Author.getAuthorsByNames` (опционально, для единообразия)

Аналогично `getPicturesByNames`, но `WHERE name IN (...)` для таблицы `tbl_authors`.

**Решает**: для автора, у которого есть `Author`-сущность (с описанием/коротким/предупреждением) — раньше делался 1 SQL на автора. На странице «Все авторы» (не наш случай, но в будущем) это тоже N+1. **Не блокирует фичу** — оставлено на backlog, если `buildFromSongs` будет переиспользоваться.

---

## Границы изменений (что НЕ меняется)

### НЕ меняется схема БД
- Никаких миграций.
- Никаких изменений в `deploy/karaoke-db/`.
- Никаких новых индексов (текущие индексы на `tbl_pictures.name` и `tbl_albums.id` достаточны для `WHERE IN`).

### НЕ меняется NDJSON-контракт
- Те же 5 типов сообщений (`meta`/`album`/`song`/`done`/`error`).
- Те же DTO (`ZakromaStreamMessageDto`, `ZakromaAlbumMetaPublicDto`, `ZakromaAlbumSongPublicDto`).
- Меняется только **ритмика отправки**: вместо «по 1 сообщению с flush после каждого» — «album сразу + до 50 song в буфере с одним flush на пачку».

### НЕ меняется публичное API Закромов
- `state.zakroma` (Vuex) — структура та же.
- `loadZakromaStream` action — сигнатура та же.
- `ZakromaPublicDto` / `ZakromaAlbumPublicDto` / `ZakromaAlbumSongPublicDto` — поля те же.

### НЕ меняется nginx-конфиг
- `proxy_buffering off` для `/api/public/zakroma/stream` уже включён (см. `docs/features/zakroma-stream-progress.md` секция «Ловушки nginx»).
- Может потребоваться `proxy_buffer_size 8k` или больше для batched-чанков — backlog (замерить post-merge).

### НЕ затрагивается `webvue3` (админка)
- Изменения только в `karaoke-public` (публичный SPA).

---

## State transitions

`composable.useZakromaStreamProgress` имеет 4 состояния (`idle`, `streaming`, `loaded`, `error`). Эта фича **не вводит новых состояний**, только ускоряет переход `idle → streaming → loaded`.

| Before | After | Notes |
|--------|-------|-------|
| idle (state очищен, прогрессометр скрыт) | streaming (первая партия видна, прогрессометр 0→N%) | Без изменений |
| streaming (2500 песен × ~5мс каждое = 12.5 сек) | streaming (2500 песен × ~1мс каждое = 2.5 сек) | Благодаря batched flush (R2) |
| loaded | loaded | Без изменений |
| error | error | Без изменений |

**Новое поведение при переключении вкладки** (R3):
- В активной вкладке: каждые 50 сообщений → microtask yield → Vue render.
- В скрытой вкладке: yield'ов нет → данные копятся в `albums.value` пачкой → при возврате через `visibilitychange` → `nextTick()` проталкивает state.

Это **не state transition** — это оптимизация внутри существующего `streaming`-состояния.

---

## Validation rules (из спеки)

Из спеки [spec.md](./spec.md#requirements):
- **FR-001**: первая партия песен на экране через ≤ 2 сек. → Достигается через R1 (batch lookup, backend быстрее).
- **FR-002**: полная отрисовка через ≤ 7 сек. → Достигается через R1 + R2 + R3.
- **FR-003**: прогрессометр real-time. → Уже было в спеке 181; не ломаем.
- **FR-004**: прогрессометр появляется за ≤ 100мс. → Уже было в спеке 181 (после `meta`); не ломаем.
- **FR-005**: при возврате на вкладку прогрессометр показывает актуальное значение. → Достигается через R3.
- **FR-006**: если загрузка завершилась в фоне — прогрессометр скрыт (не «доигрывает»). → Достигается через R3 (`nextTick()` на `visibilitychange`).
- **FR-007**: для малых авторов (≤ 50 песен) прогрессометр не мелькает. → Уже было в спеке 181 (debounce 300мс); не ломаем.
- **FR-008**: загрузка отменяема. → Уже было в спеке 181 (`AbortController`); не ломаем.
- **FR-009**: ошибка сервера → прогрессометр скрыт, retry-кнопка. → Уже было; не ломаем.
- **FR-010**: для ≤ 200 песен поведение не ухудшается. → Достигается за счёт R1 (вместо 6 SQL → 4 SQL, экономия минимальна, регрессии нет).

**Все 10 FR валидируются через quickstart.md сценарии.**
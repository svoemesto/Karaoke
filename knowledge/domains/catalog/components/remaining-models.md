# Component: remaining-models

> **Домен**: [catalog](../domain.md)
> **Компонента**: оставшиеся модели karaoke-app, не покрытые
> [entities-catalog.md](entities-catalog.md).

## Ответственность

Детальное описание моделей, которые НЕ являются DB-entity в полном
смысле (нет `KaraokeDbTable`), но являются критическими DTO
с реальной логикой.

---

## `StemJob`

**Файл**: `karaoke-app/.../model/StemJob.kt`. **Таблица**: `tbl_stem_jobs`.

**Премиум-фича «Создать минусовку из аудиофайла»**. Сущность живёт
**только на LOCAL** (не синхронизируется с продом).

**Поля** (по grep `@KaraokeDbTableField`):

- `id BIGINT PK`
- `site_user_id BIGINT NOT NULL` — FK на `SiteUser`
- `mode VARCHAR NOT NULL` — `DEMUCS2` / `DEMUCS5`
- `status VARCHAR NOT NULL` — `WAITING` / `WORKING` / `DONE` / `ERROR`
- `original_file_name VARCHAR` — имя файла
- `original_ext VARCHAR` — расширение
- `file_size_bytes BIGINT` — размер
- `expires_at TIMESTAMP` — срок жизни (auto-delete)
- `delete_requested BOOLEAN DEFAULT false` — пользователь нажал «удалить»
- `error_message TEXT`

**Константы**:
- `StemJobStatus.{WAITING, WORKING, DONE, ERROR}`
- `StemJobMode.{DEMUCS2, DEMUCS5}`
- `StemJob.MAX_FILE_SIZE_BYTES` (лимит upload)
- `StemJob.MAX_ACTIVE_JOBS_PER_USER` (лимит очереди)
- `StemJob.ALLOWED_EXTENSIONS`

**Lifecycle** (по `StemJobPollScheduler`):

1. User загружает файл через `PublicStemJobController.create` →
   `StemJob.createNew(...)` → `STATUS = WAITING`, INSERT в
   `tbl_stem_jobs`. Файл сохраняется в temp-dir НЕ в MinIO.
2. `StemJobPollScheduler.pollWaiting` (каждые 45с) скачивает файл
   с karaoke-web через `InternalStemJobController`, проверяет
   длительность (ffprobe), создаёт `KaraokeProcess` (TYPE=`STEM_JOB_*`,
   THREAD_LANE=`STEM_JOBS`).
3. `KaraokeProcessWorker.doStart` → Demucs → upload в MinIO.
4. `StemJob.status = DONE`. MinIO содержит `stemjobs/{id}/<stem>.mp3`.
5. User скачивает через `PublicStemJobController.download` (стрим
   из MinIO через `StorageApiClientWeb`).
6. `StemJobPollScheduler.cleanup` (каждые 5мин) удаляет
   `DONE + expiresAt < now` или `deleteRequested = true`.

**Связь**: см.
[schedulers.md](../../../domains/processing/components/schedulers.md) +
[storage-flow.md](../../../domains/storage/components/storage-flow.md).

---

## `Publication`

**Файл**: `karaoke-app/.../model/Publication.kt`. **Таблица**: `tbl_publications` (предположительно).

**Содержит дату публикации + ссылки на 12 версий `SongDTO`** —
`publish10`, `publish11`, ..., `publish21` — ежедневные публикации
песни в разных форматах.

**Sync**: НЕ участвует (нет в `SyncRegistry.all`). Только LOCAL —
используется для UI/отчётов.

**Использование**: в cross-tab отчётах (`CrossSong.publications`).

---

## `Zakroma`

**Файл**: `karaoke-app/.../model/Zakroma.kt`.

**НЕ entity** — это **агрегат для публичного сайта**:
"Закрома автора" — все его песни, сгруппированные по альбомам.

**Метод**:
```kotlin
companion object {
    fun getZakroma(
        author: String,
        database: KaraokeConnection,
        onlyPublished: Boolean = false,
    ): ... 
}
```

**`onlyPublished`**:
- `true` (по умолчанию для публичных) — только песни с
  `readiness >= 3` (как в `StatBySong`).
- `false` (admin) — все песни редактора.

**Связь**: `archive/docs/features/special-orders.md`.

---

## `EventTypes`

**Файл**: `karaoke-app/.../model/EventTypes.kt`. (предположительно)

**Константы** для типов событий в `tbl_events` (`WebEvent.event_type`):
`rest`, `download`, `share`, и т.д. (нужно grep'нуть — Pass 343+).

---

## `Messages`

**Файл**: `karaoke-app/.../model/Messages.kt`. (предположительно)

**DTO для сообщений** в SSE (recordChangeMessage, RecordAddMessage,
RecordDeleteMessage, ProcessWorkerStateMessage, ProcessCountWaitingMessage, Message).

Каждое используется в `SseNotification.companion object` (см.
[sse domain](../../../domains/sse/domain.md)).

---

## `KaraokeDbTableDto` (базовый DTO)

**Файл**: `karaoke-app/.../model/KaraokeDbTableDto.kt`. (предположительно)

Базовый DTO для всех entity. Все `*Dto.kt` файлы расширяют его.

---

## `SongOutputFile`, `SongRenderContext`

**Файлы**: `SongOutputFile.kt`, `SongRenderContext.kt`. (предположительно)

Контексты для рендера. Не entity — DTO, передаваемые между
рендером (MLT) и кодом.

---

## `SongShortInfoDto`, `KaraokeDbTableDto`

**Файлы**: `SongShortInfoDto.kt`, `KaraokeDbTableDto.kt`.

**DTO** для разных ответов API:
- `SongShortInfoDto` — короткая инфа о песне (id, fileName, status).
- `KaraokeDbTableDto` — базовый для всех entity.

---

## `Messages`, `SongField`, `SongVersion`, `SongState`, `SongType`

**Файлы**: `Messages.kt`, `SongField.kt`, `SongVersion.kt`, `SongState.kt`, `SongType.kt`.

**Enum / DTO** для песен:

- `SongField` — поля, по которым cross-tab строится (`DATE`, `AUTHOR`, `TIME`).
- `SongVersion` — `LYRICS`, `KARAOKE`, `DEMO`, `CHORDS`, `TABS`.
- `SongState` — состояние MLT.
- `SongType` — тип песни.

---

## Известные TODO

- [ ] `EventTypes` — точные константы.
- [ ] `Messages` — каждое поле каждого recordChange/add/delete/process.
- [ ] `SongOutputFile` / `SongRenderContext` — контракты.
- [ ] `Publication` — точные колонки БД.

## Код (физическая реализация)

Все в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/`.

## Changelog

- **Pass 348** (2026-09-09): Initial. Автор: agent (Karaoke).
# Component: InternalStemJobController

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `InternalStemJobController` — server-to-server для
> StemJob processing.

## Файл

`karaoke-web/.../controllers/InternalStemJobController.kt`

## Назначение

**Server-to-server** эндпоинты для **karaoke-app** (`StemJobPollScheduler` /
`StemJobProcessing`) — **НЕ** проходят через `SiteAuthInterceptor`.

**Защита**: отдельный **shared-secret заголовок** `X-Internal-Secret`:
- Значение — `Karaoke.stemJobsInternalSecret` на стороне karaoke-app.
- Значение — `stemjobs.internal-secret` на стороне karaoke-web.
- Задаётся админом **одинаковым** на обеих сторонах при деплое.

## Endpoints

| URL | Что |
|---|---|
| `/api/internal/stem-jobs/{id}/raw` | Сырой файл, загруженный пользователем (в `temp-dir` karaoke-web) |
| `/api/internal/stem-jobs/{id}/ack` | Подтверждение, что файл забран и обработан — можно удалить |

## Логика

### `/raw` — отдача сырого файла

karaoke-app через `StemJobPollScheduler.pollWaiting` (каждые 45с)
делает `takeJob(job)`:

1. Скачивает файл через `InternalStemJobController` (этот endpoint).
2. Проверяет длительность через `ffprobe` ДО постановки в очередь
   Demucs.
3. Создаёт `KaraokeProcess` (TYPE=`STEM_JOB_*`, THREAD_LANE=`STEM_JOBS`).

### `/ack` — подтверждение

После обработки (успешно или с ошибкой) karaoke-app вызывает `/ack`.
karaoke-web **удаляет** файл из `temp-dir`.

**Best-effort**: если `/ack` не дойдёт, temp-файл всё равно
зачистится по TTL (см. `StemJobTempCleanupScheduler`, Pass 344).

## Hot paths

- **Каждые 45 секунд** `StemJobPollScheduler.pollWaiting` — может
  скачивать файлы.
- **Per StemJob** — 1 download + 1 ack.

## Связь

- [storage-flow.md](../../storage/components/storage-flow.md) — общий
  поток.
- [stem-job.md](../../catalog/components/remaining-models.md#stemjob) —
  entity.
- [schedulers.md](../../processing/components/schedulers.md) —
  StemJobPollScheduler + StemJobTempCleanupScheduler.

## Известные TODO

- [ ] **`X-Internal-Secret`** — детали валидации (Pass 343+).
- [ ] **TTL** — точное значение для StemJobTempCleanupScheduler.

## Changelog

- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).
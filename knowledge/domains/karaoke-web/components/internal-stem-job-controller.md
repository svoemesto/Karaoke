# Component: InternalStemJobController

> **Домен**: [karaoke-web](../domain.md)
> **Компонента**: `InternalStemJobController` — server-to-server для
> StemJob processing.


## Ответственность | Responsibility


`InternalStemJobController` — server-to-server для StemJob processing.

## Файл

`karaoke-web/.../controllers/InternalStemJobController.kt`

## Назначение

**Server-to-server** эндпоинты для **karaoke-app** (`StemJobPollScheduler` /
`StemJobProcessing`) — **НЕ** проходят через `SiteAuthInterceptor`.

**Защита**: отдельный **shared-secret заголовок** `X-Internal-Secret`:
- Значение — `Karaoke.stemJobsInternalSecret` на стороне karaoke-app.
- Значение — `stemjobs.internal-secret` на стороне karaoke-web.
- Задаётся админом **одинаковым** на обеих сторонах при деплое.

## Интерфейсы и Контракты | Interfaces and Contracts

`@RequestMapping("/api/internal/stemjobs")`, авторизация — заголовок
`X-Internal-Secret` (`authorized(request)`): при пустом
`stemjobs.internal-secret` доступ закрыт всегда (fail-safe).

| URL | Что | Контракт |
|---|---|---|
| `GET /api/internal/stemjobs/{id}/raw` | Сырой файл, загруженный пользователем (в `temp-dir` karaoke-web) | 403 без валидного секрета; 404 если `StemJob` или файл не найдены; 200 `application/octet-stream` + `Content-Length` |
| `POST /api/internal/stemjobs/{id}/ack` | Подтверждение, что файл забран и обработан — можно удалить | 403 без валидного секрета; 200 всегда (файл удаляется best-effort) |

**Актуальный путь — `/api/internal/stemjobs/...`** (проверено по
`InternalStemJobController.kt`: `@RequestMapping("/api/internal/stemjobs")`,
`:46` и `:71`). Вариант `/api/internal/stem-jobs/...` в старых описаниях
не существует (Pass 477).

## Логика и Алгоритмы | Logic and Algorithms

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

## Зависимости | Dependencies

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

- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).
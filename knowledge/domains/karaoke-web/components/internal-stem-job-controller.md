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
| `PUT /api/internal/stemjobs/{id}/files/{fileName}` | **Приём приватного объекта задания** (тело — сырые байты). Спека 561 (OpenProject #361, аудит BR-258): результаты задания лежат в приватном бакете `stemjobs`, читать который `karaoke-web` не умеет — их передаёт `karaoke-app` (единственный клиент с учётными данными) | 403 без валидного секрета; 400 при недопустимом имени файла (`<stem>.<ext>` из allow-list); 200 при успехе; 5xx без содержимого объекта |
| `POST /api/internal/stemjobs/{id}/purge` | Удаление локальных копий задания (приватный каталог результатов + сырой файл) | 403 без валидного секрета; 200 всегда |

**Актуальный путь — `/api/internal/stemjobs/...`** (проверено по
`InternalStemJobController.kt`: `@RequestMapping("/api/internal/stemjobs")`,
`:46` и `:71`). Вариант `/api/internal/stem-jobs/...` в старых описаниях
не существует (Pass 477).

**Каталог приватных результатов:** `StemJobResultFiles`
(`karaoke-web/.../services/StemJobResultFiles.kt`) — раскладка
`<result-dir>/<jobId>/<stem>.<ext>`; параметр `stemjobs.result-dir`
(env `STEMJOBS_RESULT_DIR`, пусто → `${stemjobs.temp-dir}/results`).
Каталог **не публикуется** nginx'ом — единственный путь к файлу —
авторизованный `PublicStemJobController.download`.

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

### `/files/{fileName}` — передача приватных объектов (спека 561)

`karaoke-app` после загрузки результатов в приватный бакет `stemjobs`
**читает** объект (единственный клиент с учётными данными) и передаёт
его сюда: `PUT /api/internal/stemjobs/{id}/files/{fileName}`, тело — сырые
байты, заголовок `X-Internal-Secret`.

Правила приёма:

1. Секрет не настроен (пусто) → **403** всегда (fail-safe).
2. Заголовок отсутствует или не совпадает → **403**.
3. Имя файла проверяется по allow-list (`StemJob.isValidResultFileName`):
   `<stem>.<ext>`, где `stem` ∈ {accompaniment, vocals, drums, bass, other,
   original}, `ext` — `mp3` для стемов либо расширение исходника из
   `StemJob.ALLOWED_EXTENSIONS`; имя с `/`, `..` и прочим → **400**.
4. Проверки задания по БД на этом пути нет — канал доверенный; «фантомные»
   каталоги удаляет уборка по TTL.

Направление канала — admin → prod: прод-`karaoke-web` не достигает
admin-машины (firewall, см. `PublicVkAuthController`).

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

- **Спека 561** (2026-10-24, OpenProject #361, аудит BR-258): добавлены эндпоинты `PUT /{id}/files/{fileName}` и `POST /{id}/purge` — приём приватных объектов задания по тому же секрету `X-Internal-Secret`. Автор: agent (Karaoke).
- **Pass 481** (2026-09-27, spec `481-knowledge-domain-karaoke-web`): секции приведены к шаблону компонента. Автор: agent (Karaoke).
- **Pass 436-438** (2026-09-09): Initial. Автор: agent (Karaoke).
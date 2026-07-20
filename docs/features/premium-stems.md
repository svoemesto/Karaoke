# Премиум-фича «Создать минусовку из аудиофайла»

> **Status**: active
> **Feature Key**: premium-stems
> **Last Updated**: 2026-07-20

## Что делает

Пользователь karaoke-public загружает произвольный аудиофайл, получает
исходник + стемы (demucs2/demucs5 — vocals, drums, bass, other). Задание
живёт целиком на PROD-БД (не в `SyncRegistry`), админ видит все задания
в webvue3 «Минусовки» и может остановить/удалить.

## Зачем

Основной пайплайн Karaoke — обработка песен из собственной коллекции.
Премиум-фича — для пользователей, которые хотят загрузить свою песню и
получить караоке-минусовку. Это отдельный поток: свои `KaraokeProcessTypes`
(`STEM_JOB_DEMUCS2/5`), свой thread-лейн (`THREAD_LANE_STEM_JOBS`), своя
таблица (`tbl_stem_jobs`).

## Как работает (кратко)

1. **Загрузка файла** — пользователь в karaoke-public отправляет
   multipart на `POST /api/public/stem-jobs`. Файл временно лежит на
   диске `karaoke-web` (она не пишет в MinIO напрямую).
2. **Создание задания** — `karaoke-web` создаёт запись в `tbl_stem_jobs`
   на PROD-БД, статус `PENDING`.
3. **Polling** — `StemJobPollScheduler` (на стороне karaoke-app, но он
   не развёрнут на проде — поэтому файл забирает karaoke-web через
   HTTP с общим секретом `X-Internal-Secret`).
4. **Demucs** — `KaraokeProcessTypes.STEM_JOB_DEMUCS2/5` запускает
   `demucs` как subprocess в thread-лейне `THREAD_LANE_STEM_JOBS`.
5. **Финализация** — `runFunctionWithArgs` → `executeFinalizeStemJob` —
   загружает стемы в MinIO, обновляет статус на `COMPLETED`.
6. **Уведомление пользователя** — через SSE-канал (или polling из UI).
7. **Cleanup** — `StemJobCleanup` периодически удаляет старые задания
   (> N дней) и их файлы.

## Инварианты / правила

- **MUST**: задания НЕ участвуют в `SyncRegistry` (по образцу
  `tbl_site_chat_messages`) — append-only PROD-only.
- **MUST**: `settingsId=0` (нет привязки к `Settings`/`tbl_settings`).
- **MUST**: файл передаётся karaoke-app через HTTP с `X-Internal-Secret`
  (см. [DEVELOPMENT.md#premium-фича-создать-минусовку-из-аудиофайла-stemjob-tbl_stem_jobs](../../DEVELOPMENT.md)).
- **MUST**: премиум-доступ проверяется на стороне karaoke-web
  (через `tbl_site_users` и подписку).
- **SHOULD**: `Demucs5` (5 стемов) значительно дольше `Demucs2`
  (vocals/accompaniment). По умолчанию — `Demucs2`; `Demucs5` — по запросу.

## Известные ловушки

- **Длинные файлы**: Demucs на 10-минутном треке идёт 30+ минут. UI должен
  показывать прогресс через polling, не вечная загрузка.
- **Disk pressure**: 10 одновременных Demucs-заданий × 200MB временных
  файлов = 2GB. Мониторьте `/sm-karaoke/system/tmp/`.
- **Vpn/антифрод**: некоторые IP-адреса Demucs не запускает (защита от
  парсинга). На prod-сервере это OK; на admin-машине с VPN может ломаться.

## Ссылки на ключевые классы/файлы

- [`StemJob.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/StemJob.kt) — модель задания
- [`StemJobController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StemJobController.kt) — REST для пользователя
- [`StemJobsAdminController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/StemJobsAdminController.kt) — admin-управление
- [`StemJobPollScheduler.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StemJobPollScheduler.kt) — polling из karaoke-web
- [`StemJobCleanup.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/StemJobCleanup.kt) — очистка старых заданий
- [`tbl_stem_jobs.sql`](../../deploy/karaoke-db/) — таблица
- [`docs/stemjobs-admin-guide.md`](../stemjobs-admin-guide.md) — настройка и эксплуатация

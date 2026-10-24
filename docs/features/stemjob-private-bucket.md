# Feature: Приватный бакет `stemjobs` и закрытый канал отдачи стемов (BR-258)

**Spec**: [`specs/561-impl-stem-bucket/plan.md`](../../specs/561-impl-stem-bucket/plan.md)
**Исходная спека**: [`specs/556-close-stem-bucket-anonymous/spec.md`](../../specs/556-close-stem-bucket-anonymous/spec.md)
**Status**: active (спека 561, OpenProject #361, аудит BR-258)
**Created**: 2026-10-24
**Last updated**: 2026-10-24
**Source**: OpenProject #361 — «S1: результаты и исходники премиум-задания „Создать минусовку“ кладутся в тот же бакет karaoke, что и публичные картинки/mp3 (BR-258)»
**Feature Key**: `stemjob-private-bucket`

## Что делает

Закрывает анонимный доступ к результатам премиум-задания «Создать минусовку» (стемы и
загруженный пользователем исходный файл) — спека 561, OpenProject #361, аудит BR-258 (S1).

Три части:

1. **Физическое разделение.** Приватные объекты задания кладутся в отдельный бакет `stemjobs`
   **без публичной bucket policy**. Публичный бакет `karaoke` (картинки/обложки/превью/mp3
   песен) не меняется и по-прежнему раздаётся анонимно через `/minio/karaoke/...`.
2. **Программный инвариант закрытости.** При старте `karaoke-app` приложение само приводит
   свои бакеты к безопасному состоянию: `stemjobs` → private, `karaoke` → public. Закрытость
   не зависит от ручных действий администратора.
3. **Закрытый канал отдачи.** `karaoke-web` не умеет читать MinIO с учётными данными
   (`WebKaraokeStorageServiceImpl` — заглушка), а presigned-URL через nginx path-proxy
   ломается переписыванием пути (SigV4). Поэтому приватный объект читает приложение, имеющее
   доступ (`karaoke-app`), и передаёт его в `karaoke-web` по **уже настроенному** общему
   секрету `X-Internal-Secret` (fail-safe) — по образцу `InternalStemJobController`.
   Владелец задания получает файл через прежний публичный эндпоинт скачивания.

## Зачем

Аудит кода #336 (находка P-06 / BR-258, severity **S1**): результаты задания и загруженный
исходный файл клались в бакет `karaoke`, у которого bucket policy разрешает анонимный
`s3:GetObject` на весь префикс, а прод-nginx проксирует `/minio/<bucket>/<object>` без
авторизации. Итог: `stemjobs/<id>/<stem>.mp3` и `stemjobs/<id>/original.<ext>` читались
анонимным GET по угадываемому URL — обход премиум-гейта и утечка чужого пользовательского
аудио.

Правка закрывает доступ двумя независимыми рубежами: приватная bucket policy (MinIO сам
отдаёт 403 анонимному клиенту) **и** отсутствие приватных объектов в публичном бакете.

## Как работает

### Раскладка объектов

| Что | Бакет | Ключ | Кто читает |
|---|---|---|---|
| Стем задания | `stemjobs` (private) | `<jobId>/<stem>.mp3` | `karaoke-app` (с учётными данными) |
| Исходный файл | `stemjobs` (private) | `<jobId>/original.<ext>` | `karaoke-app` (с учётными данными) |
| Legacy-объекты (до правки) | `karaoke` (public) | `stemjobs/<jobId>/<stem>.<ext>` | удаляются уборкой |

Константы и хелперы — `karaoke-app/.../model/StemJob.kt`:
`STORAGE_BUCKET`, `PUBLIC_STORAGE_BUCKET`, `LEGACY_OBJECT_PREFIX`, `objectKey()`,
`legacyObjectKey()`, `resultFileName()`, `validStemNames()`, `isValidResultFileName()`.

### Загрузка и передача (`karaoke-app`)

- `StemJobProcessing.executeFinalizeStemJob`:
  1. загрузка исходника и стемов в **приватный** бакет `stemjobs` (`SAC_APP.uploadFile`);
  2. передача каждого объекта в `karaoke-web` (`StemJobResultPublisher.publish`) — приложение
     **читает объект из бакета** (единственный клиент с учётными данными) и отдаёт его по
     внутреннему каналу;
  3. только после подтверждения передачи — `DONE` + `expiresAt = now + 24 ч`.
  Если передача не удалась (3 попытки на сетевую ошибку, без повтора на 403) — задание
  помечается `ERROR`, а уже загруженные объекты удаляются: пользователь не получает
  «обещанное, но недоступное» скачивание.
- `StemJobResultPublisher` (`karaoke-app/.../StemJobResultPublisher.kt`) — клиент канала:
  `PUT <stemJobsWebInternalUrl>/api/internal/stemjobs/{id}/files/{fileName}`, заголовок
  `X-Internal-Secret: <Karaoke.stemJobsInternalSecret>`, тело — сырые байты объекта.
- `StemJobStoragePolicyInitializer` (`karaoke-app/.../StemJobStoragePolicyInitializer.kt`) —
  при старте (`ApplicationReadyEvent`): `stemjobs` → private, `karaoke` → public. Fail-safe:
  ошибка связи с MinIO логируется (`infra.stemjobs.storage`) и **не** роняет старт.

### Приём и отдача (`karaoke-web`)

- `InternalStemJobController`:
  - `PUT /api/internal/stemjobs/{id}/files/{fileName}` — приём приватного объекта; секрет
    обязателен (fail-safe: пустой настроенный секрет → 403 всегда), имя файла проверяется по
    allow-list (`<stem>.<ext>`, без разделителей пути) → иначе 400;
  - `POST /api/internal/stemjobs/{id}/purge` — удаление локальных копий задания;
  - существующие `/raw` и `/ack` — без изменений.
- `StemJobResultFiles` (`karaoke-web/.../services/StemJobResultFiles.kt`) — раскладка
  приватных копий: `<result-dir>/<jobId>/<stem>.<ext>`. Каталог **не публикуется** nginx'ом;
  единственный путь к файлу — авторизованный эндпоинт.
- `PublicStemJobController.download` — отдаёт файл из приватного каталога (прежде читался по
  анонимному адресу `/minio/karaoke/<key>`). Проверки прежние: владелец задания, статус
  `DONE`, TTL (24 ч), допустимый стем. Формат ответа прежний (`Content-Type`,
  `Content-Disposition`, коды 404/400).

### Уборка

- `StemJobCleanup.cleanupJob` (`karaoke-app`) — удаляет объекты из приватного бакета, **legacy-объекты**
  из публичного бакета `karaoke` и вызывает `purge` на `karaoke-web`.
- `StemJobTempCleanupScheduler` (`karaoke-web`) — safety-net: сырые загрузки старше 2 ч,
  каталоги результатов старше 26 ч (TTL 24 ч + запас).

### Параметры

| Параметр | Где | Env | Смысл |
|---|---|---|---|
| `stemjobs.internal-secret` | `karaoke-web/src/main/resources/application.yml` | `STEMJOBS_INTERNAL_SECRET` | общий секрет `X-Internal-Secret` (уже был) |
| `stemjobs.result-dir` | `karaoke-web/src/main/resources/application.yml` | `STEMJOBS_RESULT_DIR` | каталог приватных копий; пусто → `${stemjobs.temp-dir}/results` |
| `stemJobsWebInternalUrl` | `Karaoke.properties` (karaoke-app) | — | базовый URL `karaoke-web` для internal-канала (уже был) |
| `stemJobsInternalSecret` | `Karaoke.properties` (karaoke-app) | — | значение секрета на стороне отправителя (уже был) |

Значения секретов в репозиторий не попадают (Constitution VIII).

## Инварианты

1. Приватные объекты задания **не лежат** в публичном бакете `karaoke` — только в `stemjobs`
   с пустой bucket policy.
2. Анонимный GET к `/minio/stemjobs/<key>` не отдаёт содержимого (403 от MinIO).
3. Публичные файлы бакета `karaoke` (картинки/превью/mp3 песен) остаются анонимно доступны.
4. Пустой настроенный секрет = канал закрыт всегда (fail-safe): «пустой секрет + пустой
   заголовок» не открывает доступ.
5. Приватный объект отдаётся только владельцу задания; чужим, анонимным и истёкшим — 404
   без раскрытия факта существования.
6. Задание помечается `DONE` только после подтверждённой передачи объектов в `karaoke-web`.
7. TTL 24 ч и периодическая уборка сохранены — в том числе для legacy-объектов.
8. Содержимое приватного объекта не попадает в журнал (только источник, задание, имя объекта).
9. Формат публичного API не изменён (FR-011); изменён только источник байтов.
10. Значения секретов не попадают в репозиторий, сборку и журналы.

## Известные ловушки

- **`karaoke-web` не умеет читать MinIO с учётными данными.** `WebKaraokeStorageServiceImpl` —
  заглушка: все методы бросают `UnsupportedOperationException`. Presigned-URL через
  path-proxy не работает: nginx переписывает `/minio/<bucket>/<key>` → `/<bucket>/<key>`, а
  подпись SigV4 покрывает canonical URI — подписанный и полученный MinIO пути не совпадают.
  Поэтому приватные объекты отдаёт приложение с доступом.
- **Направление канала — только admin → prod.** Прод-`karaoke-web` не имеет прямого доступа
  к admin-машине `nsa-i9` (firewall, см. `PublicVkAuthController`). Вариант «web тянет объект у
  приложения» был бы нерабочим на проде.
- **Секрет не настроен = премиум-скачивание не работает.** При пустом `STEMJOBS_INTERNAL_SECRET`
  (или на любой из сторон) канал закрыт fail-safe: задание уйдёт в `ERROR` с сообщением
  «Ошибка передачи результатов на сайт». Симметричная настройка секрета — обязательное
  операционное условие.
- **Legacy-объекты в публичном бакете живут до 24 часов после развёртывания** — их удаляет
  уборка (`StemJobCleanup`), а не миграция. В этот период анонимный GET к
  `/minio/karaoke/stemjobs/<id>/...` ещё работает — это ожидаемый переходный период.
- **Каталог результатов по умолчанию внутри контейнера** (`${STEMJOBS_TEMP_DIR}/results`):
  перезапуск `karaoke-web` удаляет копии, и скачивание вернётся только после повторной
  обработки задания. Для продакшена указывайте `STEMJOBS_RESULT_DIR` на постоянный том.
- **Ошибка связи с MinIO при старте `karaoke-app` не роняет приложение** (fail-safe) — но
  тогда инвариант закрытости не применится: следить за `infra.stemjobs.storage` в журнале.
- **`setBucketPublic("karaoke")` перезаписывает bucket policy целиком.** Сегодня политика
  `karaoke` — `s3:GetObject` на весь бакет, поэтому фактического изменения доступа нет; но
  если политику сузить вручную, старт `karaoke-app` её расширит.
- **Проверка задания в server-to-server канале по БД не выполняется** — канал доверенный
  (доступ есть только у приложения со знанием секрета); «фантомные» каталоги удаляет
  уборка по TTL.

## Ссылки

- [`specs/556-close-stem-bucket-anonymous/spec.md`](../../specs/556-close-stem-bucket-anonymous/spec.md) — исходная спека (контракт владельца)
- [`specs/561-impl-stem-bucket/plan.md`](../../specs/561-impl-stem-bucket/plan.md)
- [`specs/561-impl-stem-bucket/research.md`](../../specs/561-impl-stem-bucket/research.md) — решения D1…D8 и отклонённые альтернативы
- [`specs/561-impl-stem-bucket/contracts/stemjob-private-files-contract.md`](../../specs/561-impl-stem-bucket/contracts/stemjob-private-files-contract.md) — контракт server-to-server канала
- [`specs/561-impl-stem-bucket/data-model.md`](../../specs/561-impl-stem-bucket/data-model.md)
- [`specs/561-impl-stem-bucket/quickstart.md`](../../specs/561-impl-stem-bucket/quickstart.md) — операционные шаги на проде
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md) — словарь бакетов и решение о стартовом инварианте
- [`knowledge/domains/storage/components/storage-flow.md`](../../knowledge/domains/storage/components/storage-flow.md) — поток скачивания StemJob
- [`knowledge/domains/karaoke-web/components/internal-stem-job-controller.md`](../../knowledge/domains/karaoke-web/components/internal-stem-job-controller.md) — эндпоинты внутреннего канала
- [`knowledge/domains/karaoke-web/components/public-controllers-6.md`](../../knowledge/domains/karaoke-web/components/public-controllers-6.md) — скачивание результата задания
- [`knowledge/domains/monitoring/components/log-categories.md`](../../knowledge/domains/monitoring/components/log-categories.md) — категории `infra.stemjobs.security` / `infra.stemjobs.storage`

## Связанные ADR

- ADR нет: изменение локальное, в рамках существующих контуров хранения и server-to-server
  канала (ADR-0004 — `karaoke-app` живёт только на admin-машине; `local-0003` — MinIO как
  shared-хранилище).

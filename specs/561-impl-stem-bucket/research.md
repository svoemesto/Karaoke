# Research: 561 — Приватный бакет `stemjobs` и закрытый канал отдачи стемов (BR-258)

**Ветка**: `561-impl-stem-bucket` · **Дата**: 2026-10-24 · **Спека**: [../556-close-stem-bucket-anonymous/spec.md](../556-close-stem-bucket-anonymous/spec.md)
**Issue**: OpenProject #361 (аудит #336, BR-258, S1)

## Pre-flight (Constitution IX — Knowledge-first)

| Шаг | Что прочитано / найдено |
|---|---|
| 1. README | `knowledge/README.md`, `knowledge/domains/README.md` — домен `storage` (L2), `karaoke-web` (L2) |
| 2. Grep ×4 | `stemjobs\|minio` (320 совпадений), `presigned\|SigV4` (7), `internal-secret\|X-Internal-Secret` (18), `bucket public\|setBucketPublic` — по `knowledge/` |
| 3. Домен | `knowledge/domains/storage/domain.md` + `components/{karaoke-storage-service,storage-api-client,storage-flow}.md` |
| 4. ADR | `knowledge/adr/local-0001…local-0008.md` — релевантны `local-0003` (MinIO как shared-хранилище, atomic overwrite) и `local-0006` (логирование в `karaoke-web`) |
| 5. Код | `StemJobProcessing.kt`, `StemJobCleanup.kt`, `StemJobPollScheduler.kt`, `PublicStemJobController.kt`, `InternalStemJobController.kt`, `StorageApiClient*.kt`, `KaraokeStorageService.kt`, `StemJobTempCleanupScheduler.kt`, `deploy/prod-single-host/{80to8897,nginx.conf,docker-compose-web.yml,docker-compose-storage.yml}` |

## Ключевые факты, установленные по коду (не по памяти)

**Ф1. `karaoke-web` не умеет читать MinIO с учётными данными.**
`karaoke-web/.../services/WebKaraokeStorageServiceImpl.kt:35-123` — все 20 методов
интерфейса `KaraokeStorageService` бросают `UnsupportedOperationException`. Бин нужен только
для DI (14 контроллеров держат его конструкторной зависимостью, см. `knowledge/domains/karaoke-web/domain.md`
§ Инварианты, п. 3).

**Ф2. Единственный рабочий путь чтения из `karaoke-web` — неподписанный GET/HEAD через
nginx path-proxy.** `PublicStemJobController.kt:166` — `URL("$minioProxyUrl/minio/karaoke/$encodedPath")`.
Прод-nginx `deploy/prod-single-host/80to8897` (`location /minio/`) проксирует на `127.0.0.1:8890`
(MinIO) **без авторизации** — анонимный GET любого объекта публичного бакета отдаётся как есть.

**Ф3. Presigned-URL через этот прокси нереализуем (SigV4 ломается переписыванием пути).**
Прокси переписывает `/minio/<bucket>/<key>` → `/<bucket>/<key>`, а подпись SigV4 покрывает
canonical URI запроса — подписанный и фактически полученный MinIO пути не совпадают.
Это же объясняет, почему «просто сделать бакет приватным» нельзя без смены пути чтения:
публичные картинки/превью/mp3 лежат в том же бакете `karaoke` и раздаются анонимно
(`FR-002`).

**Ф4. Объекты премиум-задания сегодня лежат в ПУБЛИЧНОМ бакете `karaoke`.**
`StemJobProcessing.kt:193-196` — `val bucket = "karaoke"`, ключ `stemjobs/${job.id}/$stem.mp3`;
`StemJobCleanup.kt:28` — тот же бакет при уборке. Приватный бакет `stemjobs` в коде **не
используется нигде**, хотя он уже описан в словаре бакетов `knowledge/domains/storage/domain.md`
(«Премиум-фича „Создать минусовку": загруженные + результаты»).

**Ф5. `karaoke-app` имеет учётные данные к REMOTE-MinIO.** `StorageApiClientImpl`
(`StorageApiClient.kt:133+`) — прямой `MinioClient` к `storage.remote-endpoint`
(прод-хоста `188.127.240.124:9000`, L4-прокси nginx с allow-list админской машины).
`karaoke-app` в прод не разворачивается — это admin-only приложение (ADR-0004).

**Ф6. Направление server-to-server канала может быть только admin → prod.**
`karaoke-web` (прод) **не имеет прямого доступа к admin-машине `nsa-i9` (firewall)** —
документировано в `karaoke-web/.../controllers/PublicVkAuthController.kt:33` (и в
`PublicVkIdAuthController`), где из-за этого сделан ручной fallback с curl-командой.
Все работающие каналы admin→prod: `GET /api/internal/stemjobs/{id}/raw`,
`POST /api/internal/stemjobs/{id}/ack`, `POST /changerecords`.

**Ф7. Секретная пара уже настроена.** `stemjobs.internal-secret` (env `STEMJOBS_INTERNAL_SECRET`,
`karaoke-web/src/main/resources/application.yml`) ↔ `Karaoke.stemJobsInternalSecret`
(`karaoke-app/.../Karaoke.kt:365`, свойство `KaraokeProperties`). Роли: **karaoke-app —
клиент** (шлёт `X-Internal-Secret`), **karaoke-web — сервер** (проверяет, fail-safe:
пустой секрет → 403 всегда, `InternalStemJobController.kt:41-44`).

**Ф8. На проде MinIO и `karaoke-web` — в одной docker-сети `deploy_karaokenet`**
(`docker-compose-storage.yml` + `docker-compose-web.yml`), а учётные данные MinIO
(`STORAGE_KEY`/`STORAGE_SECRET`) передаются в контейнер `karaoke-web`. То есть технически
прямое чтение SDK из `karaoke-web` сегодня возможно — но оно **не соответствует утверждённой
спеке** (FR-004/FR-005: чтение приватных объектов идёт через server-to-server канал с
`X-Internal-Secret`), поэтому не рассматривается (см. D3, отклонённые альтернативы).

**Ф9. Уборка на web-side.** `StemJobTempCleanupScheduler` (web) удаляет из `stemjobs.temp-dir`
только **файлы** (`file.isFile`) старше 2 часов — это safety-net для сырых загрузок.
Результаты задания живут 24 часа (`StemJob.RETENTION_HOURS`), поэтому для них нужен отдельный
каталог и отдельный TTL.

## Решения

### D1. Приватные объекты переезжают в отдельный приватный бакет `stemjobs`

**Что меняется.** `StemJobProcessing.executeFinalizeStemJob` грузит исходник и стемы в
бакет `stemjobs` (ключ `<jobId>/original.<ext>`, `<jobId>/<stem>.mp3`) вместо бакета `karaoke`.
`StemJobCleanup.cleanupJob` удаляет оттуда же. Публичная политика бакета `karaoke` **не
меняется** — публичные картинки/превью/mp3 песен продолжают раздаваться анонимно.

**Почему так.** Решение владельца №1 (спека 556, 2026-10-24). Ключевой довод: политика
публичности в MinIO выражается на уровне **бакета** (bucket policy), а не префикса.
Действующая политика `karaoke` — `s3:GetObject` на `arn:aws:s3:::karaoke/*` (весь бакет),
поэтому «выборочно закрыть префикс» внутри него нельзя: bucket policy не умеет `Deny`
(только `Allow`), а `Allow` на публичную часть + запрет на остальное невыразимы.
Физическое разделение бакета — единственный способ сделать «приватно» выразимым и
проверяемым.

**Альтернативы и почему отклонены.**
- *«Deny» на префикс `stemjobs/` в общем бакете* — невыразимо в bucket policy MinIO
  (и отдельно отклонено владельцем как «вариант А»).
- *Отдельный префикс + отдельный публичный бакет для публичных файлов* — потребовал бы
  переезда всех публичных объектов `karaoke` (картинки/превью/mp3, ~18k песен) и переписывания
  всех `/minio/karaoke/...` URL на сайте — риск регрессии публичного сайта (FR-002) несоразмерен.
- *Оставить в `karaoke`, закрыть бакет целиком* — ломает публичные файлы (FR-002).

**Следствие для старых объектов.** Объекты, созданные до правки, лежат в `karaoke` по
ключу `stemjobs/<jobId>/...` и ещё до 24 часов читаются анонимно. Миграция не нужна
(решение владельца: «старые объекты истекут сами»), но уборка должна **добить** их —
иначе старые копии остались бы в публичном бакете навсегда (в бакете `karaoke` автоочистки
нет, см. инвариант 6 в `knowledge/domains/storage/domain.md`). Поэтому `StemJobCleanup`
удаляет объекты **и** из нового приватного бакета, **и** из legacy-места в публичном.

### D2. Инвариант закрытости обеспечивается программно при старте `karaoke-app`

**Что меняется.** Новый компонент `karaoke-app/.../StemJobStoragePolicyInitializer.kt`
на `ApplicationReadyEvent` приводит REMOTE-хранилище к безопасному состоянию:
- бакет `stemjobs` — создать (если нет) и выставить **private** (`setBucketPrivate` → пустая
  bucket policy);
- бакет `karaoke` — выставить **public** (`setBucketPublic` → `s3:GetObject` на весь бакет),
  т.е. публичная часть остаётся публичной.

**Почему так.** Решение владельца №2 + FR-006: закрытость не должна зависеть от ручных
действий администратора в каждом развёртывании и должна восстанавливаться сама.
Механизм `setBucketPrivate`/`setBucketPublic` уже есть в `StorageApiClient`
(`StorageApiClient.kt:445-498`), так что новый код — только оркестрация вызовов.

**Fail-safe.** Ошибка связи с MinIO **не роняет старт приложения**: исключение ловится,
пишется в журнал (`infra.stemjobs.storage`), приложение стартует. Иначе неверная
конфигурация хранилища блокировала бы запуск всего admin-приложения.

**Альтернативы и почему отклонены.**
- *Ручная настройка политики в MinIO-консоли* — ровно то, что FR-006 запрещает.
- *Проверка политики на каждом чтении* — не устраняет «съехавшую» политику и добавляет
  round-trip к каждой загрузке/скачиванию.
- *Политика «karaoke» целиком* — не меняется (FR-002): публичные файлы по контракту
  раздаются анонимно.

### D3. Канал отдачи приватного объекта — server-to-server, admin → prod, по образцу `InternalStemJobController`

**Что меняется.** Новый внутренний API на `karaoke-web` (сосед существующего
`InternalStemJobController`, тот же защитный контур):

| Метод | URL | Тело | Назначение |
|---|---|---|---|
| `PUT` | `/api/internal/stemjobs/{id}/files/{fileName}` | байты объекта (`application/octet-stream`) | karaoke-app передаёт приватный объект |
| `POST` | `/api/internal/stemjobs/{id}/purge` | — | karaoke-app просит удалить локальные копии |

`karaoke-app` (компонент с учётными данными) **читает объект из приватного бакета**
(`SAC_APP.downloadFile`) и передаёт его в `karaoke-web` по этому каналу; `karaoke-web`
складывает его в свой приватный каталог результатов и отдаёт владельцу задания из своего
`PublicStemJobController.download` — с проверкой владельца, статуса и TTL (FR-003).

**Почему так (выбор направления).** Вариантов было два:

1. **web → app** (web тянет объект у приложения по запросу пользователя) — архитектурно
   чище (MinIO остаётся единственным источником истины, копий нет), но **невозможно в
   текущей топологии**: Ф6 — прод-`karaoke-web` не достигает admin-машины (firewall).
   Канал был бы мёртвым на проде, то есть премиум-скачивание перестало бы работать.
2. **app → web** (приложение передаёт объект в web) — **работает**: это ровно то
   направление, в котором уже ходят `/raw`, `/ack` и `/changerecords`. Выбран этот вариант.

**Почему «по образцу `InternalStemJobController`».** Образец задаёт не направление, а
контур защиты: endpoint под `/api/internal/**` (вне `SiteAuthInterceptor`), заголовок
`X-Internal-Secret`, fail-safe (пустой секрет → 403 всегда), 403 без деталей, ответы
`404` там, где факт существования не должен утекать. Новые эндпоинты — прямые соседи
существующего контроллера и используют **тот же** секрет (D4).

**Почему не «presigned-URL» и не «прямое чтение MinIO из web».**
- presigned — Ф3: подпись не переживает переписывание пути nginx-прокси;
- прямое чтение SDK из `karaoke-web` технически возможно (Ф8), но противоречит
  утверждённой FR-004/FR-005: чтение приватных объектов идёт через server-to-server канал
  с общим секретом, а не в обход него. Это изменение контракта спеки — не наша правка.

**Следствие (и его честная цена).** Объект существует в двух местах: в приватном бакете
`stemjobs` (источник истины, TTL 24 ч, удаляется уборкой) и в приватном каталоге
`karaoke-web` (рабочая копия для отдачи владельцу, тоже 24 ч, удаляется уборкой и
`purge`). Второе место недоступно извне: каталог не публикуется nginx'ом, отдаётся только
через авторизованный `PublicStemJobController.download`. Каталог результатов по умолчанию —
`${stemjobs.temp-dir}/results`; для сохранения копий при перезапуске контейнера рекомендуется
указать `STEMJOBS_RESULT_DIR` на постоянном томе (см. quickstart).

### D4. Секрет — существующий `X-Internal-Secret`, новый не заводится

**Что меняется.** Ничего в настройке секретов: используется пара `stemjobs.internal-secret`
(env `STEMJOBS_INTERNAL_SECRET` на `karaoke-web`) ↔ `Karaoke.stemJobsInternalSecret`
(свойство в `Karaoke.properties` на `karaoke-app`). Роли не меняются относительно Ф7:
`karaoke-app` — клиент (шлёт заголовок), `karaoke-web` — сервер (проверяет).

**Почему так.** Решение владельца №3 + FR-005: новый секрет не заводится — у канала уже
есть настроенная пара, и отдельный секрет означал бы второй «неизвестно где настроенный»
параметр.

**Fail-safe (сохраняется).** Если секрет не настроен (пусто) — доступ закрыт **всегда**:
`authorized()` возвращает `false` до сравнения, поэтому «пустой секрет + пустой заголовок»
не открывает канал. Это общий принцип образца (см. research D2/D5 спеки 554).

### D5. Формат server-to-server канала: `PUT .../files/{fileName}` + `POST .../purge`

**Что меняется.** Тело — сырые байты объекта, имя файла — в пути, расширение — часть имени.

**Почему так.**
- **Сырые байты, а не multipart:** приложению достаточно `HttpURLConnection` (как в
  `ackStemJobRawFileConsumed`), без ручной сборки multipart-body; на стороне web —
  `@RequestBody ByteArray` (лимит запроса 200 МБ, `application.yml`).
- **Имя файла в пути, а не в теле:** web проверяет имя по **фиксированному allow-list**
  (`StemJob.ALLOWED_EXTENSIONS` +allow-list имён стемов) и **не ходит в БД** — значит, нет
  обращения к `tbl_stem_jobs` на server-to-server пути, а тест не требует БД. Плюс
  исключены path traversal (`..`, `/`, абсолютные пути) — имя файла не может выйти за
  пределы каталога задания.
- **`purge` отдельным эндпоинтом, а не флагом в `ack`:** `ack` означает «сырой файл забран
  и обработан — можно удалить» и вызывается в финализации, когда результаты ещё НЕ должны
  удаляться. Смешивать два смысла в одном эндпоинте — источник ошибок.

**Альтернативы и почему отклонены.**
- *multipart с полем `stem`* — требует ручной сборки multipart на стороне `karaoke-app`;
  выигрыш (одно поле вместо имени в пути) не окупает сложность клиента.
- *Передача готового presigned-URL* — Ф3.
- *Отдавать объект напрямую из MinIO в браузер владельца* — Ф3 + анонимный/неанонимный
  доступ к L4-порту MinIO закрыт allow-list'ом nginx (9000), а публичный прокси `/minio/`
  не авторизует.

### D6. Каталог результатов на стороне `karaoke-web` и его TTL

**Что меняется.** Новый параметр `stemjobs.result-dir` (env `STEMJOBS_RESULT_DIR`),
по умолчанию `${stemjobs.temp-dir}/results`. Раскладка: `<result-dir>/<jobId>/<stem>.<ext>`.
`StemJobTempCleanupScheduler` дополнительно удаляет каталоги заданий старше TTL (24 ч + запас).

**Почему так.** Сырые загрузки (`<temp-dir>/<jobId>.<ext>`) живут 2 часа (safety-net,
Ф9), а результаты должны быть доступны 24 часа (`StemJob.RETENTION_HOURS`) — в одном
каталоге с разными TTL нельзя. Отдельный каталог также отделяет «сырое» от «готового».

### D7. Уборка: приватный бакет + legacy-объекты в публичном бакете + локальные копии

**Что меняется.** `StemJobCleanup.cleanupJob`:
1. удаляет объекты из приватного бакета `stemjobs` (`<jobId>/...`);
2. удаляет **legacy-объекты** из публичного бакета `karaoke` (`stemjobs/<jobId>/...`) —
   до 24 часов после деплоя они остаются анонимно читаемыми, и уборка обязана их убрать;
3. вызывает `POST /api/internal/stemjobs/{id}/purge` — `karaoke-web` удаляет свои копии.

**Почему так.** FR-008 (TTL и периодическая уборка сохраняются) + D1 (legacy-объекты не
должны пережить свою уборку в публичном бакете).

### D8. Отказ в доступе виден в журнале

**Что меняется.** Новая категория `infra.stemjobs.security`:
- `stemjobs:security:denied` (WARN) — отказ во внутреннем канале: причина
  (`missing_secret` / `invalid_secret` / `secret_not_configured`), источник, идентификатор
  задания и имя файла. **Содержимое объекта не пишется** (FR-007).
- `stemjobs:security:download-denied` (WARN) — отказ в отдаче приватного объекта
  пользователю (чужое задание / истёкшее / файл отсутствует): источник, задание, стем.
  Содержимое не пишется.

**Почему так.** FR-007 + SC-004 (100 % отклонённых анонимных попыток видны в журнале
за 60 секунд) + конвенция категорий `infra.<subsystem>.<feature>`
(`knowledge/domains/monitoring/components/log-categories.md`).

## Проверенные и отклонённые альтернативы (сводно)

| # | Альтернатива | Почему отклонена |
|---|---|---|
| А1 | Закрыть бакет `karaoke` целиком | Ломает публичные файлы сайта (FR-002) |
| А2 | `Deny` на префикс в bucket policy | Bucket policy MinIO не выражает `Deny`; отклонено владельцем |
| А3 | Presigned-URL через `/minio/` | SigV4 не переживает переписывание пути (Ф3) |
| А4 | Чтение MinIO из `karaoke-web` напрямую (SDK, та же docker-сеть) | Противоречит FR-004/FR-005 (обход server-to-server канала) — это правка контракта спеки, не реализация |
| А5 | Направление web → app (web тянет объект у приложения) | Прод не достигает admin-машины (firewall, Ф6) — канал не заработает на проде |
| А6 | Новый отдельный секрет для канала | Решение владельца №3 + FR-005 |
| А7 | Хранение приватных объектов только на диске `karaoke-web` (без MinIO) | FR-013 требует бакет `stemjobs`; MinIO — источник истины и место TTL-уборки |
| А8 | Отдельный бакет на каждый вид объекта (`stemjobs-original`, `stemjobs-stems`) | Два бакета вместо одного — без выигрыша в безопасности (весь бакет приватен) |

## Открытые вопросы (за владельцем)

1. **Куда физически положить результаты на проде** — `STEMJOBS_RESULT_DIR` должен указывать
   на постоянный том контейнера `karaoke-web` (иначе копии переживут только до перезапуска
   контейнера). Значение по умолчанию — `${STEMJOBS_TEMP_DIR}/results` (внутри контейнера).
2. **Секрет `STEMJOBS_INTERNAL_SECRET`** должен быть одинаковым на обеих сторонах
   (`karaoke-app`: `Karaoke.properties → stemJobsInternalSecret`; `karaoke-web`: env).
   Значение в репозиторий не попадает.
3. **Порядок развёртывания**: сначала `karaoke-app` (создаёт приватный бакет и выставляет
   политику при старте), затем `karaoke-web` (появляются новые внутренние эндпоинты).
   Обратный порядок приведёт к тому, что `karaoke-app` начнёт передавать файлы в ещё не
   готовый канал — см. quickstart.

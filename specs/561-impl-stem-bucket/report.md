# Отчёт по #361 — Закрыть анонимный доступ к стемам и файлам премиум-заданий (BR-258, спека 561)

> **Спека**: [spec 556 (утверждена владельцем)](../556-close-stem-bucket-anonymous/spec.md) |
> **План**: [plan.md](plan.md) | **Ветка**: `561-impl-stem-bucket` |
> **Дата**: 2026-10-24 | **Аудит**: #336, порция P-06, BR-258 (severity **S1**).
> **Issue**: OpenProject #361 — статус `In progress` → (после merge) `In review`.

## Что было

Результаты премиум-задания «Создать минусовку» (стемы) и загруженный пользователем исходный
файл клались в **публичный** бакет `karaoke`:

```kotlin
// karaoke-app/.../StemJobProcessing.kt:193-196 (до правки)
val bucket = "karaoke"
SAC_APP.uploadFile(bucket, "stemjobs/${job.id}/original.${job.originalExt}", uploadFile.absolutePath)
stemFiles.forEach { (stem, file) -> SAC_APP.uploadFile(bucket, "stemjobs/${job.id}/$stem.mp3", file.absolutePath) }
```

Публичная bucket policy бакета `karaoke` — `s3:GetObject` на `arn:aws:s3:::karaoke/*`
(`StorageApiClient.kt:445-470`), а прод-nginx проксирует `/minio/<bucket>/<object>` **без
авторизации** (`deploy/prod-single-host/80to8897`, `location /minio/` → `127.0.0.1:8890`).

Итог: `stemjobs/<id>/<stem>.mp3` и `stemjobs/<id>/original.<ext>` читались **анонимным GET**
по угадываемому URL (`id` — последовательность `tbl_stem_jobs_id_seq`) — обход премиум-гейта и
утечка чужого пользовательского аудио в окне до 24 часов (TTL задания).

Дополнительно: словарь бакетов в `knowledge/domains/storage/domain.md` уже описывал отдельный
бакет `stemjobs` («премиум-фича: загруженные + результаты») — код разошёлся с замыслом.

## Что изменено

| Файл | Что |
|---|---|
| `karaoke-app/.../model/StemJob.kt` | Константы раскладки: `STORAGE_BUCKET = "stemjobs"` (приватный), `PUBLIC_STORAGE_BUCKET = "karaoke"`, `LEGACY_OBJECT_PREFIX = "stemjobs/"`, `ALL_STEM_NAMES`; хелперы `objectKey()`, `legacyObjectKey()`, `resultFileName()`, `validStemNames()`, `isValidResultFileName()` (allow-list имён файлов канала). |
| `karaoke-app/.../StemJobProcessing.kt` | Загрузка исходника и стемов в **приватный** бакет `stemjobs` (ключи `<jobId>/…`) вместо публичного `karaoke`; после загрузки — передача объектов в `karaoke-web` (шаг ниже); `DONE` выставляется **только** после подтверждённой передачи; при неудачной передаче — `ERROR` с понятным сообщением и удаление уже загруженных объектов. |
| `karaoke-app/.../StemJobResultPublisher.kt` (NEW) | Клиент server-to-server канала: **читает** объект приватного бакета (`SAC_APP.downloadFile` — единственный клиент с учётными данными) и передаёт его в `karaoke-web` как `PUT /api/internal/stemjobs/{id}/files/{fileName}` (тело — сырые байты, заголовок `X-Internal-Secret`). До 3 попыток на сетевую ошибку, **без** повтора на 403 (отказ по правам = расхождение секретов). Журнал `infra.stemjobs.transfer`. |
| `karaoke-app/.../StemJobStoragePolicyInitializer.kt` (NEW) | Инвариант закрытости **программно при старте `karaoke-app`** (решение владельца №2, FR-006): бакет `stemjobs` → private, бакет `karaoke` → public. Fail-safe: недоступность MinIO логируется (`infra.stemjobs.storage`) и **не** роняет старт приложения. |
| `karaoke-app/.../StemJobCleanup.kt` | Уборка удаляет объекты из приватного бакета, **legacy-объекты из публичного** `karaoke` (`stemjobs/<jobId>/…` — иначе они «пережили» бы уборку и остались бы анонимно читаемыми) и вызывает `POST /api/internal/stemjobs/{id}/purge` на `karaoke-web`. |
| `karaoke-web/.../services/StemJobResultFiles.kt` (NEW) | Единая точка раскладки приватных копий: `<result-dir>/<jobId>/<stem>.<ext>`, `purge(jobId)`. Каталог **не публикуется** nginx'ом. |
| `karaoke-web/.../services/StemJobLoader.kt` (NEW) | Узкое место загрузки задания (тест-шов: проверка права на скачивание проверяется без БД). |
| `karaoke-web/.../controllers/InternalStemJobController.kt` | Новые эндпоинты внутреннего канала: `PUT /api/internal/stemjobs/{id}/files/{fileName}` (приём объекта; 403 без валидного секрета — **fail-safe**, 400 при недопустимом имени файла) и `POST /api/internal/stemjobs/{id}/purge`. Существующие `/raw` и `/ack` — без изменений. Журнал `infra.stemjobs.security` — отказ без содержимого объекта. |
| `karaoke-web/.../controllers/PublicStemJobController.kt` | Скачивание отдаёт файл из **приватного каталога результатов** вместо анонимного GET `/minio/karaoke/<key>` (тот путь удалён). Проверки прежние: владелец задания, статус `DONE`, TTL (24 ч), допустимый стем; формат ответа прежний (`Content-Type`, `Content-Disposition`, 404/400). Отказ пишется в `infra.stemjobs.security` (`stemjobs:security:download-denied`). |
| `karaoke-web/.../services/StemJobTempCleanupScheduler.kt` | Safety-net: дополнительно удаляются каталоги результатов старше TTL (26 ч = 24 ч + запас); сырые загрузки — как раньше (2 ч). |
| `karaoke-web/src/main/resources/application.yml` | Новый параметр `stemjobs.result-dir: ${STEMJOBS_RESULT_DIR:}` (пусто → `${stemjobs.temp-dir}/results`). Секрет — прежний `stemjobs.internal-secret` (новых секретов нет). |
| `karaoke-app/src/test/.../StemJobStorageLayoutTest.kt` (NEW) | 8 тестов раскладки: приватный бакет ≠ публичный, ключи, legacy-ключ, allow-list имён, список стемов. |
| `karaoke-app/src/test/.../StemJobStoragePolicyInitializerTest.kt` (NEW) | 2 теста: `stemjobs` → private и `karaoke` → public; ошибка хранилища не роняет старт. |
| `karaoke-web/src/test/.../InternalStemJobFilesTest.kt` (NEW) | 8 тестов канала: без секрета → 403 и файл не сохранён; неверный секрет → 403; пустой секрет + пустой заголовок → 403 (fail-safe); корректный секрет → файл в приватном каталоге; недопустимое имя → 400; `purge` удаляет копии; `purge` без секрета → 403; отказ в журнале без содержимого. |
| `karaoke-web/src/test/.../PublicStemJobDownloadTest.kt` (NEW) | 6 тестов: владелец получает файл и прежние заголовки; чужое задание → 404 без содержимого; истёкшее → 404; неизвестный стем → 400; отсутствующий файл → 404; отказ в журнале без содержимого объекта. |
| `karaoke-web/src/test/.../PublicStemJobNoAnonymousProxyTest.kt` (NEW) | Guard «до/после»: контроллер выдачи результата **не держит** адрес анонимного прокси MinIO. Тест компилируется и на коде до правки — падает там (см. «Проверки»). |
| `docs/features/stemjob-private-bucket.md` (NEW) | Per-feature документ (FR-009). |
| `knowledge/domains/storage/domain.md` | Словарь бакетов (`stemjobs` — приватный), «Решение 5» (перенос + стартовый инвариант), закрыт gap «Bucket policies». |
| `knowledge/domains/storage/components/storage-flow.md` | «Поток 4: StemJob» переписан (загрузка в приватный бакет → передача по каналу → отдача владельцу); presigned-вариант помечен **нереализуемым** (SigV4). |
| `knowledge/domains/storage/components/karaoke-storage-service.md`, `storage-api-client.md` | Кто применяет политику бакетов; `StorageApiClientWeb` больше не используется для выдачи стемов. |
| `knowledge/domains/karaoke-web/components/internal-stem-job-controller.md` | Контракт новых эндпоинтов канала + правила приёма. |
| `knowledge/domains/karaoke-web/components/public-controllers-6.md` | Откуда теперь берётся файл при скачивании. |
| `knowledge/domains/monitoring/components/log-categories.md` | Новые категории `infra.stemjobs.security`, `infra.stemjobs.storage`, `infra.stemjobs.transfer`. |
| `specs/561-impl-stem-bucket/*` | research (решения D1…D8 и отклонённые альтернативы), plan, контракт канала, data-model, quickstart (операционные шаги), tasks (28 задач — выполнены). |

### Ключевое решение: направление канала — admin → prod

`karaoke-web` не умеет читать MinIO с учётными данными (`WebKaraokeStorageServiceImpl` —
заглушка, все методы бросают `UnsupportedOperationException`), presigned-URL через
path-proxy ломается переписыванием пути (SigV4). Вариант «web тянет объект у приложения по
запросу пользователя» **неработоспособен на проде**: `karaoke-web` (прод) не имеет прямого
доступа к admin-машине `nsa-i9` (firewall — зафиксировано в коде
`PublicVkAuthController.kt:33`, там же сделан ручной fallback). Поэтому канал идёт в
работающую сторону (admin → prod), тем же протоколом и тем же секретом, что уже работающие
`/raw`, `/ack` и `/changerecords`. Полный разбор альтернатив — `research.md` (D3, Д3/Ф6).

## Проверки

| Команда | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:compileKotlin :karaoke-app:compileKotlin --parallel` | **PASS** |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test` | **PASS** — 0 failures / 0 errors; новые тесты: `InternalStemJobFilesTest` 8, `PublicStemJobDownloadTest` 6, `PublicStemJobNoAnonymousProxyTest` 1, `StemJobStorageLayoutTest` 8, `StemJobStoragePolicyInitializerTest` 2 |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` | **PASS** |
| `bash tools/check-subagent-isolation.sh` | **PASS** (ветка `561-impl-stem-bucket`, worktree `.worktrees/impl361`) |
| `bash tools/check-knowledge-structure.sh` | **PASS** (9/9) |
| `python3 tools/lint-knowledge.py` | **PASS** — «All documentation passes architectural style and structural checks» |
| `python3 tools/check-doc-references.py` | **PASS** — битых ссылок/путей/эндпоинтов нет |
| `bash tools/check-feature-doc.sh docs/features/*.md` | **PASS** — все документы валидны, включая `stemjob-private-bucket.md` |
| guard'ы pre-commit (`check-gradle-user-home`, `check-docker-config`, `check-container-restart`, `check-frontend-build`, `check-docker-image-tags`, `check-no-jpa-imports`, `check-no-mp4-mentions`) | **PASS** (R-11: 130 упоминаний — все в baseline) |
| grep значения секрета в диффе | **пусто** — в коде только имена параметров (`stemjobs.internal-secret`, `stemJobsInternalSecret`, `STEMJOBS_RESULT_DIR`) |
| `cd webvue3 && npm run lint` | **не требуется** — фронтенд не затронут |
| `nginx -t` | **не требуется** — nginx-конфиги не менялись |

### Тест «до/после» (как проверено)

На коде **до** правки (production-файлы откачены, оставлен только guard-тест) — **FAIL**:

```
> Task :karaoke-web:test FAILED
PublicStemJobNoAnonymousProxyTest > контроллер выдачи результата не обращается к MinIO через анонимный прокси() FAILED
    org.opentest4j.AssertionFailedError at PublicStemJobNoAnonymousProxyTest.kt:23
BUILD FAILED
```

После правки — **PASS** (все 25 новых тестов зелёные). Остальные новые тесты используют новые
API (`StemJob.STORAGE_BUCKET`, `PUT /files/...`), поэтому на коде до правки они не
компилируются — это и есть их «красное» состояние.

## Поведение после правки

1. Анонимный `GET https://sm-karaoke.ru/minio/stemjobs/<id>/vocals.mp3` → **403 от MinIO**
   (бакет приватный, bucket policy пустая).
2. В публичном бакете `karaoke` приватных объектов больше нет; legacy-объекты
   (`karaoke/stemjobs/<id>/…`) удаляются уборкой в течение 24 часов после развёртывания.
3. Публичные файлы (`/minio/karaoke/<file>`) — без изменений (FR-002).
4. Владелец задания скачивает свой стем через прежний URL — файл отдаётся, заголовки прежние.
5. Чужое/истёкшее задание — 404, факт существования не раскрывается.
6. Отказ виден в журнале: `infra.stemjobs.security` (источник, задание, имя объекта — без
   содержимого).

## Что осталось за владельцем (операционные шаги на проде)

1. **Секрет** — одно и то же значение в двух местах (в репозиторий не попадает!):
   - `karaoke-app`: свойство `stemJobsInternalSecret` в `Karaoke.properties`;
   - `karaoke-web`: env `STEMJOBS_INTERNAL_SECRET` (`deploy/prod-single-host/do.env`).
   Пустое значение = канал закрыт (fail-safe) → задания будут уходить в `ERROR` с
   сообщением «Ошибка передачи результатов на сайт».
2. **Каталог результатов** — рекомендуется задать `STEMJOBS_RESULT_DIR` на постоянный том
   `karaoke-web` (по умолчанию `${STEMJOBS_TEMP_DIR}/results` — внутри контейнера, не
   переживает перезапуск). Переменная прокидывается в
   `deploy/prod-single-host/docker-compose-web.yml` рядом с `STEMJOBS_INTERNAL_SECRET`.
3. **Порядок развёртывания**: сначала `karaoke-app` (создаёт приватный бакет и выставляет
   политики; секрет должен быть задан ДО старта), затем `karaoke-web` (появляются эндпоинты
   канала). Обратный порядок → задания уйдут в `ERROR`.
4. **Перезапуск `karaoke-app`** — по согласию владельца и по правилу AGENTS.md Pass 380
   (сначала остановить очередь `KaraokeProcess` и дождаться её остановки).
5. **Проверка после развёртывания** — таблица в
   [`quickstart.md`](quickstart.md) («Что проверить после развёртывания на боевом контуре»):
   анонимный GET к `/minio/stemjobs/...` → 403; публичный файл → 200; свой стем → 200;
   чужое задание → 404; в журнале `karaoke-app` — строка `infra.stemjobs.storage` о политике.
6. **Наблюдение 7 суток** (SC-001…SC-004).

## Что НЕ сделано (вне объёма)

- Прод-деплой, рестарт контейнеров, работа с прод-БД и MinIO — только владельцем.
- Push, PR, merge — только лид.
- Транзакционность применения SQL (BR-012) — не в этой задаче.
- «Политика всего бакета `karaoke` анонимно читаем» — отдельный вопрос (публичные файлы по
  контракту раздаются анонимно).
- Остальные дефекты аудита (BR-011 — в #357; BR-018, BR-019, BR-123, BR-518 — отдельные
  задачи волны A).

## Ветка и коммиты

- Ветка: `561-impl-stem-bucket` (от `master` = `cf97b82f`), worktree
  `/home/nsa/Karaoke/.worktrees/impl361`. Главная копия `/home/nsa/Karaoke` не тронута.
- Коммиты:
  - `be0f2051` — `spec(561)`: приватный бакет stemjobs и закрытый канал отдачи стемов —
    research/plan/контракт/data-model/quickstart (#361, BR-258);
  - `523e26e0` — `fix(561)`: закрыть анонимный доступ к стемам и исходникам премиум-задания
    (#361, BR-258) `[tracker-claim-361]` — код, тесты, `docs/features/stemjob-private-bucket.md`,
    `knowledge/`, `tasks.md` (28 задач выполнены);
  - `docs(561)`: отчёт для трекера #361 — этот файл `[tracker-claim-361]`.
- OpenProject #361: `claim` выполнен (статус `In progress`, assignee `ai-agent`).
  `add-comment` (этот отчёт) и `mark-review` — после merge.

# Tasks: 561 — Приватный бакет `stemjobs` и закрытый канал отдачи стемов (BR-258)

**Input**: Design documents from `/specs/561-impl-stem-bucket/`
**Prerequisites**: plan.md, research.md, data-model.md, contracts/stemjob-private-files-contract.md, quickstart.md
**Tests**: включены — спека FR-010 требует регрессионный тест «до/после».
**Organization**: задачи сгруппированы по пользовательским сценариям; каждый сценарий —
независимо проверяемый кусок, который можно выполнить и проверить отдельно.

## Format: `[ID] [P?] [Story] Description`

- **[P]** — задача может выполняться параллельно с другими [P]-задачами в том же Phase
- **[US1]/[US2]/[US3]** — задача относится к пользовательскому сценарию спеки
- Все файлы — относительно корня репозитория

## Path Conventions

- Модуль приложения: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Модуль веба: `karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/`
- Тесты: `karaoke-app/src/test/kotlin/...`, `karaoke-web/src/test/kotlin/...`
- Per-feature документ (FR-009): `docs/features/stemjob-private-bucket.md`
- Knowledge: `knowledge/domains/storage/`, `knowledge/domains/karaoke-web/`,
  `knowledge/domains/monitoring/components/log-categories.md`
- Спека трекера: `specs/561-impl-stem-bucket/report.md`

---

## Phase 1: Setup

**Purpose**: зафиксировать раскладку приватных объектов и подготовить конфигурацию без
изменения поведения.

- [x] **T001** [US1] Вынести константы раскладки в `karaoke-app/.../model/StemJob.kt`:
      `STORAGE_BUCKET = "stemjobs"` (приватный), `PUBLIC_STORAGE_BUCKET = "karaoke"`
      (публичный), `LEGACY_PUBLIC_BUCKET = "karaoke"`; хелперы `objectKey(jobId, stem, ext)`,
      `legacyObjectKey(jobId, stem, ext)`, `resultFileName(stem, originalExt)`,
      `validStemNames(mode)` (allow-list имён стемов). KDoc со ссылкой на
      `specs/561-impl-stem-bucket/data-model.md`.
- [x] **T002** [US1] Добавить в `karaoke-web/src/main/resources/application.yml` параметр
      `stemjobs.result-dir` (env `STEMJOBS_RESULT_DIR`, default `${stemjobs.temp-dir}/results`)
      с комментарием: приватный каталог результатов, не публикуется nginx'ом.
- [x] **T003** [US1] Регрессионный тест «до/после» на раскладку:
      `karaoke-app/src/test/kotlin/.../StemJobStorageLayoutTest.kt` — приватный бакет
      `stemjobs` (не `karaoke`), ключ `<jobId>/<stem>.<ext>`, legacy-ключ известен уборке,
      allow-list стемов корректен для DEMUCS2/DEMUCS5. **До правки тест падает.**
- [x] **T004** [US1] Проверка: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew
      :karaoke-app:test --tests '*StemJobStorageLayout*'` — зелёный; на коде без T001 — красный.

**Checkpoint**: раскладка зафиксирована в коде и тестом; поведение не менялось; публичный
API не тронут.

---

## Phase 2: Foundational (закрытый канал)

**Purpose**: серверная проверка права на уровне приложения — канал приёма приватных
объектов и fail-safe секрета. От этого зависят все сценарии.

- [x] **T005** [US1] `karaoke-web/.../services/StemJobResultFiles.kt` (новый): единая точка
      раскладки путей (`root`, `jobDir(jobId)`, `fileFor(jobId, stem, originalExt)`),
      allow-list имён файлов (`<stem>.<ext>` без `/`/`..`), `purge(jobId)`.
- [x] **T006** [US1] `karaoke-web/.../controllers/InternalStemJobController.kt`: добавить
      `PUT /api/internal/stemjobs/{id}/files/{fileName}` (сырые байты) и
      `POST /api/internal/stemjobs/{id}/purge` — с тем же fail-safe секретом
      (`authorized()`), что и существующие `/raw` и `/ack`; ответы 400/403/200 по
      контракту `contracts/stemjob-private-files-contract.md`.
- [x] **T007** [US1] Логирование отказов в новой категории `infra.stemjobs.security`:
      `stemjobs:security:denied` (WARN: причина, источник, jobId, имя файла — **без
      содержимого**), `stemjobs:security:rejected` (400), `stemjobs:security:stored` (INFO),
      `stemjobs:security:purged` (INFO).
- [x] **T008** [US1] Тесты канала: `karaoke-web/src/test/kotlin/.../InternalStemJobFilesTest.kt`
      — без секрета → 403 и файл не сохранён; неверный секрет → 403; пустой секрет + пустой
      заголовок → 403 (fail-safe, ловушка образца); корректный секрет → файл сохранён в
      приватный каталог; недопустимое имя (`../x`, `evil.exe`, `sub/dir.mp3`) → 400;
      `purge` удаляет каталог задания.
- [x] **T009** [US1] Проверка: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew
      :karaoke-web:test --tests '*InternalStemJobFiles*'` — зелёный.

**Checkpoint**: канал принимает объект только при корректном секрете; пустой секрет не
открывает канал; в журнале видно отказ без содержимого объекта.

---

## Phase 3: User Story 1 — Аноним не скачает чужой стем и исходный файл (P1)

**Story goal**: приватные объекты лежат в отдельном бакете без публичной политики; инвариант
закрытости поддерживается программно; анонимный GET по адресу хранилища содержимого не
отдаёт.

**Independent test**: анонимный запрос к `/minio/stemjobs/...` и к L4-порту хранилища → отказ;
политика бакета после старта `karaoke-app` — private (quickstart, проверки 1-2).

- [x] **T010** [US1] `karaoke-app/.../StemJobProcessing.kt`: загрузка исходника и стемов в
      приватный бакет `stemjobs` (ключи `<jobId>/...`) вместо публичного `karaoke`.
- [x] **T011** [US1] `karaoke-app/.../StemJobResultPublisher.kt` (новый): прочитать объект
      приватного бакета через клиент с учётными данными (`SAC_APP.downloadFile`) и передать
      в `karaoke-web` по `PUT /api/internal/stemjobs/{id}/files/{fileName}` с заголовком
      `X-Internal-Secret`; повтор до 3 попыток на сетевую ошибку, без повтора на 403.
- [x] **T012** [US1] `karaoke-app/.../StemJobProcessing.kt`: порядок финализации —
      загрузка в приватный бакет → передача в `karaoke-web` → `DONE` + `expiresAt`;
      при неудачной передаче — `ERROR` с понятным сообщением **и** удаление уже загруженных
      объектов (в т.ч. legacy-объектов), чтобы не осталось «обещанного, но недоступного».
- [x] **T013** [US1] `karaoke-app/.../StemJobStoragePolicyInitializer.kt` (новый): при старте
      (`ApplicationReadyEvent`) — бакет `stemjobs` создать (если нет) и выставить private,
      бакет `karaoke` — public. Fail-safe: ошибка связи с хранилищем логируется
      (`infra.stemjobs.storage`) и **не** роняет старт приложения.
- [x] **T014** [US1] Тесты инварианта: `karaoke-app/src/test/kotlin/.../StemJobStoragePolicyInitializerTest.kt`
      — `stemjobs` → private, `karaoke` → public, ошибка хранилища не пробрасывается.
- [x] **T015** [US1] Проверка: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew
      :karaoke-app:test --tests '*StemJobStorage*'` — зелёный.

**Checkpoint**: приватные объекты не лежат в публичном бакете; политика закрытости
поддерживается кодом; анонимный доступ к ним закрыт двумя независимыми рубежами
(политика бакета + отсутствие объектов в публичном бакете).

---

## Phase 4: User Story 2 — Владелец задания по-прежнему скачивает свои файлы (P2)

**Story goal**: легитимный сценарий владельца работает как раньше — тот же URL, тот же
`Content-Type`/`Content-Disposition`, те же коды отказа.

**Independent test**: тест на стороне приложения — запрос с корректной авторизацией
возвращает нужные байты; чужое/истёкшее задание → 404 (quickstart, проверка 3).

- [x] **T016** [US2] `karaoke-web/.../controllers/PublicStemJobController.kt`: скачивание
      отдаёт файл из приватного каталога результатов (`StemJobResultFiles`) вместо анонимного
      GET `/minio/karaoke/<key>`; проверки владельца/статуса/TTL/stem — прежние; формат
      ответа прежний (FR-011).
- [x] **T017** [US2] `karaoke-app/.../StemJobCleanup.kt`: уборка удаляет объекты из
      приватного бакета **и** legacy-объекты из публичного `karaoke`
      (`stemjobs/<jobId>/...`), затем вызывает `POST /api/internal/stemjobs/{id}/purge`
      на `karaoke-web` (best-effort).
- [x] **T018** [US2] `karaoke-web/.../services/StemJobTempCleanupScheduler.kt`: уборка
      каталогов результатов старше TTL задания (24 ч + запас) — safety-net на случай
      недошедшего `purge`.
- [x] **T019** [US2] Тесты скачивания: `karaoke-web/src/test/kotlin/.../PublicStemJobDownloadTest.kt`
      — владелец получает байты файла и прежние заголовки; чужое задание → 404;
      истёкшее → 404; неизвестный стем → 400; файл отсутствует → 404; **в ответе нет
      обращения к публичному прокси MinIO** (регрессия BR-258).
- [x] **T020** [US2] Проверка: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew
      :karaoke-web:test --tests '*PublicStemJobDownload*'` — зелёный.

**Checkpoint**: премиум-фича не сломана; публичный API в прежнем формате; TTL и уборка
сохранены (FR-008).

---

## Phase 5: User Story 3 — Политика доступа не разъезжается и видна в журнале (P3)

**Story goal**: администратор видит применение политики и отказы; отказ не выдаёт
содержимого объекта и не раскрывает факт существования.

**Independent test**: в тестах — отказ пишется в `infra.stemjobs.security` с источником и
именем объекта; в записи нет байтов объекта.

- [x] **T021** [US3] `karaoke-web/.../controllers/PublicStemJobController.kt`: логировать
      отказ в отдаче приватного объекта (`stemjobs:security:download-denied`, WARN) —
      источник, jobId, stem; без содержимого.
- [x] **T022** [US3] Тест журнала: запись об отказе содержит источник и объект
      (`infra.stemjobs.security`), но **не** содержит байтов/содержимого файла.
- [x] **T023** [US3] Проверка: `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew
      :karaoke-web:test :karaoke-app:test` — зелёный.

**Checkpoint**: отказ наблюдаем, деталей и содержимого не выдаёт.

---

## Phase 6: Polish & Cross-Cutting

**Purpose**: документация, знание, финальные проверки.

- [x] **T024** Обновить `docs/features/stemjob-private-bucket.md` (FR-009) — описание фичи,
      раскладка бакетов, контракт канала, операционные шаги, что за владельцем.
- [x] **T025** Обновить `knowledge/`:
      - `knowledge/domains/storage/domain.md` — словарь бакетов (`stemjobs` — приватный,
        `karaoke` — публичный; ключи `<jobId>/...`), решение о стартовом инварианте;
      - `knowledge/domains/storage/components/storage-flow.md` — поток скачивания StemJob
        (прежний «presigned → nginx-proxy» **нереализуем**, см. research Ф3), новая схема;
      - `knowledge/domains/storage/components/karaoke-storage-service.md` /
        `storage-api-client.md` — где применяется политика бакетов;
      - `knowledge/domains/karaoke-web/components/internal-stem-job-controller.md` — новые
        эндпоинты канала;
      - `knowledge/domains/karaoke-web/components/public-controllers-6.md` — отдача
        приватного файла владельцу;
      - `knowledge/domains/monitoring/components/log-categories.md` — категория
        `infra.stemjobs.security` (+ `infra.stemjobs.storage`).
- [x] **T026** Проверки проекта (все — PASS):
      `compileKotlin` (оба модуля), `test` (оба модуля), `ktlintCheck`,
      `tools/check-subagent-isolation.sh`, `tools/check-knowledge-structure.sh`,
      `tools/check-doc-references.py`, `tools/check-feature-doc.sh docs/features/*.md`,
      grep значения секрета в диффе — пусто, pre-commit хуки — проходят.
- [x] **T027** `specs/561-impl-stem-bucket/report.md` — отчёт для трекера: что было,
      что изменено, проверки, что осталось за владельцем.
- [x] **T028** Коммит в ветку `561-impl-stem-bucket` (без push и PR — это делает лид).

**Checkpoint**: документация соответствует коду, проверки зелёные, отчёт готов.

---

## Dependencies

```
T001 ──► T010 ──► T011 ──► T012
  │                 │
  └──► T003/T004    └──► T014/T015

T002 ──► T005 ──► T006 ──► T007 ──► T008 ──► T009
                   │                    │
                   └──► T016 ──► T019 ──► T020
                        ▲
T013 (инвариант) ───────┘
T017 (уборка) ──► T018
T021/T022 ──► T023
T024/T025 ──► T026 ──► T027 ──► T028
```

**Критично**: Phase 2 (канал) и Phase 3 (перенос в приватный бакет) нельзя разворачивать
по отдельности: без канала `karaoke-web` не сможет отдать файл владельцу, а без переноса
закрытость не наступит. Phase 4 (скачивание) обязателен вместе с ними.

## Parallel Execution Examples

- T003 (тест раскладки) и T002 (конфиг web) независимы.
- T005 (paths) и T013 (инвариант) независимы.
- T024 (документ фичи) и T025 (knowledge) независимы.

## Implementation Strategy

1. **Phase 1** — фиксируем раскладку (тест «до/после» уже красный на коде без правки).
2. **Phase 2** — закрываем канал (fail-safe секрет, тесты).
3. **Phase 3** — переносим объекты в приватный бакет и включаем стартовый инвариант.
4. **Phase 4** — переключаем скачивание на приватный каталог, чиним уборку (в т.ч. legacy).
5. **Phase 5** — журналирование отказов.
6. **Phase 6** — документация, knowledge, проверки, отчёт, коммит.

**Обратная совместимость**: публичный API сайта и публичные файлы не меняются; изменяется
только источник байтов приватных объектов и их раскладка в хранилище.

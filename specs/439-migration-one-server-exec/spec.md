# Feature Specification: Исполнение переезда sm-karaoke.ru на один сервер

**Feature Branch**: `439-migration-one-server-exec`

**Created**: 2026-09-23

**Status**: Draft

**Input**: Wayfinder-карта OpenProject #165 («Переезд sm-karaoke.ru: прод +
MinIO на один сервер»). Все 12 решений карты приняты; артефакты —
`specs/165-migration-one-server-research/` (PR #531, merged). Эта спека —
**исполнение** runbook `specs/165-migration-one-server-research/cutover-runbook.md`.

## OpenProject Tracking *(MANDATORY — see AGENTS.md § Issue-tracker OpenProject)*

- **Issue ID**: `#178` («Исполнение переезда sm-karaoke.ru: прод + MinIO на один сервер»).
- **Title**: «Исполнение переезда sm-karaoke.ru: прод + MinIO на один сервер».
- **Created in OpenProject**: 2026-09-23.
- **Workflow**:
  1. **Claim**: `bash tools/tracker.sh claim-issue 178` — выполнено 2026-09-23
     (assignee + status `In progress` через PATCH, т.к. CLI `claim-issue`
     падает на смене assignee для Task — прецедент #152).
  2. **Add comment с отчётом** (после merge):
     `bash tools/tracker.sh add-comment 178 --file specs/439-migration-one-server-exec/report.md`.
  3. **Mark review** (после add-comment): `bash tools/tracker.sh mark-review 178`.
  4. **Close** (owner, после ревью): `bash tools/tracker.sh close-issue 178`.

## Knowledge References *(MANDATORY — see Constitution Principle IX)*

### Pre-flight log

- **Дата pre-flight**: 2026-09-23
- **Grep-запросы**:
  1. `grep -rln "деплой|deploy|сервер" knowledge/system/` →
     `deploy-overview.md`, `do-sh.md`, `ci-tools.md`, `dev-pc-exception.md`.
  2. `grep -rln "MinIO|storage|two-db" knowledge/domains/` →
     `storage/domain.md`, `storage/components/*`, `processing/components/two-db-sync.md`,
     `caching/components/*`, `health/components/*`.
  3. `grep -rln "nginx|reverse.proxy|TLS|сертификат" knowledge/` →
     `system/infra/deploy-overview.md`, `storage/domain.md`,
     `karaoke-web/domain.md`, `sse/domain.md`.
  4. `grep -rln "sm-karaoke|188.119.64.111|89.125.103.63|minio-proxy" knowledge/` →
     `storage/domain.md`, `storage/components/storage-flow.md`,
     `system/02-containers.md`, `system/infra/deploy-overview.md`.
  5. `grep -rln "DB_REMOTE|sync" knowledge/domains/processing/` →
     `two-db-sync.md`, `run-entity-sync.md`, `domain.md`.

### Knowledge files consulted

- [`knowledge/system/infra/deploy-overview.md`](../../knowledge/system/infra/deploy-overview.md)
  — текущее описание двухмашинного деплоя, `do.sh`, nginx; **подлежит обновлению по итогам**.
- [`knowledge/system/02-containers.md`](../../knowledge/system/02-containers.md)
  — C4 L2: контейнеры, admin + прод-сервер; **подлежит обновлению (объединение хостов)**.
- [`knowledge/domains/storage/domain.md`](../../knowledge/domains/storage/domain.md)
  — Local/Remote MinIO, `StorageApiClient`, nginx-`minio-proxy`, инварианты.
- [`knowledge/domains/processing/components/two-db-sync.md`](../../knowledge/domains/processing/components/two-db-sync.md)
  — two-DB sync (`DB_REMOTE_HOST`, `SyncRemoteClient`, `/changerecords`, recordhash).
- [`knowledge/domains/storage/components/storage-flow.md`](../../knowledge/domains/storage/components/storage-flow.md)
  — потоки local/remote storage, circuit breaker.
- [`knowledge/adr/0004-karaoke-app-admin-only.md`](../../knowledge/adr/0004-karaoke-app-admin-only.md)
  — `karaoke-app` только на admin-машине (не переезжает).
- [`knowledge/adr/local-0001-karaoke-properties-defaults.md`](../../knowledge/adr/local-0001-karaoke-properties-defaults.md)
  — конфигурация через `KaraokeProperties`.

### Результат pre-flight

Релевантные документы найдены; решения карты #165 им не противоречат.
Knowledge **рассинхронизируется** после переезда (описывает два хоста) —
обновление входит в объём этой спеки (FR-012).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Подготовка нового хоста (Priority: P1)

Как владелец, я хочу, чтобы новый хост `188.127.240.124` был полностью
подготовлен (Docker, nginx, каталоги, swap, секреты, deploy-файлы), и сайт
поднялся на нём **до** переключения DNS, чтобы cutover занял минуты, а не часы.

**Why this priority**: без подготовленного хоста окно простоя растягивается;
данные (446 ГБ MinIO) нужно перелить заранее.

**Independent Test**: на новом хосте `do.sh ps` показывает 4 контейнера `Up`;
`tools/migration-smoke.sh 188.127.240.124` → все PASS; `mc du` видит бакет.

**Acceptance Scenarios**:

1. **Given** пустой хост `188.127.240.124`, **When** выполнены Фазы 0-2 runbook,
   **Then** Docker/Compose/nginx/mod-stream установлены, `docker-compose`
   доступен как команда (враппер), `/etc/keys` и `/root/Karaoke/deploy` на месте.
2. **Given** подготовленный хост и запущенный MinIO, **When** выполнен bulk
   `mc mirror`, **Then** в бакете `karaoke` 59 707 объектов / ≈444.45 GiB;
   `.minio.sys` не скопирован.
3. **Given** подготовленный хост, **When** restore дампа БД, **Then**
   `count(tbl_songs)=26549`, `count(tbl_site_users)=86`, recordhash-триггеры на месте.
4. **Given** поднятый стек, **When** `migration-smoke.sh 188.127.240.124`,
   **Then** 15/15 PASS **до** изменения DNS.

---

### User Story 2 — Cutover в ночном окне (Priority: P1)

Как владелец, я хочу переключить прод на новый хост в окне ≤20 мин с
гарантией консистентности данных и понятным rollback.

**Why this priority**: это и есть «переезд»; без него US1 — просто стенд.

**Independent Test**: после окна `https://sm-karaoke.ru` отдаёт новый IP,
`migration-smoke.sh sm-karaoke.ru` → 15/15, данные совпадают.

**Acceptance Scenarios**:

1. **Given** подготовленный хост, **When** заморожена запись и выполнен
   финальный `mc mirror --overwrite --remove` + `mc diff`, **Then** diff пуст.
2. **Given** замороженная запись, **When** финальный дамп + restore, **Then**
   данные на новом хосте не старше момента заморозки.
3. **Given** переключённые A-записи, **When** истёк TTL, **Then** `access.log`
   старого хоста не получает новых запросов (≈2 ч наблюдения).

---

### User Story 3 — Перенастройка admin и sync (Priority: P2)

Как владелец, я хочу, чтобы admin-машина (`karaoke-app`) после переезда
синхронизировалась с прод-БД на новом хосте, а мониторинг прод-контейнеров
работал.

**Why this priority**: без этого останавливается публикация/рендер и sync
LOCAL↔SERVER.

**Independent Test**: ручная «Синхронизация в 1 клик» из admin проходит;
в логах `AutoOneClickSyncScheduler` нет ошибок; `ProdContainerCheck` зелёный.

**Acceptance Scenarios**:

1. **Given** `docker-compose-app.yml` без `DB_REMOTE_HOST`, **When** добавлен
   проброс и `.env` обновлён, **Then** `karaoke-app` коннектится к
   `188.127.240.124:5433`.
2. **Given** новый хост, **When** из admin вызвана синхронизация, **Then**
   `POST https://sm-karaoke.ru/changerecords` → 2xx, счётчики строк совпадают.

---

### User Story 4 — Документация и вычистка (Priority: P3)

Как сопровождающий, я хочу, чтобы `knowledge/` и репо-деплой описывали
**однохостовую** топологию, а `new_comp` был помечен deprecated.

**Independent Test**: линтеры knowledge зелёные; `deploy/prod-single-host/`
содержит канон; `new_comp` помечен deprecated.

**Acceptance Scenarios**:

1. **Given** переезд завершён, **When** обновлены `02-containers.md`,
   `deploy-overview.md`, `storage/domain.md`, `architecture-notes.md`,
   **Then** они описывают один хост + локальный MinIO.
2. **Given** новый канон в репо, **When** прочитан `deploy/prod-single-host/README.md`,
   **Then** он воспроизводит раскладку нового хоста.

---

### Edge Cases

- **Bulk MinIO обрывается** на середине: `mc mirror` идемпотентен, повтор
  продолжает с места; финальный `mc diff` — критерий полноты.
- **Диск источника переполнен** (был 94%): только чтение, никакой записи на старый storage.
- **Сертификат leaf вместо fullchain**: nginx не строит цепочку → брать
  fullchain (2 серта) с прода, не с Яндекс.Диска (#169).
- **OOM на новом хосте** при холодном старте `karaoke-web`: heap `-Xmx1200m` +
  swap 8 ГБ; при OOM — снизить heap и повторить (не трогая БД/MinIO).
- **DNS ещё не переключён, а данные уже «уехали»**: записи на новом хосте за
  окно проверки переносятся вручную при rollback (короткое окно 2–4 ч).
- **`minio/minio` тянется из Docker Hub**: pull провалится (404) — использовать
  `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`.
- **`ENABLE_APP_GPU=1`** на новом хосте без NVIDIA: выставить `0` (иначе
  compose с nvidia-reservation падает).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Новый хост MUST иметь Docker Engine 29.x + команду
  `docker-compose` (враппер `exec docker compose "$@"`), host-nginx 1.28.3 +
  `libnginx-mod-stream`, swap 8 ГБ, `vm.swappiness=10`.
- **FR-002**: Раскладка MUST быть: `/root/Karaoke/deploy` (канон из
  `deploy/prod-single-host/`), данные в
  `/sm-karaoke/system/{Караоке-db,Караоке-storage,dumps}`.
- **FR-003**: MinIO MUST работать из образа
  `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z`; порты API/console —
  только loopback (`127.0.0.1:8890`/`8891`), `8890` доступен admin-машине.
- **FR-004**: Перенос MinIO MUST быть `mc mirror` в два прохода (bulk + инкремент);
  `.minio.sys` НЕ копировать; финальный `mc diff` пуст.
- **FR-005**: Перенос БД MUST быть `pg_dump`(`--clean --create --if-exists`)+restore;
  физические файлы — только fallback.
- **FR-006**: host-nginx MUST: TLS fullchain `/etc/keys/`, `/minio/` →
  `127.0.0.1:8890` с HTTP-кэшем 24 ч/404-5мин, `/api/`, `/changerecords`,
  `/song` (bot-split), `/` → `127.0.0.1:7907`, `/smartcaptcha/`, `/yookassa/`,
  `stream{5433→127.0.0.1:8832, allow 185.26.28.109}`.
- **FR-007**: `karaoke-web` MUST стартовать с `-Xmx1200m` (не 8g/1048m).
- **FR-008**: Admin MUST получить проброс `DB_REMOTE_HOST` в
  `docker-compose-app.yml`, значение → `188.127.240.124`; дефолты
  `application.yml` обоих модулей → новый IP.
- **FR-009**: Cutover MUST выполняться по runbook: заморозка → инкремент MinIO →
  финальный дамп/restore → подъём → smoke по IP → DNS; окно ≤20 мин.
- **FR-010**: DNS MUST: TTL apex/www → 300 заранее; в окне A → `188.127.240.124`;
  `mail`/MX/SPF/TXT/PTR не менять; rollback — вернуть A на старый IP.
- **FR-011**: Приёмка MUST прогоняться `tools/migration-smoke.sh` (до и после DNS)
  + checklist `acceptance-checklist.md`; UI-верификация — владельцем.
- **FR-012**: `knowledge/` MUST быть обновлён (`02-containers.md`,
  `deploy-overview.md`, `storage/domain.md`, `architecture-notes.md`);
  `deploy/new_comp/` помечен deprecated.
- **FR-013**: Systemd-таймеры `karaoke-db-backup.timer` (05:00, retention 7 дн)
  и `karaoke-docker-prune.timer` MUST работать на новом хосте.
- **FR-014**: Старые серверы MUST оставаться выключенными (не удалёнными)
  ≥7 дней; выключает владелец.

### Key Entities

- **NewHost**: `188.127.240.124` (`sm-karaoke`), Ubuntu 26.04, 6 vCPU, 3.8 ГБ, 985 ГБ.
- **OldProd**: `188.119.64.111` (`karaoke-prod`) — выводится из эксплуатации.
- **OldStorage**: `89.125.103.63` — MinIO-источник, выводится.
- **Admin**: `nsa-i9` (`185.26.28.109`) — остаётся, sync-клиент.
- **DeployCanon**: `deploy/prod-single-host/` — канонический deploy нового хоста.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: `tools/migration-smoke.sh` на новом хосте (до DNS) и на домене
  (после) → **все проверки PASS** (baseline 15/15).
- **SC-002**: Данные совпадают: `tbl_songs`=26 549, `tbl_site_users`=86,
  MinIO 59 707 объектов / ≈444.45 GiB, md5 выборочных объектов совпадает.
- **SC-003**: Окно заморозки (от остановки писателей до DNS) **≤20 мин**.
- **SC-004**: После истечения TTL старый прод получает **0** новых запросов
  за 2 ч наблюдения.
- **SC-005**: Нет OOM-kill и circuit-breaker OPEN на новом хосте в первые 24 ч.
- **SC-006**: Ручная синхронизация из admin проходит; `AutoOneClickSyncScheduler`
  без ошибок.
- **SC-007**: Знания и репо-деплой описывают однохостовую топологию; knowledge-линтеры зелёные.

## Assumptions

- Владелец выполняет шаги, помеченные «владелец» (остановка старых
  контейнеров, DNS в reg.ru, гашение серверов) либо даёт явное согласие агенту.
- SSH `root@sm-karaoke`, `root@188.119.64.111`, `root@89.125.103.63` настроен.
- Секреты (`do.env`/`.env`) переносятся вручную `scp`, в git не попадают.
- Почта/`mail`/MX вне scope (мертва).
- Ужесточение TLS (1.0/1.1) и перевыпуск сертификата (2027) — вне scope.

# Research: Локальная Jira для AI-агента (spec 295)

**Branch**: `295-jira-local-integration`
**Date**: 2026-09-02
**Spec**: [spec.md](./spec.md)

## Summary

Фича — локальный Jira Data Center в Docker для AI-агента. Решение использует
**официальный образ Atlassian** (`atlassian/jira-software`), **внешний Postgres 13+** в
named-volume, **CLI на bash** (`tools/jira.sh`) через REST API v3, и **паттерн бэкапа
через systemd-timer** (как уже принято в Karaoke для `karaoke-db-backup.{timer,sh}`).

## Technology Decisions

### 1. Jira Software Data Center vs Jira Cloud

**Decision**: **Jira Software Data Center** (self-hosted, официальный Docker-образ
`atlassian/jira-software:<version>`).

**Rationale**:
- Полностью локальный сервис — данные не уходят в Atlassian Cloud (требование Constitution § I, self-contained).
- Бесплатен для разработки и личного использования (Data Center evaluation license).
- Полный REST API (v2 + v3) — никаких ограничений Cloud Free tier (до 10 пользователей / 2 GB).
- Готовый официальный Docker-образ с поддержкой JDK 11/17 и подключением к внешнему Postgres.

**Alternatives considered**:
- ❌ **Jira Cloud Free tier** — требует регистрацию на atlassian.net, API-токен через UI;
  данные хранятся у Atlassian. Нарушает self-contained.
- ❌ **Greenlight / mock-jira-server** (например, `sdudek/mock-jira-server`) — не
  production-grade, нет UI, нет workflow. Не подходит для реального тестирования.
- ❌ **Jira CLI-эмулятор (`/rest/api/3/...`)** — то же самое, нет реального workflow.

### 2. Версия Jira: 9.12.x LTS (или выше LTS на момент установки)

**Decision**: использовать **последний LTS на момент установки**, жёсткое условие:
- Совместимость с **Postgres 13/14/15** (см. Atlassian compatibility matrix).
- Поддержка **JDK 11** (по умолчанию в образе `eclipse-temurin:11-jdk-jammy`).
- **REST API v3** (новый формат с ADF — Atlassian Document Format).

**Rationale**: Atlassian выпускает LTS-версии каждые ~12 месяцев. На 2026-09
актуальный LTS — Jira 9.12+ или 10.x (если Atlassian выпустил). Закрепим в
`deploy/jira-docker-compose.yml` как переменную `JIRA_IMAGE_TAG` для возможности
обновления.

**Alternatives considered**:
- ❌ **Jira 8.x LTS** — старше, EOL приближается; нет поддержки ADF в REST v3.
- ❌ **Latest non-LTS** — недетерминирован (нарушает Convention «не использовать
  `node:latest`» в расширенном смысле — нестабильный тег = риск регрессов при `pull`).

### 3. Стек CLI: bash + curl + jq

**Decision**: CLI реализован на **bash** с использованием **curl** для HTTP и **jq**
для парсинга JSON-ответов.

**Rationale**:
- Bash — нативен для Karaoke (см. `deploy/do.sh`, `karaoke-db-backup.sh`,
  `tools/specify-bootstrap.sh` — везде bash). Один язык = меньше cognitive load.
- `curl` есть в любом Linux и в Alpine-варианте busybox (но мы используем не-alpine).
- `jq` стабилен с 2014 года, версия 1.7+ — стандарт де-факто.
- CLI-команды короткие (≤200 строк каждая), Python overkill для HTTP-обёртки.
- Bash даёт прямой доступ к env-переменным (`$JIRA_URL`, `$JIRA_TOKEN`) без
  виртуального окружения.

**Alternatives considered**:
- ❌ **Python (requests + argparse)** — добавляет зависимость от Python-окружения,
  `pip install`, venv. Karaoke — bash-first.
- ❌ **Go binary** — нужен `go build`, кросс-компиляция. Избыточно для CLI-обёртки.
- ❌ **Node.js (axios)** — нужен `node_modules`, тяжёлая зависимость.

### 4. Бэкап: systemd-timer (как в Karaoke) вместо cron

**Decision**: использовать **systemd-timer + .service** по образцу существующего
`deploy/karaoke-db-backup.{timer,sh}`. Файлы будут называться
`deploy/jira-db-backup.{timer,service,sh}`.

**Rationale**:
- **Уже работает в Karaoke** — пользователь знаком с шаблоном (`OnCalendar=*-*-* 05:00:00`,
  `Persistent=true`, `RandomizedDelaySec=300`).
- Интеграция с **systemd journald** для логов (без отдельного logrotate).
- `Persistent=true` — если машина была выключена в момент срабатывания, бэкап
  запустится при следующем старте (catch-up).
- Не требует `crontab -e` (который не всегда доступен из docker-контейнера).

**Alternatives considered**:
- ❌ **Cron** — работает, но требует отдельный logrotate-конфиг и не имеет catch-up
  при выключенной машине.

### 5. pg_dump через `docker exec` (как в `karaoke-db-backup.sh`)

**Decision**: `pg_dump` запускается **из хоста через `docker exec jira-db pg_dump ...`**
(а не как sidecar внутри контейнера Jira).

**Rationale**:
- Идентично существующему паттерну в `deploy/karaoke-db-backup.sh` (строка 50:
  `docker exec karaoke-db pg_dump ...`). Пользователь узнает код.
- Не нужно добавлять cron внутри контейнера Postgres (нарушает single-responsibility).
- Креды Postgres берутся из env на хосте, не нужно монтировать секрет в контейнер.

**Alternatives considered**:
- ❌ **Sidecar-скрипт внутри `jira-db`** — нарушает single-responsibility контейнера.
- ❌ **Снапшот docker volume** (`docker run --rm -v jira-postgres-data:/data busybox tar ...`) —
  медленнее restore, нужно останавливать Postgres для consistency.

### 6. Хранилище секретов: `.env.local-jira` в корне репо (НЕ в `deploy/`)

**Decision**: секреты Jira живут в **`.env.local-jira`** в корне Karaoke (на том же
уровне, что `.env.local`, `.gitignore` для Karaoke).

**Rationale**:
- Это **локальный** сервис на admin-машине (не прод-сервер), отдельный от Karaoke.
- `deploy/.env` уже зарезервирован для **прод-секретов Karaoke** (Postgres,
  MinIO, Docker Hub PAT — см. Constitution § VIII.2). Чтобы не путать — Jira
  хранит свои секреты отдельно.
- Файл `.env.local-jira` добавляется в `.gitignore` (или явно игнорируется по
  существующему паттерну `*.env.*`).

**Alternatives considered**:
- ❌ **`deploy/.env-jira`** — смешивает с прод-секретами Karaoke, риск случайного
  коммита через `git add deploy/`.
- ❌ **`~/.local-jira.env`** — нет версионирования конфига (что если пользователь
  потеряет файл? Нет `install.sh` для воспроизводимой установки).

### 7. Healthcheck: HTTP-проверка `/status` (не `/rest/api/3/serverInfo`)

**Decision**: healthcheck = `curl -fsS http://localhost:8080/status` (публичный
endpoint без аутентификации, отдаёт `{"state":"RUNNING"}` если Jira готова).

**Rationale**:
- `/status` — публичный, не требует API-токена (подходит для `HEALTHCHECK` в Dockerfile
  и для внешних мониторов).
- `/rest/api/3/serverInfo` требует Basic auth → healthcheck усложняется.
- `/status` отвечает быстро (≤100ms) при рабочей Jira, возвращает 503 при старте/остановке.

**Alternatives considered**:
- ❌ **TCP-порт check** (`nc -z localhost 8080`) — порт открыт, но Jira может
  быть в процессе инициализации (5-10 минут при первом старте).
- ❌ **`pg_isready` к Postgres** — проверяет БД, но не Jira-приложение.

### 8. REST API версия: v3 (с ADF)

**Decision**: использовать **`/rest/api/3/...`** (новая версия, ADF-формат описаний).

**Rationale**:
- ADF (Atlassian Document Format) — это JSON-дерево для описаний и комментариев.
- v3 поддерживает те же эндпоинты, что v2, плюс новые (agile boards, permissions).
- CLI формирует ADF через `jq` (простые конструкции: параграф, заголовок, список).

**Alternatives considered**:
- ❌ **REST API v2 (wiki-markup)** — проще для CLI (markdown → wiki), но Atlassian
  рекомендует v3 для новых интеграций и **deprecated** v2 для новых фич.

### 9. Rate-limit handling: экспоненциальный backoff (см. Clarification Q3)

**Decision**: при получении HTTP 429 CLI выполняет retry с backoff `2s → 4s → 8s`,
до 3 попыток суммарно (≤14 секунд ожидания), затем ошибка пользователю.

**Rationale**: подтверждено пользователем в Clarification Q3 (2026-09-02).

### 10. Структура каталогов: `tools/` + `deploy/jira-docker-compose.yml`

**Decision**:
- `tools/jira.sh` — основной CLI (с подкомандами из FR-005).
- `tools/jira-backup.sh` — обёртка для systemd-timer (вызывает `docker exec jira-db pg_dump`).
- `tools/jira-restore.sh` — восстановление из последнего дампа.
- `tools/install-jira.sh` — first-run setup (создаёт `.env.local-jira`, делает `docker compose up -d`).
- `deploy/jira-docker-compose.yml` — отдельный compose-файл (НЕ добавление в существующий `do.sh`,
  чтобы не затрагивать основной стек).
- `docs/jira-setup.md` — пошаговая документация.
- `docs/features/jira-local-integration.md` — per-feature документ (FR-009 Constitution § VI).

**Rationale**:
- Соответствует существующей структуре Karaoke (`tools/` для скриптов, `deploy/` для compose,
  `docs/` для документации).
- **Отдельный compose-файл** — изоляция от Karaoke-стека; можно `down` Jira без влияния
  на Karaoke (FR-011).
- **Per-feature документ** обязателен по Constitution § VI / FR-009 — для каждой
  ключевой подсистемы.

### 11. Тестирование: ручное + smoke-скрипт `tools/jira-smoke-test.sh`

**Decision**: автоматические тесты НЕ создаются (как и для Karaoke — тесты
`@Disabled` по Constitution § Governance). Вместо этого:
- `tools/jira-smoke-test.sh` — bash-скрипт, проверяющий базовый сценарий:
  1. `list-projects` → проверка, что проект `KARAOKE` существует.
  2. `create-issue --summary "smoke-test" --description "..."` → создать задачу.
  3. `get-issue <KEY>` → проверить, что задача существует.
  4. `add-comment <KEY> --file <(echo "smoke test comment")` → добавить комментарий.
  5. `close-issue <KEY>` → закрыть задачу.
  6. Проверить, что все 5 шагов вернули HTTP 2xx.

**Rationale**:
- Соответствует общему подходу Karaoke (нет unit-тестов, есть ручная проверка).
- Smoke-скрипт запускается пользователем вручную после установки и при обновлении
  версии Jira.

### 12. Webhook (FR-014): отложен, polling MVP

**Decision**: webhook НЕ реализуется в MVP. Используется polling (по cron / вручную).

**Rationale**: подтверждено в Clarification Q3 (2026-09-02) и спецификации (FR-014 — SHOULD, не MUST).
Реализация webhook требует публичного endpoint (туннель типа ngrok) — отдельный класс
проблем (TLS, auth, reconnection). Polling достаточен для личного использования.

## Best Practices Researched

### Atlassian Jira DC REST API

- **Базовый URL**: `http://localhost:8080/rest/api/3/` (для self-hosted) или `/rest/api/2/`
  (legacy).
- **Аутентификация**: HTTP Basic с `email:api_token` (для DC) или `username:password`
  (для Server/DC legacy).
- **Основные эндпоинты**:
  - `GET /rest/api/3/project/search` — список проектов.
  - `POST /rest/api/3/issue` — создать issue (тело — JSON с `fields`).
  - `GET /rest/api/3/search?jql=assignee=ai-agent AND status="To Do"` — поиск через JQL.
  - `PUT /rest/api/3/issue/{issueIdOrKey}` — обновить (status через transitions).
  - `POST /rest/api/3/issue/{issueIdOrKey}/comment` — добавить комментарий.
  - `GET /rest/api/3/issue/{issueIdOrKey}/transitions` — список доступных переходов.
  - `POST /rest/api/3/issue/{issueIdOrKey}/transitions` — выполнить переход.
- **ADF (Atlassian Document Format)** — JSON-структура для description и comment body.
  Минимальный документ: `{"type":"doc","version":1,"content":[{"type":"paragraph","content":[{"type":"text","text":"Hello"}]}]}`.

### pg_dump Custom Format (-Fc)

**Decision**: использовать `-Fc` (custom format, compressed) для всех бэкапов Jira.

**Rationale**:
- `-Fc` сжимает данные (уменьшает размер в 3-5 раз vs SQL-text).
- `pg_restore` умеет работать только с `-Fc` для selective restore (например,
  восстановить только одну таблицу).
- Совместимо с `--clean --if-exists` для атомарного пере-создания схемы.

### systemd-timer Best Practices

- `OnCalendar=*-*-* 03:00:00` — ежедневно в 03:00 (вне пиковых часов).
- `Persistent=true` — catch-up после downtime.
- `RandomizedDelaySec=300` — рандомизация ±5 минут (если несколько машин).
- `WantedBy=timers.target` — стандартная активация.
- Логи в `journalctl -u jira-db-backup.service` (без отдельного logrotate).

## Resolved NEEDS CLARIFICATION

Все `NEEDS CLARIFICATION` маркеры из шаблона плана разрешены:

| Маркер | Резолюция |
|--------|-----------|
| Language/Version CLI | Bash (curl + jq), совместим с bash 4.4+ (есть на Ubuntu 22.04+, Debian 11+) |
| Primary Dependencies CLI | `curl` (≥7.80), `jq` (≥1.6), `bash` (≥4.4) — все есть на admin-машине |
| Storage | PostgreSQL 13+ (named volume `jira-postgres-data`), pg_dump custom format |
| Testing | Ручное + `tools/jira-smoke-test.sh` (по образцу Karaoke) |
| Target Platform | Linux x86_64 (Debian 11+/Ubuntu 22.04+, RHEL 9+) |
| Project Type | CLI-утилита + docker-compose сервис (НЕ web-service, НЕ library) |
| Performance Goals | `list-issues` ≤5 сек @ 1000 задач (SC-002) |
| Constraints | Healthcheck ≤3 сек (SC-008); полный backup ≤10 мин @ 1 GB (SC-009) |
| Scale/Scope | 1 пользователь (`ai-agent`) + 1 admin, проект ≤1000 задач |

## Open Questions / Risks

### Риск 1: Долгий старт Jira (5-15 минут)

Jira Software Data Center при первом старте:
1. Запускает JVM с Tomcat.
2. Инициализирует схему в Postgres (≈3-5 минут).
3. Загружает плагины из `/var/atlassian/jira/plugins` (≈1-2 минуты).
4. Делает self-check и начинает отвечать на HTTP.

**Mitigation**: healthcheck с `start_period=600s` (10 минут) и `interval=30s` —
docker будет показывать `starting` пока Jira не ответит на `/status`.

### Риск 2: Лицензия Data Center

Data Center evaluation license длится 30 дней, потом требует продления.
Для непрерывной работы нужно либо:
- Купить DC-лицензию (вне scope).
- Использовать Community-версию / OSS-аналог (но это НЕ Jira).

**Mitigation**: для личного использования (один разработчик, одна admin-учётка,
одна AI-учётка) — license не проверяется жёстко; Jira продолжит работать после
expiry, но будет показывать nag-screen. Документируем в `docs/jira-setup.md`.

### Риск 3: Порт 8080 может быть занят на admin-машине

На `nsa-i9` Karaoke-web слушает 8080? Нужно проверить. Если занят — выбрать другой
порт (например, 8090) и пробросить в `jira-docker-compose.yml`.

**Mitigation**: в `install-jira.sh` — автодетект занятого порта с фолбэком на 8090.

## Next Steps (Phase 1)

- Сгенерировать `data-model.md` (описание entities из spec + конкретные JSON-схемы для ADF).
- Сгенерировать `contracts/jira-rest-api.md` (эндпоинты, форматы запросов/ответов).
- Сгенерировать `quickstart.md` (пошаговый сценарий проверки end-to-end).
- Заполнить `plan.md` (Technical Context + Constitution Check + Project Structure).

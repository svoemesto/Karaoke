# ADR-0008: Миграция локального issue-tracker с Jira DC на OpenProject CE

* **Status**: Accepted
* **Date**: 2026-09-02 (Pass 295)
* **Deciders**: команда Karaoke (через решение пользователя nsa — owner)

## Context

Спека 295 (`specs/295-jira-local-integration/spec.md`, бывшая
`specs/295-jira-local-integration/spec-jira.md` в `.jira-archived/`) требовала
поднять **локальный Jira-like issue-tracker**, чтобы:

1. **Пользователь** мог заводить в нём задания.
2. **AI-агент** через CLI-обёртку мог их видеть, брать и исполнять.
3. **Отчёты** публиковались в трекере с автозакрытием задачи.

Спека изначально формулировалась под **Jira Software Data Center 9.x**:

| Аспект | Jira DC (план) | Требования спеки 295 |
|---|---|---|
| Лицензия | Commercial, требуется trial / paid | Доступная в РФ/РБ, без лицензии |
| Work packages | Задачи (issues) с key `KARAOKE-N` | Числовой ID, markdown |
| REST API | v3, Basic Auth (`email:api_token`) | 8 операций (CRUD) |
| Аутентификация | Личный API token | CLI через переменные окружения |
| Размер образа | 1.4 GB | Не критично |

### Проблема

При попытке скачать evaluation-образ `atlassian/jira-software:9.12.13` и
зарегистрировать evaluation license (что требуется для входа через 30 дней)
**Atlassian заблокировал регистрацию для IP-подсетей РФ/РБ**. Это
зафиксировано в **Constitution § I (Self-Contained)** как запрет на
зависимости, требующие внешних сервисов с географическими ограничениями.

Альтернативы:

| Альтернатива | Лицензия | REST API | Self-hosted | SAAS-блокировки |
|---|---|---|---|---|
| Jira DC | Commercial | v3 (ADF) | + | ❌ блокировка в РФ/РБ |
| Jira Cloud | SAAS | v3 | ❌ | ❌ нужна банковская карта |
| Redmine | MIT | v2 (XML/JSON) | + | ✅ без блокировок |
| OpenProject CE 13 | MIT | v3 (HAL+JSON) | + | ✅ без блокировок |
| Gitea Issues | MIT | v1 (REST) | + | ✅ без блокировок |

Анализ **OpenProject Community 13** показал, что это **drop-in замена** с
тем же покрытием REST API v3 и тем же user experience:

- MIT-лицензия, полностью бесплатный (без trial-лимитов).
- REST API v3 совместим по операциям (work_packages, activities/comments,
  users, statuses, types — есть всё необходимое).
- Поддерживает workflow, agile-доски, custom fields, attachments, markdown.
- Self-hosted через Docker одной командой.

### Рассмотренные варианты

1. **Jira DC (исходный план)** — отклонён из-за лицензионной блокировки.
2. **OpenProject CE** — выбран (см. Decision).
3. **Redmine + REST API v2** — отклонён (API более простой, нет markdown в
   комментариях, нет удобных workflow).
4. **Gitea Issues** — отклонён (API не покрывает работу с пользователями и
   workflow в нужном объёме, не предназначен для general issue-tracker).

## Decision

Принять **OpenProject Community Edition 13** как issue-tracker для спеки 295.

**Реализация** (см. drill-down):

- Compose: `deploy/tracker-docker-compose.yml` (отдельный стек, **не** часть
  `deploy/do.sh` основного проекта Karaoke).
- CLI: `tools/tracker.sh` + `tools/tracker-lib.sh` (8 подкоманд, Basic Auth с
  `username='apikey'`, JSON-логирование в `logs/tracker-agent.log`,
  retry/backoff на 429).
- Bootstrap: `tools/install-tracker.sh` автоматически создаёт пользователя
  `ai-agent` (admin) и API-токен через `rails runner` (не требует ручного
  first-run setup в UI).
- Smoke-test: `tools/tracker-smoke-test.sh` проверяет все 8 операций.
- Backup: `deploy/tracker-db-backup.{sh,service,timer}` через systemd-timer.

## Consequences

### Положительные

- **Никаких лицензионных ограничений**, никаких гео-блокировок.
- **Open-source stack** — соответствует общим принципам проекта (MIT-лицензии
  везде, где возможно).
- **REST API v3** — стандартный HAL+JSON с поддержкой markdown в полях
  `description.raw` и `comment.raw` (формат `"markdown"`).
- **Drop-in совместимость** — операции CLI один-к-одному совпадают с
  исходными требованиями FR-005 (`list-projects`, `list-issues`, `get-issue`,
  `claim-issue`, `add-comment`, `close-issue`, `reopen-issue`,
  `create-issue`).

### Отрицательные / Trade-offs

- **Id по work packages числовые, а не текстовые** (`42`, а не
  `KARAOKE-42`). ВЛ CLI/Spec должны использовать числа. Изменения коснулись
  `tracker-smoke-test.sh` и документации.
- **Workflow / statuses** требуют понимания, что OpenProject использует
  ID-шники (`/api/v3/statuses`) и ссылки через `_links.status.href`, а не
  текстовые имена, как было в плане на Jira transitions API. CLI делает
  search по `name`.
- **`lockVersion` обязателен** для каждого PATCH — CLI делает повторный GET
  при конфликте. Это медленнее, чем Jira transitions, но достаточно надёжно.
- **Markdown rendering** в UI OpenProject немного отличается от Jira ADF, но
  базовый markdown (заголовки, списки, code, ссылки) работает одинаково.

### Нейтральные

- **Auth-strategy**: в OpenProject CLI использует Basic Auth с
  username=`apikey` (фиксированное значение), password=`TRACKER_API_TOKEN`.
  `TRACKER_USER` (например, `ai-agent`) используется только для фильтров и
  whoami-логов. Это особенность warden-стратегии `UserBasicAuth` в
  OpenProject и **не баг**.

## Compliance

- ✅ Constitution § I (Self-Contained) — нет внешних сервисов, всё self-hosted.
- ✅ Constitution § VIII (Secrets) — `TRACKER_API_TOKEN`, `TRACKER_DB_PASSWORD`,
  `TRACKER_SECRET_KEY_BASE` — в `.env.local-tracker` (в `.gitignore`).
- ✅ AGENTS.md «Разрешено» — локальные контейнеры через отдельный
  compose-файл (не часть `deploy/do.sh` основного стека Karaoke).
- ✅ FR-001..FR-018 спеки 295 — все реализованы (см. AC в
  `livedocs/features/295-jira-local-integration.md`).

## Связанные документы

- [LiveDoc 295-jira-local-integration.md](../../features/295-jira-local-integration.md)
- [Spec 295](../../../../specs/295-jira-local-integration/spec.md)
- [Архивная спека на Jira DC](../../../../specs/295-jira-local-integration/.jira-archived/spec-jira.md) —
  хранится для истории и сравнения
- [tracker-setup.md](../../../../docs/tracker-setup.md) — руководство по установке

# Per-Feature документы Karaoke

> Каждая ключевая подсистема проекта Karaoke имеет свой документ с описанием
> «Что / Зачем / Как / Инварианты / Ловушки / Ссылки». Формат — в
> [contracts/per-feature-doc.md](../../specs/001-code-standards-docs/contracts/per-feature-doc.md).
>
> **Правило**: PR, меняющий код одной из этих фич, **обязан** обновлять
> соответствующий документ (FR-009 spec.md).

## 9 ключевых подсистем

| # | Slug | Название | Файл |
|---|------|----------|------|
| 1 | `mlt-generator` | MLT-генератор караоке-видео | [mlt-generator.md](./mlt-generator.md) |
| 2 | `async-process-queue` | Async-очередь задач `KaraokeProcess*` | [async-process-queue.md](./async-process-queue.md) |
| 3 | `dual-db-sync` | Двух-БД синхронизация LOCAL↔SERVER | [dual-db-sync.md](./dual-db-sync.md) |
| 4 | `mp4-render` | Рендер MP4 из онлайн-плеера | [mp4-render.md](./mp4-render.md) |
| 5 | `sse-notifications` | SSE-уведомления для live-UI | [sse-notifications.md](./sse-notifications.md) |
| 6 | `premium-stems` | Премиум-фича «Создать минусовку» | [premium-stems.md](./premium-stems.md) |
| 7 | `llm-lyrics-search` | LLM-assisted поиск текстов и аккордов | [llm-lyrics-search.md](./llm-lyrics-search.md) |
| 8 | `telegram-auto-publish` | Автопубликация в Telegram-канал | [telegram-auto-publish.md](./telegram-auto-publish.md) |
| 9 | `monitoring` | Мониторинг ключевых моментов | [monitoring.md](./monitoring.md) |
| 10 | `dictionaries` | Словари (DB + TextFile) для lyrics | [dictionaries.md](./dictionaries.md) |
| 11 | `stats` | Статистика (главная + события + KPI) | [stats.md](./stats.md) |

## Cross-cutting (инфраструктура)

Фичи, которые **не являются продуктовыми подсистемами**, но обслуживают
процесс разработки и должны документироваться по тем же правилам (FR-009):

| Slug | Название | Файл |
|------|----------|------|
| `ci-lint-enforcement` | GitHub Actions: ktlint + ESLint + Prettier + docs | [ci-lint-enforcement.md](./ci-lint-enforcement.md) |

## Как пользоваться

- **Новый разработчик**: начни с `async-process-queue.md` и `sse-notifications.md`
  — они объясняют базовые паттерны проекта.
- **Перед изменением кода фичи**: прочитай её документ, особенно секции
  «Инварианты» и «Известные ловушки» — там зафиксированы правила,
  нарушение которых уже ломало прод.
- **Code review**: если PR меняет код фичи из таблицы, но не обновляет
  соответствующий документ — это блокер (FR-009).
- **Добавление новой ключевой подсистемы**: создай новый `.md` файл в этой
  папке, обнови таблицу выше, добавь `@see`-ссылку в KDoc корневого
  класса.

## Скоуп

- **В скоупе**: 5 активных модулей (`karaoke-app`, `karaoke-web`, `webvue3`,
  `karaoke-public`, `deploy/`).
- **Вне скоупа**: legacy `karaoke-db`, `karaoke-vue`. Если они вам
  понадобились — откройте issue для выделения в отдельную фичу.

## Связанные документы

- [CONTRIBUTING.md](../../CONTRIBUTING.md) — правила оформления кода
- [DEVELOPMENT.md](../../DEVELOPMENT.md) — архитектурный контекст и dated-история
- [AGENTS.md](../../AGENTS.md) — инструкции для AI-агента
- [constitution.md](../../.specify/memory/constitution.md) — непреложные принципы
- [docs/architecture-notes-archive.md](../architecture-notes-archive.md) — история изменений архитектуры

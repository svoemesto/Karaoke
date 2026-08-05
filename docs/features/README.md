# Per-Feature документы Karaoke

> Каждая ключевая подсистема проекта Karaoke имеет свой документ с описанием
> «Что / Зачем / Как / Инварианты / Ловушки / Ссылки». Формат — в
> [contracts/per-feature-doc.md](../../specs/001-code-standards-docs/contracts/per-feature-doc.md).
>
> **Правило**: PR, меняющий код одной из этих фич, **обязан** обновлять
> соответствующий документ (FR-009 spec.md).

## 20 ключевых подсистем

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
| 12 | `special-orders` | Спецзаказные авторы (виртуальная плашка в Закромах) | [special-orders.md](./special-orders.md) |
| 13 | `songs-table` | Таблица песен в админке (webvue3) | [songs-table.md](./songs-table.md) |
| 14 | `player-transpose` | Транспонирование аудио в онлайн-плеере (админка + публичный премиум) | [player-transpose.md](./player-transpose.md) |
| 15 | `vk-news-auto-publish` | Автопубликация новостей в группу ВКонтакте | [vk-news-auto-publish.md](./vk-news-auto-publish.md) |
| 16 | `premium-auto-publish` | Премиум-публикация (Telegram+VK) при становлении песни доступной | секция внутри [telegram-auto-publish.md](./telegram-auto-publish.md) |
| 17 | `news-publish-backfill` | Backfill флагов публикации готовых песен + kill-switch на sync-окне | [news-publish-backfill.md](./news-publish-backfill.md) |
| 18 | `news-templates` | Шаблоны авто-новостей сайта (title+body, вкладка в `PublishTemplatesView`) | [news-templates.md](./news-templates.md) |
| 19 | `approve-pipeline` | Approve-пайплайн: рендер DEMO + Telegram + sync related → новость «в коллекции» | [approve-pipeline.md](./approve-pipeline.md) |
| 20 | `song-free-access` | Временное окно бесплатного доступа к песням (1 месяц после эфира + «всегда бесплатно») | [song-free-access.md](./song-free-access.md) |
| 21 | `homepage-latest-news` | Блок «Последние 5 новостей» на главной странице сайта (SPA + Thymeleaf) | [homepage-latest-news.md](./homepage-latest-news.md) |
| 22 | `vk-id-auth` | Авторизация через VK ID (id.vk.ru) — получение и автообновление user-token для `photos.*` / `video.save` | [vk-id-auth.md](./vk-id-auth.md) |

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

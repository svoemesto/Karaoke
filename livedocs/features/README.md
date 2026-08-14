# features/ — SDD слой (индекс)

> Сводки фич (≤ 2 страницы каждая). Drill-down — в `specs/<NNN>-*/spec.md`.

## Содержимое

| Файл | Что делает | Drill-down |
|------|------------|------------|
| [171-admin-subscriptions-history.md](171-admin-subscriptions-history.md) | Админ-таблицы: Подписки, История прослушиваний, Временные ссылки | [specs/171-.../spec.md](../../specs/171-admin-subscriptions-history/spec.md) |
| [176-authors-new-albums-badge.md](176-authors-new-albums-badge.md) | Бейдж «новые альбомы» в пункте меню «Авторы» | [specs/176-.../spec.md](../../specs/176-authors-new-albums-badge/spec.md) |
| [177-fix-process-count-waiting-spam.md](177-fix-process-count-waiting-spam.md) | Устранение спама PROCESS_COUNT_WAITING в SSE | [specs/177-.../spec.md](../../specs/177-fix-process-count-waiting-spam/spec.md) |
| [180-og-seo-html.md](180-og-seo-html.md) | SEO-HTML (Schema.org JSON-LD + OG + Twitter) вместо PNG | [specs/180-.../spec.md](../../specs/180-og-seo-html/spec.md) |
| [181-zakroma-author-load-progress.md](181-zakroma-author-load-progress.md) | Real-time прогресс через NDJSON-стрим | [specs/181-.../spec.md](../../specs/181-zakroma-author-load-progress/spec.md) |
| [182-editor-self-assign-tasks.md](182-editor-self-assign-tasks.md) | Self-assign заданий редакторами (atomic SELECT FOR UPDATE + idempotency) | [specs/182-.../spec.md](../../specs/182-editor-self-assign-tasks/spec.md) |
| [184-approve-status-choice.md](184-approve-status-choice.md) | Условный запуск конвейера при выборе idStatus 5/6 | [specs/184-.../spec.md](../../specs/184-approve-status-choice/spec.md) |
| [185-song-dto-audit-sponsr-remove.md](185-song-dto-audit-sponsr-remove.md) | Аудит Song DTO, удаление спонсорских полей | [specs/185-.../spec.md](../../specs/185-song-dto-audit-sponsr-remove/spec.md) |
| [186-zakroma-songs-fast-load.md](186-zakroma-songs-fast-load.md) | Оптимизация загрузки песен в Закромах | [specs/186-.../spec.md](../../specs/186-zakroma-songs-fast-load/spec.md) |
| [187-site-traffic-anomaly-investigation.md](187-site-traffic-anomaly-investigation.md) | Расследование аномалии трафика сайта | [specs/187-.../spec.md](../../specs/187-site-traffic-anomaly-investigation/spec.md) |

## Конвенции

- Имя файла: `<NNN-slug>.md`, где NNN — номер спеки.
- Размер: ≤ 2 страницы (≤ 80 строк).
- Шаблон: [`templates/feature-summary.md`](../templates/feature-summary.md).
- Frontmatter: обязательные `status`, `slug` + опциональный `related` (связь с bounded contexts и C4 уровнями).

## Связь с другими слоями

- **Drill-down**: `specs/<NNN>-*/spec.md` (полная спека с US/FR/SC).
- **Cross-references**: `domain/<context>.md` (какие bounded context'ы затрагивает).
- **Архитектура**: `architecture/L<n>-*.md` (где реализован).

## Когда добавлять новую сводку

1. Создана новая фича (Pass N+1) → мигрировать сводку в features/.
2. Существующая фича протухла → обновить сводку (не удалять).
3. Сводка устарела безвозвратно → перевести `status: Archived` (не удалять из git).
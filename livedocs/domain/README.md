# domain/ — DDD слой (индекс)

> Bounded contexts проекта Karaoke + ubiquitous language glossary.

## Содержимое

| Файл | Bounded Context | Aggregate Roots | Ключевые понятия |
|------|-----------------|-----------------|------------------|
| [catalog.md](catalog.md) | Каталог | Song, Album, Author, Genre | песня, альбом, исполнитель |
| [processing.md](processing.md) | Обработка | KaraokeVideo, MLTProject, RenderMp4Params | MLT, Demucs, Sheetsage, stem |
| [rendering.md](rendering.md) | Рендеринг видео (drill-down для processing) | KaraokeVideo file, MLTProject | MP4, mko, JPEG quality 95 |
| [publishing.md](publishing.md) | Публикация | PublishWindow, Subscription | эфир, on-air, exclusive, premium |
| [stats.md](stats.md) | Статистика и аналитика | StatBySong, SiteEvent | visitor, bot, funnel, воронка |
| [identity.md](identity.md) | Идентификация | SiteUser, Session | JWT, cookie, principal |
| [editorial.md](editorial.md) | Редакторы | EditorAssignment, ReviewTask | self-assign, idempotent, race |

## Ubiquitous Language (корневые термины)

| Термин | Определение | Где определён |
|--------|-------------|----------------|
| **Песня (Song)** | Единица каталога. Aggregate root в catalog. | [catalog.md](catalog.md) |
| **Караоке-видео (KaraokeVideo)** | Результат обработки — видео со встроенными титрами. Aggregate root в processing. | [processing.md](processing.md) |
| **MLT-проект (MLTProject)** | Проект melt/MLT, описывающий слои караоке-видео. | [processing.md](processing.md) |
| **Стем (Stem)** | Разделённая аудио-дорожка (вокал / аккомпанемент). | [processing.md](processing.md) |
| **Эфир (On-Air)** | Песня в открытом доступе (publish_date истёк). | [publishing.md](publishing.md) |
| **Exclusive** | Песня доступна только по подписке. | [publishing.md](publishing.md) |
| **Self-assign** | Назначение задания на себя редактором. | [editorial.md](editorial.md) |

## Конвенции

- Имя файла: `<context>.md` (kebab-case, существительное в единственном числе).
- Размер: ≤ 3 страницы (≤ 120 строк).
- Шаблон: [`templates/bounded-context.md`](../templates/bounded-context.md).
- Frontmatter: `status`, `slug`, `type: bounded-context`, `related`.

## Связь с другими слоями

- **Реализация**: `architecture/L3-components.md` (где живёт код контекста).
- **Cross-references**: `features/<NNN>.md` (фичи, работающие с контекстом).
- **Drill-down**: `docs/features/*.md` (legacy per-feature документы по конкретным подсистемам).

## Когда добавлять новый bounded context

1. Выделен новый aggregate root (новая доменная сущность) → создать context.
2. Имя должно совпадать с реальным разделением в коде (один AR на context).
3. Если AR работает в 2+ разных доменах — разделить на 2 context'а.
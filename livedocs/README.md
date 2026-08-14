# LiveDocs — корневой манифест

> **LiveDocs** — единый актуальный каталог знаний о проекте Karaoke для
> AI-агентов и разработчиков. Объединяет SDD / DDD / C4 в общем каркасе.

## С чего начать

1. Прочитайте этот файл (назначение, навигация).
2. Откройте [`INDEX.md`](./INDEX.md) — карту слоёв и decision tree.
3. Перейдите в нужный слой по задаче:
   - **Задача про фичу** (что она делает) → `features/`.
   - **Задача про модуль / домен** (что это такое) → `domain/`.
   - **Задача про архитектуру** (из чего система) → `architecture/`.

## Слои LiveDocs

| Слой | Назначение | Каталог | Пример |
|------|------------|---------|--------|
| **SDD** | Сводки фич (≤ 2 стр.) | [`features/`](features/) | [`features/182-editor-self-assign-tasks.md`](features/182-editor-self-assign-tasks.md) |
| **DDD** | Bounded contexts + ubiquitous language | [`domain/`](domain/) | [`domain/catalog.md`](domain/catalog.md) |
| **C4** | Архитектурные диаграммы (L1/L2/L3) | [`architecture/`](architecture/) | [`architecture/L1-system-context.md`](architecture/L1-system-context.md) |
| **Templates** | Шаблоны для новых записей | [`templates/`](templates/) | [`templates/feature-summary.md`](templates/feature-summary.md) |

## Главное правило

**AI-агент при старте сессии читает `livedocs/README.md` + `livedocs/INDEX.md` ПЕРВЫМ.**
Только если в LiveDocs нет нужной информации — лезть в полные спеки
(`specs/NNN-*/spec.md`) или governance/ловушки (`AGENTS.md` Q&A).

## Каноническое написание

- **LiveDocs** (система/каталог, CamelCase, с `s`).
- **LiveDoc** (один документ, CamelCase, без `s`).
- Директории: lowercase (`livedocs/`).
- Заголовки и сущности: CamelCase (`LiveDocs`, `LiveDoc`).

## Конвенции (кратко)

- Имена файлов: kebab-case без нумерации (для `domain/`, `architecture/`).
  Исключение — `features/<NNN-slug>.md`.
- Frontmatter (YAML): `status` (Active/Deprecated/Archived), `slug`, `related`.
- Размер: ≤ 2-3 страницы на LiveDoc.
- Язык: русский (соответствует `AGENTS.md`).

Подробности — [`../docs/livedocs-conventions.md`](../docs/livedocs-conventions.md).

## Процесс обновления

- Изменение кода → в том же PR обновить соответствующий LiveDoc.
- Новая фича → создать `features/<NNN-slug>.md` по шаблону.
- CI-gate: `bash tools/check-livedocs-structure.sh` на каждом PR.

## Валидация

```bash
bash tools/check-livedocs-structure.sh
```

## Drill-down

- Полные спеки фич: [`../specs/`](`../specs/`).
- Governance и ловушки: [`../AGENTS.md`](../AGENTS.md), [`../.specify/memory/constitution.md`](../.specify/memory/constitution.md).
- Per-feature документы: [`../docs/features/`](../docs/features/) (legacy, drill-down).
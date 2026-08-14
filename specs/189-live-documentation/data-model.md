# Data Model: LiveDocs

**Phase**: 1 (design)
**Date**: 2026-08-14
**Branch**: `189-live-documentation`

## Назначение

Этот документ описывает **сущности LiveDocs** — концептуальные единицы каталога.
В отличие от runtime data model (таблицы БД, JSON DTO), LiveDocs — это
**файлы-документы** с фиксированной структурой. Здесь описано:

1. Какие типы сущностей существуют (FeatureSummary, BoundedContext, C4Level, ...).
2. Какие обязательные/опциональные поля у каждого типа (frontmatter + body).
3. Какие отношения между сущностями (related, references).
4. Какие валидационные правила (минимальный/максимальный размер, формат).

Все сущности — файлы `.md` в директории `livedocs/`. Версионируются через git.
Валидируются через `tools/check-livedocs-structure.sh` + (опционально) markdownlint.

---

## 1. Feature Summary (SDD)

**Путь**: `livedocs/features/<NNN-slug>.md`
**Пример**: `livedocs/features/182-editor-self-assign-tasks.md`
**Источник**: `specs/NNN-slug/spec.md` (drill-down)

### Frontmatter (обязательный)

```yaml
---
status: Active | Deprecated | Archived
slug: <NNN-slug>
related:
  - ../specs/<NNN-slug>/spec.md        # Обязательно — ссылка на исходную спеку
  - ../domain/<context>.md             # Опционально — связь с bounded context
  - ../architecture/<topic>.md         # Опционально — связь с архитектурным слоем
---
```

### Body (фиксированная структура)

```markdown
# <Заголовок фичи> (LiveDoc)

> Это LiveDoc-сводка. Drill-down — [specs/<NNN-slug>/spec.md](../../specs/<NNN-slug>/spec.md).

## Что делает
[1-2 абзаца: что делает фича, кому нужна, какой проблемы решает]

## User Stories (краткий список)
- **US1** [заголовок] — [1 строка]
- **US2** [заголовок] — [1 строка]
[≤ 5 user stories, подробности — в исходной спеке]

## Functional Requirements (указатель)
- **FR-NNN**: [1 строка описания]
[≤ 5 ключевых FR, полный список — в исходной спеке]

## Acceptance Criteria
- [ ] AC1: [Given/When/Then — 1 строка]
- [ ] AC2: [Given/When/Then — 1 строка]
[≤ 5 AC, подробности — в исходной спеке]

## Связанные LiveDocs
- Domain: [catalog.md](../domain/catalog.md) | [processing.md](../domain/processing.md) | ...
- Architecture: [L3-components.md](../architecture/L3-components.md) | ...

## Код
- Модуль: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/<package>/`
- Frontend: `webvue3/src/components/<Entity>/`
- API: `POST /api/<endpoint>` ([ApiController.kt:NNN](../../karaoke-web/...))

## История
- Создан: <YYYY-MM-DD>
- Последнее обновление: <YYYY-MM-DD>
```

### Validation rules

| Правило | Проверка | Где |
|---------|----------|-----|
| Frontmatter содержит `status` | `grep -l '^status:' <file>` | bash-скрипт |
| Frontmatter содержит `slug` | `grep -l '^slug:' <file>` | bash-скрипт |
| `related` ссылается на существующий файл | `test -f $(echo $related | sed ...)` | bash-скрипт (опционально) |
| Body содержит H1 | `grep -c '^# ' <file>` ≥ 1 | markdownlint (опционально) |
| Размер ≤ 2 страницы | `wc -l <file>` ≤ 80 | bash-скрипт |

### Relationships

- **Feature Summary → Spec**: обязательная связь 1-к-1 (одна сводка = одна спека).
- **Feature Summary → BoundedContext(s)**: 1-ко-многим (фича может затрагивать
  несколько контекстов; например, фича 182 — catalog + identity + editorial).
- **Feature Summary → C4Level(s)**: 1-ко-многим (фича может жить в нескольких
  архитектурных слоях).

---

## 2. Bounded Context (DDD)

**Путь**: `livedocs/domain/<context>.md`
**Пример**: `livedocs/domain/catalog.md`, `livedocs/domain/processing.md`
**Источник**: DDD-книги (Eric Evans, Vaughn Vernon)

### Frontmatter (обязательный)

```yaml
---
status: Active | Deprecated | Archived
slug: <context>
type: bounded-context
related:
  - ../features/<NNN-slug>.md         # Связь с фичами, работающими в этом контексте
  - ../architecture/L3-components.md   # Опционально — где живёт код контекста
---
```

### Body (фиксированная структура)

```markdown
# Bounded Context: <Context>

> Это LiveDoc для DDD bounded context. Описывает границы домена, ubiquitous language,
> aggregate roots, ключевые инварианты.

## Назначение
[1-2 абзаца: зачем существует этот контекст, какую часть домена закрывает]

## Aggregate Roots
- **<AR1>**: [что это, зачем нужен, ключевые инварианты]
- **<AR2>**: [аналогично]

## Entities
- **<E1>**: [жизненный цикл, identity]
- **<E2>**: [аналогично]

## Value Objects
- **<VO1>**: [что описывает, как сравнивается]

## Domain Events
- **<Event1>**: [когда публикуется, кто консьюмит]
- **<Event2>**: [аналогично]

## Ubiquitous Language (глоссарий)
| Термин | Определение | Пример в коде |
|--------|-------------|----------------|
| <Term1> | <определение> | `<ClassName>`, `<tableName>` |
| <Term2> | <определение> | `<ClassName>` |

## Связанные фичи
- [182-editor-self-assign-tasks.md](../features/182-editor-self-assign-tasks.md)
- [184-approve-status-choice.md](../features/184-approve-status-choice.md)
[≤ 5 фич — остальные через full-text search]

## Связанные LiveDocs
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код
- Модели: `karaoke-app/src/main/kotlin/.../model/<Context>*.kt`
- Сервисы: `karaoke-app/src/main/kotlin/.../service/<Context>*.kt`
- DTO: `karaoke-app/src/main/kotlin/.../dto/<Context>*DTO.kt`
- SQL: `deploy/karaoke-db/<NNN>_<table>.sql`

## История
- Создан: <YYYY-MM-DD>
- Последнее обновление: <YYYY-MM-DD>
```

### Validation rules

| Правило | Проверка | Где |
|---------|----------|-----|
| Frontmatter содержит `type: bounded-context` | `grep '^type: bounded-context' <file>` | bash-скрипт |
| Body содержит секцию `## Aggregate Roots` | `grep '^## Aggregate Roots' <file>` | bash-скрипт |
| Body содержит секцию `## Ubiquitous Language` | `grep '^## Ubiquitous Language' <file>` | bash-скрипт |
| Файл не содержит запрещённых символов | (нет, Markdown — UTF-8) | — |

### Relationships

- **BoundedContext → Feature(s)**: 1-ко-многим (контекст может обслуживать
  несколько фич; одна фича может работать с несколькими контекстами).
- **BoundedContext → Code (L3)**: 1-к-1 (контекст реализован в конкретных
  модулях karaoke-app, см. секцию «Код»).

---

## 3. C4 Level (Architecture)

**Путь**: `livedocs/architecture/L<n>-<topic>.md`
**Примеры**:
- `livedocs/architecture/L1-system-context.md` — система и внешние пользователи.
- `livedocs/architecture/L2-containers.md` — приложения + хранилища.
- `livedocs/architecture/L3-components.md` — компоненты внутри karaoke-app.
- `livedocs/architecture/data-sync.md` — тематический (LOCAL ↔ SERVER).

### Frontmatter (обязательный)

```yaml
---
status: Active | Deprecated | Archived
slug: <topic>
type: c4-level | topic
level: L1 | L2 | L3 | topic
related:
  - ../domain/<context>.md             # Какие контексты охватывает
  - ../features/<NNN-slug>.md          # Какие фичи затрагивает
---
```

### Body (фиксированная структура)

```markdown
# C4 Level <N>: <Заголовок>

> Это LiveDoc для C4 уровня N. Описывает [что показывает уровень — system context
> / containers / components].

## Что показывает
[1 абзац: какие вопросы отвечает эта диаграмма]

## Диаграмма (Mermaid)

\`\`\`mermaid
[C4 diagram in Mermaid]
\`\`\`

## Компоненты / Контейнеры / Системы

### <Имя 1>
- **Назначение**: [1-2 строки]
- **Технология**: [Kotlin/Spring Boot, Vue 3, etc.]
- **Ответственность**: [что делает, за что отвечает]

### <Имя 2>
[аналогично]

## Связи
- **<A> → <B>**: [протокол, формат, частота]
- **<B> → <C>**: [аналогично]

## Связанные LiveDocs
- Domain: [catalog.md](../domain/catalog.md) | ...
- Features: [182-...](../features/182-...) | ...

## История
- Создан: <YYYY-MM-DD>
- Последнее обновление: <YYYY-MM-DD>
```

### Validation rules

| Правило | Проверка | Где |
|---------|----------|-----|
| Frontmatter содержит `type: c4-level` или `type: topic` | `grep '^type: (c4-level|topic)'` | bash-скрипт |
| Body содержит Mermaid-блок | `grep -c '^\`\`\`mermaid' <file>` ≥ 1 | bash-скрипт |
| Файл для L1/L2/L3 содержит `## Что показывает` | `grep '^## Что показывает' <file>` | bash-скрипт |

### Relationships

- **C4Level → BoundedContext(s)**: 1-ко-многим (уровень охватывает несколько контекстов).
- **C4Level → Feature(s)**: 1-ко-многим (фичи реализуются в компонентах/контейнерах).
- **C4 L1 → C4 L2 → C4 L3**: иерархия (drill-down). L1 показывает «кто
  пользуется системой», L2 — «из чего она состоит», L3 — «как устроена внутри».

---

## 4. Manifest (README)

**Путь**:
- `livedocs/README.md` — корневой манифест каталога.
- `livedocs/features/README.md` — манифест слоя features.
- `livedocs/domain/README.md` — манифест слоя domain.
- `livedocs/architecture/README.md` — манифест слоя architecture.
- `livedocs/templates/README.md` — манифест шаблонов.

### Frontmatter (нет)

README — это **короткие навигационные документы**, не LiveDocs. Не имеют
frontmatter (это валидируется bash-скриптом — `head -1 README` не должен
быть `---`).

### Body (короткий, ≤ 30 строк)

```markdown
# <Название слоя> — LiveDocs

> Короткое описание назначения слоя (1-2 предложения).

## Содержимое

| Файл | Тип | Описание |
|------|-----|----------|
| [182-...](182-...) | Feature Summary | ... |
| [catalog.md](catalog.md) | Bounded Context | ... |
| [L1-...](L1-...) | C4 Level | ... |

## Конвенции

[Краткое описание конвенций слоя — именование, формат, ссылки]

## Связь с другими слоями

- **Из features/**: drill-down на `specs/<NNN>/spec.md`.
- **В domain/**: ссылки в frontmatter `related`.
```

### Validation rules

| Правило | Проверка | Где |
|---------|----------|-----|
| Нет frontmatter (`---` в начале) | `head -1 <file> != '---'` | bash-скрипт |
| Размер ≤ 30 строк | `wc -l <file>` ≤ 30 | bash-скрипт (опционально) |
| Body содержит таблицу | `grep -c '^|' <file>` ≥ 1 | markdownlint (опционально) |

---

## 5. Template

**Путь**: `livedocs/templates/<type>-template.md`
**Примеры**:
- `livedocs/templates/feature-summary.md`
- `livedocs/templates/bounded-context.md`
- `livedocs/templates/c4-level-L1.md`
- `livedocs/templates/c4-level-L2.md`
- `livedocs/templates/c4-level-L3.md`

### Frontmatter (нет — это заготовка)

Шаблон — это **заготовка без frontmatter** (чтобы при копировании не дублировать
метаданные). Агент, создавая новый LiveDoc, копирует template и заполняет
frontmatter вручную.

### Body

Содержит **плейсхолдеры** в `<>`-скобках, которые агент заменяет при заполнении.
Пример:

```markdown
# <Заголовок фичи> (LiveDoc)

> Это LiveDoc-сводка. Drill-down — [specs/<NNN-slug>/spec.md](../../specs/<NNN-slug>/spec.md).

## Что делает
[1-2 абзаца]
...
```

### Validation rules

| Правило | Проверка | Где |
|---------|----------|-----|
| Нет frontmatter | `head -1 <file> != '---'` | bash-скрипт |
| Содержит плейсхолдеры `<...>` | `grep -c '<.*>' <file>` ≥ 3 | bash-скрипт (опционально) |

---

## Summary: все сущности LiveDocs

| Сущность | Путь | Frontmatter | Размер | Назначение |
|----------|------|-------------|--------|------------|
| **Feature Summary** | `livedocs/features/<NNN-slug>.md` | да | ≤ 2 стр. | SDD-сводка фичи |
| **Bounded Context** | `livedocs/domain/<context>.md` | да | ≤ 3 стр. | DDD-описание контекста |
| **C4 Level L1/L2/L3** | `livedocs/architecture/L<n>-<topic>.md` | да | ≤ 2 стр. | Архитектурная диаграмма |
| **Topic** | `livedocs/architecture/<topic>.md` | да | ≤ 2 стр. | Тематический архитектурный документ |
| **Manifest (README)** | `livedocs/{,features,domain,architecture,templates}/README.md` | нет | ≤ 30 стр. | Навигация по слою |
| **Template** | `livedocs/templates/<type>-template.md` | нет | любое | Заготовка для нового LiveDoc |

Всего в first slice: ~25-35 файлов (см. `plan.md` → Project Structure).

---

## Cardinality / Count Constraints

Из спеки (FR-001 — FR-018 + SC-006 — SC-008):

| Слой | Минимум | Максимум | Обоснование |
|------|---------|----------|-------------|
| `features/` | ≥ 5 | без лимита | SC-006 (proof of concept — 5 фич) |
| `domain/` | ≥ 5 | без лимита | SC-007 (5 bounded contexts) |
| `architecture/` | ≥ 3 (L1+L2+L3) | без лимита | SC-008 (все 3 уровня обязательны) |
| `templates/` | ≥ 5 | без лимита | D-9 (5 шаблонов) |
| `livedocs/README.md` | 1 | 1 | Единственный корневой манифест |
| `livedocs/INDEX.md` | 1 | 1 | Единственная карта с decision tree |

---

## Identity & Uniqueness Rules

- **Feature Summary**: уникален по `<NNN-slug>` (одна сводка на одну спеку).
  NNN должен существовать в `specs/<NNN>-*/`.
- **Bounded Context**: уникален по `<context>` (kebab-case имя).
  Имя должно совпадать с реальным разделением в коде (один aggregate root
  per context).
- **C4 Level**: уникален по комбинации `<level>-<topic>`.
  L1/L2/L3 — обязательные уровни, topic — произвольные дополнительные.
- **Template**: уникален по `<type>` (feature-summary, bounded-context, c4-level-L1/2/3).

---

## State Transitions

У каждой сущности есть поле `status`:

- **Active** — актуальный документ, описывает текущее состояние.
- **Deprecated** — устаревший, но ещё не удалён (нужно для обратной совместимости
  и для понимания «как было раньше»). В `INDEX.md` показывается с пометкой.
- **Archived** — полностью устаревший, перенесён в `docs/archive/livedocs-<date>/`.
  Удалять из git **НЕ рекомендуется** (история важна).

**Переходы** (кто и когда):

```
Active → Deprecated  → Archived
         ↑
         (любой разработчик может пометить как Deprecated)
         (перевод в Archived — после квартала в Deprecated)
```

Будет реализовано в Pass 2+ (сейчас все документы создаются со статусом `Active`).

---

## Validation Pipeline

```
[ Новый .md файл в livedocs/ ]
        ↓
[ git commit ]
        ↓
[ GitHub Actions: lint.yml ]
        ↓
[ bash tools/check-livedocs-structure.sh ]
   ├─ Существование обязательных файлов
   ├─ Наличие frontmatter (status, slug)
   ├─ Наличие body-секций (## Что делает, ## Aggregate Roots, ...)
   ├─ Наличие Mermaid-блоков (для architecture/)
   ├─ Длина AGENTS.md ≤ 100 строк
   └─ Минимум 5 файлов в features/ и domain/, 3 уровня C4
        ↓
[ exit code 0 → OK; ≠ 0 → блокирует merge ]
```
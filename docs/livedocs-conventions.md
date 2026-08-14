# LiveDocs — конвенции

> Это мета-документ о системе LiveDocs. Сама система — в `../livedocs/`.
> Спецификация фичи: [`../specs/189-live-documentation/spec.md`](../specs/189-live-documentation/spec.md).

## Назначение

**LiveDocs** — единый актуальный каталог знаний о проекте Karaoke для AI-агентов
и разработчиков. Объединяет три подхода в одном каркасе:

| Подход | Что даёт | Артефакт |
|--------|----------|----------|
| **SDD** (Specification-Driven Development) | Фичи как спецификации | `livedocs/features/<NNN-slug>.md` |
| **DDD** (Domain-Driven Design) | Ubiquitous language, bounded contexts | `livedocs/domain/<context>.md` |
| **C4** (Context → Container → Component → Code) | Архитектурные диаграммы | `livedocs/architecture/L<n>-*.md` |

## Структура

```
livedocs/
├── README.md             # Этот манифест (короткий)
├── INDEX.md              # Карта слоёв + decision tree
├── features/             # SDD: сводки фич
├── domain/               # DDD: bounded contexts
├── architecture/         # C4: диаграммы + темы
└── templates/            # Шаблоны для новых записей
```

## Главное правило

**AI-агент при старте сессии читает `livedocs/README.md` и `livedocs/INDEX.md` ПЕРВЫМ.**
Только если в LiveDocs нет нужной информации — лезть в `specs/NNN-*/spec.md`
(полные спеки) или `AGENTS.md` Q&A (governance/ловушки).

## Конвенции

### Именование файлов

- `livedocs/features/<NNN-slug>.md` — фичи (NNN = номер спеки).
- `livedocs/domain/<context>.md` — bounded contexts (kebab-case, без нумерации).
- `livedocs/architecture/L<n>-<topic>.md` — C4 уровни (L1/L2/L3 + topic).
- Исключение: `<NNN-slug>` для фич — формат диктуется нумерацией.

### Frontmatter (YAML)

Каждый LiveDoc (кроме README-манифестов и шаблонов) содержит:

```yaml
---
status: Active | Deprecated | Archived
slug: <kebab-case>
type: feature-summary | bounded-context | c4-level | topic  # опционально
related:                                  # опционально
  - ../domain/catalog.md
---
```

### Размер

- Feature Summary: ≤ 2 страницы (≤ 80 строк).
- Bounded Context: ≤ 3 страницы (≤ 120 строк).
- C4 Level: ≤ 2 страницы (≤ 80 строк).
- Topic: ≤ 3 страницы (≤ 120 строк).
- README-манифест: ≤ 30 строк.

## Процесс обновления

1. **Изменение кода** → в том же PR обновить соответствующий LiveDoc
   (FR-014, US3).
2. **Добавление новой фичи** → создать `livedocs/features/<NNN-slug>.md`
   по шаблону `livedocs/templates/feature-summary.md`.
3. **CI-gate**: `bash tools/check-livedocs-structure.sh` запускается на каждом
   PR, exit ≠ 0 блокирует merge (FR-015).

## Валидация

```bash
bash tools/check-livedocs-structure.sh
```

Проверяет: наличие обязательных файлов, ≥ 5 фич, ≥ 5 contexts, L1+L2+L3,
frontmatter, длину AGENTS.md (≤ 100 строк), интеграцию с CI.

## См. также

- [`../specs/189-live-documentation/spec.md`](../specs/189-live-documentation/spec.md) — спецификация фичи.
- [`../specs/189-live-documentation/data-model.md`](../specs/189-live-documentation/data-model.md) — описание сущностей LiveDocs.
- [`../specs/189-live-documentation/contracts/`](../specs/189-live-documentation/contracts/) — frontmatter schema + шаблоны.
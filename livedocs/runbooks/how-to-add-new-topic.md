# How to: добавить новый topic-документ в `livedocs/architecture/`

## Prerequisites

- Понимание, ЗАЧЕМ создаётся topic (vs ADR / BC / feature).
- Знание связанных LiveDocs (architecture/L1-L3, domain).

## Когда создавать topic-документ

✅ **Создавайте `architecture/<topic>.md`**, если тема:
- **Drill-down для C4** уровня (например, `mlt-pipeline.md` для L3).
- **Конвенция / паттерн** проекта (например, `nginx-conventions.md`,
  `jackson-conventions.md`, `documentation-conventions.md`).
- **Кросс-cut concern** (например, `observability.md`, `cache-invalidation.md`,
  `idempotency.md`).
- **Drill-down для конкретной подсистемы** (например, `dual-db-access.md`,
  `data-sync.md`).

❌ **Не** создавайте, если:
- Это описание фичи → `features/<NNN>.md`.
- Это описание domain → `domain/<context>.md`.
- Это архитектурное **решение** (а не описание) → ADR в `decisions/`.

## Steps

### 1. Создать файл

```bash
cd /path/to/Karaoke
touch "livedocs/architecture/my-topic.md"
# Английское зеркало (опционально):
touch "livedocs-en/my-topic.md"
```

### 2. Frontmatter

```yaml
---
status: Active
slug: my-topic
type: topic
related:
  - ../L3-components.md               # если привязан к конкретному C4 уровню
  - ../domain/processing.md           # если затрагивает BC
  - ../../features/NNN-related-feature.md
---
```

`type: topic` — обязательно (отличает от c4-level, который тоже в `architecture/`).

### 3. Структура

```markdown
# <Тема> (drill-down для ...)

> <One-sentence description>

## Что показывает

<Краткое описание темы. 1-2 абзаца.>

**Когда читать**:
- Буллеты — типичные случаи.

## Диаграмма (Mermaid)

\`\`\`mermaid
flowchart LR
    A[Component A]
    B[Component B]
    A --> B
\`\`\`

## Подразделы

### <API / таблица / паттерн>

### Что МОЖНО / НЕЛЬЗЯ

### Когда что-то идёт не так (таблица troubleshooting)

## Связанные LiveDocs

- <links>

## Код

- <paths>

## История

- Создан: <date>
- Последнее обновление: <date>
```

### 4. Обновить `architecture/README.md`

В таблице «Тематические (drill-down по конкретной теме)» добавить строку:
```markdown
| [my-topic.md](my-topic.md) | <Тема> |
```

### 5. Добавить секцию в CHANGELOG

[`CHANGELOG.md`](../../CHANGELOG.md) — добавить секцию.

### 6. CI + commit

```bash
bash tools/check-livedocs-structure.sh  # 7/7 PASS
git add -A
git commit -m "189-live-documentation: new topic my-topic"
git push origin 189-live-documentation
gh pr create --base master --title "livedocs/architecture/my-topic"
gh pr merge <PR> --merge
```

## Acceptance criteria

- [ ] Frontmatter валиден (status, slug, type: topic, related).
- [ ] Mermaid-блок есть (минимум 1 диаграмма).
- [ ] Размер ≤ 3 стр. (≤ 120 строк).
- [ ] Таблица troubleshooting (если применимо).
- [ ] Раздел «Связанные LiveDocs».
- [ ] `architecture/README.md` — обновлён.
- [ ] `bash tools/check-livedocs-structure.sh` → 7/7 PASS.
- [ ] `bash tools/check-livedocs-cross-links.sh` → 0 broken.

## Примеры хороших topic-документов

- `architecture/mlt-pipeline.md` — drill-down для L3 (рендеринг).
- `architecture/nginx-conventions.md` — конвенция по nginx (User-Agent routing).
- `architecture/observability.md` — кросс-cut concern.
- `architecture/cache-invalidation.md` — паттерн setWebvueProp.

Все они написаны по одному шаблону. Используйте как референс.

## Related

- LiveDocs: [`architecture/README.md`](../../architecture/README.md),
  [`runbooks/how-to-update-livedocs.md`](how-to-update-livedocs.md),
  [`runbooks/how-to-add-new-adr.md`](how-to-add-new-adr.md).
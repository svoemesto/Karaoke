# How to: добавить новый bounded context в `livedocs/domain/`

## Prerequisites

- Понимание DDD (Bounded Contexts, Aggregate Roots, Ubiquitous Language).
- Зачем нужен **отдельный** context (не влезет в существующий).
- Знание агрегатов, событий, vocabulary.

## Когда выделять контекст

✅ Создавайте новый `livedocs/domain/<context>.md`, если:
- **Свой собственный AR** (агрегат root), не пересекающийся с каталогом/processing/identity.
- **Своя Ubiquitous Language** (термины, специфичные для контекста).
- **Свои транзакции** (не shared с другими).
- Пример: `stats.md` (StatBySong + tbl_events), `rendering.md` (drill-down для processing).

❌ **Не** создавайте, если:
- Можно описать как topic в `livedocs/architecture/<topic>.md`.
- Это фича одного AR существующего контекста.

## Steps

### 1. Создать файл

```bash
cd /path/to/Karaoke
cp livedocs/templates/bounded-context.md livedocs/domain/<new-context>.md
# отредактировать
```

### 2. Заполнить шаблон

Используйте [`templates/bounded-context.md`](../../templates/bounded-context.md).

**Обязательные секции**:
- **Aggregate Roots**: список AR с инвариантами.
- **Entities**: остальные сущности (без identity/business-cycle, но важны).
- **Value Objects**: неизменяемые типы (enum'ы, snapshot'ы).
- **Domain Events**: что происходит в контексте.
- **Ubiquitous Language (глоссарий)**: **минимум 5 терминов**.
- **Related LiveDocs**: ссылки на смежные context'ы + features.
- **Architecture** (если применимо): ссылки на L1/L2/L3 и topic-документы.
- **Код**: где в репозитории живёт контекст.

### 3. Frontmatter

```yaml
---
status: Active
slug: <new-context>
type: bounded-context
related:
  - ../domain/<neighbor1>.md
  - ../domain/<neighbor2>.md
  - ../features/<related-feature>.md
  - ../architecture/<topic>.md
---
```

### 4. Обновить `livedocs/domain/README.md`

Добавить строку в таблицу:

```markdown
| [new-context.md](new-context.md) | <Имя BC> | <AR1>, <AR2> | <Ключевые понятия> |
```

### 5. Обновить смежные документы

В existing contexts/features добавьте ссылку на новый BC в `related:`,
если они взаимодействуют.

### 6. Проверить `bash tools/check-livedocs-structure.sh` → 7/7 PASS

```bash
bash tools/check-livedocs-structure.sh
# → Files with frontmatter: NN/NN
# → OK: LiveDocs structure valid (7/7 checks passed)
```

### 7. Commit + push + PR

```bash
git add -A
git commit -m "189-live-documentation: add bounded context <new-context>"
git push origin 189-live-documentation
gh pr create --base master --title "189-live-documentation: bounded context <new-context>"
gh pr merge <PR> --merge
```

## Verification

- [x] В `livedocs/domain/README.md` — новая строка.
- [x] В `livedocs/architecture/README.md` — cross-link (если применимо).
- [x] CI 7/7 PASS.
- [x] Глоссарий содержит **минимум 5** терминов с примерами из кода.
- [x] Все ссылки `../...` валидны.

## Rollback (если что-то не так)

1. Удалить `livedocs/domain/<new-context>.md` (`git rm`).
2. Убрать строку из `livedocs/domain/README.md`.
3. Убрать ссылки из смежных документов.

## Когда НЕ выделять контекст

- AR — просто wrapper вокруг существующего (например, `SongFileMetaInfo`).
- Терминология совпадает с существующим каталогом — лучше добавить как
  раздел в `catalog.md`.
- Только одна фича — опишите её в `features/<NNN>.md`.

## Related

- LiveDocs: [domain/README.md](../../domain/README.md),
  [INDEX.md](../../INDEX.md).
- [ADR-0001](../architecture/decisions/0001-raw-jdbc.md) — почему SQL без ORM.
- Eric Evans «Domain-Driven Design» (книга).
- Vaughn Vernon «Implementing Domain-Driven Design».
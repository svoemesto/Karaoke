# How to: добавить новый ADR в `livedocs/architecture/decisions/`

## Prerequisites

- Понимание контекста решения и альтернатив (см. [Michael Nygard «Documenting
  Architecture Decisions»](http://thinkrelevance.com/blog/2011/11/15/documenting-architecture-decisions))
- Решение **достаточно значимое** (не chore/микро-фикс).
- Знание всех LiveDocs связей (architecture/L1-L3, domain, features).

## Когда создавать ADR

✅ **Создавайте**, если решение:
- Затрагивает ≥ 2 subsystem'ы (например, raw JDBC касается и `karaoke-app`,
  и `karaoke-web`, и sync).
- Может быть пересмотрено в будущем (нужно объяснить потомкам).
- Имеет несколько альтернатив (с обоснованием).
- NON-NEGOTIABLE принцип (закреплено в Constitution).

❌ **Не** создавайте, если:
- Это просто code-style решение (используйте `CONTRIBUTING.md`).
- Это bugfix (используйте `specs/<NNN>/spec.md` или `livedocs/features/<NNN>.md`).
- Это уже покрыто в ADR-0001..0006 (см. существующие).

## Steps

### 1. Создать файл

```bash
cd /path/to/Karaoke
NEXT=$(ls -1 livedocs/architecture/decisions/ | grep -E '^[0-9]{4}-' | tail -1 | cut -d- -f1)
NEXT=$((NEXT + 1))
touch "livedocs/architecture/decisions/${NEXT}-my-decision.md"
# И английскую зеркальную версию:
touch "livedocs-en/decisions/${NEXT}-my-decision.md"
```

### 2. Заполнить шаблон

```markdown
# ADR-NNNN: <Краткий Заголовок>

* **Status**: Accepted | Proposed | Deprecated | Superseded
* **Date**: <YYYY-MM-DD>
* **Deciders**: <команда / человек>
* **Related**: <опционально — ссылки на смежные ADR / LiveDocs>

> **English version**: [livedocs-en/decisions/NNNN-...md](link)

## Context

<Что за проблема? Какие ограничения / требования привели к решению?>

## Decision

<Что именно решено. Чёткий, конкретный commitment.>

## Consequences

### Positive
- Плюсы.

### Negative
- Минусы (явно указываем «мы это приняли»).

### Neutral
- Что меняется нейтрально.

## Alternatives Considered

<Что ещё рассматривали, почему НЕ выбрали. Можно 2-4 альтернативы.>

## References

- <связанные LiveDocs, Constitution, external links>
```

### 3. Обновить `decisions/README.md`

Добавить строку в index-таблицу:
```markdown
| [NNNN](NNNN-my-decision.md) | <Заголовок> | Accepted | <date> |
```

### 4. Синхронизировать английскую версию

Если ваш ADR важен для AI-агентов на английском, обновите
`livedocs-en/decisions/NNNN-my-decision.md` (может быть краткий перевод).

### 5. Обновить `architecture/README.md`

Добавьте ADR в таблицу секции «ADR» в [`README.md`](../../architecture/README.md).

### 6. Добавить секцию в `CHANGELOG.md`

В [`CHANGELOG.md`](../../CHANGELOG.md) добавить новую секцию с кратким
описанием.

### 7. CI + commit

```bash
bash tools/check-livedocs-structure.sh  # 7/7 PASS
bash tools/check-livedocs-cross-links.sh  # 0 broken

git add -A
git commit -m "ADR-NNNN: <заголовок>"
git push origin 189-live-documentation
gh pr create --base master --title "ADR-NNNN: <заголовок>"
gh pr merge <PR> --merge  # БЕЗ --delete-branch (ветка живёт)
```

## Acceptance criteria

- [ ] Статус: `Accepted` (только после merge).
- [ ] Файл существует: `livedocs/architecture/decisions/NNNN-<slug>.md`.
- [ ] Файл существует: `livedocs-en/decisions/NNNN-<slug>.md` (английский перевод).
- [ ] `decisions/README.md` — обновлён.
- [ ] `architecture/README.md` — обновлён.
- [ ] `CHANGELOG.md` — добавлена секция.
- [ ] `bash tools/check-livedocs-structure.sh` → 7/7 PASS.
- [ ] `bash tools/check-livedocs-cross-links.sh` → 0 broken.

## When to update an ADR

- ADR immutable после `Accepted`. Если решение изменилось — создайте
  **новый** ADR, superseding старый (статус = «Superseded by NNNN»).

## Шаблон

См. существующие ADR (`0001-0006`) как примеры. Все они написаны по одному
шаблону (Context → Decision → Consequences → Alternatives → References).

## Related

- LiveDocs: [`decisions/README.md`](../../architecture/decisions/README.md),
  [`architecture/README.md`](../../architecture/README.md).
- Michael Nygard «Documenting Architecture Decisions» — оригинальный паттерн.
- MADR (Markdown ADR) — современный шаблон.
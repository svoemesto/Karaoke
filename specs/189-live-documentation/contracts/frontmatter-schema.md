# Frontmatter Schema (Contract)

**Назначение**: формальная схема YAML frontmatter для всех LiveDoc-файлов
(кроме README-манифестов и шаблонов). Используется `check-livedocs-structure.sh`
для валидации.

## Общая структура

```yaml
---
<field1>: <value1>
<field2>: <value2>
---
```

Обязательные поля: `status`, `slug`. Опциональные: `type`, `related`, `level`.

## Поля

### `status` (обязательное, enum)

Возможные значения:
- `Active` — актуальный документ.
- `Deprecated` — устаревший (но не удалён).
- `Archived` — полностью архивный (перенесён в `docs/archive/livedocs-<date>/`).

Пример: `status: Active`

### `slug` (обязательное, string)

Уникальный kebab-case идентификатор документа. Должен совпадать с именем файла
без расширения `.md`.

Примеры:
- Для файла `livedocs/features/182-editor-self-assign-tasks.md` → `slug: 182-editor-self-assign-tasks`
- Для файла `livedocs/domain/catalog.md` → `slug: catalog`
- Для файла `livedocs/architecture/L1-system-context.md` → `slug: L1-system-context`

### `type` (опциональное, enum)

Тип документа. Возможные значения:
- `feature-summary` — для `livedocs/features/<NNN-slug>.md`.
- `bounded-context` — для `livedocs/domain/<context>.md`.
- `c4-level` — для `livedocs/architecture/L<n>-*.md`.
- `topic` — для `livedocs/architecture/<topic>.md` (не C4 уровень).

Если `type` не указан — bash-скрипт НЕ валидирует тип (считается, что
это feature-summary по умолчанию).

Пример: `type: bounded-context`

### `related` (опциональное, list of strings)

Список путей к связанным LiveDoc-файлам (относительно корня репозитория).
Каждый путь должен существовать в файловой системе (опциональная проверка).

Формат пути:
- `../<другой-слой>/<файл>.md` — относительный путь внутри `livedocs/`.
- `../../specs/<NNN>/spec.md` — путь к исходной спеке (для feature-summary).

Пример:
```yaml
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../../specs/182-editor-self-assign-tasks/spec.md
```

### `level` (опциональное, enum)

Для `c4-level` — обязательное. Возможные значения: `L1`, `L2`, `L3`.

Пример: `level: L1`

## Полные примеры

### Feature Summary

```yaml
---
status: Active
slug: 182-editor-self-assign-tasks
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../domain/editorial.md
  - ../../specs/182-editor-self-assign-tasks/spec.md
---
```

### Bounded Context

```yaml
---
status: Active
slug: catalog
type: bounded-context
related:
  - ../features/182-editor-self-assign-tasks.md
  - ../features/186-zakroma-songs-fast-load.md
  - ../architecture/L3-components.md
---
```

### C4 Level L1

```yaml
---
status: Active
slug: L1-system-context
type: c4-level
level: L1
related:
  - ../domain/catalog.md
  - ../domain/publishing.md
  - ../architecture/L2-containers.md
---
```

## Validation rules (для bash-скрипта)

1. **Каждый .md файл (кроме README и templates) MUST начинаться с `---`**:
   ```bash
   head -1 <file> | grep -q '^---$' || fail
   ```

2. **В пределах первых 10 строк MUST быть `status:`**:
   ```bash
   head -10 <file> | grep -q '^status:' || fail
   ```

3. **`status` MUST быть одним из {Active, Deprecated, Archived}**:
   ```bash
   head -10 <file> | grep '^status:' | awk '{print $2}' | grep -qE '^(Active|Deprecated|Archived)$' || fail
   ```

4. **В пределах первых 15 строк MUST быть `slug:`**:
   ```bash
   head -15 <file> | grep -q '^slug:' || fail
   ```

5. **`slug` MUST быть kebab-case и совпадать с именем файла**:
   ```bash
   expected_slug=$(basename <file> .md)
   head -15 <file> | grep '^slug:' | awk '{print $2}' | grep -qE "^${expected_slug}$" || fail
   ```

Эти правила покрывают 90% случаев. Более сложные проверки (типы, level, related)
добавляются в Pass 2+, если потребуются.
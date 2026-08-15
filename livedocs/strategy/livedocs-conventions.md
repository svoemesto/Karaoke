---
status: Active
slug: livedocs-conventions
related:
  - ../README.md
  - ../INDEX.md
  - ../CHANGELOG.md
  - ../templates/feature-summary.md
---

# LiveDocs — правила именования и процесс обновления

> Соглашения для контрибьюторов (AI-агентов и людей) LiveDocs.
> Создан по **FR-012** спеки 189.

## Именование файлов

### Файлы фич (`livedocs/features/`)

- **Шаблон**: `<NNN>-<slug>.md`
- **NNN**: трёхзначный номер спеки (например `001`, `042`, `188`).
  Допустимо расширение до 4+ цифр (например `1000`).
- **slug**: kebab-case латиницей, ≤ 60 символов.
  Соответствует каталогу `specs/<NNN>-<slug>/spec.md`.
- **Примеры**:
  - `livedocs/features/001-registration-via-vk-id.md`
  - `livedocs/features/164-complete-guest-share-link.md`

### Файлы bounded contexts (`livedocs/domain/`)

- **Шаблон**: `<bounded-context>.md`
- **slug**: kebab-case, описывает **доменную область** (НЕ фичу).
- **Примеры**: `catalog.md`, `processing.md`, `identity.md`, `editorial.md`.

### Файлы архитектуры (`livedocs/architecture/`)

- **Шаблон**: `<topic>.md` или `c4-<level>.md`.
- **slug**: kebab-case, описывает **архитектурный аспект**.
- **Примеры**: `database.md`, `ci-cd-pipeline.md`, `c4-level-l1.md`.

### ADR (`livedocs/architecture/decisions/`)

- **Шаблон**: `NNN-<short-title>.md`
- NNN — порядковый номер ADR в репозитории (например `001`, `042`).
- Состояние: `Proposed`, `Accepted`, `Deprecated`, `Superseded`.

### Runbooks (`livedocs/runbooks/`)

- **Шаблон**: `how-to-<task>.md` или `README.md` (для индекса).
- slug — описание задачи императивом: `how-to-deploy.md`, `how-to-migrate-prod-server.md`.

## Frontmatter (обязателен для всех LiveDoc кроме templates)

```yaml
---
status: Active          # Active | Draft | Deprecated
slug: <file-name>       # БЕЗ префикса NNN
related:                # Список ссылок на связанные LiveDoc
  - ../README.md
  - ../../specs/189-live-documentation/spec.md
---
```

## Структура файла фичи (`livedocs/features/<NNN-slug>.md`)

1. **Frontmatter** (status/slug/related).
2. **H1**: `# <NNN> — <Title> (LiveDoc)`.
3. **Blockquote** со ссылкой на `specs/<NNN-slug>/spec.md` (drill-down).
4. **Что делает** — 1-2 абзаца.
5. **User Stories** (краткий список).
6. **Functional Requirements** (указатель на FR).
7. **Acceptance Criteria**.
8. **Связанные LiveDocs** (Domain, Architecture, др. features).
9. **Код** — пути к .kt/.vue/.js (НЕ копировать!).
10. **История** — даты создания/обновления.

## Процесс обновления

### При добавлении новой фичи (FR-013)

1. Создать `specs/<NNN>-<slug>/spec.md` через `/speckit.specify`.
2. Создать `livedocs/features/<NNN>-<slug>.md` через
   `bash tools/gen-spec-skeleton.sh <NNN>` — автоматически создаётся skeleton.
3. Заполнить skeleton (Что делает, US, FR, AC, Связанные LiveDocs, Код, История).
4. Добавить `related:` (можно через `bash tools/gen-related-suggest.sh <file>`).
5. Локально: `bash tools/check-livedocs-structure.sh && bash tools/check-livedocs-cross-links.sh`.
6. PR → CI → merge.

### При изменении bounded context (FR-014)

В том же PR обновить:

- `livedocs/domain/<context>.md` — если меняется BC.
- `livedocs/features/<NNN>-*.md` — если затронута фича из этого BC.
- `livedocs/CHANGELOG.md` — запись с датой и кратким описанием.

### При изменении C4 уровня (FR-014)

В том же PR обновить:

- `livedocs/architecture/c4-level-l{1,2,3}.md`.
- `livedocs/architecture/<topic>.md` (если изменён конкретный topic).
- `livedocs/CHANGELOG.md`.

### При любом изменении LiveDoc

1. Дописать секцию в `livedocs/CHANGELOG.md` (в том же PR).
2. Обновить `related:` если появились новые связи.
3. Проверить CI (7/7 PASS обязательно).

## Чек-лист перед PR

- [ ] Frontmatter валиден (status, slug, related).
- [ ] Cross-links (`../X.md`, `related:`) — все резолвятся.
- [ ] Размер файла ≤ 200 строк (warning > 150).
- [ ] Нет битых внешних ссылок (lychee strict в CI).
- [ ] `livedocs/CHANGELOG.md` обновлён.
- [ ] Если изменён BC или C4 — обновлены связанные LiveDoc (FR-014).
- [ ] Если добавлена новая фича — создан LiveDoc skeleton (FR-013).

## Инструменты

| Скрипт | Назначение |
|--------|-----------|
| `bash tools/check-livedocs-structure.sh` | Структура (7 проверок) |
| `bash tools/check-livedocs-cross-links.sh` | Cross-links valid |
| `bash tools/check-livedocs-external-links.sh` | Внешние https:// |
| `bash tools/check-livedocs-coverage.sh` | Покрытие (7 проверок) |
| `bash tools/gen-related-suggest.sh <file>` | Подбор `related:` |
| `bash tools/extract-kdoc-refs.sh <file>` | Извлечение `@see` |
| `bash tools/gen-spec-skeleton.sh <NNN>` | Skeleton по spec.md |

## История

- **Создан**: 2026-08-15 (Pass 2+ спеки 189, FR-012).
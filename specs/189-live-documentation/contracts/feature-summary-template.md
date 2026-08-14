# Feature Summary Template (Contract)

**Назначение**: компактный шаблон для `livedocs/features/<NNN-slug>.md`.
Полная версия шаблона — в `livedocs/templates/feature-summary.md` (после
создания LiveDocs).

## Frontmatter

```yaml
---
status: Active
slug: <NNN-slug>
related:
  - ../../specs/<NNN-slug>/spec.md
  - ../domain/<context>.md
---
```

## Body

```markdown
# <Заголовок фичи> (LiveDoc)

> Это LiveDoc-сводка. Drill-down — [specs/<NNN-slug>/spec.md](../../specs/<NNN-slug>/spec.md).

## Что делает

[1-2 абзаца: что делает фича, кому нужна, какой проблемы решает]

## User Stories (краткий список)

- **US1** [заголовок] — [1 строка сути]
- **US2** [заголовок] — [1 строка сути]
- **US3** [заголовок] — [1 строка сути]

## Functional Requirements (указатель)

- **FR-001**: [1 строка описания]
- **FR-002**: [1 строка описания]

## Acceptance Criteria

- [ ] AC1: [Given ...] When ... Then ...
- [ ] AC2: [Given ...] When ... Then ...

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md)
- Architecture: [L3-components.md](../architecture/L3-components.md)

## Код

- Модуль: `karaoke-app/src/main/kotlin/.../<package>/`
- Frontend: `webvue3/src/components/<Entity>/`
- API: `POST /api/<endpoint>` ([ApiController.kt](../../karaoke-web/...))

## История

- Создан: <YYYY-MM-DD>
- Последнее обновление: <YYYY-MM-DD>
```

## Конвенции

- **Размер**: ≤ 2 страницы (≤ 80 строк).
- **Стиль**: русский язык, без эмодзи (соответствует `AGENTS.md`).
- **Ссылки**: относительные пути (`../<слой>/<файл>.md`).
- **Обязательные секции**: «Что делает», «Связанные LiveDocs», «История».
- **Опциональные секции**: «User Stories», «Functional Requirements»,
  «Acceptance Criteria», «Код» (зависит от того, насколько полная нужна сводка).

## Validation

Будет валидироваться через `tools/check-livedocs-structure.sh`:

- Наличие frontmatter с `status` и `slug`.
- Наличие секции `## Что делает`.
- Наличие секции `## Связанные LiveDocs`.
- Наличие секции `## История`.
- Размер ≤ 80 строк.
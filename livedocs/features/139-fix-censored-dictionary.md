---
status: Active
slug: 139-fix-censored-dictionary
related:
  - ../domain/publishing.md
  - ../features/140-fix-zakroma-censored-database.md
  - ../features/141-fix-censored-web-storage-globals.md
  - ../../specs/139-fix-censored-dictionary/spec.md
  - ../../archive/docs/features/dictionaries.md
---

# 139 — Цензурирование {songNameCensored} на проде (LiveDoc)

> Drill-down — [specs/139-fix-censored-dictionary/spec.md](../../specs/139-fix-censored-dictionary/spec.md).

## Что делает

На серверной части авто-публикации (Telegram/ВК/новости) плейсхолдер
`{songNameCensored}` не работал — название появлялось **as-is** с матом.

**Корневая причина**: словари должны читаться из БД (`tbl_dictionaries`),
однако на `karaoke-web` (проде без `karaoke-app` бина) обращение к
`String.censored()` падало с `IllegalStateException: Property APP_WORK_ON_SERVER
should be initialized` → ловилось как общее исключение → возврат исходной
строки без цензурирования.

**Фикс** (в связке с `140`, `141`):
- `String.censored()` требует явный `database` параметр.
- Глобалы `KSS_APP`/`SAC_APP`/`APP_WORK_*` явно пробрасываются через DI.
- Регексп — Unicode (`\p{L}\p{N}`).
- Тест покрытия словаря.

## User Stories (краткий список)

- **US1** (P1): Цензурированное название реально публикуется без мата.

## Functional Requirements (указатель)

- **FR-001**: Плейсхолдер `{songNameCensored}` в шаблоне новости → цензурированное имя.
- **FR-002**: Добавить слово `нах[у]й` в словарь → публикация содержит `нах*й`.
- **FR-003**: Юнит-тесты покрывают словарь + регексп + интеграция.

## Acceptance Criteria

- [ ] **AC1**: Добавить `нах[у]й` → публикация содержит `нах*й` (Telegram/ВК/новость).
- [ ] **AC2**: На dev `karaoke-web` (без `karaoke-app`) `censored()` тоже работает.
- [ ] **AC3**: Тесты проходят.

## Связанные LiveDocs

- Domain: [publishing.md](../domain/publishing.md) (news/auto-publish)
- Feature: [140-fix-zakroma-censored-database.md](../features/140-fix-zakroma-censored-database.md) (root cause), [141-fix-censored-web-storage-globals.md](../features/141-fix-censored-web-storage-globals.md) (DI)
- Specs: `089-auto-news-song-release` (news templating)

## Код

- Backend: `karaoke-app/.../service/NewsService.kt` — обработка `{songNameCensored}`
- Backend: `karaoke-app/.../Extentions.kt:210` — `censored(text, database, dict)` — без дефолтов-глобалов
- Backend: `karaoke-app/.../service/Dictionary.kt:23` — DI для `storageService`
- Tests: `karaoke-app/src/test/.../CensoredTest.kt`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
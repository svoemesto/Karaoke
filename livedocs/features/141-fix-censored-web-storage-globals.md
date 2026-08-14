---
status: Active
slug: 141-fix-censored-web-storage-globals
related:
  - ../features/139-fix-censored-dictionary.md
  - ../features/140-fix-zakroma-censored-database.md
  - ../architecture/L3-components.md
  - ../architecture/censoring.md
  - ../domain/publishing.md
  - ../../specs/141-fix-censored-web-storage-globals/spec.md
---

# 141 — Цензурирование на karaoke-web: глобалы + Unicode regex (LiveDoc)

> Drill-down — [specs/141-fix-censored-web-storage-globals/spec.md](../../specs/141-fix-censored-web-storage-globals/spec.md).

## Что делает

После `140-fix-zakroma-censored-database` Закрома перестали падать, но словарь
«Censored» возвращался **пустым** (с `lateinit property KSS_APP has not been
initialized`) и цензурирование тихо деградировало.

**Два независимых бага**:

1. **`KSS_APP`/`SAC_APP`/`APP_WORK_*` не инициализированы в `karaoke-web`**
   (на проде работает только `karaoke-web`, без `KaraokeAppService` модуля
   `karaoke-app`, который их инициализирует). `Dictionary.kt:23` использует
   `storageService: KaraokeStorageService = KSS_APP` — первое обращение падало
   с `UninitializedPropertyAccessException`, `TextFileDictionary.dict:60` тихо
   ловил и возвращал пустой список.

2. **Unicode regex** — словарь может содержать русские символы
   (`нах[у]й`), нужно `\p{L}\p{N}` (Unicode letter/number) вместо `[A-Za-z0-9]`.

**Фикс**: явное пробрасывание `storageService` через DI; регексп Unicode.

## User Stories (краткий список)

- **US1** (P1): Словарь «Censored» НЕ пустой на karaoke-web (проде).
- **US2** (P1): Цензурирование реально работает на публичных endpoint'ах (`/zakroma`, новости).

## Functional Requirements (указатель)

- **FR-001**: Явное пробрасывание `storageService` через DI (или @Lazy-инициализация).
- **FR-002**: Регексп `\p{L}\p{N}` для токенизации.
- **FR-003**: Покрытие юнит-тестами обоих сценариев.

## Acceptance Criteria

- [ ] **AC1**: `/api/public/zakroma` — словарь Censored не пустой (видно в логах, при init SUCCESS).
- [ ] **AC2**: Тест: добавить слово `нах[у]й` → публикация содержит `нах*й`.
- [ ] **AC3**: Юнит-тест на `\p{L}\p{N}` (русские/китайские/emoji символы).

## Связанные LiveDocs

- Feature: [139-fix-censored-dictionary.md](../features/139-fix-censored-dictionary.md) (предыдущая), [140-fix-zakroma-censored-database.md](../features/140-fix-zakroma-censored-database.md)
- Architecture: [L3-components.md](../architecture/L3-components.md) (storageService wiring), [censoring.md](../architecture/censoring.md) (общий паттерн)
- Domain: [publishing.md](../domain/publishing.md) (публикации, где применяется цензурирование)

## Код

- Backend: `karaoke-app/.../service/Dictionary.kt:23` — убрать дефолт `= KSS_APP`, сделать `required`
- Backend: `karaoke-app/.../service/TextFileDictionary.kt:60` — убрать catch-all (throw)
- Backend: `karaoke-web/.../config/KaraokeWebConfig.kt` — DI для `storageService`
- Backend: `karaoke-app/.../Extentions.kt:210` — Unicode regex `\p{L}\p{N}` в `censored()`
- Tests: `karaoke-app/.../test/.../DictionaryTest.kt`, `CensoredTest.kt`

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
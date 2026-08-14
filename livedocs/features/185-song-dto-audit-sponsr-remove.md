---
status: Active
slug: 185-song-dto-audit-sponsr-remove
related:
  - ../domain/catalog.md
  - ../domain/identity.md
  - ../architecture/jackson-conventions.md
  - ../../specs/185-song-dto-audit-sponsr-remove/spec.md
---

# 185 — Аудит Song DTO, удаление спонсорских полей (LiveDoc)

> Drill-down — [specs/185-song-dto-audit-sponsr-remove/spec.md](../../specs/185-song-dto-audit-sponsr-remove/spec.md).

## Что делает

Аудит и очистка `SongDTO` / `SongPublicDTO`: удаление legacy полей
(`sponsr_*`), которые больше не используются. Аналогичные фичи уже сделаны для
`AuthorDTO` (PR #48-#49, см. Q&A «Jackson `is`-prefix в `AGENTS.md`»).

**Ключевая ловушка**: Jackson по умолчанию отбрасывает префикс `is` в Kotlin
boolean-полях (`isSpecialOrder` → JSON `specialOrder`). Без `@JsonProperty`
фронт получает `undefined`, форма не сохраняется. Все boolean-поля DTO должны
иметь `@JsonProperty("isOriginalName")`.

## User Stories (краткий список)

- **US1**: Админ открывает редактирование песни → не видит полей `sponsr_*` (они удалены из UI).
- **US2**: Поле `isSpecialOrder` корректно сохраняется через JSON API (с `@JsonProperty`).

## Functional Requirements (указатель)

- **FR-001**: Удалить `sponsr_*` поля из `SongDTO` (не нужны больше).
- **FR-002**: Boolean-поля `SongDTO` / `SongPublicDTO` MUST иметь `@JsonProperty`.
- **FR-003**: Никаких регрессий в API (поля, которые реально используются, не трогать).

## Acceptance Criteria

- [ ] **AC1**: `sponsr_*` поля отсутствуют в `SongDTO`.
- [ ] **AC2**: Boolean-поле `isActive` (или аналогичное) корректно round-trip'ит через JSON.
- [ ] **AC3**: pre-existing boolean-поля в `AuthorDTO` сохраняют поведение после очистки (регрессионный тест).
- [ ] **AC4**: Обновить `docs/features/jackson-conventions.md` если найдены новые кейсы.

## Связанные LiveDocs

- Domain: [catalog.md](../domain/catalog.md)
- Architecture: [jackson-conventions.md](../architecture/jackson-conventions.md) — главный reference по `is`-prefix.

## Код

- DTO: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/dto/SongDTO.kt`, `SongPublicDTO.kt`
- Тесты: ручные + `bash tools/check-jsdoc-coverage.sh karaoke-public`
- Исторический контекст: PR #48-#49 (AuthorDTO фикс)

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
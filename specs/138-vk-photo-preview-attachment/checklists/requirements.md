# Specification Quality Checklist: Надёжное превью публикации ВК через прикрепление обложки фото

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-04
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Спецификация описывает решение через `photos.getWallUploadServer` + `photos.saveWallPhoto` + `attachments=photo<owner>_<id>` в `wall.post`. Это **надёжный** способ: VK берёт фото из API, не парся URL.
- Ключевой технический факт: user-token с scope `photos` уже настроен через Implicit Flow (02.08.2026, `ApiController.kt:7065` — scopes `video,photos,wall,offline`). Спецификация 123 от 03.08.2026 сделала неверный вывод о необходимости модерации — на тот момент user-token ещё не был настроен.
- Fallback через `docs.*` методы предусмотрен для случаев потери scope `photos` в user-token.
- Размер PNG расширяется с 537×240 до 1200×630 (стандарт Open Graph, рекомендуемый VK).
- Существующие инварианты (`specs/121-vk-news-auto-publish`, `specs/130-vk-preview-generation`) сохраняются: идемпотентность по `Song.idVk`, process-local lock, rate-limit, retry, прогрев PNG.
- После проверки готов к `/speckit.clarify` или `/speckit.plan`.
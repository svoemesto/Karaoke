# Specification Quality Checklist: Шаблоны автоматических новостей сайта

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-03
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

- Спецификация описывает третью вкладку/платформу в существующем
  компоненте `PublishTemplatesView.vue` по образцу ВК/Telegram.
- Ключевое отличие: шаблон состоит из двух полей (`title`/`body`), а не
  одного `caption` — отражает структуру `tbl_news`.
- Хранение шаблонов (Clarification Q1, 2026-08-03): `tbl_public_settings`
  (Postgres) — единый источник истины, читается и с admin, и с прода
  через JDBC. `KaraokeProperties`-файл для этих 4 ключей НЕ
  используется (он недоступен на проде, где рендерятся новости).
- Набор плейсхолдеров (Clarification Q2, 2026-08-03): гибрид —
  составные `{albumYearSuffix}`, `{bodyDetails}` (инкапсулируют
  существующие хелперы, byte-идентичность дефолтов) + расширенный
  набор granular ВК/Telegram: `{author}`, `{songName}`,
  `{songNameCensored}`, `{year}`, `{album}`, `{link}`, `{id}`,
  `{newsBody}`, `{descriptionHeader}`, `{descriptionFooter}`,
  `{description}`, `{descriptionWithTimecodes}`. Маркер `{demoVideo}`
  НЕ включён.
- Заводские дефолты совпадают с текущими хардкод-формулировками
  (`SongReleaseAnnouncementService`), чтобы выпуск фичи был
  прозрачным (FR-010, FR-015, SC-002).
- Все пункты чеклиста проходят — спецификация готова к `/speckit.plan`.
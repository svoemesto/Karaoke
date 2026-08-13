# Specification Quality Checklist: SEO-HTML вместо генерации PNG для ботов

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-13
**Feature**: [spec.md](./spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
  - Спека упоминает конкретные файлы Kotlin (`PublicOgSongController.kt`) и
    nginx-конфиг (`80to8897`) только как точки изменения и для ссылок — это
    не implementation details, а contract существующего кода.
- [x] Focused on user value and business needs
  - Главная ценность — убрать дорогую генерацию PNG для ботов и дать им
    больше структурированной информации для индексации (User Story 1).
- [x] Written for non-technical stakeholders
  - User Stories описаны в терминах «бот делает запрос» / «оператор видит
    в логах» / «сниппет формируется в Telegram», а не «endpoint вызывает
    BufferedImage.createGraphics()».
- [x] All mandatory sections completed
  - User Scenarios & Testing ✓, Requirements ✓, Success Criteria ✓,
    Assumptions ✓, Key Entities ✓.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
  - Все спорные моменты разрешены через informed defaults (см. Assumptions):
    FR-009 оставляет song-vk-image endpoint для обратной совместимости;
    JSON-LD выбран `MusicRecording`; fallback обложки — `KARAOKE_LOGO.png`.
- [x] Requirements are testable and unambiguous
  - FR-001..FR-015 — каждый проверяем (через curl, grep по логам, schema
    validator, view-source).
- [x] Success criteria are measurable
  - SC-001 (< 100 мс TTFB), SC-002 (0 обращений к song-vk-image в логах),
    SC-003 (нет ошибок Schema.org), SC-004..SC-007 — все измеримы.
- [x] Success criteria are technology-agnostic (no implementation details)
  - SC говорит про TTFB, логи nginx, Google Search Console — это наблюдаемые
    результаты, а не «Spring Boot endpoint» / «JSON-LD библиотека».
- [x] All acceptance scenarios are defined
  - 4 User Story × 2–4 сценария = 12 acceptance scenarios (см. spec.md).
- [x] Edge cases are identified
  - 6 edge cases в секции «Edge Cases» (id=0, очень большой id, длинный
    текст, nginx-502, не-Yandex-боты, SKIP+другие теги, параллельные
    запросы).
- [x] Scope is clearly bounded
  - FR-009 явно говорит, что song-vk-image НЕ удаляется; FR-012 — что
    nginx-конфиг НЕ меняется; User Story 4 P3 (feature-flag) — отдельный
    backlog, не в скоупе первой версии.
- [x] Dependencies and assumptions identified
  - Список User-Agent'ов в nginx (Assumption); путь к картинке альбома в
    MinIO (Assumption); формат PNG 400×400 (Assumption); nginx-конфиг
    неизменен (FR-012).

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
  - FR-001..FR-015 связаны со сценариями в User Stories и Success Criteria.
- [x] User scenarios cover primary flows
  - US1 (поисковый бот индексирует), US2 (соцсети формируют сниппет),
    US3 (крайние случаи), US4 (отключение при необходимости) — primary
    flow покрыт.
- [x] Feature meets measurable outcomes defined in Success Criteria
  - SC-001..SC-007 — каждый проверяем через curl / grep / schema validator.
- [x] No implementation details leak into specification
  - Раздел «Связанные документы» ссылается на конкретные файлы только как
    точки изменения, не описывая внутренности реализации.

## Notes

- Спека готова к `/speckit.plan` без дополнительных уточнений.
- Решения, которые можно пересмотреть на этапе планирования (если появятся
  новые обстоятельства):
  - JSON-LD тип (`MusicRecording` vs `Song`) — выбран `MusicRecording` как
    более специализированный; миграция тривиальна.
  - Оставить или удалить `/api/public/song-vk-image/{id}` — выбрано
    «оставить для обратной совместимости»; можно удалить отдельным
    изменением после аудита ссылок.
  - Дефолтный логотип (`KARAOKE_LOGO.png`) — согласован с текущим
    логотипом сайта; точное местоположение в MinIO уточняется на этапе
    планирования.
- Спека не вводит новых миграций БД и не требует изменений в nginx-конфиге
  — это сознательное ограничение для минимального blast radius.

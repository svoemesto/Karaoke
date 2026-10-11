# Specification Quality Checklist: Свой Docker-registry на sm-karaoke.ru

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-11
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

**Итерация 2 (2026-10-11, после ответов владельца) — все пункты пройдены.**

Оба маркера закрыты словом владельца и внесены в спеку как требования:

1. Как сборка получает базовые образы → указание нашего registry в описаниях
   сборки (**FR-017**), с обязательным расширением guard (**FR-014**, **SC-009**).
2. Namespace для перенесённых сторонних образов → служебный namespace с сохранением
   исходного пути (**FR-018**).

Раздел «Открытые вопросы» заменён на «Решённые уточнения» с записью обоих решений и
отклонённых альтернатив — чтобы через месяц не пришлось выяснять заново, почему
выбран именно этот путь.

**Итерация 1 (2026-10-11, после написания спеки).**

Единственный незакрытый пункт — `No [NEEDS CLARIFICATION] markers remain`: в спеке
осознанно оставлены **2 маркера** из допустимых трёх. Оба вынесены в раздел
«Открытые вопросы» и выносятся владельцу:

1. Как admin-машина получает базовые образы **при сборке** — этот путь карта
   решений не покрыла; он выяснился при написании спеки.
2. Namespace для перенесённых сторонних образов.

Оба маркера значимо влияют на объём работ (первый — на то, закрыт ли путь к
внешнему реестру полностью или только наполовину; второй — на схему имён и,
через неё, на модель доступа), и разумного умолчания без слова владельца нет.

**Осознанные отступления, зафиксированные честно:**

- `FR-014` и `SC-009` называют конкретный guard проекта
  (`tools/check-docker-image-tags.sh`) и его текущий шаблон сопоставления. Это
  единственное место, где спека касается внутренностей. Оправдано тем, что здесь
  описан **регресс защиты**: при смене имён образов guard перестаёт видеть
  запрещённые теги молча — то есть правило R-04/R-05 перестаёт работать, оставаясь
  «зелёным». Убрать это из требований значило бы потерять находку pre-flight.
- Спека не называет выбранную технологию registry: она зафиксирована как вход
  (решение владельца) и относится к плану, а не к спецификации.

**Терминологическая развязка.** ADR
[`local-0003-shared-minio-image-cache.md`](../../../knowledge/adr/local-0003-shared-minio-image-cache.md)
уже использует слово «образ» для медиа-изображений. Спека вводит явный термин
«контейнерный образ» в разделе Key Entities и повторяет развязку в Assumptions.
Существующий ADR не переписывается.

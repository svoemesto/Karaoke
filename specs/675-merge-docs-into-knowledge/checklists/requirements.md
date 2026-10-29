# Specification Quality Checklist: Слияние слоя `docs/` в `knowledge/` и запрет параллельной документации

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-29
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — *для этой фичи «реализация» = состояние репозитория (какие файлы где лежат, какие проверки что требуют); спека формулирует проверяемые состояния, а не пошаговый рецепт правок. Порядок работ и названия скриптов упомянуты только там, где это часть контракта (путь per-feature документа, целевой путь генератора, проверка BR-кодов), и вынесены из требований в Notes/Assumptions.*
- [x] Focused on user value and business needs — ценность: один слой документации, невозможность возврата параллельного слоя, отсутствие потерь (BR-коды), объяснимость старых путей.
- [x] Written for non-technical stakeholders — аудитория спеки определена явно: агенты и сопровождающие проекта, а не читатели кода.
- [x] All mandatory sections completed — OpenProject Tracking, Knowledge References, User Scenarios & Testing, Requirements, Success Criteria, Assumptions.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — маркеров 0; все решения, принятые владельцем 2026-10-29, перенесены в требования как проверяемые утверждения (FR-001…FR-024).
- [x] Requirements are testable and unambiguous — каждое FR проверяется конкретным гейтом или сверкой (перечень гейтов — в FR-012…FR-015 и в критерии приёмки KARAOKE-332).
- [x] Success criteria are measurable — 11 критериев с числами (0 файлов, 510/510 кодов, 26/26 документов, 73/73 учтённых, 10/10 решений).
- [x] Success criteria are technology-agnostic (no implementation details) — *критерии задают наблюдаемые состояния репозитория; названия проверок приведены как «чем измеряется», а не как «как реализовать». Единственное исключение — FR-012/FR-013: сам guard является требованием продукта (запрет и его симметричность в обеих копиях CI-конфигурации), а не выбором реализации.*
- [x] All acceptance scenarios are defined — 5 историй, 13 сценариев (Given/When/Then).
- [x] Edge cases are identified — 10 граничных случаев: глубина относительных ссылок, генерируемый API-вывод, рантайм-копия скилла, рассинхрон двух копий CI, архивные per-feature, BR-коды без зеркала, уже битые ссылки, `specs/` (только md-ссылки), `livedocs/` (каталога нет), сравнительные таблицы в скиллах.
- [x] Scope is clearly bounded — Out of scope (архив, `livedocs/`, сравнительные таблицы, генераторные артефакты) вынесен в Assumptions; вторая волна (дочистка) объявлена отдельной задачей.
- [x] Dependencies and assumptions identified — 7 допущений: решения карты как контракт, Plane как трекер, feature-ветка, read-only инфраструктура команды, операционная синхронизация рантайм-копий, границы второй волны.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001…FR-024 связаны со SC-001…SC-011 и критерием завершения из KARAOKE-332 (12 пунктов).
- [x] User scenarios cover primary flows — покрыты: поиск документации в одном месте (P1), защита от возврата параллельного слоя (P1), обновление per-feature документа при правке кода (P2), отсутствие потерь в реестре аудита (P2), объяснимость старых путей (P3).
- [x] Feature meets measurable outcomes defined in Success Criteria — да; обратная прослеживаемость FR ↔ SC зафиксирована в таблице карты переноса.
- [x] No implementation details leak into specification — детали реализации (скрипты переписывания, порядок коммитов, номера строк инструментов) вынесены в карту решений KARAOKE-323 и в Notes; спека содержит только проверяемые требования.

## Notes

- **Источник требований** — wayfinder-карта [KARAOKE-323](https://tracker.sm-karaoke.ru/svoemesto/projects/KARAOKE/issues/323): 10 решённых тикетов-решений (KARAOKE-324…KARAOKE-333). Спека не переоткрывает эти решения; расхождения со картой считаются дефектом спеки, а не предметом её правки.
- **Риск, зафиксированный в спеке:** «одна атомарная волна» (FR-023) — длина ревью компенсируется порядком коммитов внутри PR, а не разбиением на волны (см. KARAOKE-332: расхождение состояний между волнами дороже длины ревью).
- **Проверка issue-link** (`tools/check-spec-issue-link.py`): секция `## OpenProject Tracking` присутствует, поля `Issue ID` (`KARAOKE-334`), `Title`, `Workflow` заполнены, обязательные команды `tracker.sh claim-issue` / `add-comment` / `mark-review` присутствуют.
- **Перед `/speckit-plan`**: спека уходит владельцу на слово. Правки FR/SC — только по слову владельца (спека — контракт).

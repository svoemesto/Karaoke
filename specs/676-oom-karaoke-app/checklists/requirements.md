# Specification Quality Checklist: OOM в karaoke-app — конечная раскладка публикаций и превентивные меры

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-10-30
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

**Проверка 2026-10-30 (после ответов владельца и сверки с research).**

- **3 × [NEEDS CLARIFICATION] закрыты решениями владельца от 2026-10-30** (канал доставки алерта; значения
  лимитов и пороги; политика хранения дампов) — решения зафиксированы в § «Решения владельца» и в тексте
  FR-007/FR-011/FR-015/FR-018. Спека готова к `/speckit-plan`.
- **«No implementation details»** — помечено как пройденное с оговоркой: по доменной конвенции репозитория
  спека обязана указывать файлы и точки входа как «указатели» (этого требует шаблон — раздел Knowledge
  References). Поведенческий контракт (FR/SC) от реализации не зависит; стек, библиотеки и внутренние
  структуры не предопределяются, кроме уже существующих в проекте (MonitorCheck, SLF4J, docker-compose).
- **«Written for non-technical stakeholders»** — помечено как пройденное с оговоркой: задача инфраструктурная,
  аудитория спеки — владелец проекта и инженеры; пользовательские сценарии описаны в терминах наблюдаемого
  поведения («страница открывается», «хост не деградирует», «алерт виден до OOM»), а не в терминах кода.
- **Объём спеки скорректирован по факту**: первопричина (бесконечный цикл) **уже починена в master**
  (коммит `4d643554`, 2026-10-24, BR-188). Спека не переписывает починку — она закрепляет её инвариантом
  и регрессионным тестом (FR-001…FR-005) и покрывает невыполненное: лимит памяти контейнера (FR-006…FR-008),
  дамп heap при OOM (FR-009…FR-013), мониторинг heap (FR-014…FR-019), базу знаний (FR-020…FR-021).
  Это зафиксировано в § «Что уже сделано (проверено по коду, а не по описанию задачи)».
- **Расхождение с описанием задачи** (не ошибка спеки, а уточнение): в задаче строки бага указаны как
  `Publication.kt:1541-1558` и `:1604-1621`, а место OOM — `:1547`. В master эти строки заняты кодом разбора
  дат публикации (BR-191) — файл менялся после инцидента; актуальные точки: `popPublicationStacks`
  (`Publication.kt:2056`) и её вызовы (`:1733`, `:1789`).
- **Исправлено по итогам research** (495 строк, sha256 `853a95e3…`, 10 расхождений): (1) регрессионные
  тесты на починку **есть** (`audit/PkgS212BehaviorTest.kt:43-65`, `audit/PkgS212SourceContractsTest.kt:33-38`)
  — в спеке утверждение «теста нет» снято, FR-004 переформулирован (покрыт вспомогательный уровень, не
  уровень вызывающих); (2) `karaoke-app` развёрнут на **admin-машине `nsa-i9`**, а не на прод-хосте —
  FR-006…FR-011 перепривязаны на `deploy/docker-compose-app.yml`; (3) все три легаси-точки входа **без
  авторизации** и только одна реально вызывается из UI — вынесено в «Известные ограничения» и в Out of scope
  (предложена отдельная задача); (4) доставка алертов для `karaoke-app` — журнал + SSE + `/api/monitor/alerts`
  (ADR `local-0013` относится к infra-серверу — атрибуция исправлена); (5) фактический `-Xmx8g` не отслеживается
  git → FR-008 теперь требует фиксировать пару «heap ↔ предел» в репозитории; (6) перечень документов базы
  знаний расширен до 12 (FR-020).

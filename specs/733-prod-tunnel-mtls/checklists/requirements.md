# Specification Quality Checklist: Постоянный туннель админ-машина→прод + mTLS к прод-БД

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-09
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs) — спека описывает ЧТО и ЗАЧЕМ; конкретные инструменты (WireGuard, Postgres) названы как предмет решения владельца, а не как обязательная реализация; формат строки лога и имена переменных вынесены в «предположения» и в базу знаний.
- [x] Focused on user value and business needs — ценность: конвейер не встаёт при смене адреса; доступ к данным не зависит от IP.
- [x] Written for non-technical stakeholders — сценарии описаны в терминах «владелец/дежурный/конвейер», не «настроить wg-quick».
- [x] All mandatory sections completed — Tracking, Knowledge References, User Scenarios, Requirements, Success Criteria, Assumptions, Out of Scope.

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — владелец ответил на развилки: формат работ (через спеку, 2026-11-09) и судьба HTTP-каналов админки (внутрь туннеля, 2026-11-09 — уточнение после первой редакции спеки).
- [x] Requirements are testable and unambiguous — каждое FR проверяемо наблюдением (конфиг, лог, попытка подключения, снимок конфигурации).
- [x] Success criteria are measurable — SC-001…SC-008 с числами (0 правок, ≤60 c, ≤15 мин, 100% отклонений).
- [x] Success criteria are technology-agnostic (no implementation details) — SC не называют инструментов, кроме наблюдаемых артефактов (конфигурация, лог, попытка подключения).
- [x] All acceptance scenarios are defined — 5 пользовательских историй, 14 acceptance-сценариев, 10 edge cases.
- [x] Edge cases are identified — UDP-блокировка, конфликт подсети, просрочка сертификата, поведение автоматики allow-list в режиме туннеля, перезагрузки хоста и контейнера.
- [x] Scope is clearly bounded — раздел «Out of Scope» (вариант B, HTTP-каналы, MinIO-mTLS, VPN-стек).
- [x] Dependencies and assumptions identified — раздел «Assumptions» (9 пунктов) + FR-010/FR-013.

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria — FR-001…FR-026 связаны со сценариями и SC.
- [x] User scenarios cover primary flows — P1: смена адреса, защита БД, админские HTTP-операции в туннеле; P2: закрытие портов; P3: диагностика.
- [x] Feature meets measurable outcomes defined in Success Criteria — SC-001…SC-003 покрывают боль (смена адреса), SC-004 — криптографическую часть, SC-005 — закрытие портов.
- [x] No implementation details leak into specification — технические детали вынесены в задачу `KARAOKE-407` (фазы, риски) и базу знаний; спека оперирует требованиями и наблюдаемыми исходами.

## Notes

- Спека готова к `/speckit-plan`. Решения владельца, учтённые в спеке: вариант A (постоянный туннель) + вариант C (mTLS к БД), задача Plane `KARAOKE-407`.
- Уточнение владельца от 2026-11-09 (после первой редакции спеки): админские HTTP-каналы (`setproperty`, `changerecords`) тоже должны работать внутри туннеля; сертификаты при этом не трогаем — имя хоста и TLS-сертификат остаются прежними, меняется только транспорт (FR-021…FR-025, US3, SC-009).
- Требует отдельного окна от владельца: Фаза переключения каналов и Фаза закрытия публичных портов (FR-017, FR-018).

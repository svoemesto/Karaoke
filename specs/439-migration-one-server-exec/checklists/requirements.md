# Requirements Quality Checklist: Переезд на один сервер

**Spec**: [../spec.md](../spec.md) | **Plan**: [../plan.md](../plan.md)
**Created**: 2026-09-23 | **Issue**: OpenProject #178

## Content Quality

- [x] Нет деталей реализации, не относящихся к требованиям (конфиги — в plan).
- [x] Сфокусировано на ценности и потребностях (переезд без потери данных/простоя).
- [x] Обязательные секции заполнены (OpenProject Tracking, Knowledge References).

## Requirement Completeness

- [x] Нет маркеров `[NEEDS CLARIFICATION]` — все решения приняты картой #165.
- [x] Требования тестируемы (FR-001..FR-014, каждый проверяем).
- [x] Success criteria измеримы (SC-001..SC-007).
- [x] Все acceptance scenarios определены (US1-US4).
- [x] Edge cases определены (bulk-обрыв, диск 94%, fullchain, OOM, GPU, Docker Hub).
- [x] Scope ограничен (бизнес-логика, admin-переезд, почта — out of scope).
- [x] Зависимости и допущения определены (`Assumptions`).

## Requirement Clarity

- [x] Требования однозначны (`-Xmx1200m`, IP, пути, порты, образ).
- [x] Success criteria измеримы (`≤20 мин`, `0 запросов`, `15/15 PASS`, счётчики).

## Feature Readiness

- [x] Все FR имеют acceptance criteria.
- [x] User scenarios покрывают primary flows (подготовка, cutover, sync, docs).
- [x] Артефакты решений доступны (`specs/165-migration-one-server-research/`).

## Готовность к исполнению

- [x] Разделение ролей агент/владелец зафиксировано.
- [x] Owner-gate соблюдён (cutover/DNS/старые контейнеры — владелец).
- [x] Rollback определён (вернуть A-записи, окно 2–4 ч).

## Notes

- Спека — **исполнение** уже принятых решений; новых продуктовых решений не требует.
- Phase 0/1 (PR конфигов) и Phase 2-4 (подготовка/данные) безопасны без owner-gate.

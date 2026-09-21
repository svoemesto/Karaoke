<!-- description task-тикета #111 (Step B: content rewrite) -->

## Question

Сократить 9 Principles (I-IX) в constitution.md до ~10 строк каждая + удалить
дубликаты R-04, R-05, R-32, R-43 → cross-ref на architecture-conventions.md
или ADR.

Зачем:
- Из Q6 (Pass 379, #104 resolution): средний scope (b) — сократить до TOP-стиля
  (короткие принципы + ADR для деталей).
- constitution.md 457 → ~280-350 строк (style TOP).

**Что должно быть в ответе**:

1. Каждый из Principles I-IX переписать:
   - **Название** — unchanged.
   - **Тело** — максимум 8-12 строк, формат:
     - Однострочное утверждение.
     - 2-3 примера/детали (cross-ref на architecture-conventions.md, ADR или knowledge/).
   - **Failure** — 1 строка: «violation → что должно произойти».

2. Удалить дубликаты:
   - **R-04 / R-05** (nginx/node теги) → кросс-реф на architecture-conventions.md.
   - **R-32** (FR-009 per-feature doc) → кросс-реф на Principle VI.
   - **R-43** (redirectErrorStream) → кросс-реф на ADR-0006.

3. **Семантика сохраняется** 1:1 — это stylistic rewrite, не content change.

4. Семантик-верификация: каждое правило из R-01…R-50 (особенно R-22-29, R-32, R-43)
   должно остаться покрытым.

## Notes

- Это **constitution amendment** (Pass 349+, governance-review required).
- Semver bump конституции (2.2.0 → 2.3.0 минимум).
- PR review требует одобрения владельца (см. CLAUDE.md § Governance-review).

## Тип

`[wayfinder:task]` (требует governance-review).

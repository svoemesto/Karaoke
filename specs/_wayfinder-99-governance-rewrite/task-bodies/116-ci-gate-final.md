<!-- description task-тикета #116 (Step E: final gate) -->

## Question

Финальный CI gate после всех governance-merges:

1. **CI 7/7 PASS** для всех PR #1-#15:
   - ktlintCheck
   - ESLint (webvue3) + Prettier --check
   - ESLint (karaoke-public) + Prettier --check
   - docs (structure + offline links)
   - baseline stats (informational)
   - KDoc coverage ≥50% (новый check-no-jpa-imports.sh, и т.д.)
   - JSDoc coverage ≥50%

2. **Governance-check gate**:
   - Все guard-скрипты (Pass 372-375 + новые #105-107) — зелёные.
   - `wc -l AGENTS.md` ≤ 200.
   - `wc -l CLAUDE.md` ≤ 100.
   - Никаких commit'ов в master напрямую (Pass 353 + governance-review guard).

3. **Соответствие Success criteria карты #101**:
   - AGENTS.md ≤ 200 строк (target ~150).
   - R-01 живёт в 1 файле.
   - R-04/R-05/R-06/R-08/R-11 живут в 1 файле (architecture-conventions.md).
   - R-39/R-40 консолидированы в 1 матрицу.
   - Hard-gate coverage: 9/50 = 18%.

4. **Финальный close #99** после прохождения всех проверок:
   `tools/tracker.sh close-issue 99`.

## Notes

- Это последний шаг имплементации. После прохождения — effort
  «Переписать governance Karaoke по образцу TOP» считается завершённым.

## Тип

`[wayfinder:task]` (verification gate).

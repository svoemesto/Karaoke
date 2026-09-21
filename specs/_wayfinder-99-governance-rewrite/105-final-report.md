# #105 Final Resolution — Создать tools/check-no-jpa-imports.sh (R-07)

## ✅ Status: done (PR #483 MERGEABLE)

## Implementation Summary

**Branch**: `386-no-jpa-imports-guard`
**PR**: https://github.com/svoemesto/Karaoke/pull/483
**Commit**: `4b084e62` (final, after amend removing Pass 372-375 guards)

## Files

1. `tools/check-no-jpa-imports.sh` (169 строк, chmod +x) — guard для R-07
   (JPA/Hibernate запрет). Ищет импорты `org.springframework.data.jpa`,
   `javax.persistence`, `hibernate` в `karaoke-app/src/main` и
   `karaoke-web/src/main`. Исключения: `*/test/*`, `*/build/*`,
   `*/node_modules/*`. Pretty-print `filename:line:match`,
   цветной вывод. `--quiet` для pre-commit, `--help` для справки.

2. `.pre-commit-config.yaml` — добавлен hook `no-jpa-imports-guard`
   (Pass 379, R-07).

3. `.github/workflows/lint.yml` — добавлен новый job `no-jpa-imports-guard`
   со step `bash tools/check-no-jpa-imports.sh --quiet`. **Без**
   Pass 372-375 guards (они падают в CI runner'е).

## Exit codes

- Локальный прогон: **exit 0** (0 JPA-импортов в репо).
- Synthetic negative test (test_jpa.kt с импортами): exit 1 + pretty-print.
- Pre-commit: Passed (все guards зелёные).

## CI Status

10/10 SUCCESS:
- ktlint, ESLint, Docs, Baseline, KDoc, JSDoc, Knowledge SSoT
  impact + structure, **`no-jpa-imports guard (Pass 379, R-07)`** ✅.

## Hard-gate coverage

6 → **7 из 50 правил** (12% → 14%) — R-07.

## Compliance

- [x] Knowledge-first MUST #0 выполнен.
- [x] Constitution II (JPA/Hibernate запрет) enforce'ится.
- [x] Hard-gate coverage 6 → 7.
- [x] CI 7/7 не сломана (governance-guards job изолирован).
- [x] Никаких merge в master (Pass 349 governance).

— Implementation-субагент для OP #105.

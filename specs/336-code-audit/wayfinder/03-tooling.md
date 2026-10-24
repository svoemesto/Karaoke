Map: #TBD

## Type

research (AFK)

## Status

OPEN

## Blocked by

(none)

## Question

Инвентаризировать, что уже ловят существующие проверки, чтобы аудит не дублировал их и не выдавал известное за новое.

Проверить:

- `config/ktlint/baseline-*.xml` — какие правила и сколько нарушений уже зафиксированы;
- `webvue3/.eslint-baseline.json`, `karaoke-public/.eslint-baseline.json` — то же для фронтендов;
- `tools/check-*.sh` (включая `check-audit-coverage.sh`, `check-kdoc-coverage.sh`, `check-jsdoc-coverage.sh`, guards R-04…R-44) — какие классы дефектов они покрывают;
- CI `.github/workflows/lint.yml` — что реально гоняется и что блокирует merge;
- есть ли статические анализаторы (detekt, SonarQube, semgrep, CodeQL) и тесты (JUnit/Playwright) — что из этого даёт доказательства для аудита;
- какие классы дефектов не покрыты ничем (конкурентность, утечки соединений, обработка исключений, контракты API/БД).

Артефакт: `specs/336-code-audit/tooling-coverage.md` — таблица «инструмент → что ловит → что не ловит → как использовать в аудите». Ветку/коммиты не создавать.

## Answer

(не разрешён)

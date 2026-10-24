- **OpenProject #369 / BR-507** (2026-10-24): в `.github/workflows/lint.yml`
  добавлены джобы `backend-build` (сборка бэкенда) и `backend-test`
  (unit-тесты `karaoke-app`/`karaoke-web`) — до этого workflow выполнял
  только линтеры и guard-скрипты. Джоб `docs-lint` и pre-commit-хуки
  `check-feature-doc` проверяют **оба** каталога per-feature документов.
  Автор: agent (Karaoke), аудит #336 (P-78-01/P-78-04).
- **OpenProject #387** (2026-10-24, аудит #336, BR-523): добавлен
  `check-marker-escape-guard.sh` — guard «до/после» для регресс-теста
  экранирования метки комментария: проверяет, что тест взведён (падает на
  уязвимом снимке кода, проходит на исправленном) и что его skip-состояние
  соответствует состоянию production-кода. Фикстуры — в
  `tools/fixtures/marker-escape/`. Подробности — в
  `knowledge/system/frontend/karaoke-public-tests.md`.

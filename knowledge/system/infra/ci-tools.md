- **OpenProject #387** (2026-10-24, аудит #336, BR-523): добавлен
  `check-marker-escape-guard.sh` — guard «до/после» для регресс-теста
  экранирования метки комментария: проверяет, что тест взведён (падает на
  уязвимом снимке кода, проходит на исправленном) и что его skip-состояние
  соответствует состоянию production-кода. Фикстуры — в
  `tools/fixtures/marker-escape/`. Подробности — в
  `knowledge/system/frontend/karaoke-public-tests.md`.
- **Pass 390** (2026-10-24, OpenProject #390, аудит #336 волна B): guard'ы
  `tools/` приведены к правилу «проверка исполняется, а не имитируется»
  (BR-574 — `check-container-restart.sh` ловит `docker compose … restart`,
  `docker start <c>`, `docker restart -t 30 <c>`; BR-578 — ключ baseline
  ESLint устойчив к сдвигу строк и к другой машине; BR-581 — пути
  `DOCKER_CONFIG`/`GRADLE_USER_HOME` от корня репозитория; BR-584 —
  `auto-kdoc.py` больше не пишет «JPA-сущность» и проверяет результат
  guard'ом `check-no-jpa-imports.sh`; BR-599 — `generate-docs.sh` задаёт
  `GRADLE_USER_HOME`; BR-602 — `tracker-poll.sh --json` разбирает TSV
  (`tracker.sh list-issues --raw`); BR-611 — `tracker-lib.sh` не меняет
  режим шелла вызывающего). Подробности — в разделе «Конвенции».
- **OpenProject #379** (2026-10-24, аудит #336 волна C, S2): инструменты
  `tools/` переведены с «проверка имитируется» на «проверка исполняется» —
  BR-571 `check-do-sh-guards.sh` больше не переписывает отслеживаемый
  `deploy/karaoke-public/Dockerfile` (эксперимент идёт на копии `deploy/` во
  временном каталоге); BR-572 `check-feature-doc.sh` учитывает отсутствующий
  документ как ошибку (раньше — `ERROR` в stdout и exit 0); BR-593/BR-594 креды
  registry не передаются через argv (`curl --config -`), проверка «неверный
  пароль → 401» реально отправляет креды, `PENDING` не затирает пин в
  `images.lock`; BR-595 `generate-eslint-baseline.sh` пишет baseline через
  `mktemp` + `mv` и различает «нарушения» (код 1) и «ошибка eslint» (код ≥ 2);
  BR-601 `tracker-poll.sh` не смешивает stderr трекера с данными и возвращает
  документированные коды 2 (OpenProject недоступен) / 3 (токен истёк).
  Baseline `tools/check-secret-in-argv.baseline` ужат: из него убраны все
  записи `registry-smoke.sh` / `registry-vendor.sh` (исправлены здесь); запись
  `analyze-prod-incident.sh` закрыта отдельно (BR-626, #408 / PR #675) и в
  baseline не возвращается.
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
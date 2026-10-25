- **OpenProject #402** (2026-10-26, аудит #336 волна E, пакет `PKG-S3-30`,
  24 BR): guard'ы `tools/` приведены к правилу «не смог проверить — не значит
  «нарушений нет»». Зафиксированы четыре класса дефектов и правила против них:
  (1) **«нет данных» ≠ «нет нарушений»** — `check-endpoint-field-coverage.sh`
  больше не пропускает пары с отсутствующим component/controller/method или без
  v-model полей (BR-573), и строит список поддерживаемых полей **из
  контроллера пары**, а не из единственного `SongUpdateMapper.kt` (BR-577);
  (2) **код возврата не теряется** — `check-eslint-baseline.sh` различает
  «eslint нашёл нарушения» (1) и «eslint упал» (≥ 2) и пишет stderr в лог
  (BR-579), `generate-docs.sh` проверяет каждый шаг (BR-598),
  `install-tracker.sh` — код `docker compose up` (BR-600),
  `tracker-lib.sh` — код `curl` (BR-604) и ошибку записи аудит-лога (BR-614),
  `tracker-bootstrap-board.sh` — код `docker exec` (BR-607);
  (3) **счётчик = число нарушений** — проверка `docker-compose … restart`
  вынесена из цикла по контейнерам (BR-575);
  (4) **контекст diff'а не выдумывается** — `check-frontend-build.sh` берёт
  контекст из полного содержимого файла, номера строк — из diff'а (BR-580).
  Дополнительно: недостижимая ветка самодиагностики в
  `check-docker-image-tags.sh` сделана достижимой и покрыта фикстурой
  (BR-576), переносимый подсчёт без PCRE в `check-enforcement.sh` (BR-586),
  извлечение Issue ID в обоих форматах в `check-spec-issue-link.py` (BR-596),
  fail-closed поведение `check-subagent-isolation.sh` при неразрешённом
  git-ref (BR-597), приватные временные файлы вместо предсказуемых `/tmp`
  (BR-582), корректные `@see`-цели в генераторах документации
  (`archive/docs/features/`, BR-583) и детектор существующего KDoc без
  фиксированного окна в 8 строк (BR-585), честный workflow трекера: без
  `report.md` комментарий не публикуется (BR-609), Issue-ID-регулярка без
  ложных срабатываний на «опубликация»/«operation»/«drop» (BR-610).
  **Конвенция пакета:** каждый правленый guard получает `--self-test` с
  негативными фикстурами — «корректное дерево → PASS», «подсовытое нарушение →
  FAIL с ненулевым кодом»; коды возврата унифицированы (0 — успех, 1 —
  нарушение, 2 — проверка не выполнена). Подробности — в
  `docs/features/pkg-s3-30.md`.
- **OpenProject #391** (2026-10-25, аудит #336 волна E, BR-508): добавлен
  `check-actions-pinned.sh` — каждый сторонний `uses:` в
  `.github/workflows/*.yml` обязан указывать полный SHA коммита (40 hex)
  с комментарием версии (`# vX.Y.Z`). Плавающий тег (`@v4`) можно
  перенаправить на другой коммит в чужом репозитории — CI исполнит новый
  код без изменения нашего репозитория и без ревью. Фикстуры «до/после» —
  `tools/fixtures/actions-pinned/`, джоб `actions-pinned-guard` в
  `lint.yml`. Обновление SHA — отдельным PR (Dependabot — решение
  владельца). Подробности — в
  `archive/docs/features/ci-lint-enforcement.md`.
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
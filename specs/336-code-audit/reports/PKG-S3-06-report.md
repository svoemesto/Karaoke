# PKG-S3-06 — отчёт по пакету guards/CI/tools (OpenProject #390, волна B, аудит #336)

> **Задача**: OpenProject #390 (PKG-S3-06, S3, 7 записей BR-574, BR-578, BR-581,
> BR-584, BR-599, BR-602, BR-611).
> **Ветка**: `570-pkg-s3-06-guards-tools` (от master `6cbaad02`).
> **Worktree**: `/home/nsa/Karaoke/.worktrees/waveB-guards-tools`.
> Главная копия `/home/nsa/Karaoke` не изменялась.
> **Дата**: 2026-10-24.

## Сводка

Все 7 записей пакета исправлены: guard'ы теперь действительно выполняют
заявленную проверку и **падают** на неправильном состоянии. Ни одной «зелёной
заглушки» не добавлено: там, где проверку выполнить нельзя, скрипт завершается
ненулевым кодом с понятным сообщением.

| BR | Файл | Что было | Что стало | Коммит |
|---|---|---|---|---|
| BR-574 | `tools/check-container-restart.sh` | ловились только `docker restart <c>`, `docker stop <c>` и `docker-compose … restart` | добавлены `docker compose … restart`, `docker start <c>`, `docker restart -t 30 <c>` | `b90e0f2c` |
| BR-578 | `tools/check-eslint-baseline.sh` (+ `tools/generate-eslint-baseline.sh`) | ключ = ruleId + file + line + column, `file` всегда `null` (`.filePath` читался внутри `.messages[]`), абсолютные пути | ключ = ruleId + путь относительно корня репозитория + текст сообщения; путь нормализуется при записи и при сравнении | `5a24cfb7` |
| BR-581 | `tools/check-docker-config.sh`, `tools/check-gradle-user-home.sh` (+ `deploy/do.sh`, `tools/registry-vendor.sh`, `tools/check-do-sh-guards.sh`, `.pre-commit-config.yaml`, `AGENTS.md`) | ожидаемый путь зашит как `/home/nsa/Karaoke/...` → на `dev-pc` pre-commit падал на каждом коммите | ожидаемый путь выводится из корня репозитория; переопределение — `KARAOKE_GRADLE_USER_HOME` / `KARAOKE_DOCKER_CONFIG` | `c6cfadce` |
| BR-584 | `tools/auto-kdoc.py` | для класса `*Entity` писалось «JPA-сущность» (запрещено Constitution II / R-07) | текст без упоминания JPA; самопроверка генератора + проверка результата guard'ом `check-no-jpa-imports.sh` | `afca683e` |
| BR-599 | `tools/generate-docs.sh` | `./gradlew` вызывался без `GRADLE_USER_HOME` (Hard Gate Pass 372) | `export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle}"` | `64619fa0` |
| BR-602 | `tools/tracker-poll.sh` (+ `tools/tracker.sh`, `tools/tracker-lib.sh`) | `--json` резал по `\t`, а продюцер выравнивал колонки пробелами → `id` = вся строка, `subject`/`status` = `null` | добавлен `list-issues --raw` (TSV), потребитель разбирает TSV по табу | `47d7f9ff` |
| BR-611 | `tools/tracker-lib.sh` | библиотека (`source`) включала `set -euo pipefail` в вызывающем шелле → ломала контракт кодов 0/1/2 | библиотека не меняет режим шелла; каждый вызывающий скрипт задаёт его сам | `47d7f9ff` |

Плюс документация и living-docs: `161ae16a` (`knowledge/system/infra/ci-tools.md`,
`knowledge/system/infra/pre-commit-config.md`, `docs/features/tracker-local-integration.md`,
`docs/tracker-setup.md`).

---

## BR-574 — `tools/check-container-restart.sh`

**Что было.** Guard заявлял в шапке перехват «`docker restart` / `docker stop` +
`docker start`», но реализация ловила только `docker restart <c>`,
`docker stop <c>` (для consent-контейнеров) и форму `docker-compose … restart`.
Формы `docker compose … restart` (v2, через пробел — именно она используется в
репозитории), `docker start <c>` и `docker restart -t 30 <c>` проходили молча.

**Что стало.** Добавлены паттерны (P-75-05, «Поправка»):

- `docker\s+compose\b.*\brestart\b` — обе формы (`docker-compose` и `docker compose`);
- `docker\s+start\s+(-[^\s]+\s+)*<c>`;
- `docker\s+restart\s+(-[^\s]+\s+)*<c>` — опции между глаголом и именем.

**До/после** (фикстура `tmp-br574-fixture.sh` в диффе, guard читает untracked-файлы):

```
docker compose restart karaoke-app (v2, без дефиса) | ДО: PASS(0)   | ПОСЛЕ: FAIL(1)
docker compose -f deploy/app.yml restart karaoke-app | ДО: PASS(0)   | ПОСЛЕ: FAIL(1)
docker start karaoke-app                       | ДО: PASS(0)   | ПОСЛЕ: FAIL(1)
docker restart -t 30 karaoke-app               | ДО: PASS(0)   | ПОСЛЕ: FAIL(1)
docker restart karaoke-app (регрессия)         | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
docker-compose ... restart (регрессия)         | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
docker stop karaoke-app (регрессия, consent)   | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
легитимный do.sh-вызов (ожидается PASS)        | ДО: PASS(0)   | ПОСЛЕ: PASS(0)
docker start не-karaoke контейнера (PASS)       | ДО: PASS(0)   | ПОСЛЕ: PASS(0)
```

Команда: `bash br390-scratch.tmp/br574-test.sh` (скрипты лежат в игнорируемом
каталоге `br390-scratch.tmp/`, в коммит не попадают).

---

## BR-578 — `tools/check-eslint-baseline.sh` (+ генератор baseline)

**Что было.** Ключ «нового нарушения» — `ruleId + file + line + column`, где
`file` брался из `filePath` ESLint. Дополнительно `filePath` читался внутри
`.messages[]` (контекст — сообщение, а не файл) → `file` был всегда `null`,
т.е. часть ключа не работала вовсе. Вставка строк выше нарушения сдвигает
`line`/`column`, а другой путь к репозиторию (CI, worktree) даёт другой `file`
→ baseline переставал совпадать, и каждое старое нарушение выглядело как «новое».

**Что стало.**

- путь нормализуется к пути **относительно корня репозитория** и при записи
  baseline, и при сравнении (работает и для абсолютного пути с другой машины);
- ключ сопоставления — `ruleId + относительный путь + текст сообщения`
  (`line`/`column` в ключ не входят), сопоставление мультимножественное:
  каждая запись baseline используется не более одного раза.

**До/после** (фейковый `npx` вместо eslint — `node_modules` в репозитории нет):

```
baseline с абс. путём другой машины | ДО: FAIL(1)   | ПОСЛЕ: PASS(0)
baseline с относит. путём, eslint — абс. | ДО: FAIL(1)   | ПОСЛЕ: PASS(0)
сдвиг строк (line 10 -> 25)        | ДО: FAIL(1)   | ПОСЛЕ: PASS(0)
РЕАЛЬНО НОВОЕ: другой ruleId       | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
РЕАЛЬНО НОВОЕ: другое сообщение     | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
РЕАЛЬНО НОВОЕ: лишняя копия         | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
РЕАЛЬНО НОВОЕ: лишний файл         | ДО: FAIL(1)   | ПОСЛЕ: FAIL(1)
```

Сторона записи (зеркало BR-578 из P-76 § Сверки) — `tools/generate-eslint-baseline.sh`:

```
ДО    file: null
ПОСЛЕ file: br390-scratch.tmp/fake-spa/src/A.vue
```

**Прогон на настоящем репозитории** (`npm ci` в `webvue3` выполнен):

```
$ bash tools/check-eslint-baseline.sh webvue3
==> Проверка webvue3...
Текущих нарушений: 0
В baseline:        0
OK: новых нарушений нет          # rc=0

$ bash tools/generate-eslint-baseline.sh webvue3
==> Записано 0 нарушений в webvue3/.eslint-baseline.json   # tracked-файл не изменился
```

---

## BR-581 — пути `GRADLE_USER_HOME` / `DOCKER_CONFIG`

**Что было.** Оба guard'а жёстко зашивали путь одной машины
(`/home/nsa/Karaoke/.docker`, `/home/nsa/Karaoke/.gradle`) и не учитывали
матрицу машин AGENTS.md. На второй машине (`dev-pc`/`dev`) проектной
директории нет → guard возвращает 1 → pre-commit-хуки
`docker-config-guard` и `gradle-user-home-guard` падали на **каждом** коммите.

**Что стало.**

- ожидаемый путь выводится из корня репозитория:
  `git rev-parse --path-format=absolute --git-common-dir` → `dirname` →
  `<main-repo-root>/.gradle` / `<main-repo-root>/.docker` (общий кэш главного
  checkout'а; в worktree — тот же каталог);
- переопределение окружением: `KARAOKE_GRADLE_USER_HOME` / `KARAOKE_DOCKER_CONFIG`;
- тот же путь используют `deploy/do.sh`, `tools/registry-vendor.sh`,
  `tools/check-do-sh-guards.sh` и хук `ktlint` в `.pre-commit-config.yaml`;
- `AGENTS.md` (Tier-1, Pass 372-375): команда приведена к переносимой,
  semver bump `3.4.1 → 3.5.0` (правка governance — через governance-PR).

**До/после** (симуляция второй машины: guard'ы скопированы в отдельный
репозиторий `/tmp/br581-fakerepo`, машинный путь заменён на путь `dev-pc`):

```
1) Эта машина
  ДО  docker: DOCKER_CONFIG не задан      PASS(0)
  ПОСЛЕ docker: DOCKER_CONFIG не задан     PASS(0)
  ДО  gradle: GRADLE_USER_HOME не задан   PASS(0)
  ПОСЛЕ gradle: GRADLE_USER_HOME не задан PASS(0)

2) Регрессия — read-only системный путь обязан падать
  ДО  docker: DOCKER_CONFIG=$HOME/.docker   FAIL(1)
  ПОСЛЕ docker: DOCKER_CONFIG=$HOME/.docker FAIL(1)
  ДО  gradle: GRADLE_USER_HOME=$HOME/.gradle   FAIL(1)
  ПОСЛЕ gradle: GRADLE_USER_HOME=$HOME/.gradle FAIL(1)

3) Вторая машина (dev-pc): проект в /tmp/br581-fakerepo
  ДО  docker (dev-pc): DOCKER_CONFIG не задан     FAIL(1)   ← баг: падал на каждом коммите
  ПОСЛЕ docker (dev-pc): DOCKER_CONFIG не задан    PASS(0)
  ДО  gradle (dev-pc): GRADLE_USER_HOME не задан   FAIL(1)
  ПОСЛЕ gradle (dev-pc): GRADLE_USER_HOME не задан PASS(0)
```

Команда: `bash br390-scratch.tmp/br581-test.sh`.

---

## BR-584 — `tools/auto-kdoc.py`

**Что было.** Для любого класса с суффиксом `Entity` генератор писал в исходники
«JPA-сущность» — текст, прямо запрещённый Constitution Principle II и R-07
(в проекте сырой JDBC, никакого JPA/Hibernate). Проверки результата не было
вовсе: утилита завершалась с кодом 0 в любом состоянии репозитория.

**Что стало.**

- ветка `Entity` переформулирована без упоминания JPA («Сущность домена для …»);
- самопроверка генератора: текст KDoc (описание и `@see`) проверяется на
  запрещённые упоминания — при нарушении запись в исходник не выполняется,
  скрипт падает с понятным сообщением (`GenerationError`);
- результат генерации проверяется guard'ом `tools/check-no-jpa-imports.sh`:
  найдено нарушение → ненулевой код возврата; guard не найден → тоже ошибка
  (молча пропускать проверку нельзя).

**До/после** (fake-репозиторий, чтобы guard сканировал тестовые файлы):

```
ДО    (auto-kdoc-before.py)   rc=0
    KDoc: /**  * JPA-сущность для user profile. … */
    JPA-упоминание: ЕСТЬ (нарушение R-07)
ПОСЛЕ (auto-kdoc-after.py)    rc=0
    KDoc: /**  * Сущность домена для user profile. … */
    JPA-упоминание: нет

«неправильное состояние» — в исходниках есть JPA-импорт:
auto-kdoc-before.py   PASS(0)      ← проверки не было
auto-kdoc-after.py    FAIL(1)      ← guard нашёл javax.persistence

самопроверка генератора: OK: GenerationError: сгенерированный текст содержит
запрещённое упоминание «jpa» (тест)
```

Команда: `bash br390-scratch.tmp/br584-test.sh`.

---

## BR-599 — `tools/generate-docs.sh`

**Что было.** `./gradlew` вызывался без `GRADLE_USER_HOME` — прямое нарушение
Hard Gate AGENTS.md Tier-1 (Pass 372): в DSH-sandbox `~/.gradle` read-only,
wrapper падает с `FileNotFoundException`. Guard `check-gradle-user-home.sh`
смотрит только окружение, содержимое скриптов не сканирует — нарушение ничем
не ловилось.

**Что стало.** `export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$REPO_ROOT/.gradle}"`
(путь от корня репозитория; значение из окружения не перетирается).

**До/после** (подставной `./gradlew`, печатающий фактический `GRADLE_USER_HOME`):

```
ДО    (generate-docs-before.sh)  GRADLE_USER_HOME=<НЕ ЗАДАН>
ПОСЛЕ (generate-docs-after.sh)   GRADLE_USER_HOME=/tmp/br599-fakerepo/.gradle
при GRADLE_USER_HOME в окружении: /custom/gradle-home (не перетирается)
```

Команда: `bash br390-scratch.tmp/br599-test.sh`.

---

## BR-602 — `tools/tracker-poll.sh` (`--json`)

**Что было.** Режим `--json` разбирал строки по `\t`, а продюсер
(`tracker_list_issues`) выравнивает колонки через `column -t -s $'\t'` —
разделитель **пробел**. В итоге `id` содержал всю строку целиком, а `subject`
и `status` всегда были `null`.

**Что стало.** Добавлен флаг `list-issues --raw` (TSV без `column -t`) —
вариант, прямо разрешённый карточкой («либо просить продюсера отдавать TSV
без `column` (флаг `--raw`)`); `tracker-poll.sh` использует `--raw` и разбирает
строки по табу, строки без таба (текст ошибки) отбрасываются. Поведение
`list-issues` по умолчанию (выровненный человекочитаемый вывод) не изменилось.

**До/после на живом OpenProject** (реальные задачи, реальный API):

```
ДО:    {"id":"72   GitHub branch protection для master (Pass 353 follow-up)              In review  ai agent    Milestone","subject":null,"status":null}
       {"id":"127  Governance: предотвращение повторных нарушений AI-агентом Hard Gates  New        unassigned  Task","subject":null,"status":null}
ПОСЛЕ: {"id":"72","subject":"GitHub branch protection для master (Pass 353 follow-up)","status":"In review"}
       {"id":"127","subject":"Governance: предотвращение повторных нарушений AI-агентом Hard Gates","status":"New"}
```

Детерминированный прогон (fake-репозиторий с заглушкой `tracker.sh`):

```
ДО    open[0] = {"id":"390  [#336] S3: guards/CI/tools (S3, 7)  New         unassigned  Task","subject":null,"status":null}
ПОСЛЕ open[0] = {"id":"390","subject":"[#336] S3: guards/CI/tools (S3, 7)","status":"New"}
human-режим: идентичен до и после (3 строки с '390')
```

Команда: `bash br390-scratch.tmp/br602-test.sh`.

---

## BR-611 — `tools/tracker-lib.sh`

**Что было.** Библиотека подключается через `source`, но в первой же строке
выполняла `set -euo pipefail` → errexit включался в вызывающем шелле.
`tools/tracker-bootstrap.sh` и `tools/tracker-implement-done.sh` явно
отключают errexit (`set -uo pipefail`) и строят на этом свой контракт кодов
возврата (0/1/2) — но `source tracker-lib.sh` включал errexit обратно.

**Что стало.** Библиотека не меняет настройки вызывающего шелла; режим задаёт
сам вызывающий скрипт:

| Скрипт | Режим шелла |
|---|---|
| `tools/tracker.sh` | `set -euo pipefail` |
| `tools/tracker-poll.sh` | `set -euo pipefail` |
| `tools/tracker-spec-for-issue.sh` | `set -euo pipefail` |
| `tools/tracker-bootstrap.sh` | `set -uo pipefail` (errexit выключен намеренно) |
| `tools/tracker-implement-done.sh` | `set -uo pipefail` (errexit выключен намеренно) |

**До/после:**

```
ДО    (библиотека с set -euo pipefail)  errexit_after_source=ON
ПОСЛЕ (библиотека не меняет режим шелла) errexit_after_source=OFF

скрипт с контрактом 0/1/2 (errexit выключен намеренно):
ДО    rc=1   ← упал с кодом промежуточной команды вместо задокументированного 2
ПОСЛЕ rc=2   ← контракт соблюдён
```

Команда: `bash br390-scratch.tmp/br611-test.sh`.

---

## Результаты проверок

| Проверка | Результат |
|---|---|
| `bash tools/check-knowledge-structure.sh` | OK — 9/9 проверок |
| `python3 tools/check-doc-references.py` | OK — 4102 ссылки, 2236 путей, 306 эндпоинтов, битых нет |
| `bash tools/check-subagent-isolation.sh` | PASS (0) |
| `python3 tools/lint-knowledge.py` | OK — «All documentation passes architectural style and structural checks» |
| `bash tools/check-eslint-baseline.sh webvue3` | PASS (0) — 0 нарушений, 0 в baseline |
| `bash tools/generate-eslint-baseline.sh webvue3` | PASS (0) — tracked-файл не изменился |
| `pre-commit run --all-files` (изменённые guard'ы) | `gradle-user-home-guard`, `docker-config-guard`, `container-restart-guard`, `frontend-build-guard`, `no-jpa-imports-guard`, `no-mp4-mentions-guard`, `subagent-isolation-guard`, `confirm-escape-guard`, `ktlint`, `eslint (webvue3)`, `lychee`, `check-doc-references`, `check-feature-doc`, field-coverage — **Passed** |
| `pre-commit run --all-files` (полный) | 2 падения — `eslint (karaoke-public)` и `prettier (karaoke-public)`: **не связаны с правками** (в `karaoke-public/` нет `node_modules`, `npx` тянет ESLint 10 и падает на `EROFS` в read-only `~/.npm/_cacache`). Базовый прогон на закешенных правках (`git stash`) даёт те же два падения |

Регрессионные прогоны «до/после» — в каталоге `br390-scratch.tmp/`
worktree'а (каталог игнорируется git, поэтому в коммиты **не** попадает —
«сломанный артефакт» по брифингу — временная копия): `br574-test.sh`,
`br578-test.sh`, `br581-test.sh`, `br584-test.sh`, `br599-test.sh`,
`br602-test.sh`, `br611-test.sh`. «ДО»-версии guard'ов взяты из
`git show HEAD:tools/<файл>` (то есть из master до правки), «ПОСЛЕ»-версии —
из рабочего дерева ветки.

Каждый изменённый guard проверен и на репозитории (PASS), и на «сломанном»
артефакте (FAIL):

| Guard | На репозитории | На сломанном артефакте |
|---|---|---|
| `check-container-restart.sh` | PASS | FAIL (docker compose / docker start / docker restart с опциями) |
| `check-eslint-baseline.sh` | PASS (0 нарушений) | FAIL (новое нарушение) |
| `check-docker-config.sh` | PASS | FAIL (read-only `$HOME/.docker`, несуществующий путь) |
| `check-gradle-user-home.sh` | PASS | FAIL (read-only `$HOME/.gradle`, несуществующий путь) |
| `auto-kdoc.py` | PASS (guard R-07 зелёный) | FAIL (JPA-импорт в исходниках) |
| `generate-docs.sh` | `GRADLE_USER_HOME` задан | FAIL — раньше переменная не задавалась вовсе |
| `tracker-poll.sh --json` | PASS (корректный JSON) | ДО — неверный разбор (id = вся строка) |

---

## Что осталось за владельцем / лидом

1. **PR и merge.** Ветка `570-pkg-s3-06-guards-tools` готова; push и PR не
   делались (запрет брифинга). Коммиты: `b90e0f2c`, `5a24cfb7`, `c6cfadce`,
   `afca683e`, `64619fa0`, `47d7f9ff`, `161ae16a` + коммит реестра/карточек.
2. **Номер PR в последнем коммите** (`specs(336): … → исправлено`) — проставить
   при создании PR (прецедент волны A: `dd793efe`).
3. **Правка `AGENTS.md`** (governance, semver bump `3.4.1 → 3.5.0`) требует
   governance-PR — это та же ветка/PR.
4. **Соседние BR из того же корня, которые НЕ входят в пакет #390** (сознательно
   не трогались, чтобы не залезать в чужие пакеты):
   - BR-575 (`check-container-restart.sh`: счётчик нарушений умножается на 13 —
     проверка `docker-compose … restart` стоит внутри цикла по контейнерам) —
     пакет #402;
   - BR-598 (`generate-docs.sh`: `set -e` без `pipefail`, код возврата
     `gradlew`/`typedoc` теряется в пайпе) — пакет #402;
   - BR-579 (`check-eslint-baseline.sh`: код возврата eslint отброшен, stderr в
     `/dev/null`) — пакет #402;
   - BR-573 / BR-596 / BR-597 и др. — пакеты #402 и #379.
5. **`check-eslint-baseline.sh` и `generate-eslint-baseline.sh`**: файлы
   `/tmp/eslint-current.json` и `/tmp/eslint-output.json` остались
   предсказуемыми (BR-582, пакет #402) — в этом пакете не менялось.
6. **Проверка на `dev-pc`** (вторая машина матрицы) не выполнялась — машина
   недоступна; поведение воспроизведено симуляцией (проект в отдельном корне
   репозитория, каталога `/home/nsa/Karaoke/...` нет).

## Реестр и карточки аудита

- `specs/336-code-audit/registry.csv`: BR-574, BR-578, BR-581, BR-584, BR-599,
  BR-602, BR-611 — статус `TODO` → `исправлено`.
- `specs/336-code-audit/findings/P-75.md` (P-75-05, P-75-09, P-75-12, P-75-15),
  `P-76.md` (P-76-07), `P-77.md` (P-77-02, P-77-11) — «Статус: исправлено».

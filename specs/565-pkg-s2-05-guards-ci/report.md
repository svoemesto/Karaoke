# Отчёт по OpenProject #369 (PKG-S2-05, волна B) — guards / CI / .github

- **Задача**: OpenProject #369 «[#336] S2: guards/CI/.github (S2, 3)»
- **Записи пакета**: BR-507, BR-509, BR-510 (реестр `specs/336-code-audit/registry.csv`)
- **Карточки-источник**: `specs/336-code-audit/findings/P-78.md` (P-78-01, P-78-03, P-78-04)
- **Ветка**: `565-pkg-s2-05-guards-ci` (worktree `.worktrees/waveB-ci`, от master `6cbaad02`)
- **PR / push**: не выполнялись (по брифингу задачи). Merge, `mark-review`, `close` — за владельцем.

---

## 1. Что изменено

### 1.1 `.github/workflows/lint.yml`

| BR | Изменение |
|---|---|
| **BR-507** (P-78-01) | Добавлены джобы **`backend-build`** (timeout 45 мин) и **`backend-test`** (timeout 30 мин). |
| **BR-509** (P-78-03) | В джобе `knowledge-structure` checkout получил `with: fetch-depth: 0`; шаг «No direct commits to master» получил `set -o pipefail` и проверку наличия `origin/master` с явной ошибкой. |
| **BR-510** (P-78-04) | Шаг `docs-lint` → `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md`. |

**BR-507 — состав новых джобов** (секреты не используются, деплой/контейнеры не задействованы):

```yaml
backend-build:
  env:
    GRADLE_USER_HOME: ${{ github.workspace }}/.gradle   # AGENTS.md Tier-1, R-372
  # setup-java@v4 (temurin 17, cache=gradle)
  run: ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel --no-daemon
  run: ./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel --no-daemon

backend-test:
  env:
    GRADLE_USER_HOME: ${{ github.workspace }}/.gradle
  # setup-java@v4 (temurin 17, cache=gradle)
  run: ./gradlew :karaoke-app:test :karaoke-web:test --parallel --no-daemon
  # + upload-artifact backend-test-reports (**/build/test-results/test/*.xml)
```

Команды — **те же, что локально** (AGENTS.md § «Обязательная проверка после изменения»,
шаги 1 и 3; DEVELOPMENT.md § Build & run). `actions/setup-java@v4` с `cache: gradle`
берёт путь кэша из `GRADLE_USER_HOME`, поэтому кэш и сборка используют один каталог.

**BR-509 — почему именно `fetch-depth: 0`:** `actions/checkout@v4` по умолчанию клонирует
один коммит; в сборке `pull_request` при этом создаётся только ref самого PR, а
`refs/remotes/origin/master` — **нет**. `git log origin/master` в таком клоне падает
(«unknown revision»), вывод пустой, счётчик `DIRECT_COMMITS` всегда 0 → guard печатал
«✅ No direct commits to master in last 24h» и никогда не падал. При `fetch-depth: 0`
checkout тянет `+refs/heads/*:refs/remotes/origin/*`, поэтому `origin/master` существует
и в PR-сборке. Размер истории репозитория — 56 МБ (2276 коммитов), т.е. полная история
для этого workflow недорога.

**BR-509 — почему шаг теперь падает при недоступной истории:** «пустой `git log`»
неоднозначен — это и «нарушений нет» (штатное состояние), и «историю не удалось
прочитать». Разведено явно: (а) `git rev-parse --verify --quiet origin/master` —
если ref недоступен, шаг падает с сообщением; (б) `set -o pipefail` — если сам
`git log` не отработал, пайплайн возвращает ненулевой код и шаг падает, а не
рапортует «нарушений нет».

### 1.2 `.pre-commit-config.yaml`

Зеркало того же дефекта BR-510, явно названное в карточке P-78-04
(«То же в pre-commit-хуке»): хук `check-feature-doc` срабатывал только по
`^archive/docs/features/.*\.md$` и проверял только архив — на живых документах
`docs/features/` хук **вообще не запускался**.

- `entry`: `./tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md`
- `files`: `^(archive/)?docs/features/.*\.md$`

### 1.3 `archive/docs/features/ci-lint-enforcement.md` (FR-009, per-feature документ фичи CI)

Актуализирован под фактическое поведение workflow: перечислены джобы сборки/тестов
и групп джобов, исправлено утверждение про `check-feature-doc.sh` (в тексте стояло
`docs/features/*.md`, а workflow выполнял `archive/docs/features/*.md` — расхождение
было как раз тем, что BR-510 и фиксирует), добавлены таблица кэширования, инварианты
(зелёная сборка+тесты как gate, «guard обязан уметь оценить master», проверка обоих
каталогов), ловушки (shallow checkout = «зелёный» guard; ssot-impact с `fetch-depth: 50`),
ссылка на карточку P-78.

### 1.4 Knowledge (SSoT)

- `knowledge/system/infra/pre-commit-config.md` — в «Связь с CI» добавлено, что CI
  выполняет ещё и сборку/тесты бэкенда; запись в Changelog.
- `knowledge/system/infra/ci-tools.md` — строка `check-feature-doc.sh`: «pre-commit»
  → «pre-commit + CI», оба каталога; запись в Changelog.

`.ssot-map.yml` не содержит правил для `.github/**` и `.pre-commit-config.yaml`,
поэтому `tools/check-ssot-impact.py` не требовал обновления knowledge; обновление
сделано по требованию задачи (#369, «Требования к реализации», п. 4).

---

## 2. Проверки «до/после»

### BR-510 — guard per-feature документов

Сценарий: во временный `docs/features/zz-br510-demo-broken.md` положен документ
без обязательных секций; команды извлечены из workflow (HEAD — «до», рабочее дерево — «после»).

| | Команда | Результат |
|---|---|---|
| **До** (HEAD) | `bash tools/check-feature-doc.sh archive/docs/features/*.md` | **exit=0** — нарушение в `docs/features/` **НЕ ПОЙМАНО** (guard прошёл) |
| **После** | `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | **exit=1** — 6 ошибок (`отсутствует секция '## Что делает'` … ) по `docs/features/zz-br510-demo-broken.md` |
| Контроль | После-команда на полном репозитории | **exit=0**, 57 документов проверены |

Зеркало в pre-commit (на временном файле `docs/features/zz-demo-hook.md`):

| | Результат |
|---|---|
| **До** (HEAD) | `check per-feature document structure ... (no files to check) Skipped` — хук не срабатывал |
| **После** | `check per-feature document structure ... Passed` — хук срабатывает |

### BR-509 — guard «No direct commits to master»

Сценарий: синтетический origin с `master` (merge-коммит + **прямой** коммит в master,
обход branch protection) + ветка-«PR». Клоны сделаны так же, как их делает
`actions/checkout`: (A) `fetch --depth=1 origin +<pr-sha>:refs/remotes/origin/refs/pull/1/merge`
— как сейчас; (B) `fetch origin +refs/heads/*:refs/remotes/origin/* +<pr-sha>:...`
— как при `fetch-depth: 0`. Шаги извлечены из workflow (HEAD / рабочее дерево).

| Сценарий | Шаг | Результат |
|---|---|---|
| A: shallow PR-клон (как сейчас) | **До** | **exit=0** — `fatal: неоднозначный аргумент «origin/master»` в stderr и «✅ No direct commits to master in last 24h» → **ложный успех** |
| A: shallow PR-клон | **После** | **exit=1** — «❌ FAIL: origin/master недоступен — проверка прямых коммитов в master невозможна» |
| B: полная история | **До** | **exit=1** — прямой коммит найден (`eb0f7e7… direct commit to master`) |
| B: полная история | **После** | **exit=1** — прямой коммит найден |

Вывод: старый шаг умел ловить обход только когда ref есть, а в реальной CI-сборке
(ref'а не было) он **не ловил ничего**. Новая связка (`fetch-depth: 0` + проверка ref)
даёт рабочий guard и не даёт «зелёного» результата при невозможной оценке.

### BR-507 — джобы сборки и тестов

| Что | Результат |
|---|---|
| До правки | В `.github/workflows/lint.yml` не было ни одной gradle-команды кроме `ktlintCheck` (`grep -E 'compileKotlin\|bootJar\|gradlew .*:.*test' ` — 0 совпадений) |
| После | Джобы `backend-build` и `backend-test` выполняют ровно те команды, что проверены локально (ниже) |
| «Джоба ловит поломку» | В `SanitizePathTest` временно добавлен падающий тест → та же команда CI: `444 tests completed, 1 failed` → `BUILD FAILED`, gradle exit=1. Файл откачен (`git checkout --`), повторный прогон — `BUILD SUCCESSFUL` |

---

## 3. Результаты проверок (факты с диска)

Все команды выполнены в worktree `/home/nsa/Karaoke/.worktrees/waveB-ci`.

| Команда | Результат | Комментарий |
|---|---|---|
| `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | **PASS** (exit=0) | 57 документов OK (27 живых + 30 архивных без README) |
| `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/lint.yml'))"` | **PASS** | YAML валиден; 15 джобов |
| `python3 -c "import yaml; yaml.safe_load(open('.pre-commit-config.yaml'))"` | **PASS** | YAML валиден |
| `bash tools/check-knowledge-structure.sh` | **PASS** (exit=0) | 9/9 проверок |
| `python3 tools/check-doc-references.py` | **PASS** (exit=0) | 4102 ссылки / 2236 путей / 306 эндпоинтов, битых нет |
| `bash tools/check-subagent-isolation.sh` | **PASS** (exit=0) | Работа в отдельном worktree (Pass 379) |
| `python3 tools/lint-knowledge.py` | **PASS** (exit=0) | Стиль и структура knowledge — без замечаний |
| `pre-commit run --files <изменённые 5 файлов>` | **PASS** | Все хуки Passed (часть — «no files to check»); `check per-feature document structure` — Passed |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel --no-daemon` | **PASS** | `BUILD SUCCESSFUL` |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:bootJar :karaoke-web:bootJar --parallel --no-daemon` | **PASS** | `BUILD SUCCESSFUL` |
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test :karaoke-web:test --parallel --no-daemon` | **PASS** | `BUILD SUCCESSFUL`; karaoke-app — 443 теста (6 skipped, `@Disabled`), karaoke-web — 75 тестов; **0 failures / 0 errors** |

Замечания по среде (не дефекты правок): локально доступен только JDK 18 (CI —
temurin 17), Kotlin-daemon в DSH-sandbox не поднимается и срабатывает штатный
fallback «Compile without Kotlin daemon» — на результат тестов не влияет.

---

## 4. Осознанные компромиссы

1. **Сборка фронтенда в CI не добавлена** (`npm run build` для `webvue3`/`karaoke-public`).
   Карточка P-78-01 приводит это как один из вариантов, но брифинг задачи (#369)
   ограничивает объём «сборка + тесты **бэкенда**», а «ровно эти три вещи, ничего
   сверх» — запрещает расширять scope. Фронтенд-сборка остаётся обязательной
   локально (AGENTS.md § «Обязательная проверка после изменения», шаг 4) и в CI не
   проверяется — это остаётся за владельцем (см. § 5).
2. **Полный набор backend-тестов, без выборочных `--tests`.** 518 тестов проходят
   за ~40-60 с локально (инкрементально) и дают полный сигнал; «разумный объём»
   урезать не потребовалось.
3. **`fetch-depth: 0` (полная история), а не ≥100.** История репозитория — 56 МБ /
   2276 коммитов; полная история надёжнее, чем подбор числа, и стоит ~10-30 с на
   runner'е.
4. **Хук `check-feature-doc` в `.pre-commit-config.yaml` тоже изменён.** Это не
   «сверх»: карточка P-78-04 прямо называет pre-commit-хук как вторую точку
   того же дефекта («нарушения FR-009 в новых per-feature документах не ловятся
   ни CI, ни локальным хуком»). Изменение — 2 строки; если владелец считает его
   лишним, его легко вынести в отдельный пакет.
5. **Обновление per-feature документа и knowledge** — по требованию задачи #369
   (п. 4 «Требования к реализации») и FR-009 (R-32). Правок кода приложения,
   конфигов сборки и БД не делалось.

---

## 5. Что осталось за владельцем

1. **Push, PR, merge** — не выполнялись (брифинг). Ветка `565-pkg-s2-05-guards-ci`
   готова к PR в master; после merge — `tracker.sh mark-review 369` и
   `tracker.sh close-issue 369` (шаги 3-4 workflow'а из AGENTS.md Tier-1).
   Комментарий с этим отчётом добавлен в карточку #369.
2. **Первый прогон новых джобов в GitHub Actions** — локально команды проверены
   (зелёные), но сам runner GitHub не запускался (нет push/PR). Первый CI-прогон
   стоит посмотреть глазами: особенно `backend-build`/`backend-test` на холодном
   Gradle-кэше (первый запуск тянет ~6 минут, таймауты 45/30 мин — с запасом).
3. **Сборка фронтенда в CI** — сознательно не добавлена (см. § 4, п. 1). Решение
   за владельцем: либо отдельная задача, либо явная фиксация в AGENTS.md, что
   фронт-сборка обязательна только локально.
4. **Наблюдение вне пакета (не правил):** джоб `ssot-impact` использует
   `fetch-depth: 50`. При глубине < 0 `actions/checkout` тянет только ref PR, и
   `origin/master`/`master` в клоне нет → `tools/check-ssot-impact.py` печатает
   `WARN: cannot resolve any of [...]` и работает как no-op (тот же механизм, что
   BR-509, но в другом джобе и с другой записью реестра). В этом пакете не
   трогалось — кандидат в отдельную задачу аудита.
5. **P-78-02** (actions, закреплённые на тегах, а не на SHA) — в этом пакете нет
   (отдельная запись реестра).

---

## 6. Коммиты

| SHA | Сообщение |
|---|---|
| `8b9a0182` | `fix(369): CI собирает и тестирует бэкенд, полная история для guard'а, оба каталога per-feature документов (BR-507/509/510)` |
| (этот коммит) | `docs(369): отчёт для трекера #369 — что изменено, проверки, что за владельцем` |

Изменённые файлы:

- `.github/workflows/lint.yml`
- `.pre-commit-config.yaml`
- `archive/docs/features/ci-lint-enforcement.md`
- `knowledge/system/infra/pre-commit-config.md`
- `knowledge/system/infra/ci-tools.md`
- `specs/565-pkg-s2-05-guards-ci/report.md` (этот отчёт)

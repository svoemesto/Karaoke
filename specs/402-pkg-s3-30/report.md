# OpenProject #402 — пакет `PKG-S3-30` (24 BR, модуль `tools/`)

> **Issue**: OpenProject #402 · **Пакет**: `PKG-S3-30` · **Волна**: E ·
> **Аудит**: #336 · **Severity**: S3 · **Ветка**: `633-pkg-s3-30` ·
> **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-30`
> **Per-feature документ**: [`docs/features/pkg-s3-30.md`](../../docs/features/pkg-s3-30.md)
> **SSoT**: [`knowledge/system/infra/ci-tools.md`](../../knowledge/system/infra/ci-tools.md)

## Итог

**24 из 24 записей закрыты.** Изменено 24 файла (+2136 / −586 строк), добавлены
2 документа (per-feature + запись в SSoT).

Пакет чинил **сами guard'ы**, которыми проверяется остальное. Общий признак
дефектов — guard **объявлял успех там, где проверка не была выполнена**:

| Тема | Записи | Суть дефекта |
|---|---|---|
| `guard-empty-input-pass` | BR-573 | пустой/непроверяемый вход → exit 0 |
| `guard-wrong-field-source` | BR-577 | данные брались не из того источника |
| `guard-counter-multiplied` | BR-575 | счётчик умножался на 13 |
| `guard-unreachable-branch` | BR-576 | ветка самодиагностики недостижима |
| `guard-discards-exit-code` | BR-579 | код возврата eslint терялся |
| `guard-diff-context-false-positive` | BR-580 | ложное срабатывание из-за контекста diff |
| `guard-failopen-no-master` | BR-597 | не смог проверить → «всё хорошо» |
| `guard-issue-id-format-mismatch` | BR-596 | поле принималось, значение не извлекалось |
| `tmp-file-predictable` | BR-582 | предсказуемые файлы в `/tmp` |
| прочие | BR-583, 585, 586, 598, 600, 603, 604, 606, 607, 608, 609, 610, 612, 613, 614 | битые `@see`, окно в 8 строк, PCRE, пайпы, `|| true`, fail-open |

Ключевое правило пакета: **guard, который не смог выполнить проверку, обязан
вернуть ненулевой код**. Коды унифицированы: `0` — успех, `1` — нарушение /
ошибка данных, `2` — проверка не могла быть выполнена.

## Карточки записей

### BR-573 — `tools/check-endpoint-field-coverage.sh:74–81` (guard-empty-input-pass)

**Что не так.** Отсутствующий `component`/`controller` приводил к `[WARN] … (пропускаем)`
и `continue`: пара не попадала ни в PASS, ни в FAIL, а guard завершался с
кодом 0. Отчёт при этом показывал «ИТОГО: 0/1 пар PASS» — то есть самый
непроверяемый случай выглядел как «нарушений нет». То же и для пары, у
компонента которой нет ни одного v-model-поля, и для несуществующего метода
контроллера.

**Как правильно.** «Нет данных» ≠ «нет нарушений»: непроверяемая пара
считается ошибкой (счётчик `UNCHECKABLE_PAIRS`), печатается `[ERROR] …`,
в итоговой строке видно `0/0 пар PASS (из 1 в endpoint-pairs.yml)`, а код
возврата — 1. Отдельно: пустой `endpoint-pairs.yml` → код 2 (проверка не
выполнена вовсе).

### BR-575 — `tools/check-container-restart.sh:100–126` (guard-counter-multiplied)

**Что не так.** Проверка `docker-compose … restart` (паттерн 3) находилась
внутри цикла `for container in "${KARAOKE_CONTAINERS[@]}"`, хотя `$container` в
ней не участвует. Одна запрещённая строка давала 13 нарушений.

**Как правильно.** Паттерн без контейнера вынесен из цикла и считается один
раз на файл; счётчик показывает реальное число нарушений, список нарушений
в отчёте — список файлов.

### BR-576 — `tools/check-docker-image-tags.sh:200–207` (guard-unreachable-branch)

**Что не так.** Самодиагностика «матчер ослеп» жила в ветке `n/a`:
`if looks_like_watched "$ref"`. Но `looks_like_watched` сравнивала базовое имя
точно (`nginx`/`node`), а `verdict_for` отдавала `n/a` ровно тогда, когда база
**не** равна `nginx`/`node`. Ветка была недостижима: матчер ослепнуть не мог.

**Как правильно.** Семейство nginx/node определяется **до** вынесения вердикта,
по исходному `ref`, и нечётко (`nginx*`/`node*`): `nginx-unprivileged`,
`node-exporter` и т.п. — варианты того же семейства, whitelist их не
покрывает, значит тег проверить нечем → `UNRECOGNIZED` + fail. В `--selftest`
добавлены кейсы «вердикт `n/a`, но имя похоже» (12 → 14 фикстур).

### BR-577 — `tools/check-endpoint-field-coverage.sh:96–122` (guard-wrong-field-source)

**Что не так.** Список поддерживаемых полей для **любой** пары брался из
одного жёстко зашитого `karaoke-app/.../controllers/SongUpdateMapper.kt`.
Fallback на `@RequestParam` был недостижим (`if [ ! -f "${MAPPER}" ]` — файл
маппера существует всегда), и его regex `/^    \):.*\{? *$/` был битым. Итог:
guard проверял не тот эндпоинт, который заявляла пара, а поля чужого маппера.

**Как правильно.** Источник — **контроллер пары**: тело метода
`controller_method` извлекается из файла `controller` (скобочный баланс),
поддерживаемые поля собираются из (1) мапперов, на которые метод
контроллера **ссылается** (`XxxMapper` + `karaoke-app/src/main/kotlin/**/XxxMapper.kt`),
(2) `@RequestParam` самого метода, (3) обращений `all["поле"]`. Per-pair
whitelist теперь берётся из `tools/check-<component-slug>-field-coverage.whitelist.yml`
(для SongEdit — `check-songedit-field-coverage.whitelist.yml`), глобальный
whitelist — по имени компонента.

### BR-579 — `tools/check-eslint-baseline.sh:54–56, 75–77` (guard-discards-exit-code)

**Что не так.** Код возврата `eslint` терялся (`set +e` … `set -e` вокруг
`npx eslint`), stderr уходил в `/dev/null`. «ESLint упал» (exit ≥ 2: битый
конфиг, нет `node_modules`) и «ESLint нашёл нарушения» (exit 1) были
неразличимы — в обоих случаях guard доходил до `OK: новых нарушений нет`.
Падение `npm install` (строки 54–56) тоже игнорировалось.

**Как правильно.** `ESLINT_EXIT` сохраняется; exit ≥ 2 → код 2 с выводом
stderr; пустой/невалидный JSON → код 2; `npm install` с ошибкой → код 2.
Промежуточные файлы — в приватном `mktemp -d` (BR-582).

### BR-580 — `tools/check-frontend-build.sh:77–95` (guard-diff-context-false-positive)

**Что не так.** Контекст «есть ли `cd webvue3` перед `npm run`» строился
**только из списка добавленных строк**. Если `cd webvue3` был в файле до
правки (строка контекста diff'а), guard его не видел и ложно срабатывал,
блокируя корректный коммит (обход — `git commit --no-verify`). Номер строки
тоже был индексом в списке добавленных строк, а не строкой файла.

**Как правильно.** Контекст — **полное содержимое файла**; проверяются только
строки, номера которых пришли из diff'а (`git diff -U0`, hunk-заголовки
`@@ -a,b +c,d @@` → строки `c..c+d-1`); номер строки в отчёте — реальный.

### BR-582 — `tools/check-no-legacy-tbl-settings.sh:71` (tmp-file-predictable)

**Что не так.** Промежуточные файлы писались в предсказуемые `/tmp/_nocomm.txt`
и `/tmp/_sql.txt` — другой пользователь машины мог создать их заранее или
подменить symlink'ом.

**Как правильно.** Приватный `mktemp -d` + `trap`-очистка (как в
`check-endpoint-field-coverage.sh`). Дополнительное локация BR'а —
`check-eslint-baseline.sh:117` (`/tmp/eslint-current*.json`) — закрыта там же.

### BR-583 — `tools/auto-jsdoc.py:78–80` (autodoc-missing-docs-ref)

**Что не так.** Генераторы писали `@see docs/features/<slug>.md`, но живые
per-feature документы лежат в `archive/docs/features/` — ссылки в исходниках
были битыми (в самих исходниках уже стоит правильный `archive/...`).

**Как правильно.** `NAME_SLUG_OVERRIDE` (auto-jsdoc.py),
`DIR_TO_SLUG`/`CLASS_SLUG_OVERRIDE` (auto-kdoc.py) переведены на
`archive/docs/features/`; в `auto-kdoc-quality.py` `@see` вынесен из единого
шаблона в `CLASS_SEE` (хранится вместе с описанием класса). Во всех трёх
генераторах существование `@see`-цели **проверяется перед записью** в исходники
и дублируется в `--self-test` — битая ссылка больше не уходит в код молча.

### BR-585 — `tools/auto-kdoc.py:196–202` (autokdoc-second-kdoc-clobber)

**Что не так.** Наличие KDoc определялось просмотром фиксированного окна в 8
строк (`range(max(0, decl_line - 8), decl_line)`). KDoc длиннее 8 строк
(описание с `@param`/`@return`) в окно не попадал → генератор вставлял
**второй** KDoc прямо над существующим.

**Как правильно.** Поиск по закрывающему `*/` с последующим подъёмом до
`/**` — как уже сделано в `tools/auto-kdoc-quality.py:89-104`.

### BR-586 — `tools/check-enforcement.sh:50` (enforcement-gnugrep-always-ok)

**Что не так.** Подсчёт `enforced` шёл через `grep -cP` (PCRE). На сборках без
PCRE grep падал, stderr глушился, результат был пустым, `[ "" -gt 0 ]` давало
«integer expression expected» — и скрипт всё равно печатал «OK: enforcement
покрыт» и завершался с 0. Скрипт **никогда** не возвращал ненулевой код.

**Как правильно.** Переносимый `grep -cE` (POSIX ERE); счётчики проверяются
на «число» (иначе код 2); проверка, которая не могла быть выполнена (нет
`CONTRIBUTING.md` / нет MUST-правил), даёт код 2.

### BR-596 — `tools/check-spec-issue-link.py:68–89` (guard-issue-id-format-mismatch)

**Что не так.** Guard **принимал** два формата поля Issue ID (`**Issue ID**`
и заголовок `### Issue ID`), но значение извлекал только из жирного формата.
Спека с заголовком проходила проверку «поле есть», `issue_id` оставался
`None` → `has_issue = False` → обязательные Workflow-команды
(`tracker.sh claim-issue/add-comment/mark-review`) **не проверялись**.
Guard fail-open на целый класс спек.

**Как правильно.** Значение извлекается в обоих форматах (в той же строке или
на следующих непустых строках); поле без значения — ошибка, а не «issue нет».

### BR-597 — `tools/check-subagent-isolation.sh:79–86` (guard-failopen-no-master)

**Что не так.** `git log "master..${current_branch}"` при отсутствии ref'а
`master` (или битом клоне) молча отдавал пустоту, guard печатал «⚠️ ветка не
содержит новых commits» и возвращал 0. «Не удалось вычислить» и «нарушений
нет» были неразличимы.

**Как правильно.** Код возврата `git rev-parse`/`git log` проверяется; базовая
ветка разрешается явно (`master` → `origin/master` → `main` → `origin/main`);
невозможность проверить → код 2 (fail-closed).

### BR-598 — `tools/generate-docs.sh:13` (pipeline-hides-exit-code)

**Что не так.** В скрипте был только `set -e` (без `-o pipefail`), а все четыре
шага сборки — пайпы в `tail -3`. Код возврата пайпа = код `tail` (всегда 0),
поэтому падение dokka/typedoc не прерывало скрипт: он доходил до
«✅ Готово!» при отсутствующей документации.

**Как правильно.** `set -euo pipefail` + явная проверка кода возврата каждого
шага (`if ! … ; then echo "ERROR: …"; exit 1; fi`).

### BR-600 — `tools/install-tracker.sh:130–135` (compose-up-error-swallowed)

**Что не так.** `docker compose … up -d 2>&1 | grep … | tail -10 || true`
проглатывал код возврата compose: при падении скрипт печатал
«✓ Контейнеры запущены» и уходил дальше.

**Как правильно.** Вывод compose сохраняется отдельно, его код возврата
проверяется, фильтрация вывода не влияет на код; падение → `exit 1`.

### BR-603 — `tools/tracker-poll.sh:33` (default-assignee-before-env)

**Что не так.** `ASSIGNEE="${TRACKER_AGENT_USER:-ai-agent}"` вычислялось
**до** `tracker_load_env` (строка 68). В этот момент `.env.local-tracker` ещё
не был прочитан → скрипт молча опрашивал `ai-agent` вместо пользователя из env.

**Как правильно.** Значение по умолчанию вычисляется после `tracker_load_env`;
явный `--assignee` имеет приоритет.

### BR-604 — `tools/tracker-lib.sh:168–182` (http-code-unreachable-branch)

**Что не так.** `curl -w "%{http_code}" … || echo "000"` при ошибке соединения
печатал в stdout `000` (из `-w`) **и** дописывал ещё `000` из `||` → `000000`.
Такое значение не совпадало ни с одной веткой `case` → guard сообщал
«неожиданный HTTP статус 000000» и возвращал 5 вместо 2. Ветка «соединение не
установлено» была недостижима.

**Как правильно.** Код возврата `curl` проверяется явно, статус приводится к
`"000"`. То же — в `tracker_healthcheck`.

### BR-606 — `tools/tracker-smoke-test.sh:74–84` (set-e-kills-diagnostics)

**Что не так.** Под `set -euo pipefail` присваивание
`PROJECTS_LINES=$(… | grep -v … | grep -v … | wc -l)` при пустом выводе
убивало скрипт: `grep` без совпадений возвращает 1, `pipefail` пробрасывает
код в пайп, `errexit` — в скрипт. Вместо диагностики
«list-projects (нет проектов)» пользователь видел тихий выход с кодом 1.

**Как правильно.** `|| true` в пайпе + явная проверка. Дополнительно устранён
SIGPIPE-риск в `grep -q … <<< "$VAR"` и `head -1` (код 141 ломал пайп под
`pipefail`).

### BR-607 — `tools/tracker-bootstrap-board.sh:167–175` (set-e-hides-rails-error)

**Что не так.** `OUT=$(docker exec … 2>&1 | tail -1)` под `set -euo pipefail`
при падении rails-runner убивал скрипт прямо на этой строке — объявленная
диагностика «Не удалось создать/обновить доску: …» не печаталась никогда.

**Как правильно.** Код возврата `docker exec` проверяется; в сообщение идёт
**полный** вывод (раньше `tail -1` отрезал причину ошибки). То же — для шага
workflow-transitions.

### BR-608 — `tools/tracker-bootstrap-board.sh:142–163` (hardcoded-status-ids)

**Что не так.** Правила переходов были зашиты числами
`rules = [[1, 9], [7, 9], [3, 9], [9, 7], [9, 12], [12, 9]]` — это дефолтные
ID статусов OpenProject (1 = New, 3 = Specified, 7 = In progress,
9 = In review, 12 = Closed; проверено read-only запросом
`GET /api/v3/statuses`). На другой инсталляции числа указывают на другие
статусы. Шаг workflow-transitions при этом всегда рапортовал успех
(`ADDED=$(… jq '.added // 0')` → 0 при пустом выводе).

**Как правильно.** Статусы резолвятся по имени (`Status.find_by(name: …)`);
переходы, для которых статуса нет, пропускаются и **перечисляются** в выводе
(`missing_statuses`); роли резолвятся по имени с числовым fallback; шаг больше
не рапортует успех при неудаче.

### BR-609 — `tools/tracker-implement-done.sh:93–123` (stub-report-not-report)

**Что не так.** При отсутствии `report.md` скрипт генерировал stub-отчёт из
`git log` и **публиковал его комментарием**, а затем переводил задачу в
In review. В трекере оказывался отчёт-заглушка («замените на настоящий
отчёт»), а workflow OpenProject считался выполненным без отчёта.

**Как правильно.** Отсутствие `report.md` — ошибка с кодом 1: комментарий не
публикуется, статус не меняется.

### BR-610 — `tools/tracker-bootstrap.sh:44–54` (issue-id-regex-false-positive)

**Что не так.** `оп\w*`/`op\w*` без ограничения слова матчили обычные слова:
«опубликация 5» → 5, «операция 4» → 4, «operation 7» → 7, «drop 9» → 9 —
агент получал claim **чужой** задачи.

**Как правильно.** «оп»/«op» — только отдельное слово (перед ним обязателен
не-буквенный разделитель); «задача/задачи/таск/task» — по-прежнему с
любым окончанием; «openproject»/«open project» — как раньше. Добавлен
`--self-test` (14 положительных + 8 отрицательных фикстур).

### BR-612 — `tools/specify-bootstrap.sh:150–153` (preflight-marker-manual)

**Что не так.** `RESERVE_OUT="$(./tools/reserve-branch-number.sh "$SLUG" 2>/dev/null || true)"`
проглатывал и stderr, и код возврата. При сбое (нет сети, конфликт тега)
`FEATURE_NUM` оказывался пустым → `BRANCH="-<slug>"` — ветка создавалась с
мусорным именем, ошибка терялась.

**Как правильно.** stderr показывается, код возврата проверяется, `FEATURE_NUM`
обязан быть числом — иначе ветка не создаётся. Дополнительно: файл-маяк
`.specify/.preflight-<slug>.ok` теперь создаёт сам pre-flight
(`tools/spec-knowledge-preflight.sh --create-marker <slug>`), а не агент
вручную через `touch` — это и есть `preflight-marker-manual` в
root_cause_key.

### BR-613 — `tools/tracker-spec-for-issue.sh:90–94` (default-next-num-unreachable)

**Что не так.** `NEXT_NUM="${NEXT_NUM:-0}"` был недостижим: под
`set -euo pipefail` пайп `ls | grep | grep | sort | tail` падал, если ни одной
спеки не найдено, и скрипт умирал на этой строке — до дефолта.

**Как правильно.** Пайп обёрнут в `|| true`, результат валидируется как число;
нет спек → `NEXT_NUM=1`.

### BR-614 — `tools/tracker-lib.sh:139–140` (audit-log-error-swallowed)

**Что не так.** `mkdir -p … || true` и `echo "$entry" >> "$LOG_FILE" || true`
проглатывали ошибку записи аудит-лога полностью: при недоступном каталоге не
было ни ошибки, ни следа.

**Как правильно.** Ошибка → предупреждение в stderr **один раз за сессию**
(флаг `TRACKER_AUDIT_WARNED`), а не на каждый вызов.

## Негативные тесты (до/после)

Все сценарии воспроизводимы. «ДО» — оригинал из `git show HEAD:tools/<файл>`,
«ПОСЛЕ» — исправленный файл в worktree.

### 1. BR-575 — счётчик умножается на 13

Фикстура: git-репозиторий, в `deploy/do.sh` добавлена строка
`docker-compose -f deploy/docker-compose.yml restart`.

```
=== BR-575 ДО (оригинал) ===
❌ НАРУШЕНИЕ: deploy/do.sh содержит 'docker-compose/docker compose ... restart'
Найдено нарушений: 13 (контейнеры: docker-compose docker-compose docker-compose
  docker-compose docker-compose docker-compose docker-compose docker-compose
  docker-compose docker-compose docker-compose docker-compose)
exit=1

=== BR-575 ПОСЛЕ (исправленный) ===
❌ НАРУШЕНИЕ: deploy/do.sh содержит 'docker-compose/docker compose ... restart'
Найдено нарушений: 1 (файлы: deploy/do.sh)
exit=1
```

### 2. BR-577 — проверялся не тот источник полей

Фикстура: пара `SongEdit.vue ↔ /api/song/update`, контроллер
`FakeController.songs2Update` **не вызывает** `SongUpdateMapper`, но
`SongUpdateMapper.kt` лежит по боевому пути (`.../karaokeapp/controllers/`),
а компонент шлёт `v-model="song.alpha"`.

```
=== BR-577 ДО (оригинал) ===
[PASS] webvue3/src/components/Songs/edit/SongEdit.vue ↔ /api/song/update (1/1)
=== ИТОГО: 1/1 пар PASS ===
exit=0                       ← зелёный, хотя контроллер поля alpha не принимает

=== BR-577 ПОСЛЕ (исправленный) ===
[FAIL] webvue3/src/components/Songs/edit/SongEdit.vue ↔ /api/song/update (0/1, MISSING=1):
  MISSING: alpha
=== ИТОГО: 0/1 пар PASS (из 1 в endpoint-pairs.yml) ===
exit=1
```

### 3. BR-573 — «нет данных» = «нет нарушений»

Та же фикстура, но `controller:` указывает на несуществующий файл.

```
=== BR-573 ДО (оригинал) ===
[WARN] controller не найден: …/pkg/AbsentController.kt (пропускаем)
=== ИТОГО: 0/1 пар PASS ===
exit=0                       ← пара не проверена, guard «зелёный»

=== BR-573 ПОСЛЕ (исправленный) ===
[ERROR] controller не найден: …/pkg/AbsentController.kt — пару проверить невозможно
=== ИТОГО: 0/0 пар PASS (из 1 в endpoint-pairs.yml) ===
FAIL: 1 пар невозможно проверить (нет component/controller/method или нет v-model полей).
      Пустой набор данных — не «нарушений нет»: проверка должна быть выполнена.
exit=1
```

### 4. BR-580 — ложное срабатывание из-за контекста diff

Фикстура: в `deploy/do.sh` закоммичено `cd webvue3`, затем добавлена строка
`npm run build` (в diff'е `cd webvue3` — строка контекста).

```
=== BR-580 ДО (оригинал) ===
❌ НАРУШЕНИЕ: deploy/do.sh строка 1 — 'npm run' без 'cd webvue3'/'cd karaoke-public'
   Текущая строка: npm run build
Найдено нарушений: 1
exit=1                       ← ложное срабатывание + неверный номер строки (1 вместо 3)

=== BR-580 ПОСЛЕ (исправленный) ===
✅ Все вызовы npm run имеют правильный cd webvue3/cd karaoke-public
exit=0
```

Настоящее нарушение (добавленная строка `npm run build` без `cd`) по-прежнему
ловится — это кейс `no-cd` в `--self-test` (ожидается 1 нарушение).

### 5. BR-579 — код возврата eslint отброшен

Фикстура: модуль с битым `.eslintrc.json` (eslint падает с кодом 2).

```
=== BR-579 ДО (оригинал) ===
В baseline:        0
…/check-eslint-baseline.sh: строка 146: [: : ожидается целочисленное выражение
…/check-eslint-baseline.sh: строка 156: [: : ожидается целочисленное выражение
OK: новых нарушений нет
exit=0                       ← eslint упал, guard отрапортовал «нарушений нет»

=== BR-579 ПОСЛЕ (исправленный) ===
ERROR: eslint завершился с кодом 2 — это сбой самого линтера
       (не «найдены нарушения»): результат проверки недостоверен.
--- stderr eslint ---
ESLint: 8.57.1
Failed to read JSON file at …/.eslintrc.json:
Error: Expected property name or '}' in JSON at position 2 (line 1 column 3)
exit=2
```

### 6. BR-597 — fail-open, когда git недоступен

```
=== BR-597 ДО (оригинал, вне git-репозитория) ===
✅ На master — isolation check not applicable
exit=0                       ← проверка не выполнялась, guard «зелёный»

=== BR-597 ПОСЛЕ (исправленный) ===
❌ Не удалось определить текущую ветку (git rev-parse failed).
   Проверка изоляции НЕ выполнена — fail-closed, а не «всё хорошо».
exit=2
```

### 7. BR-604 — ветка «соединение не установлено» была недостижима

```
=== BR-604 ДО (оригинал) ===
ERROR: неожиданный HTTP статус 000000
exit=5                       ← неверная ветка и неверный код

=== BR-604 ПОСЛЕ (исправленный) ===
ERROR: OpenProject недоступен по http://127.0.0.1:1, проверьте `docker ps | grep openproject`
exit=2
```

### 8. BR-586 — подсчёт без PCRE и «всегда успех»

```
=== BR-586 ДО (оригинал), CONTRIBUTING без MUST-правил ===
INFO: нет MUST-правил в … — нечего проверять
exit=0
=== BR-586 ДО (оригинал), файла нет ===
INFO: … не существует — пропускаю проверку enforcement.
exit=0

=== BR-586 ПОСЛЕ (исправленный), без MUST-правил ===
ERROR: нет MUST-правил в … — проверять нечего (набор данных пуст).
exit=2
=== BR-586 ПОСЛЕ (исправленный), файла нет ===
ERROR: … не существует — проверить enforcement невозможно.
exit=2
```

### 9. Остальные — `--self-test` фикстуры

| Guard | Фикстур | Что проверяется |
|---|---|---|
| `check-endpoint-field-coverage.sh` | 4 | корректное дерево → PASS; контроллер без маппера → FAIL; нет контроллера → FAIL; пустой `endpoint-pairs.yml` → FAIL |
| `check-container-restart.sh` | 6 | чистый контент → 0; `docker restart/stop/start` → 1; `docker-compose`/`docker compose … restart` → **1** (BR-575) |
| `check-docker-image-tags.sh` (`--selftest`) | 14 | в т.ч. `nginx-unprivileged:1.25` → `unrecognized` (BR-576) |
| `check-frontend-build.sh` | 6 | `cd` из контекста diff'а → 0 (BR-580); `npm run` без `cd` → 1; `cd` в другой каталог → 1; комментарий между `cd` и `npm run` → 0 |
| `check-enforcement.sh` | 4 | корректный CONTRIBUTING → 0 + числа; нет файла → 2; нет MUST-правил → 2; в скрипте нет `grep -P` |
| `check-spec-issue-link.py` | 8 | оба формата Issue ID; спека с заголовком → Workflow-команды **проверяются** (BR-596) |
| `auto-jsdoc.py` | 65 целей | все `@see`-цели существуют |
| `auto-kdoc.py` | 4 | цели существуют; длинный KDoc (>8 строк) распознан; короткий распознан; без KDoc — вставка нужна |
| `auto-kdoc-quality.py` | 2 | цели существуют; `@see` не единый шаблон |
| `tracker-bootstrap.sh` | 14 + 8 | Issue ID распознаётся в 14 формах; 8 ложных срабатываний устранены (BR-610) |

### 10. Негативные тесты скриптов трекера (без `--self-test`)

| BR | Команда | Ожидаемо |
|---|---|---|
| BR-609 | `bash tools/tracker-implement-done.sh 402` (нет `report.md`) | exit 1, комментарий не опубликован, статус не изменён |
| BR-603 | `TRACKER_AGENT_USER=env-agent` в `.env.local-tracker` + прогон `tracker-poll.sh` | `ASSIGNEE=env-agent`, а не `ai-agent` |
| BR-614 | `tracker_log_audit` при недоступном `LOG_FILE` | `WARN: не удалось записать аудит-лог: …` в stderr |
| BR-613 | `NEXT_NUM` при пустом `specs/` | `NEXT_NUM=1`, скрипт не умирает |
| BR-612 | `specify-bootstrap.sh` при сбое `reserve-branch-number.sh` | stderr показан, exit ≠ 0, ветка не создана |

Фактический вывод (BR-609):

```
[tracker-implement-done] detected Issue ID: #402
[tracker-implement-done] ERROR: отчёт не найден: каталог спеки для Issue #402 не определён.
[tracker-implement-done]        Комментарий НЕ публикуется, задача НЕ переводится в In review.
[tracker-implement-done]        Создайте отчёт о работе: specs/<NNN>-<slug>/report.md
exit=1
```

Фактический вывод (BR-614):

```
WARN: не удалось записать аудит-лог:  — аудит не записан
```

## Прогоны

### Каждый изменённый guard на чистом дереве → PASS

| Guard | Команда | exit |
|---|---|---|
| `check-endpoint-field-coverage.sh` | `bash tools/check-endpoint-field-coverage.sh` | 0 |
| `check-container-restart.sh` | `bash tools/check-container-restart.sh` | 0 |
| `check-docker-image-tags.sh` | `bash tools/check-docker-image-tags.sh --quiet` | 0 |
| `check-eslint-baseline.sh` | `bash tools/check-eslint-baseline.sh webvue3` | 0 |
| `check-frontend-build.sh` | `bash tools/check-frontend-build.sh` | 0 |
| `check-no-legacy-tbl-settings.sh` | `bash tools/check-no-legacy-tbl-settings.sh` | 0 |
| `check-enforcement.sh` | `bash tools/check-enforcement.sh` | 0 |
| `check-spec-issue-link.py` | `python3 tools/check-spec-issue-link.py` | 0 |
| `check-subagent-isolation.sh` | `bash tools/check-subagent-isolation.sh` | 0 |

Все `--self-test` / `--selftest` — exit 0 (см. таблицу выше).

### Требуемый CI-набор

| Проверка | exit |
|---|---|
| `bash tools/check-knowledge-structure.sh` | 0 |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | 0 |
| `python3 tools/check-doc-references.py` | 0 |
| `python3 tools/check-recordhash-consistency.py` | 0 |
| `bash tools/check-no-mp4-mentions.sh` | 0 |
| `bash tools/check-feature-doc.sh docs/features/*.md` | 0 |
| `bash tools/check-subagent-isolation.sh` | 0 |
| `bash tools/check-file-permissions.sh` | 0 |
| `python3 tools/check-ssot-impact.py` | 0 |
| `bash tools/check-no-personal-fixtures.sh` | 0 |
| `bash tools/check-audit-coverage.sh` | 0 |

### Полный набор guard'ов из `.github/workflows/lint.yml`

| Джоб / guard | exit |
|---|---|
| `check-actions-pinned.sh` (+ `--self-test`) | 0 / 0 |
| `check-root-junk.sh` | 0 |
| `check-eslint-baseline.sh webvue3` / `karaoke-public` | 0 / 0 |
| `check-confirm-escape.py` | 0 |
| `check-confirm-escape-fixtures.sh` | 0 |
| `check-marker-escape-guard.sh` | 0 |
| `check-secret-in-argv.sh` (+ `--self-test`) | 0 / 0 |
| `check-file-permissions.sh` (+ `--self-test`) | 0 / 0 |
| `check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | 0 |
| `baseline-stats.sh` (informational) | 0 |
| `check-kdoc-coverage.sh --strict` | 0 |
| `check-jsdoc-coverage.sh --strict` | 0 |
| `check-ssot-impact.py` | 0 |
| `check-knowledge-structure.sh` | 0 |
| `check-no-legacy-tbl-settings.sh` | 0 |
| `check-recordhash-consistency.py` | 0 |
| `check-knowledge-cross-links.sh` | 0 |
| `lint-knowledge.py --baseline …` | 0 |
| `check-doc-references.py` | 0 |
| `check-spec-issue-link.py` | 0 |
| «No direct commits to master» (git log по `origin/master`) | 0 |
| `check-docker-image-tags.sh --quiet` | 0 |
| `check-no-jpa-imports.sh --quiet` | 0 |
| `check-value-placeholders.sh` | 0 |
| `check-no-mp4-mentions.sh --quiet` | 0 |
| `check-no-personal-fixtures.sh --quiet` | 0 |
| `check-security-headers.py` | 0 |

Предсуществующих падений не обнаружено: **все** guard'ы проходят и на `master`
(проверено до правок), и после.

### Бэкенд

Kotlin-исходники пакет не трогает, но прогон выполнен по регламенту
(с `--rerun-tasks`, иначе Gradle рапортует `UP-TO-DATE` по закешированным
классам):

```
env -u DISPLAY GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle \
  ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
    -Djava.awt.headless=true \
    :karaoke-app:test :karaoke-web:test ktlintCheck --rerun-tasks

BUILD SUCCESSFUL in 1m 7s
29 actionable tasks: 29 executed
GRADLE_EXIT=0
```

## За владельцем

1. **Роли в `tracker-bootstrap-board.sh`.** ID статусов резолвятся по имени
   (BR-608). Роли по-прежнему берутся по ID `[1, 6, 8]` (на текущем экземпляре
   это «Work package editor», «Member», «Project admin» — сверено read-only
   запросом `GET /api/v3/roles`). В коде добавлен резолв по имени с числовым
   fallback'ом, но фактический прогон rails-runner'а не выполнялся (запрет на
   работу с контейнерами/БД в этом пакете) — **нужен один ручной прогон**
   `bash tools/tracker-bootstrap-board.sh` на dev-инстансе для подтверждения,
   что переходы «In review» создались.
2. **Семейство nginx/node в `check-docker-image-tags.sh`.** Совпадение
   расширено до `nginx*`/`node*` (BR-576), чтобы ветка самодиагностики стала
   достижимой. Побочный эффект: образ вроде `prom/node-exporter` будет
   помечен `UNRECOGNIZED` (fail) — сейчас таких образов в репозитории нет.
   Если владелец хочет более узкое правило (например, только `nginx-*` и
   `node-*` с дефисом) — это решение за владельцем.
3. **Политика `check-enforcement.sh` при отсутствии `CONTRIBUTING.md`.**
   Теперь — код 2 («проверка не выполнена»). Скрипт в CI не вызывается, но
   решение (считать ли отсутствие файла ошибкой) — за владельцем.
4. **Политика `check-endpoint-field-coverage.sh` при пустом наборе пар.**
   Пустой `endpoint-pairs.yml` → код 2. Сейчас в файле одна MVP-пара; когда
   пары добавятся, проверка продолжит работать без изменений.
5. **`tools/tracker-bootstrap.sh`: «ОР #69».** Регулярка распознаёт
   кириллическое «ОП #69» (и латинское «OP #69»), но **не** «ОР #69» —
   так было и до правки; при необходимости — отдельная задача.

## Что не менялось

- `specs/336-code-audit/registry.csv` — реестр аудита не трогался (владелец
  данных аудита). Статусы записей (`TODO` → `fixed`) обновляет владелец
  аудита.
- Чужие пакеты, деплой, контейнеры, прод, БД — не трогались.
- `tools/check-endpoint-field-coverage.whitelist.yml` — остался пустым
  (используется per-pair whitelist, см. BR-577).

## Хэши изменённых файлов (sha256)

```
940b541256c1a2bc3150deb69b1eb39814edcc373aaab00e7116673601df2d7b  knowledge/system/infra/ci-tools.md
9b316b4713ed3bf145993cc7b854184d650070ce8e02ca13567c4c7d43e626e4  tools/auto-jsdoc.py
699e7019f77b7477b9f765653342f2bae4ff93798a097af0d8784618a411e515  tools/auto-kdoc-quality.py
6b33c54e2f6c2d77ae30e30ac1cad0de30614a22f7e78bf6a67f4120066ae432  tools/auto-kdoc.py
47bc20eed6790e9b8e2e6a35d1c09c697fb4929e1420add46485cc28d143e4e4  tools/check-container-restart.sh
d7445ccf711915f1236cb3cd1466a688ec427bdbce19dcd741645620fbe92707  tools/check-docker-image-tags.sh
fb87fedb03d430f92edcec9a0fb8c7140656d3c61ba74972a81b222223c84bab  tools/check-endpoint-field-coverage.sh
30bfb4d79be5f01ded60dc347f0cb7f7fac665201a5353e69d3b87edc80178a0  tools/check-enforcement.sh
06abffd45448d48360ab840b550917e08426b9394d29c140892cc21e3a481558  tools/check-eslint-baseline.sh
12804e6b75ffdc98fe3aa19ae59661e6fa2f616e8bfece68a1fd6e60329fac38  tools/check-frontend-build.sh
3d717e4feaa208a7ebd445526431760b64feaa7eb22a07d85973ee20053b4ca3  tools/check-no-legacy-tbl-settings.sh
b495943c0e67dbfc9d2aea8719d84cbff6c2600bf666add96a86f836f63fe764  tools/check-spec-issue-link.py
6ce96d607d48d9d3af4fb9d1ec34bb1affc488a6fe05f7f4fe2420485d3cdf05  tools/check-subagent-isolation.sh
1cf9c7f3a08d1e113a24cfc8a82a5a902af525ce0a497cd5343027d4c7fade3e  tools/generate-docs.sh
18acbc6e0f7527ab2aef2f693d6f18dd4c53fe4c2d49821fbf6552a37f9e4ef7  tools/install-tracker.sh
7e87bc7b56c7ba09f8abcab12865cce2e3648536ac507e94354fc4a53968896a  tools/spec-knowledge-preflight.sh
f2e6ef8c858c05ddc11e7a3bbb9254ec4a0ee2861859fcecde4f1a332a983210  tools/specify-bootstrap.sh
8acf233d729b9b06e84fc4d85d2251a8f0f533ab4eb91ed143b5a1a666fc3607  tools/tracker-bootstrap-board.sh
51e5ec68f23088b383bac0e0dfc0809342e738ffbd13c6fd3f4546865562188a  tools/tracker-bootstrap.sh
3478b5c9520031035cfc55b39c84dc85551014232d1a4ee5527d021ab06966f5  tools/tracker-implement-done.sh
a76d768636ccb1d3f4129fcf74cee86241a31a4c7754874bc3425207eea82091  tools/tracker-lib.sh
76d8aa13b445c56e623eab85130100c99c4709eb3aef0621277a2f09499d85e4  tools/tracker-poll.sh
e015bba7998de0a6387022dfb84f5266834d05555ca0096f783f82f7c7ff52d3  tools/tracker-smoke-test.sh
f9e9bf71638182c950fdc6becd1a74cb544065bae27656add62ad9dbce7cd174  tools/tracker-spec-for-issue.sh
428cf247a0fd6fa9993c2bdbf189cc6de315e962ccd3d2972039c57679471a3e  docs/features/pkg-s3-30.md
```

## Ветка и PR

- Ветка: `633-pkg-s3-30` (от `origin/master`, коммит базы `be113f8b`).
- PR и push **не выполнялись** (по условию задачи) — ветка готова к PR.
- `git commit --no-verify` не использовался; коммит сделан обычным путём.

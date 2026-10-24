# Отчёт #379 — PKG-S2-16 (волна C, S2): `tools/` — DATA, ERR, SEC

> **Issue**: OpenProject #379 (пакет `PKG-S2-16`, бэклог аудита #336)
> **Ветка**: `581-pkg-s2-16` от `master` @ `534766da`
> **Дата**: 2026-10-24
> **Записи пакета**: BR-571, BR-572, BR-593, BR-594, BR-595, BR-601
> **Источник правды**: карточки `specs/336-code-audit/findings/` (P-75-02, P-75-03,
> P-76-01, P-76-02, P-76-03, P-77-01) + ТЗ `specs/336-code-audit/backlog.md`
> (§ `### PKG-S2-16`).

## Итог

| BR | Файл | Класс | Было | Стало | Регрессия |
|---|---|---|---|---|---|
| BR-571 | `tools/check-do-sh-guards.sh` | DATA | опыт A перезаписывал отслеживаемый `deploy/karaoke-public/Dockerfile` и не имел `trap` — Ctrl-C/SIGTERM оставлял в репозитории `FROM scratch` и `.guardtest.bak` | опыт A идёт на **копии** `deploy/` во временном каталоге (`mktemp -d` + `trap … EXIT INT TERM`); отслеживаемый файл не пишется вообще; проверяется, что падение вызвано Dockerfile, а не невалидным тегом | до/после + сам guard: 3 PASS |
| BR-572 | `tools/check-feature-doc.sh` | ERR | `check_doc` возвращал 1 при отсутствующем файле, вызывающая сторона код возврата игнорировала, `errors_total` не рос → «ERROR» в выводе и **exit 0** | `check_doc "$f" \|\| errors_total=$((errors_total+1))` → exit 1 | до: rc=0, после: rc=1, контроль на реальных документах: rc=0 |
| BR-593 | `tools/registry-smoke.sh` | SEC | блок `[3]` вызывал `check` без 4-го аргумента → `http_code` не добавлял `-u` и уходил тот же **анонимный** GET, что и `[1]`; строка контракта «неверный пароль → 401» не проверялась никогда | `check 401 … "$BASE/v2/" 1 "$WRONG_USER" "$WRONG_PASS"` — заведомо неверная пара, `$PASS_CRED` не переиспользуется; креды и Bearer-токен передаются curl через stdin-конфиг (`--config -`) | до: ложный PASS (exit 0) на registry со «сломанным htpasswd», после: exit 1 + запрос `[3]` с `Authorization` |
| BR-594 | `tools/registry-vendor.sh` | DATA | при неудачном `manifest_digest` в lock-файл писалась строка с `PENDING`, затирая прежний `sha256:…`; `--verify` считал `PENDING` за `skip` — пин молча переставал проверяться | пин **не трогается**: `digest_known=0`, строка lock не переписывается, в вывод — `SKIP` + `WARN`; `PENDING` в `--verify` — **FAIL** | до: в lock `PENDING`, `--verify` rc=0; после: `sha256:aaaa1111…` сохранён, `--verify` rc=1 |
| BR-595 | `tools/generate-eslint-baseline.sh` | DATA | вывод eslint писался прямо в отслеживаемый `.eslint-baseline.json` → при падении eslint файл оставался нулевой длины; код возврата не использовался, stderr глушился | `mktemp` для вывода/eslint-ошибок/baseline, stderr не глушится, код ≥ 2 — ошибка eslint (baseline не трогаем), пустой stdout — ошибка, `mv` на место только после успешного `jq` | до: размер baseline 0 байт, rc=0; после: размер 77, rc=1; успешный путь: rc=0, 1 нарушитель |
| BR-601 | `tools/tracker-poll.sh` | ERR | `… 2>&1 \|\| true`, `OPEN_CODE=$?` брал код присваивания (0) → текст ошибки трекера попадал в `parse_lines` и считался «задачами»; агент видел «✓ Все задачи закрыты» при недоступном OpenProject | stdout и stderr разделены (stderr — в `mktemp`), код возврата `tracker.sh` сохраняется: **2** — OpenProject недоступен, **3** — токен истёк; сводки «в ревью»/«недавно закрытые» при сбое помечаются неполными | до: rc=1 и «Все задачи закрыты»; после: rc=2 / rc=3, строки нет; рабочий путь: rc=1 («открытых нет»), строка на месте |

## Регрессия «до/после»

Артефакт: [`specs/379-pkg-s2-16/regression.sh`](regression.sh) + заглушка registry
[`stub-registry.py`](stub-registry.py). Всё на заглушках: локальный HTTP-сервер вместо
registry, подставные `docker`/`curl`/`npx`/`tracker.sh`; версии «ДО» извлекаются из
`BASE_REF=534766da` (`git show`). Реальные секреты не печатаются, контейнеры/прод/БД
не затрагиваются.

```bash
bash specs/379-pkg-s2-16/regression.sh
```

Вывод (30 проверок, 0 провалов):

```
=== BR-571 — отслеживаемый Dockerfile не переписывается (tools/check-do-sh-guards.sh) ===
  PASS  ДО: дефект воспроизведён — после прерывания отслеживаемый Dockerfile изменён
        FROM scratch
  PASS  ПОСЛЕ: отслеживаемый Dockerfile не изменён после прерывания = fbf88227ee07bfea…
  PASS  ПОСЛЕ: мусорный .guardtest.bak не остался

=== BR-572 — отсутствующий per-feature документ = ошибка (tools/check-feature-doc.sh) ===
  PASS  ДО: код возврата на несуществующем файле = 0
  PASS  ПОСЛЕ: код возврата на несуществующем файле = 1
  PASS  ПОСЛЕ: контроль — реальные документы валидны = 0

=== BR-593 — «неверный пароль → 401» реально отправляет креды (tools/registry-smoke.sh) ===
  PASS  ДО: exit на registry со сломанным htpasswd (ложный PASS) = 0
  PASS  ПОСЛЕ: exit на registry со сломанным htpasswd (дефект пойман) = 1
  PASS  ПОСЛЕ: запрос проверки [3] ушёл с Authorization-заголовком (креды отправлены)

=== BR-594 — дайджест-пин не затирается значением PENDING (tools/registry-vendor.sh) ===
  PASS  ДО: что записано в колонку digest = PENDING
  PASS  ПОСЛЕ: пин в колонке digest сохранён = sha256:aaaa1111bbbb2222
  PASS  ДО: --verify при digest=PENDING (пин молча не проверяется) = 0
  PASS  ПОСЛЕ: --verify при digest=PENDING (ошибка проверки) = 1

=== BR-593/BR-594 — guard check-secret-in-argv: ДО видит нарушения, ПОСЛЕ — нет ===
  PASS  ДО (старые tools + новый baseline): exit guard = 1
  PASS  ДО: guard перечислил записей registry-smoke/registry-vendor = 5
  PASS  ПОСЛЕ (исправленные tools + тот же baseline): exit guard = 0
  PASS  ПОСЛЕ: guard не видит ни одного нарушения в registry-smoke/registry-vendor
  PASS  self-test guard'а (все правила ловят плохое и не ловят хорошее) = 0

=== BR-595 — baseline ESLint не обнуляется при падении eslint (tools/generate-eslint-baseline.sh) ===
  PASS  ДО: размер baseline после падения eslint = 0
        ДО: код возврата скрипта = 0
  PASS  ПОСЛЕ: baseline не тронут (размер) = 77
  PASS  ПОСЛЕ: код возврата скрипта = 1
  PASS  ПОСЛЕ: успешный путь — код возврата скрипта = 0
  PASS  ПОСЛЕ: успешный путь — в baseline записан 1 нарушитель = 1

=== BR-601 — ошибка трекера возвращается кодом, а не «задач нет» (tools/tracker-poll.sh) ===
  PASS  ДО: код возврата при недоступном трекере = 1
  PASS  ДО: дефект воспроизведён — вывод утверждает «Все задачи закрыты»
  PASS  ПОСЛЕ: код возврата при недоступном трекере = 2
  PASS  ПОСЛЕ: вывод не утверждает «Все задачи закрыты»
  PASS  ПОСЛЕ: код возврата при истёкшем токене = 3
  PASS  ПОСЛЕ: код возврата при доступном трекере и пустом списке = 1
  PASS  ПОСЛЕ: пустой список по-прежнему означает «Все задачи закрыты»

--- итог: PASS=30 FAIL=0 ---
```

### BR-593/BR-594 — доказательство guard'ом

```bash
bash tools/check-secret-in-argv.sh            # → ✅ OK: все 1 совпадений — в baseline (exit 0)
bash tools/check-secret-in-argv.sh --self-test # → ✅ self-test: все правила работают (exit 0)
```

До правки guard **видел** нарушения: в `tools/check-secret-in-argv.baseline` лежали
5 записей (`registry-smoke.sh` ×4, `registry-vendor.sh` ×1 — `-u user:pass` и
`-H "Authorization: Bearer …"` в argv curl). После правки этих совпадений нет:
из baseline убраны все записи этих двух файлов, guard сообщает о 1 остаточном
совпадении — это запись `tools/analyze-prod-incident.sh`, закрытая отдельно
(BR-626, #408 / PR #675); она **не возвращалась** (строка оставлена ровно как в
базовой ревизии `534766da`, чтобы merge с master удалил её штатно).

### Проверка самого guard'а BR-571 (не заглушка, реальный `do.sh`)

```bash
bash tools/check-do-sh-guards.sh
# === Опыт A: падающая сборка обязана дать ненулевой код ===
#   PASS  отслеживаемый Dockerfile не изменён (sha256 fbf88227ee07bfea…)
#   PASS  мусорных файлов (.guardtest.bak) в репозитории нет
#   PASS  build_public на падающей сборке вернул код 1 (было 0 — «молча зелёный»)
# --- итог: PASS=3 FAIL=0 ---
```

## Проверки

| Проверка | Команда | Результат |
|---|---|---|
| Backend tests | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" :karaoke-app:test` | BUILD SUCCESSFUL in 11s |
| Web tests | `… :karaoke-web:test` | BUILD SUCCESSFUL in 42s |
| Kotlin style | `… :karaoke-web:ktlintCheck` | BUILD SUCCESSFUL in 1s |
| Регрессия пакета | `bash specs/379-pkg-s2-16/regression.sh` | PASS=30 FAIL=0 |
| Секреты в argv | `bash tools/check-secret-in-argv.sh` | OK (1 совпадение — legacy вне пакета) |
| Guard self-test | `bash tools/check-secret-in-argv.sh --self-test` | все 6 правил работают |
| Knowledge SSoT | `bash tools/check-knowledge-structure.sh` | OK: 9/9 checks passed |
| Ссылки в документах | `python3 tools/check-doc-references.py` | OK: 4129 ссылок, 0 битых |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | ✅ все commits ветки уникальны |
| Per-feature документы | `bash tools/check-feature-doc.sh docs/features/*.md` | Все документы валидны |

Kotlin/TypeScript-исходники пакет не меняет — прогоны gradle подтверждают, что
правки только в `tools/` и документации ничего не сломали.

## Документация

- `knowledge/system/infra/do-sh.md` — BR-571: guard деплой-пути, эксперимент на копии.
- `knowledge/system/infra/per-feature-docs.md` — BR-572: отсутствующий документ = ошибка гейта.
- `knowledge/system/infra/registry.md` — BR-593/BR-594: правила обслуживающих инструментов.
- `knowledge/system/infra/ci-tools.md` — сводка по пакету #379.
- `docs/features/private-docker-registry.md` (FR-009) — приёмка и пины: «неверный пароль»,
  сохранность пина, креды вне argv; инварианты 7-8, ловушки 10-11.
- `docs/features/tracker-local-integration.md` (FR-009) — BR-601: stderr не смешивается
  с данными, коды 2/3.

## За владельцем

1. **BR-601, код возврата 1.** Оставлен прежним: `1` = «открытых задач нет» (так было до
   пакета; менялись только коды ошибок — 2/3). Если нужно `0` для «задач нет» — это
   отдельное решение владельца: код `1` читают сторонние агентские обёртки.
2. **BR-593, попытка входа с неверным паролем.** Блок `[3]` приёмки теперь реально шлёт
   неверную пару кредов (один вход за прогон). Если в registry включён lockout по
   неудачным логинам — политику (исключение smoke-пользователя либо отказ от проверки
   против прод-контура) задаёт владелец.
3. **BR-594, `PENDING` в lock-файле.** В текущем
   `deploy/prod-single-host/registry/images.lock` записей `PENDING` нет (проверено:
   14 строк, 0 `PENDING`), поэтому новое поведение `--verify` (FAIL вместо skip) ничего
   не ломает. Если владелец захочет отдельную колонку `pending_at` — это расширение
   формата lock-файла, решение за ним.

## Примечания

- В worktree при приёмке работы обнаружен и восстановлен **случайно затёртый**
  `tools/tracker.sh` (в файле лежала заглушка из ручного прогона, печатавшая тестовые
  TSV-строки). Файл возвращён к ревизии `534766da`; в пакет правка не входит.
- `tools/tracker.sh list-issues --raw` в master уже есть — дублирования нет;
  `tracker-poll.sh` продолжал вызывать его с `--raw`.
- Rebase не делался, master не трогался, push/PR не выполнялись, контейнеры/прод/БД
  не затрагивались.

## Коммиты

| Хэш | Что |
|---|---|
| `789e20a7` | BR-571 — `check-do-sh-guards.sh` + `knowledge/.../do-sh.md` |
| `7805fa8d` | BR-572 — `check-feature-doc.sh` + `knowledge/.../per-feature-docs.md` |
| `5819d39a` | BR-593/BR-594 — `registry-smoke.sh`, `registry-vendor.sh`, baseline + knowledge + доки |
| `4282515d` | BR-595 — `generate-eslint-baseline.sh` |
| `e4065a27` | BR-601 — `tracker-poll.sh` + knowledge `ci-tools.md` + доки |
| `53a1fc7f` | регрессия `specs/379-pkg-s2-16/{regression.sh,stub-registry.py}` |

База: `534766da` (master на момент старта пакета).

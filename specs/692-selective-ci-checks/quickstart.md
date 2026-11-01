# Quickstart: проверка фичи «выборочный запуск CI-проверок по зонам интереса»

**Feature**: 692-selective-ci-checks | **Дата**: 2026-11-01

Руководство по валидации: как убедиться, что фича работает, из консоли. Каждый сценарий —
воспроизводимая команда с ожидаемым результатом. Детали контрактов —
[contracts/ci-checks-cli.md](contracts/ci-checks-cli.md) и
[contracts/ci-checks-manifest.md](contracts/ci-checks-manifest.md).

## 0. Предусловия

```bash
cd /home/nsa/Karaoke
git branch --show-current        # ожидается: 692-selective-ci-checks
python3 -c "import yaml; print(yaml.__version__)"   # ожидается: версия PyYAML
git ls-files >/dev/null && echo "git ok"
```

Все сценарии, кроме явно помеченных «в фикстуре», выполняются в рабочем дереве и **ничего не
коммитят**. Сценарии, меняющие состояние git (stash/branch), выполняются только на
`git stash`-чистом дереве.

## 1. Манифест согласован с workflow (гейт согласованности)

```bash
bash tools/check-ci-checks-manifest.sh; echo "rc=$?"
```

Ожидается: `rc=0` и сообщение о совпадении джобов и манифеста.

Негативные проверки (каждая — на фикстуре, рабочее дерево не трогается):

```bash
bash tools/check-ci-checks-manifest.sh --self-test; echo "rc=$?"
```

Ожидается: `rc=0`; внутри — три фикстуры: чистое дерево → `0`, подсунутое нарушение → ненулевой
код, недоступные данные → `2`.

## 2. Две копии workflow синхронны (существующий гейт, расширенный)

```bash
bash tools/check-ci-workflow-sync.sh; echo "rc=$?"
```

Ожидается: `rc=0`, «наборы гейтовых команд совпадают». Гейт теперь сверяет и вызовы `deploy/...`,
и `if:`-условия джобов.

## 3. Правило выбирает применимые проверки (локально)

```bash
# правка только в knowledge/ — тяжёлые проверки бекенда/фронта не должны применяться
python3 tools/ci-checks.py --manifest config/ci/checks.yml --emit-outputs | grep -E '^(all|backend_build|backend_test|frontend_build|docs_lint|knowledge_structure)='
```

Ожидается: `all=false`, `backend_build=false`, `backend_test=false`, `frontend_build=false`,
`docs_lint=true` (или `knowledge_structure=true` — по зонам инвентаря).

Проверка с другой категорией правок:

```bash
# правка фронта admin-SPA
touch webvue3/src/.quickstart-probe && python3 tools/ci-checks.py --emit-outputs | grep -E '^(backend_build|frontend_build|frontend_lint)=' ; rm -f webvue3/src/.quickstart-probe
```

Ожидается: `backend_build=false`, `frontend_build=true`, `frontend_lint=true`.

## 4. Локальный пред-PR прогон исполняет только применимые проверки

```bash
python3 tools/ci-checks.py --manifest config/ci/checks.yml --local; echo "rc=$?"
```

Ожидается: перечень `APPLICABLE` / `SKIPPED` для всех проверок манифеста; `rc=0` при зелёных
применимых проверках.

Полный набор (страховка):

```bash
python3 tools/ci-checks.py --manifest config/ci/checks.yml --local --all; echo "rc=$?"
```

Ожидается: все проверки манифеста в состоянии `APPLICABLE` (кроме `mode: always` — они
применимы всегда).

## 5. Агрегат различает «применимо и зелёно», «неприменимо» и «не отработало»

На фикстуре (без CI):

```bash
# применимая проверка зелёная → rc=0
CI_CHEKS_OUTPUTS='{"all":"false","backend_build":"true"}' \
CI_CHEKS_RESULTS='{"changes":{"result":"success"},"backend-build":{"result":"success"}}' \
python3 tools/ci-checks.py --manifest config/ci/checks.yml --summary; echo "rc=$?"
```

Ожидается: `rc=0`, `SUMMARY: green`.

```bash
# применимая проверка красная → rc=1
CI_CHEKS_OUTPUTS='{"all":"false","backend_build":"true"}' \
CI_CHEKS_RESULTS='{"changes":{"result":"success"},"backend-build":{"result":"failure"}}' \
python3 tools/ci-checks.py --summary; echo "rc=$?"
```

Ожидается: `rc=1`, `SUMMARY: red`.

```bash
# применимая проверка без результата (джоб не стартовал) → rc=1 (fail-closed)
CI_CHEKS_OUTPUTS='{"all":"false","backend_build":"true"}' \
CI_CHEKS_RESULTS='{"changes":{"result":"success"}}' \
python3 tools/ci-checks.py --summary; echo "rc=$?"
```

Ожидается: `rc=1`.

```bash
# джоб changes упал (манифест нечитаем / неизвестна версия) → rc=1
CI_CHEKS_OUTPUTS='{}' CI_CHEKS_RESULTS='{"changes":{"result":"failure"}}' \
python3 tools/ci-checks.py --summary; echo "rc=$?"
```

Ожидается: `rc=1`.

## 6. Фикстуры реализации правила

```bash
python3 tools/ci-checks.py --self-test; echo "rc=$?"
```

Ожидается: `rc=0`, все 10 фикстур пройдены (см. контракт CLI, §6).

## 7. Merge-гейт берёт агрегированный сигнал и не обманывается пагинацией

Проверка без сети (логика выборки статусов проверяется на фикстуре гейта):

```bash
bash tools/merge-pr.sh 41 --dry-run
```

Ожидается: скрипт печатает состояние PR, ветку, head-коммит и раздел «CI» — с числом полученных
контекстов и сверкой `X-Total-Count`; при отсутствии контекста `ci-summary` — отказ с понятным
сообщением («прогон не отработал»), а не merge.

## 8. Страховка: полный набор вне PR

Полный прогон включается при:

- пуше в `master` (джоб `changes` выставляет `all=true`);
- cron `0 4 * * 1` (еженедельно);
- ручном `workflow_dispatch` с `--input full=true`.

Проверка в коде (без запуска CI):

```bash
grep -nE 'schedule:|cron|cancel-in-progress|workflow_dispatch' .forgejo/workflows/lint.yml
```

Ожидается: присутствуют `schedule:` с `cron: '0 4 * * 1'`, отдельная `concurrency`-группа с
`cancel-in-progress: false`, `workflow_dispatch` с входом `full`.

## 9. Знания и ADR

```bash
ls knowledge/adr/local-0017-selective-ci-checks.md
grep -n "R-49" knowledge/guidelines/architecture-conventions.md | head -3
grep -n "ci-summary" knowledge/system/infra/ci-tools.md knowledge/public/onboarding.md | head -5
```

Ожидается: файлы существуют, R-49 и упоминания `ci-summary` на месте.

## 10. Итоговый чек-лист готовности

| # | Проверка | Команда | Ожидание |
|---|---|---|---|
| 1 | манифест ↔ workflow | `bash tools/check-ci-checks-manifest.sh` | `rc=0` |
| 2 | гейт согласованности, фикстуры | `bash tools/check-ci-checks-manifest.sh --self-test` | `rc=0` |
| 3 | синхронность копий workflow | `bash tools/check-ci-workflow-sync.sh` | `rc=0` |
| 4 | фикстуры реализации правила | `python3 tools/ci-checks.py --self-test` | `rc=0` |
| 5 | применимость по зонам | `python3 tools/ci-checks.py --emit-outputs` | `all=false` на правке `knowledge/` |
| 6 | локальный прогон | `python3 tools/ci-checks.py --local` | только применимые проверки |
| 7 | агрегат | см. §5 | `0` / `1` по сценарию |
| 8 | знания | см. §9 | ADR, R-49, ci-tools, onboarding |

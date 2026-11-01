# Отчёт по KARAOKE-376 «SHA-пиннинг workflow не покрывает рабочую копию .forgejo/workflows/»

* **Issue**: KARAOKE-376 (Plane, проект KARAOKE, тип `type:bug`)
* **Ветка**: `706-ci-gate-fixes` → PR #51 (https://git.sm-karaoke.ru/svoemesto/Karaoke/pulls/51)
* **Дата**: 2026-11-01
* **Статус**: исправлено, PR открыт

## Находка

`tools/check-actions-pinned.sh` (BR-508, аудит #336) обходил **только** каталог
`.github/workflows/`:

```bash
WORKFLOWS_DIR="${PROJECT_ROOT}/.github/workflows"
```

При этом CI на нашем сервере исполняет **другуую** копию —
`.forgejo/workflows/lint.yml` (это следует и из `knowledge/system/infra/ci-tools.md`
§ «Где исполняется CI»). Следствие: правило BR-508 («каждый сторонний action
закреплён на полном SHA коммита») на исполняемой копии **не проверялось
вовсе**. Любой `uses:` с плавающим тегом (`uses: owner/action@v4`) в
`.forgejo/workflows/lint.yml` прошёл бы guard молча.

Механизм атаки, который guard обязан закрывать: тег в чужом репозитории можно
перенаправить на другой коммит — CI подхватит новый код **без изменения нашего
репозитория и без ревью** (supply chain).

## Проверка до правки

```
$ bash tools/check-actions-pinned.sh
OK: все сторонние uses: в 1 workflow-файл(ах) закреплены по SHA (BR-508)
```

Один файл — `.github/`; исполняемая копия `.forgejo/` не проверялась вовсе.

## Что сделано

1. `tools/check-actions-pinned.sh`: константа `WORKFLOWS_DIR` заменена на
   массив `WORKFLOWS_DIRS` — обе копии (`.github/workflows/`,
   `.forgejo/workflows/`); guard обходит все существующие каталоги, каждый
   существующий `*.yml`/`*.yaml` проверяется отдельно; при отсутствии обоих
   каталогов — ошибка с кодом `2`.
2. Форма Forgejo-действий (`uses: https://data.forgejo.org/actions/checkout@<40 hex>`)
   распознаётся корректно: разбор ref идёт по **последнему** `@`, поэтому
   полный URL-вид попадает под то же правило SHA. Проверено: все `uses:` в
   `.forgejo/workflows/lint.yml` уже запинены на полные SHA.
3. В `--self-test` добавлена регресс-проверка: guard обязан обходить обе копии
   (маркеры `.github/workflows` и `.forgejo/workflows` в исходнике).
4. Знание: `knowledge/system/infra/ci-tools.md` — строка guard'а в таблице
   «Git-гигиена и CI-конфигурация» переписана (обе копии; KARAOKE-376).

## Проверка после правки

```
$ bash tools/check-actions-pinned.sh
OK: все сторонние uses: в 2 workflow-файл(ах) обеих копий закреплены по SHA (BR-508, KARAOKE-376)

$ bash tools/check-actions-pinned.sh --self-test
OK: self-test — снимок «до» (тег) отвергнут
   OK: guard обходит копию .github/workflows
   OK: guard обходит копию .forgejo/workflows
OK: self-test — снимок «после» (SHA) принят
```

## Что НЕ менялось

* Содержание самих workflow-файлов (кроме добавления self-test'а нового
  сборщика статусов — см. KARAOKE-377) — не менялось.
* Список запиненных actions и их версии — не менялись.

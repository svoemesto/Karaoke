# Report: OpenProject #391 — `[#336] S3: .github: SEC — 1 BR` (пакет PKG-S3-19, волна E)

> **Ветка**: `609-pkg-s3-19` (worktree `.worktrees/waveE-s3-19`, от `origin/master` `35026a20`)
> **Задача**: OpenProject #391, аудит #336, порция **P-78**, запись **BR-508** (карточка **P-78-02**)
> **Дата**: 2026-10-25
> **Статус**: реализовано, проверки зелёные. Push/PR не делались (запрещено брифингом), merge — за владельцем.

## 1. Суть задачи (по карточке аудита)

**BR-508 / P-78-02** — `.github/workflows/lint.yml:50–52`, класс SEC (S3).

- **Что не так**: все сторонние actions закреплены на **плавающем теге**, а не
  на SHA коммита: `actions/checkout@v4`, `actions/setup-java@v4`,
  `actions/setup-node@v4`, `actions/upload-artifact@v4`,
  `actions/setup-python@v5`, `lycheeverse/lychee-action@v1.9.0`.
  Тег в чужом репозитории можно перенаправить на другой коммит — CI
  подтянет новый код без изменения нашего репозитория и без ревью
  (supply chain).
- **Как правильно**: `uses: actions/checkout@<sha> # v4.x.x` — стандартная
  практика hardening; обновление SHA — отдельным PR (при необходимости
  через Dependabot).

Публичное поведение приложения не меняется: правка касается только
CI-конфигурации, guard-скрипта и документации.

## 2. Что сделано

| Файл | Что |
|---|---|
| `.github/workflows/lint.yml` | **34** ссылки `uses:` (6 разных actions) закреплены полным SHA коммита + комментарий версии. Тот же коммит, который отдавал тег на момент правки, — обновления версии нет. Добавлен джоб `actions-pinned-guard` (guard + self-test). |
| `tools/check-actions-pinned.sh` | **Нов.** Guard: каждый сторонний `uses:` в `.github/workflows/*.yml` обязан иметь `owner/repo@<40 hex>`. Локальные (`./…`) и `docker://` исключены. Режимы: guard, `--quiet`, `--self-test`, проверка явных файлов (для снимков «до/после»). |
| `tools/fixtures/actions-pinned/{before,after}/workflow.yml` | **Нов.** Фикстуры self-test: «до» — теги (guard обязан упасть), «после» — SHA (обязан пройти). |
| `archive/docs/features/ci-lint-enforcement.md` | Per-feature документ (FR-009): раздел «Закрепление actions по SHA», обновлён инвариант 6, ловушка про плавающий тег, ссылка на guard. Секция `## Известные ловушки` на месте. |
| `knowledge/system/infra/ci-tools.md` | SSoT: запись про `check-actions-pinned.sh` (OpenProject #391, BR-508). |

### Закреплённые SHA (провенанс)

Получены из GitHub API (`/repos/<repo>/git/ref/tags/<tag>`) 2026-10-25;
SHA сверены с тегом по `/repos/<repo>/tags` — версия в комментарии
соответствует коммиту.

| Action | Тег на момент правки | SHA | Версия в комментарии |
|---|---|---|---|
| `actions/checkout` | `v4` | `11d5960a326750d5838078e36cf38b85af677262` | `v4.4.0` |
| `actions/setup-java` | `v4` | `cf277c60eb25467037889841efdb72551f06f6c3` | `v4.9.1` |
| `actions/setup-node` | `v4` | `49933ea5288caeca8642d1e84afbd3f7d6820020` | `v4.4.0` |
| `actions/upload-artifact` | `v4` | `ea165f8d65b6e75b540449e92b4886f43607fa02` | `v4.6.2` |
| `actions/setup-python` | `v5` | `a26af69be951a213d495a4c3e4e4022e16d87065` | `v5.6.0` |
| `lycheeverse/lychee-action` | `v1.9.0` | `22134d37a1fff6c2974df9c92a7c7e1e86a08f9c` | `v1.9.0` |

## 3. Проверка «до/после»

Guard берёт снимок старого workflow из git и проверяет его отдельно.

```
$ git show 35026a20:.github/workflows/lint.yml > /tmp/lint-before.yml
$ bash tools/check-actions-pinned.sh /tmp/lint-before.yml
FAIL: /tmp/lint-before.yml:56 — 'actions/checkout@v4': ref 'v4' не SHA коммита (нужно 40 hex + комментарий # vX.Y.Z)
FAIL: /tmp/lint-before.yml:59 — 'actions/setup-java@v4': ref 'v4' не SHA коммита ...
... (всего 34 строки FAIL — каждая uses: в старом файле)
FAIL: actions не закреплены по SHA в 1 из 1 workflow-файл(ов) (BR-508)
exit=1

$ bash tools/check-actions-pinned.sh
OK: все сторонние uses: в 1 workflow-файл(ах) закреплены по SHA (BR-508)
exit=0

$ bash tools/check-actions-pinned.sh --self-test
OK: self-test — снимок «до» (тег) отвергнут
OK: self-test — снимок «после» (SHA) принят
exit=0
```

Self-test входит в джоб `actions-pinned-guard`, поэтому проверка остаётся
взведённой и после merge: если матчер выродится в no-op, CI упадёт.

## 4. Проверки (как в CI)

| Проверка | Результат |
|---|---|
| `:karaoke-app:test` (с `GRADLE_USER_HOME`, `--max-workers=2`) | BUILD SUCCESSFUL, 88 файлов / **582 теста**, 0 failures/errors |
| `:karaoke-web:test` | BUILD SUCCESSFUL, 36 файлов / **182 теста**, 0 failures/errors |
| `:karaoke-app:ktlintCheck :karaoke-web:ktlintCheck :karaoke-db:ktlintCheck` | BUILD SUCCESSFUL |
| `bash tools/check-knowledge-structure.sh` | OK (9/9) |
| `python3 tools/lint-knowledge.py --baseline …` | OK |
| `python3 tools/check-doc-references.py` | OK (4296 ссылок, 2499 путей) |
| `python3 tools/check-recordhash-consistency.py` | OK |
| `bash tools/check-no-mp4-mentions.sh` | OK (139 в baseline) |
| `bash tools/check-feature-doc.sh docs/features/*.md archive/docs/features/*.md` | OK (все документы) |
| `bash tools/check-subagent-isolation.sh` | OK (предупреждение «нет новых commits» до коммита) |

Замечания по окружению (не влияют на результат): Kotlin daemon не может
писать в `~/.local/share/kotlin` (read-only sandbox) — Gradle штатно
переходит на компиляцию без демона; тесты от этого проходят.

**Наблюдение вне пакета**: задачи `ktlintCheck` в корневом проекте нет
(`subprojects { apply(plugin = "ktlint") }`), поэтому команда
`./gradlew ktlintCheck` из `.github/workflows/lint.yml:93` локально падает
с «task not found». Это не следствие BR-508 (джоб добавлен раньше, BR-507)
и в пакет не входит — за владельцем отдельной находкой.

## 5. За владельцем

1. **Dependabot.** Без него SHA обновляются вручную. Включать ли
   `.github/dependabot.yml` (и с каким интервалом) — решение владельца;
   в пакет PKG-S3-19 не входит (файлов по карточке — 1).
2. **Корневой `ktlintCheck`** (см. наблюдение выше): починить джоб
   `backend-lint` (`:karaoke-app:ktlintCheck :karaoke-web:ktlintCheck`)
   отдельной записью аудита.
3. **Статусы в источнике правды.** `specs/336-code-audit/registry.csv` и
   карточка `findings/P-78.md` не менялись (registry.csv запрещён
   брифингом; карточка содержит ещё три чужих BR — P-78-01/03/04).
   BR-508 остаётся `TODO` в реестре; актуальный статус — этот отчёт.

## 6. Хэши

SHA-256 изменённых файлов (рабочее дерево, до коммита):

```
e407ff43a2b1f88da125b13dc533c483e2339b3a3422d3b4ae4d598c1d788e5b  .github/workflows/lint.yml
50ac81cb4b68c1c15143b8b668c59a625b037a20a22f8a58e45974959aa2b762  tools/check-actions-pinned.sh
bbbeab608a4f60b90f6c44297a8b39959e2bc30ea7aaa7504cad65daa33cb8a7  tools/fixtures/actions-pinned/before/workflow.yml
5553caf4aa97be80da11a306e36286438ec43d94c111c4a5f92a9c755fc978fe  tools/fixtures/actions-pinned/after/workflow.yml
44a5fb3ea703ff676d42d6e2d7e72b6ae25e60129b200828918236269dbb1ccd  archive/docs/features/ci-lint-enforcement.md
ceb234d4eb2c430b955a697a0021e641d04d525ceaa9abe893f110061e5f8a30  knowledge/system/infra/ci-tools.md
```

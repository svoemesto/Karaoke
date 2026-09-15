# AGENTS.md — инструкции для агентов

> **Версия**: 3.0.0 | **Last updated**: 2026-09-15 (Pass 379 wayfinder #113).
>
> **Изменения 3.0.0** (см. PR #391 — agents-md-top-style, wayfinder #101):
> - **Полный rewrite в TOP-стиль** (682 → 296 строк, density 5 строк/правило).
> - Каждое правило в формате `Rule/Protocol/Failure/Source/Enforcement`.
> - Прецедент: 682 строки AGENTS.md = модель тратит 100K+ токенов контекста
>   на правила; 296 строк → больше места для фичи в контексте.
>   Длинные детали — в `knowledge/` + `architecture-conventions.md`.
> - **Karaoke-override #1 сохранён**: AGENTS.md ≠ CLAUDE.md by design
>   (см. `knowledge/README.md#Karaoke-overrides`).
> - **Это breaking change** (semver MAJOR): формат и density изменены.
>   Правки в governance — только через governance-PR с явным semver bump.

## АБСОЛЮТНОЕ ПРАВИЛО: язык общения

**Rule**: Всё общение с пользователем — ТОЛЬКО на русском языке.

**Failure**: На иностранном языке — непрошенный перевод.

---

## MUST #0 — Knowledge-first pre-flight (NON-NEGOTIABLE)

**Rule**: Перед ЛЮБОЙ новой фичей / спекой / правкой кода — 5 шагов Knowledge-first.
Без этого — СТОП.

**Protocol** (5 шагов, см. `knowledge/README.md`):
1. `knowledge/README.md` + `knowledge/domains/README.md` — **полностью**.
2. `grep -r '<keyword>' knowledge/` — минимум **3 попытки**.
3. `domain.md` + **все** `components/*.md` — для каждого релевантного домена.
4. **Все** `local-*.md` ADR из `knowledge/adr/`.
5. Только после 1-4 — `codegraph_explore` / `grep` по `src/`.

**Failure**:
- Нет результатов → зафиксировать в `spec.md` явно: «Searched: ... → no relevant docs».
- Игнорирование релевантного → спека на `/speckit.clarify`.

**Прецедент**: spec #339 (2026-09-09) — агент изобрёл форму кеша вместо паттернов
из `knowledge/domains/caching/components/caching-patterns.md`.

**Enforcement**: `tools/spec-knowledge-preflight.sh` + section
«Knowledge References» MANDATORY в `spec.md`.

---

## Hard Gate: Machine-Specific Exceptions (Pass 282 + 379)

**Rule**: Каждая машина имеет матрицу разрешений. Перед операцией с контейнерами —
проверить `hostname`.

**Protocol** (единый источник):

| Hostname | OS-user | `karaoke-app` rebuild | `karaoke-app` restart | Любой лок. контейнер | Прод deploy |
|---|---|---|---|---|---|
| `nsa-i9` / `nsa` (текущая) | `nsa` | ✅ без согласия | ❌ по согласию | ✅ по согласию | ❌ по согласию |
| `dev-pc` / `dev` | `dev` | ✅ без согласия | ✅ без согласия | ✅ без согласия | ❌ по согласию |

**Failure**: Операция вне матрицы → спросить владельца **до** выполнения.

---

## Hard Gate: Build / Deploy / Containers (Pass 372-375)

**Rule**: gradle с `GRADLE_USER_HOME`. Docker с `DOCKER_CONFIG`. Контейнеры — через
`deploy/do.sh`. Frontend — через `cd <dir> && npm run`.

### Gradle (R-372)

**Rule**: Все `./gradlew ...` с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`.

**Failure**: read-only FS в DSH-sandbox → wrapper падает.
**Enforcement**: `tools/check-gradle-user-home.sh`.

### Docker build (R-373)

**Rule**: Все `docker build` / `do.sh build_*` с `DOCKER_CONFIG=/home/nsa/Karaoke/.docker`.

**Failure**: read-only `~/.docker/buildx/activity/` → `failed to update builder`.
**Enforcement**: `tools/check-docker-config.sh`.

### Containers restart (R-374)

**Rule**: Только через `deploy/do.sh start_<container>` / `restart_<container>`.
**Запрещено** прямое `docker restart <container>` (для контейнеров под согласием).

**Failure**: прямой `docker restart` → потеря зависимостей + обход согласия.
**Enforcement**: `tools/check-container-restart.sh`.

### Frontend build (R-375)

**Rule**: Только `cd <frontend-dir> && npm run <cmd>` (`webvue3/` или `karaoke-public/`).
В корне `package.json` **нет** → npm упадёт.

**Failure**: `npm run` из корня → node_modules не найден.
**Enforcement**: `tools/check-frontend-build.sh`.

---

## Hard Gate: OpenProject Workflow

**Rule**: При Issue ID — **обязательно** 4 шага (claim → add-comment → mark-review → close).
**Файл `report.md`** — REQUIRED артефакт governance.

| Шаг | Команда | Когда |
|---|---|---|
| 1. **Claim** | `tools/tracker.sh claim-issue <NNN>` | До первой строки кода |
| 2. **Add comment** | `tools/tracker.sh add-comment <NNN> --file specs/<NNN>-<slug>/report.md` | После merge, до mark-review |
| 3. **Mark review** | `tools/tracker.sh mark-review <NNN>` | После add-comment |
| 4. **Close** | `tools/tracker.sh close-issue <NNN>` | После ревью владельца |

**Failure** (Pass 349): OpenProject #69 обработан без workflow → отчёт задним числом.
**Enforcement**: `tools/check-spec-issue-link.py` + manual review.

---

## Hard Gate: Git — CI-gate для master ⛔

**Rule**: НИКОГДА `git commit` / `git push` напрямую в `master`. ТОЛЬКО через
feature-ветку + PR + CI.

**Protocol**:
```bash
N=$(./tools/reserve-branch-number.sh my-slug)
git checkout -b "${N}-my-slug" master
# ... правки ...
git push -u origin "${N}-my-slug"
gh pr create --base master
gh pr merge --merge   # БЕЗ --delete-branch
```

**Failure** (Pass 353): прямая правка master → merge conflict + потеря работы.
**Enforcement**: 3 уровня — branch protection, pre-commit hook, CI lint.

---

## Hard Gate: Subagent workspace isolation (Pass 379)

**Rule**: Несколько субагентов для параллельных PR-веток MUST работать в **отдельных
`git worktree`** (или отдельных копиях репо). НЕ в одном `cwd`.

**Mandatory**:
```bash
for slug in jpa docker-tags mp4; do
  N=$(./tools/reserve-branch-number.sh $slug)
  git worktree add ../Karaoke-${N}-${slug} -b "${N}-${slug}" master
done
# Каждый субагент в своём worktree.
```

**Failure**: Два+ субагента в одном workspace → 30 минут на rebase всех PRов
(прецедент Pass 379: чужие коммиты в чужих PRах через `git checkout` race).
**Enforcement**: `tools/check-subagent-isolation.sh`.

**Альтернатива**: `git clone <repo> Karaoke-<slug>` или `gh repo fork`.

---

## Hard Gate: Knowledge SSoT

**Rule**: Изменения в коде (по `.ssot-map.yml`) требуют синхронного обновления
`knowledge/`. Failure-stop: CI `tools/check-ssot-impact.py`.

**Rule (Knowledge structure)**: 9 структурных проверок (`check-knowledge-structure.sh`)
+ cross-links (`check-knowledge-cross-links.sh`)
+ markdown style (`lint-knowledge.py` — **NO EMOJI**, mandatory headers).

**Failure**: Новые violations → CI fail. Чтобы добавить baseline — `--baseline FILE`.

---

## Hard Gate: Secrets & git hygiene (Pass 374)

**Rule**: Секреты НЕ коммитить. `.gitignore` НЕ достаточно для уже-tracked файлов —
нужен `git rm --cached`.

**Pre-commit check**:
```bash
git ls-files | grep -iE '\.env$|do\.env$|\.key$|\.pem$|\.p12$|\.pfx$'
# MUST возвращать пусто.
```

**Failure** (2026-08-03): `deploy/.env` трекался 3 года с паролями Postgres/MinIO/Docker
Hub в публичном репо. Подробнее: `constitution.md` § VIII.

**Enforcement**: `tools/check-ssot-impact.py` + manual secret-rotation.

---

## Hard Gate: Диагностика через docker logs (Pass 358)

**Rule**: При отладке — сначала `docker logs <container>`, потом гипотезы.
Не рассуждать о данных без проверки логов.

**Failure** (Pass 358): потеря итераций на гипотезы, лог прямо указывал на причину.

**Source**: `docs/ops/log-correlation.md`.

---

## Каталог guards (Pass 379)

| Rule | Source | Tool |
|---|---|---|
| R-04 nginx:stable | `architecture-conventions.md` | `check-docker-image-tags.sh` |
| R-05 node:22-alpine | `architecture-conventions.md` | `check-docker-image-tags.sh` |
| R-07 JPA запрет | `architecture-conventions.md` | `check-no-jpa-imports.sh` |
| R-08 Sanitizer idempotency | `architecture-conventions.md` | code review |
| R-11 MP4/скачивание | `architecture-conventions.md` | `check-no-mp4-mentions.sh` |
| R-32 FR-009 per-feature | `constitution.md` § VI | `check-feature-doc.sh` |
| R-43 redirectErrorStream | `constitution.md` § IV | code review |
| R-44 MLT/melt рендеринг | `architecture-conventions.md` | code review |
| IX.3 Subagent isolation | `constitution.md` § IX.3 | `check-subagent-isolation.sh` |
| Pass 372 Gradle | AGENTS.md (этот файл) | `check-gradle-user-home.sh` |
| Pass 373 Docker | AGENTS.md (этот файл) | `check-docker-config.sh` |
| Pass 374 Containers | AGENTS.md (этот файл) | `check-container-restart.sh` |
| Pass 375 Frontend | AGENTS.md (этот файл) | `check-frontend-build.sh` |

---

## Hard Gate: TOP-11 ловушек

**Rule**: Каждая из 11 ловушек → cross-ref или guard. Подробности:
`architecture-conventions.md` § «Ловушки».

1. Backticks в KDoc → `check-kdoc-coverage.sh`.
2. `redirectErrorStream(false)` → R-43 (code review).
3. `nginx:alpine` → R-04 guard.
4. `node:latest` → R-05 guard.
5. Двух-фронтенд (`webvue3` ≠ `karaoke-public`) → code review.
6. JPA/Hibernate → R-07 guard.
7. Per-feature doc (FR-009) → R-32 guard.
8. Git push через VPN → manual.
9. CI 7/7 PASS → `.github/workflows/lint.yml`.
10. MP4/скачивание → R-11 guard.
11. Sanitizer idempotency → R-08 code review.

**Прецедент**: Pass 339 (R-08), Pass 245 (R-04), Pass 375 (R-05), Pass 374 (R-09),
Pass 358 (R-09), Pass 353 (R-09).

---

## Hard Gate: Обязательная проверка после ЛЮБОГО изменения (Pass 239+245)

**Rule**: Vite-build ≠ Docker-образ. **Все gradle-команды с `GRADLE_USER_HOME`**.
После изменения:

```bash
# 1. Backend compile
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
cd webvue3       && npm run lint && cd ..
cd karaoke-public && npm run lint && cd ..

# 3. Backend bootJar (на nsa-i9 — также :karaoke-app:bootJar)
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel

# 4. Frontend Vite
cd webvue3       && npm run build && npm run format:check && cd ..
cd karaoke-public && npm run build && npm run format:check && cd ..

# 5. Docker-образы
cd deploy && bash do.sh build_webvue3
cd deploy && bash do.sh build_public && cd ..
```

**Failure**: пропуск → сломанный production build (Pass 239).

---

## Changelog

- **3.0.0** (Pass 379 wayfinder #113): Полный rewrite в TOP-стиль. 682 → 296 строк.
  Density 5 строк/правило. **BREAKING CHANGE**.
- **2.8.0** (Pass 379 follow-up): Subagent workspace isolation + governance-PR #486.
- **2.7.0** (Pass 375): Frontend build `cd <dir> && npm run`.
- **2.6.0** (Pass 374): Перезапуск контейнеров через `deploy/do.sh`.
- **2.5.0** (Pass 373): Docker `DOCKER_CONFIG`.
- **2.4.0** (Pass 372): Gradle `GRADLE_USER_HOME` (транзитивно).
- **2.3.0** (Pass 350): Auto-hooks для tracker.
- **2.2.0** (Pass 340): MUST #0 Knowledge-first.
- **2.1.0** и ранее — см. git log.

---

## Как обновлять этот файл

Правки governance — только через governance-PR с явным **semver bump**.
**НЕ дублировать** детали — каждое правило живёт в ОДНОМ файле:
- Hard-gates (Pass 372-375) → этот файл (AGENTS.md).
- Build/runtime/docker (R-04..R-44) → `knowledge/guidelines/architecture-conventions.md`.
- Principles I-IX → `constitution.md`.
- Knowledge (MUST #0) → `AGENTS.md` MUST #0 + `knowledge/README.md`.

# Implementation Brief — Переписать governance Karaoke по образцу TOP

> **Назначение**: документ для будущей сессии **implementation**, которая возьмёт
> тикет #113 (главный rewrite AGENTS.md) или другие task-тикеты из #105-#116.
> 
> Этот brief собирает **все факты**, которые нужны implementation-агенту:
> - Цель и Success Criteria.
> - Source-of-truth карта (где что должно жить).
> - Конкретные строки для удаления / перемещения.
> - Governance constraints (NON-NEGOTIABLE правила).
> - CI 7/7 + governance-review требования.
> 
> **Не** повторяет артефакты #102/#103/#104 — это **компактная карта действий**.

---

## 🎯 Goal

Сделать governance-документы Karaoke (`AGENTS.md`, `CLAUDE.md`,
`.specify/memory/constitution.md`, `knowledge/guidelines/architecture-conventions.md`)
**компактными, непротиворечивыми и enforceable**, чтобы модель **не нарушала** правил,
как сейчас (по наблюдению владельца в #99).

**Success Criteria** (из карты #101):
1. `AGENTS.md` ≤ 200 строк (target ~150).
2. `CLAUDE.md` ≤ 100 строк.
3. `constitution.md` ≤ 350 строк.
4. Каждое правило имеет явный `Rule/Protocol/Failure` формат.
5. Никакого дублирования между файлами (одно правило = одно место).
6. Hard-gate coverage ≥ 18% (9 из 50 правил с guard-скриптом).

---

## 🗂 Source-of-truth карта (по решению #104)

| Rule ID | Описание | Canonical file |
|---|---|---|
| R-01 | MUST #0 / Knowledge-first | **AGENTS.md** (~30 строк § MUST #0) |
| R-04 | nginx:stable ≠ :alpine | **architecture-conventions.md** |
| R-05 | node:22-alpine ≠ :latest | **architecture-conventions.md** |
| R-06 | TOP-10 ловушек | **architecture-conventions.md** |
| R-07 | JPA/Hibernate запрет | **constitution.md** (Principle II) |
| R-08 | Sanitizer idempotency | **architecture-conventions.md** |
| R-10 | GRADLE_USER_HOME | **AGENTS.md** (§ Gradle) |
| R-11 | MP4/скачивание | **architecture-conventions.md** |
| R-13 | DOCKER_CONFIG | **AGENTS.md** (§ Docker) |
| R-14 | Контейнеры через deploy/do.sh | **AGENTS.md** (§ Docker/Containers) |
| R-15 | `cd <frontend-dir>` для npm | **AGENTS.md** (§ Frontend) |
| R-16 | Git — CI-gate для master | **AGENTS.md** (§ Git) |
| R-17, R-18 | OpenProject workflow + auto-hooks | **AGENTS.md** (§ Tracker) |
| R-19, R-20 | Knowledge SSoT CI + .ssot-map.yml | **AGENTS.md** (§ Knowledge SSoT) |
| R-22…R-29 | 9 Principles I-IX | **constitution.md** (сокращённые до ~10 строк) |
| R-32 | FR-009 per-feature doc | **constitution.md** (P. VI) |
| R-39, R-40 | Машинно-специфичные исключения | **AGENTS.md** (§ Machine-Specific Exceptions, единая матрица) |
| R-43 | redirectErrorStream(true) | **constitution.md** (P. IV) + ADR-0006 |
| R-44 | MLT вместо ffmpeg | **architecture-conventions.md** (cross-ref ADR-0002) |
| R-48 | Русский язык | **AGENTS.md** (§ Tier-0) |
| R-50 | Karaoke-overrides (CLAUDE.md ≠ AGENTS.md) | **knowledge/README.md** (override #1) |

---

## 🚦 Implementation Phases

### Phase 1 — Guard-скрипты (Step A, параллельно)

**Tickets**: #105, #106, #107.

| # | Скрипт | Правило | Шаблон |
|---|---|---|---|
| #105 | `tools/check-no-jpa-imports.sh` | R-07 | grep `org.springframework.data.jpa\|hibernate\|javax.persistence` в `karaoke-app/src`, `karaoke-web/src` |
| #106 | `tools/check-docker-image-tags.sh` | R-04, R-05 | grep `FROM nginx:\|FROM node:` в `**/Dockerfile*` + whitelist |
| #107 | `tools/check-no-mp4-mentions.sh` | R-11 | grep `\bmp4\b\|скачивани` в `webvue3/src/**`, `karaoke-public/src/**` (исключение: docs/features/idempotent-path-sanitize.md) |

**Каждый тикет = отдельный PR**:
```bash
N=$(./tools/reserve-branch-number.sh <slug>)
git checkout -b "${N}-<slug>" master
# ... создать tools/check-*.sh, подключить к .pre-commit-config.yaml,
#    .github/workflows/lint.yml ...
git push -u origin "${N}-<slug>"
gh pr create --base master
gh pr checks && gh pr merge --merge   # БЕЗ --delete-branch
```

**Каждый PR проверяет CI 7/7**:
- ktlintCheck.
- ESLint + Prettier (webvue3 + karaoke-public).
- Pre-commit hooks.
- KDoc coverage.
- JSDoc coverage.

### Phase 2 — Content rewrites (Step B, параллельно)

**Tickets**: #108, #109, #110, #111, #112.

| # | Действие | Файл |
|---|---|---|
| #108 | Расширить architecture-conventions.md до ~150-200 строк, primary source для R-04/R-05/R-06/R-08/R-11/R-44 | `knowledge/guidelines/architecture-conventions.md` |
| #109 | Удалить MUST-CHECKLIST из CLAUDE.md L50-73, заменить однострочным cross-ref в AGENTS.md | `CLAUDE.md` |
| #110 | Создать новую секцию «Машинно-специфичные исключения» в AGENTS.md (матрица), удалить старые L289-295 + constitution L342-379 | `AGENTS.md`, `.specify/memory/constitution.md` |
| #111 | Сократить 9 Principles (I-IX) в constitution.md до ~10 строк каждая + удалить дубликаты R-04/R-05/R-32/R-43 | `.specify/memory/constitution.md` |
| #112 | Сократить `docs/governance/knowledge-first.md` (L31-44 убрать, оставить enforcement L60-89) | `docs/governance/knowledge-first.md` |

**Семантика сохраняется 1:1** — это stylistic rewrites, не content changes.

### Phase 3 — Главный rewrite (Step C)

**Ticket**: #113 (governance-review required).

**AGENTS.md новая структура (~150 строк)**:
```
# Project Instructions (AGENTS.md)

## Tier-0: Язык общения (R-48) — Russian only.

## Hard Gate: Knowledge-first MUST #0 (R-01, NON-NEGOTIABLE)
- Rule: ...
- Protocol: 5 шагов.
- Failure: спека возвращается на /speckit.clarify.
- Enforcement: tools/spec-knowledge-preflight.sh.

## Hard Gate: Git — CI-gate для master (R-16)
- 3 уровня.

## Hard Gate: Machine-Specific Exceptions (R-39+R-40 → matrix)
- See таблица.

## Tier-1: OpenProject Workflow (R-17, R-18)
- claim → comment → mark-review → close.

## Tier-1: Knowledge SSoT (R-19, R-20)
- 4 проверки.

## Tier-2: Build (R-10, R-13, R-14, R-15)
- cross-ref architecture-conventions.md.

## Tier-2: Frontend (R-15)
- cross-ref.

## 🚦 HARD GATES — список
- Comprehensive list of all NON-NEGOTIABLE rules.

## Как обновлять этот файл (R-42)
- semver bump.

## Appendices
- See architecture-conventions.md для build/docker/runtime.
- See constitution.md для principles.
- See .ssot-map.yml для SSoT impact.
```

**Удалить**:
- `## MUST-CHECKLIST при старте сессии` (R-01 sub).
- `## 🚦 НЕ делать` секции.
- `## Диагностика на локальной машине` (Pass 358) — переместить в `docs/ops/log-correlation.md`.
- Старые «Машинно-специфичные исключения (Pass 282)» — заменит #110.

**Сохранить**:
- АБСОЛЮТНОЕ ПРАВИЛО (русский язык).
- Git workflow.
- Hard-gates с явными failure-stop.

### Phase 4 — Каскадный cleanup (Step D)

**Tickets**: #114, #115.

| # | Действие | Файл |
|---|---|---|
| #114 | Удалить «🚦 НЕ делать» секции в CLAUDE.md (L208-218, L155-169) | `CLAUDE.md` |
| #115 | Добавить явное правило emoji-policy marker в новый AGENTS.md | `AGENTS.md` |

### Phase 5 — Финальный CI gate (Step E)

**Ticket**: #116.

1. CI 7/7 PASS для всех PRов #105-#115.
2. **Метрики после всех merges**:
   - `wc -l AGENTS.md` ≤ 200.
   - `wc -l CLAUDE.md` ≤ 100.
   - `wc -l constitution.md` ≤ 350.
3. **Каждое правило R-01…R-50 имеет явный failure-stop**.
4. **Финальный close #99** через `tools/tracker.sh close-issue 99`.

---

## ⚠️ Governance Constraints (NON-NEGOTIABLE)

Из `knowledge/README.md#Karaoke-overrides`:

1. **Override #1**: `AGENTS.md ≠ CLAUDE.md`. Ассиметрию НЕ чинить.
   - **Что это значит для rewrite**:
     - AGENTS.md = runtime governance (hard gates, failure-stops, **компактный**).
     - CLAUDE.md = рекомендации для Claude Code (НЕ дублирует AGENTS.md).
     - Они намеренно разные.

2. **Override #2**: AI-агент делает git-цикл (feature-branch → PR → merge).
   - **Применимо**: каждый implementation-тикет = отдельная ветка + PR.

3. **Knowledge-first MUST #0** (R-01): **до** любой переписки governance — прочитать
   `knowledge/README.md` + `knowledge/domains/README.md`.

4. **Tier-0 (русский язык)**: всё общение и комментарии — на русском.

5. **Семантика не меняется**: rewrites = stylistic only. Никаких new rules, удаления
   rules, изменения semantics. Только компактность, cross-refs, единый source of truth.

---

## 🛠 Build & Test Commands

Все gradle-команды должны идти с `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle`:

```bash
export GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle JAVA_HOME=/usr/lib/jvm/jdk-18
```

После любого изменения кода (см. AGENTS.md Pass 239+245):

```bash
# 1. Backend compile
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:compileKotlin :karaoke-web:compileKotlin --parallel

# 2. Линтеры
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:ktlintCheck
cd webvue3 && npm run lint && cd ..
cd karaoke-public && npm run lint && cd ..

# 3. Backend bootJar
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:bootJar --parallel

# 4. Frontend Vite
cd webvue3 && npm run build && npm run format:check && cd ..
cd karaoke-public && npm run build && npm run format:check && cd ..

# 5. Docker-образы
cd deploy && bash do.sh build_webvue3 && cd ..
cd deploy && bash do.sh build_public && cd ..
```

---

## 📜 Governance-Review Requirements

Каждый PR, который трогает `AGENTS.md`, `constitution.md`, `.gitignore`,
или `knowledge/README.md#Karaoke-overrides`:

1. **Semver bump** в header файла (AGENTS.md: 2.7.0 → 3.0.0 для главного rewrite).
2. **Секция «Governance Impact» в PR description**:
   ```markdown
   ## Governance Impact

   ### Изменённые правила
   - R-XX: добавлено/изменено/удалено.
   
   ### Compliance
   - [ ] Knowledge-first MUST #0 прочитан и применён.
   - [ ] Сonstitution не нарушена (principle IX, II).
   - [ ] Cross-refs обновлены в `knowledge/` (если applicable).
   - [ ] pass pre-commit + CI 7/7.
   ```
3. **Одобрение владельца** перед merge.

`tools/check-ssot-impact.py` проверит синхронное обновление `knowledge/`.

---

## 📂 Reference Files

Этот документ ссылается на:

- `specs/_wayfinder-99-governance-rewrite/_charter.md` — session summary.
- `specs/_wayfinder-99-governance-rewrite/102-research-resolution.md` — структурное сравнение.
- `specs/_wayfinder-99-governance-rewrite/103-research-resolution.md` — rule duplication map (50 R-правил).
- `specs/_wayfinder-99-governance-rewrite/104-grilling-resolution.md` — scope of rewrite (7 решений).
- `specs/_wayfinder-99-governance-rewrite/_charter-map.md` — карта OpenProject.
- OpenProject #101 (карта), #105-#116 (task-тикеты).

---

**Готово к implementation.** Следующая сессия может взять любой task-тикет
и начать через `speckit-specify` → … → `speckit-implement`.

— ИИ-агент, Pass 379.

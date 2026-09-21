# #103 Resolution — Rule duplication map для governance Karaoke

> **Resolution submitted**: 2026-09-14, Pass 379.
> **Resolver**: research-субагент `bf7b5366` (background).
> **Skill**: `wayfinder` + Knowledge-first MUST #0.
> **Scope**: расширение #102 (R-01…R-10) до 50 правил + cross-reference table.
> **Verdict**: НЕ выносился — это наблюдение, не интерпретация.

---

## 1. Inventory правил (Karaoke)

Расширено поверх R-01…R-10 из #102 (Pass 379). Новые правила: R-11…R-50.
Категоризация по источнику: **AGENTS.md** — runtime governance (canonical),
**constitution.md** — нормативная база (NON-NEGOTABLE), **CLAUDE.md** —
рекомендации для Claude Code (см. `knowledge/README.md#Karaoke-overrides` п.1),
**knowledge/guidelines/** — справочные сводки.

| Rule ID | Однострочное описание | Канонический файл (candidate) |
|---|---|---|
| R-01 | MUST #0 — Knowledge-first pre-flight (NON-NEGOTIABLE) | AGENTS.md L74-122 |
| R-02 | codegraph_explore ДО Knowledge-first ЗАПРЕЩЁН | AGENTS.md L74-122 + constitution.md Principle IX |
| R-03 | Прецедент #339 (агент изобрёл форму кеша) | AGENTS.md L80-86 (в составе R-01) |
| R-04 | `nginx:stable` ЗАПРЕТ `nginx:alpine` | architecture-conventions.md L20, L71 |
| R-05 | `node:22-alpine` ЗАПРЕТ `node:latest` | architecture-conventions.md L21, L72 |
| R-06 | TOP-10 ловушек (KDoc backticks, redirectErrorStream, JPA, MP4, trial, sanitizer…) | architecture-conventions.md L67-79 |
| R-07 | JPA/Hibernate ЗАПРЕЩЁН, raw JDBC + recordhash | constitution.md Principle II (L84-94) |
| R-08 | Sanitizer idempotency `sanitize(sanitize(s)) == sanitize(s)` | constitution.md Principle II / knowledge/adr/ |
| R-09 | Секреты НЕ коммитить (.env/.key/.pem) | constitution.md Principle VIII (L191-256) |
| R-10 | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle` (транзитивно) | AGENTS.md L333-376 |
| R-11 | MP4/скачивание НЕ упоминать в рекламе и комментариях | CLAUDE.md L134, L215 (нет в constitution) |
| R-12 | «Без trial» в первом раунде (anti-fraud дороже выгоды) | CLAUDE.md L147, L217 |
| R-13 | `DOCKER_CONFIG=/home/nsa/Karaoke/.docker` (Pass 373) | AGENTS.md L378-427 |
| R-14 | Перезапуск контейнеров ТОЛЬКО через `deploy/do.sh` (Pass 374) | AGENTS.md L429-487 |
| R-15 | Frontend build: `cd <frontend-dir> && npm run <cmd>` (Pass 375) | AGENTS.md L489-583 |
| R-16 | Git — CI-gate для master (3 enforcement layers) | AGENTS.md L297-325 |
| R-17 | Issue-tracker OpenProject workflow (claim → comment → mark-review) | AGENTS.md L234-283 |
| R-18 | Tracker auto-hooks (`tracker-bootstrap.sh` + `tracker-implement-done.sh`) | AGENTS.md L247-281 |
| R-19 | Knowledge SSoT CI/pre-commit (4 проверки) | AGENTS.md L123-132 |
| R-20 | `.ssot-map.yml` — карта обязательных SSoT-обновлений | AGENTS.md L134-158 |
| R-21 | Диагностика: смотреть логи `docker logs` ДО гипотез (Pass 358) | AGENTS.md L165-225 |
| R-22 | Self-contained пайплайн (НЕ cloud-only в горячем пути) | constitution.md Principle I (L73-82) |
| R-23 | Двух-БД синхронизация через SyncRegistry (LOCAL ↔ SERVER) | constitution.md Principle III (L95-103) |
| R-24 | Async-очередь задач с парсингом stdout + `redirectErrorStream(true)` | constitution.md Principle IV (L105-114) |
| R-25 | Двух-фронтенд: webvue3 ≠ karaoke-public | constitution.md Principle V (L116-127) |
| R-26 | Code Standards: KDoc coverage, ESLint baseline, FR-009 per-feature | constitution.md Principle VI (L129-152) |
| R-27 | Cross-Machine Setup: .gitignore_global, blame-ignore-revs, .gitattributes | constitution.md Principle VII (L153-189) |
| R-28 | Cross-machine документация: docs/onboarding.md + claude-code-setup.md | constitution.md Principle VII.4 (L178-185) |
| R-29 | Knowledge-first при разработке фич (Principle IX) | constitution.md L257-308 |
| R-30 | KDoc coverage (FR-006) + JSDoc coverage (≥50%, блокирует merge) | CLAUDE.md L106-119 |
| R-31 | ESLint/Prettier baseline (FR-007): новые нарушения CI-fail | constitution.md Principle VI.FR-007 (L136-142) |
| R-32 | Per-feature документ обновлять при правке (FR-009) | constitution.md Principle VI.FR-009 (L143-148) |
| R-33 | Knowledge update (FR-014): при изменении bounded context — синхронить knowledge/ | AGENTS.md L287 |
| R-34 | Git workflow: feature-branch + PR + CI, НЕ `--no-verify`/`--admin` | CLAUDE.md L155-169 |
| R-35 | Governance-review для правок AGENTS.md/constitution.md/.gitignore | CLAUDE.md L208-225 |
| R-36 | Сайт-центричная модель: площадки = техканал, НЕ реклама | CLAUDE.md L148, L216 |
| R-37 | Модель D (гибрид): эфир-N-дней + 1 трек/исполнитель + 1 альбом/исполнитель free | CLAUDE.md L145-146 |
| R-38 | Иерархия документации (knowledge → constitution → AGENTS.md → …) | AGENTS.md L227-232 |
| R-39 | Машинно-специфичные исключения (Pass 282): `nsa-i9`/`nsa` | AGENTS.md L289-295 |
| R-40 | Машинно-специфичные исключения: `dev-pc`/`dev` (любой локальный контейнер) | constitution.md L342-345, L373-379 |
| R-41 | Перед коммитом: 4 шага (linters + KDoc + Prettier + pre-commit) | CLAUDE.md L76-104 |
| R-42 | «Как обновлять AGENTS.md» — semver bump, ветка `0XX-agents-md-update` | AGENTS.md L599-601 |
| R-43 | `ProcessBuilder.redirectErrorStream(true)` обязательно (ADR-0006) | constitution.md L110-111 |
| R-44 | MLT/melt рендеринг (НЕ ffmpeg в основном пути) | constitution.md L75 + ADR-0002 |
| R-45 | MLT queue lanes: HEAVY_RENDER=0, LIGHT_BACKGROUND=-1, REMOTE_STORE_UPLOAD=-2 | constitution.md L112-114 |
| R-46 | Per-feature doc в том же PR (FR-009) — иначе PR rejection | CLAUDE.md L131, L161 |
| R-47 | GitHub Actions CI 7/7 PASS (ktlint/ESLint/Prettier/Docs/Baseline/KDoc/JSDoc) | CLAUDE.md L106-119 |
| R-48 | Язык общения — ТОЛЬКО русский | AGENTS.md L70-72 |
| R-49 | Pre-commit hooks: 9 проверок (ktlint/eslint/prettier + 5 governance guards) | AGENTS.md L41-43 + code-style.md L114-115 |
| R-50 | Karaoke-overrides: CLAUDE.md ≠ AGENTS.md by design | knowledge/README.md L81-84 |

**Итого: 50 правил** (R-01…R-50).

## 2. Cross-reference table

| Rule ID | AGENTS.md | CLAUDE.md | constitution.md | architecture-conv.md | code-style.md | knowledge/README.md | docs/governance/ | docs/strategy/ |
|---|---|---|---|---|---|---|---|---|
| R-01 | L74-122 | L50-69 (MUST-CHECKLIST) | L257-308 (P. IX) | — | — | L62-74 (cross-link) | L48-57 | — |
| R-02 | L74-122 (MUST #0) | L218 (❌ codegraph ДО Knowledge) | L287-288, L359-362 (п.5.8) | — | — | — | — | — |
| R-03 | L80-86 (прецедент) | L204 (MCP) | L259-264 | — | — | — | L4-17 | — |
| R-04 | L287 (❌ nginx:alpine) | L127, L213 | L325-326, L358 | L20, L71 | L91 | — | — | — |
| R-05 | L287 (❌ node:latest) | L128, L213 | L326-327, L358 | L21, L72 | L92 | — | — | — |
| R-06 | — | L123-135 (полный список) | — | L67-79 (дословная копия) | L31-32, L91-93 | — | — | — |
| R-07 | — | L130, L177, L214 | L84-94 (P. II), L358 | L17, L74 | L67-69 | — | — | — |
| R-08 | — | L135 | — | L32 | — | — | — | — |
| R-09 | L287 (❌ секреты) | — | L191-256 (P. VIII), L351-355 | — | — | — | — | — |
| R-10 | L329, L335-357 (5 локаций) | L78-82, L85, L110 | — | — | — | — | — | — |
| R-11 | — | L134, L215 | — | L30, L78 | — | — | — | — |
| R-12 | — | L147, L217 | — | — | — | — | — | L145-146 (external) |
| R-13 | L378-427 (5 локаций) | — | — | — | — | — | — | — |
| R-14 | L429-487 (5 локаций) | — | — | — | — | — | — | — |
| R-15 | L489-583 (5 локаций) | L86-87, L111-112 | — | — | — | — | — | — |
| R-16 | L297-325 | L165, L210 | — | — | — | — | — | — |
| R-17 | L234-283 | — | — | — | — | — | — | — |
| R-18 | L247-281 | — | — | — | — | — | — | — |
| R-19 | L123-132 | — | — | — | — | — | — | — |
| R-20 | L134-158 | — | — | — | — | — | — | — |
| R-21 | L165-225 | — | — | — | — | — | — | — |
| R-22 | — | — | L73-82 (P. I) | — | — | — | — | — |
| R-23 | — | — | L95-103 (P. III) | L17, L33-34 | — | — | — | — |
| R-24 | — | — | L105-114 (P. IV) | — | L22-23 | — | — | — |
| R-25 | — | L129 | L116-127 (P. V) | — | — | — | — | — |
| R-26 | L116-117 (cross-ref FR-009) | — | L129-152 (P. VI) | — | — | — | — | — |
| R-27 | — | L211, L233 | L153-189 (P. VII) | — | — | — | — | — |
| R-28 | — | — | L178-185 (P. VII.4) | — | — | — | — | — |
| R-29 | L114-121 (sync) | L50-69 | L257-308 | — | — | L62-74 | L48-57 | — |
| R-30 | — | L106-119 | L131-135 | — | L97-116 | — | — | — |
| R-31 | — | L106-119 | L136-142 | — | L100-103 | — | — | — |
| R-32 | L116-117 | L131, L161, L194 | L143-148 | — | — | — | — | — |
| R-33 | L287 (FR-014) | — | — | — | — | — | — | — |
| R-34 | — | L155-169 | L397-403 | — | — | — | — | — |
| R-35 | L599-601 | L208-225 | L427-449 (Governance) | — | — | — | — | — |
| R-36 | — | L148, L216 | — | — | — | — | — | growth.md |
| R-37 | — | L145-146 | — | L28 | — | — | — | growth.md |
| R-38 | L227-232 | — | L418 (Governance §1) | — | — | — | — | — |
| R-39 | L289-295 | — | — | — | — | — | — | — |
| R-40 | — | — | L342-345, L373-379 | — | — | — | — | — |
| R-41 | — | L76-104 | — | — | L97-116 | — | — | — |
| R-42 | L599-601 | L211, L233 | — | — | — | — | — | — |
| R-43 | — | L72, L126 | L110-111 | — | L22-23 | — | — | — |
| R-44 | — | — | L75 | L43-44, L64 | — | — | — | — |
| R-45 | — | — | L112-114 | L43 | — | — | — | — |
| R-46 | — | L131, L161 | L143-148 | — | — | — | — | — |
| R-47 | L297-325 | L106-119 | — | — | L97-116 | — | — | — |
| R-48 | L70-72 | — | — | — | — | — | — | — |
| R-49 | L41-43 | L99 | — | — | L114-115 | — | — | — |
| R-50 | — | — | — | — | — | L81-84 (override #1) | — | — |

## 3. Duplicates (≥2 файлов)

| Rule ID | Files count | Severity | Source-of-truth candidate |
|---|---|---|---|
| R-01 | **5** | **HIGH** (core governance) | **AGENTS.md** (hard gate + failure-stop). CLAUDE.md → удалить MUST-CHECKLIST L50-73, оставить cross-ref; knowledge/README.md → cross-link, не дублировать шаги; docs/governance/knowledge-first.md → оставить как «операционную памятку», но убрать L48-57 |
| R-04 | **4** | **MEDIUM** | **architecture-conventions.md** (L20) — declarative SSoT. CLAUDE.md L127 + AGENTS.md L287 → удалить, оставить cross-ref |
| R-05 | **4** | **MEDIUM** | **architecture-conventions.md** (L21). Те же действия |
| R-06 | **2 дословно** | **MEDIUM** | **architecture-conventions.md** (canonical). CLAUDE.md → удалить список, оставить однострочный cross-ref |
| R-07 | **4** | **HIGH** (P. II NON-NEGOTIABLE) | **constitution.md** (Principle II). CLAUDE.md + architecture-conv + code-style → cross-ref |
| R-10 | **3 файла / 5 локаций** | **MEDIUM** | **AGENTS.md** (L333-376, единая секция). CLAUDE.md → cross-ref `→ AGENTS.md § Gradle` |
| R-11 | **2** | **LOW** | **architecture-conventions.md** |
| R-13 | **1 файл / 5 локаций** | LOW | AGENTS.md (single source) |
| R-14 | **1 файл / 5 локаций** | LOW | AGENTS.md |
| R-15 | **2 файла** | **MEDIUM** | **AGENTS.md** (Pass 375). CLAUDE.md → cross-ref |
| R-32 | **3** | **MEDIUM** | **constitution.md** (P. VI FR-009). AGENTS.md + CLAUDE.md → cross-ref |
| R-43 | **3** | **MEDIUM** | **constitution.md** (P. IV) + ADR-0006 |

**Severity**:
- **HIGH** — правило описывает core governance / NON-NEGOTABLE. Дубликаты вводят модель в заблуждение.
- **MEDIUM** — есть единый источник по факту, но формально дублируется.
- **LOW** — текст немного разный (вариант адаптации).

## 4. Orphans (только в одном файле, без cross-reference)

| Rule ID | Где живёт | Severity | Рекомендация |
|---|---|---|---|
| R-12 | CLAUDE.md L147, L217 | LOW | Оставить (strategy, есть cross-ref в growth.md) |
| R-22 | constitution.md L73-82 | LOW | Оставить как SSoT |
| R-24 | constitution.md L105-114 | LOW | Оставить как SSoT |
| R-45 | constitution.md L112-114 | LOW | Оставить. Можно cross-ref в `architecture-conventions.md` L43 |
| R-48 | AGENTS.md L70-72 | LOW | Оставить (single source для runtime-коммуникации) |
| R-39 | AGENTS.md L289-295 | LOW | Оставить. **Конфликт**: см. R-40 |
| R-40 | constitution.md L342-345, L373-379 | **MEDIUM** | **Дубликат R-39** с другим hostname. Консолидировать в одной матрице |
| R-13, R-14, R-15 | AGENTS.md (Pass 373-375) | LOW | Только что добавлены, единый источник |
| R-19 | AGENTS.md L123-132 | LOW | Оставить |
| R-20 | AGENTS.md L134-158 | LOW | Оставить |
| R-21 | AGENTS.md L165-225 | LOW | Оставить |

## 5. Content conflicts

| Rule ID | Конфликт | Стороны |
|---|---|---|
| **R-39 vs R-40** | **Машинно-специфичные исключения** — две машины, одно правило, два разных host-specific исключения (nsa-i9 vs dev-pc). | AGENTS.md L289-295 vs constitution.md L340-379 |
| **R-34 vs R-16** | **Git workflow**: разный enforcement (AGENTS.md hard gate vs CLAUDE.md advisory). | AGENTS.md (hard) vs CLAUDE.md (advisory) |
| **R-50 (declared asymmetry)** | `knowledge/README.md#Karaoke-overrides` п.1 «Ассиметрию не «чинить»». **Намеренный** конфликт с TOP-философией. | knowledge/README.md vs неявный TOP-benchmark |
| **R-22 vs R-44** | Self-contained пайплайн (P. I) vs допускает внешние API с одобрения. Не конфликт, но порог нигде не формализован — нет guard-скрипта. | constitution.md P. I |

## 6. Summary для grilling (тикет #104)

### Top-5 кандидатов на консолидацию (highest severity × highest file-count)

1. **R-01** (MUST #0 / Knowledge-first) — HIGH severity, 5 файлов.
   Канон: **AGENTS.md L74-122**.
   Действия: CLAUDE.md L50-73 → cross-ref; knowledge/README.md L62-74 → cross-link; docs/governance/knowledge-first.md → оставить enforcement-секцию (L60-89), удалить дублирующие шаги L31-44.

2. **R-07** (JPA/Hibernate запрет, P. II NON-NEGOTIABLE) — HIGH, 4 файла.
   Канон: **constitution.md L84-94**.
   Действия: CLAUDE.md L130/L177/L214 → cross-ref; architecture-conventions.md L17/L74 → удалить строку; code-style.md L67 → cross-ref.

3. **R-06** (TOP-10 ловушек, дословная копия) — MEDIUM, 2 дословно (CLAUDE.md L123-135 vs architecture-conventions.md L67-79, character-by-character).
   Канон: **architecture-conventions.md L67-79**.
   Действия: CLAUDE.md → удалить список, оставить однострочный cross-ref.

4. **R-04 + R-05** (nginx:stable + node:22-alpine) — MEDIUM, 4 файла каждый.
   Канон: **architecture-conventions.md L20-21**.
   Действия: CLAUDE.md + AGENTS.md L287 → cross-ref; constitution.md L325-327, L358 → cross-ref.

5. **R-10** (GRADLE_USER_HOME) — MEDIUM, 5 локаций в AGENTS.md + CLAUDE.md.
   Канон: **AGENTS.md L333-376**.
   Действия: CLAUDE.md L78-82/L85/L110 → удалить дублирующие команды, оставить cross-ref.

### Top-3 кандидатов на удаление (полностью лишние)

1. **docs/governance/knowledge-first.md** — 158 строк, полная 6-я копия R-01.
   Действия: оставить только enforcement-секцию (L60-89), удалить дублирующие шаги L31-44.

2. **«🚦 НЕ делать» секция в CLAUDE.md (L208-218)** — 8 запретов, каждый уже описан в другом файле (R-16, R-35, R-04, R-05, R-07, R-11, R-36, R-12, R-02).
   Действия: удалить всю секцию, добавить cross-ref на каноны.

3. **CLAUDE.md «Git workflow» L155-169** — список «НЕ делать» (force-push в main/master, `git add ./*`, `--no-verify`) — все уже в AGENTS.md (R-16, R-49).
   Действия: удалить L164-168, оставить L159-162 как 1-строчный cross-ref.

### Top-3 кандидатов на hard-gate (advisory → must be hard-gate)

1. **R-07 (JPA/Hibernate запрет)** — 4 файла, 0 guard-скриптов.
   Действия: `tools/check-no-jpa-imports.sh` — grep `org.springframework.data.jpa|hibernate|javax.persistence` в `karaoke-app/src` и `karaoke-web/src`. Pre-commit + CI gate.

2. **R-04 + R-05 (nginx:stable, node:22-alpine)** — 4 файла каждый, 0 guard-скриптов.
   Действия: `tools/check-docker-image-tags.sh` — grep `FROM nginx:|FROM node:` в `deploy/**/Dockerfile*` + `**/Dockerfile*`.

3. **R-11 (MP4/скачивание запрет)** — 2 файла, 0 guard-скриптов.
   Действия: `tools/check-no-mp4-mentions.sh` — grep `\bmp4\b|скачивани` в `webvue3/src/**/*.vue` (исключить `docs/features/idempotent-path-sanitize.md`).

### Дополнительные наблюдения

- **Enforcement distribution**: только 6 правил из 50 имеют machine-readable guard-скрипт (R-01, R-10, R-13, R-14, R-15, R-16). Остальные 44 — declarative.
- **CLAUDE.md vs AGENTS.md**: 12 правил из 50 частично живут в CLAUDE.md. По `knowledge/README.md#Karaoke-overrides` п.1 — намеренная ассиметрия, но в среднем каждое такое правило имеет «зеркало» в AGENTS.md с slightly different emphasis → drift risk.
- **TOP-компактность** (из #102): 117 строк / 22 правила vs Karaoke 601+237+457+79 строк / 50 правил. Плотность сигнала: TOP = ~5 строк/правило, Karaoke = ~25 строк/правило.
- **Hard-gate enumeration**: TOP имеет 6 правил с явным маркером «Rule + Failure + Mandatory Action». Karaoke — только 2 (MUST #0 + tracker auto-hooks). Лексический маркер «Hard Gate» отсутствует.
- **Conflict R-39 vs R-40** — единственное реальное content conflict (две машины, одно правило). Рекомендация: консолидировать в единую матрицу в AGENTS.md + cross-ref из constitution.md.

### Замечание по формату

Правил оказалось 50, что на верхней границе ожидания 30-50. Если хочется
укрупнить — можно схлопнуть FR-006/007/009 в одну R-26 (Code Standards целиком),
тогда получится ~45 правил. Решение за grilling в тикете #104.

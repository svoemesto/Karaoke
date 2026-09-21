# #102 Resolution — Структурное сравнение governance Karaoke vs TOP

> **Resolution submitted**: 2026-09-14, Pass 379.
> **Resolver**: research-субагент `c9af77de` (background).
> **Skill**: `wayfinder` + Knowledge-first MUST #0.
> **Scope**: фактчеклист для следующего grilling-раунда (тикет #104).
> **Verdict**: НЕ выносился — это наблюдение, не интерпретация.

---

## 1. Длины и распределение содержимого

| Файл | Karaoke | TOP | Примечание |
|---|---|---|---|
| AGENTS.md | **600 строк** | **117 строк** | ×5.13 разница. AGENTS.md в Karaoke = 51% всего governance-веса |
| CLAUDE.md | **237 строк** | **117 строк (= AGENTS.md, дословно идентичны)** | В TOP: sym-link с правилом «Mandatory Action» синхронизировать. В Karaoke: **намеренно расходятся** (`knowledge/README.md#Karaoke-overrides`, п.1: «Ассиметрию не чинить») |
| constitution.md | 457 строк | 453 строк | Сопоставимы по длине; оба длинные за счёт HTML-комментариев Sync Impact Report |
| architecture-conventions.md | 79 строк | **отсутствует** | В TOP вместо неё — `docs/README.md` (111 строк) как живая карта C4 |
| AGENTS_LOCAL.md (TOP only) | — | 122 строки | Машинно-локальные правила (имя агента «Тузьма Квенов», git workflow) — Karaoke ничего аналогичного не имеет |
| **Секции (##/###)** | AGENTS=27, CLAUDE=16 (43 суммарно) | 22 | |
| **Средняя длина секции** | AGENTS=22.2 строки, CLAUDE=14.8 строки | **5.3 строки** | TOP-секции в ~4× компактнее |
| **Эмодзи** | AGENTS=53, CLAUDE=21 (74 суммарно) | 5 (🧠📚🛠🔍⚙️ — все в заголовках H2) | В Knowledge `tools/lint-knowledge.py` emoji **запрещены** в `knowledge/`, но в `AGENTS.md`/`CLAUDE.md` — обильное использование 🚦 |

## 2. Rule duplication index (Karaoke)

Правила, упоминаемые в 2+ файлах governance-стека Karaoke:

| Rule ID | Краткое | Файлы и строки |
|---|---|---|
| **R-01** | MUST #0 / Knowledge-first pre-flight | `AGENTS.md` L74-122 (полная норма + failure-stop), `CLAUDE.md` L50-69 (MUST-CHECKLIST «дублирует MUST #0»), `constitution.md` L257-308 (Principle IX), `knowledge/README.md` L62-74 (протокол работы агента), `knowledge/guidelines/architecture-conventions.md` — отсутствует. **5 файлов** |
| **R-02** | Категорически запрещено: codegraph_explore ДО Knowledge-first | `AGENTS.md` L112 («НЕ ЗАМЕНЯЕТ»), `CLAUDE.md` L204 + L218, `constitution.md` L287-288 + L359-362. **4 файла** |
| **R-03** | Прецедент #339 (агент изобрёл форму кеша) | `AGENTS.md` L80-86, `CLAUDE.md` L204, `constitution.md` L259-264, `knowledge/README.md` (cross-linked из ADR), `docs/governance/knowledge-first.md` (полностью). 5 файлов |
| **R-04** | `nginx:stable` запрет `nginx:alpine` | `CLAUDE.md` L127, L213; `architecture-conventions.md` L20; `constitution.md` L325-326, L358. **4 файла** |
| **R-05** | `node:22-alpine` запрет `node:latest` | `CLAUDE.md` L128, L213; `architecture-conventions.md` L21; `constitution.md` L326, L358. **4 файла** |
| **R-06** | TOP-10 ловушки (KDoc backticks, redirectErrorStream, sanitize idempotency и т.д.) | `CLAUDE.md` L123-135 (полный список), `architecture-conventions.md` L67-79 (дословно копия, L70 = «Всегда true», L71 = «нет bash, контейнер падает» и т.д.). **2 файла, дословное копирование** |
| **R-07** | JPA/Hibernate запрещён (raw JDBC + recordhash) | `CLAUDE.md` L130, L177, L214; `architecture-conventions.md` L17, L74; `constitution.md` L86-91 (Principle II), L312. **4 файла** |
| **R-08** | Sanitizer idempotency (`sanitize(sanitize(s)) == sanitize(s)`) | `CLAUDE.md` L135, `architecture-conventions.md` L32. 2 файла |
| **R-09** | Секреты (`.env`/`.key`/`.pem` НЕ коммитить) | `AGENTS.md` L287, `constitution.md` L191-255 (Principle VIII, 5 пунктов + чек-лист). 2 файла |
| **R-10** | GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle | `AGENTS.md` L329, L335-357 (полная секция + guard-скрипт), `CLAUDE.md` L78-82, L85, L110. 3 файла |

**В TOP**: правило «Symmetric Instructions» (L15-16) делает дубликаты **by design** — каждое изменение любого из файлов = обязательный sync в оба.

## 3. Failure-stop coverage (Karaoke)

| Раздел | Failure-stop | Категория |
|---|---|---|
| **MUST #0 Knowledge-first** (L74-122) | ✅ **enforceable**: `tools/spec-knowledge-preflight.sh` (Pass 340), `tools/check-knowledge-structure.sh`, `tools/check-knowledge-cross-links.sh`, `tools/lint-knowledge.py`, `.ssot-map.yml` + `check-ssot-impact.py`. Failure-stop явно: «спека возвращается на `/speckit.clarify`» | **enforceable** |
| **GRADLE_USER_HOME** (L333-376) | ✅ **enforceable**: `tools/check-gradle-user-home.sh` для pre-commit + CI | **enforceable** |
| **DOCKER_CONFIG** (L378-427) | ✅ **enforceable**: `tools/check-docker-config.sh` для pre-commit + CI | **enforceable** |
| **Перезапуск контейнеров через `deploy/do.sh`** (L429-487) | ✅ **enforceable**: `tools/check-container-restart.sh` для pre-commit + CI | **enforceable** |
| **Frontend build `cd <frontend-dir>`** (L489-583) | ✅ **enforceable**: `tools/check-frontend-build.sh` | **enforceable** |
| **Git — CI-gate для master** (L297-325) | ✅ **enforceable**: 3 уровня — GitHub branch protection, pre-commit hook `pre-commit-block-master.sh`, CI lint step | **enforceable** |
| **Git workflow в CLAUDE.md** (L155-169) | ⚠️ «НЕ делать»-список без failure-stop — «всегда проверять git status» (advisory) | **слабый** |
| **TOP-10 ловушки в CLAUDE.md** (L123-135) | ❌ **нет failure-stop** для KDoc-backticks, redirectErrorStream, JPA/Hibernate, MP4/скачивание. Только «см. оферту», «использовать X» | **нет failure-stop** |
| **Стратегия проекта в CLAUDE.md** (L139-151) | ❌ **нет failure-stop**: «visitor→registration», «без trial», «только онлайн» — только advisory | **нет failure-stop** |
| **🚦 НЕ делать в CLAUDE.md** (L208-218) | ❌ **нет failure-stop**: 8 запретов (коммитить в master, менять AGENTS.md без согласования, nginx:alpine, node:latest, JPA/Hibernate, MP4/скачивание, площадки, trial) — **без enforcement-скрипта** | **нет failure-stop** |
| **🚦 ОБЯЗАТЕЛЬНО перед каждым `git commit`** (L76-104) | ⚠️ Слабый: 4 шага списком, но failure-stop — только «не коммить с `--no-verify`», без CI-скрипта, который бы их enforcement'ил как **обязательный pre-push** (есть только pre-commit hooks ktlint/eslint) | **слабый** |
| **🚦 CI 7/7 PASS** (L106-119) | ✅ **enforceable**: через `.github/workflows/lint.yml` (упоминается неявно) | **enforceable** |
| **🚦 MUST-CHECKLIST** (L50-73) | ⚠️ Слабый: «если шаг пропущен — СТОП», но без скрипта, который бы ловил факт пропуска. Enforcement делегирован `tools/spec-knowledge-preflight.sh` (Pass 340), но только для спекификаций, не для ad-hoc правок | **слабый** |
| **Tech stack секции** (L28-46 CLAUDE.md, L312-329 constitution.md) | ❌ Нет failure-stop — только declarative | **нет failure-stop** |
| **Обязательная проверка после ЛЮБОГО изменения кода** (AGENTS.md L585-597) | ⚠️ Слабый: 5 шагов списком, нет автоматического enforcement'а на уровне «запретить коммит, если шаг 3 (bootJar) не выполнен» | **слабый** |

**Распределение**:
- **enforceable**: 6 правил (все добавлены в Pass 340-375 как OP #83 follow-up)
- **слабый**: 4 правила
- **нет failure-stop**: 5 правил

## 4. Hard-gate enumeration (TOP rules отсутствуют или слабее в Karaoke)

| TOP Hard-Gate (строка) | В Karaoke | Расхождение |
|---|---|---|
| **«Hard Gate: Documentation First»** (TOP/AGENTS.md L5-8): «FORBIDDEN from searching codebase/docs until you read `docs/README.md`» с явным «**Failure**: ... is a critical protocol violation» | ⚠️ **Есть аналог MUST #0** в Karaoke (AGENTS.md L74), но **без маркера «Hard Gate»** и без явного термина «FORBIDDEN». Failure-stop формулировка мягче: «спека возвращается на /speckit.clarify» | Формулировка: TOP — «FORBIDDEN» + «critical protocol violation»; Karaoke — «MUST» + «failure-stop» |
| **«Symmetric Instructions»** (TOP L14-16): «CLAUDE.md и AGENTS.md MUST remain identical. If you modify one, you MUST immediately apply the same changes to the other.» | ❌ **Полностью противоположное правило**: `knowledge/README.md#Karaoke-overrides`, п.1 явно: «**Ассиметрию НЕ чинить**» | Конфликт governance-философии |
| **«Mandatory Action: use codebase indexing tools / read / search to verify»** (Duty of Doubt, TOP L18-22) | ❌ **Нет аналога** в Karaoke. Karaoke полагается на принцип «Memory + Knowledge SSoT» вместо «verify-on-demand» | TOP приоритизирует verify-on-demand, Karaoke — pre-flight pre-loaded |
| **«Strict No Guessing Policy»** (Problem Solving, TOP L24-30): «Chain of Proof: [Trigger] → [Specific Code Line/Config] → [Observed Behavior] → [Proven Reason]» | ❌ **Нет аналога** в Karaoke. Ближайшее — `AGENTS.md` § «Диагностика на локальной машине» (L165-225), но без Chain-of-Proof ритуала | TOP формализует RCA, Karaoke — checklists |
| **«Mandatory Context Retrieval»** (TOP L36-38): «Before starting ANY task ... you MUST first read `docs/README.md`» | ✅ **Дублируется** в Karaoke MUST #0 | Эквивалент |
| **«Subagent Initialization Protocol»** (TOP L105-112): 6-поинтовый **inject в каждый subagent prompt** (Hard Gate, SSoT-Verified, L3 Abstraction, Protocol Sequence, Linking Protocol, Template Mandate) | ❌ **Нет аналога** в Karaoke. Karaoke имеет только общие правила; subagent-инжекция не формализована | TOP явно требует передачи 6 правил в каждый subagent; Karaoke — неявно |
| **«Editing Files (Tool: `edit`): oldString must be unique»** (TOP L114-117) | ❌ Нет аналога в governance (упоминается как ad-hoc hint) | TOP кодифицирует поведение инструмента |
| **«NEVER use emojis in any communication or files»** (TOP L11-12) | ❌ **Прямо противоположное**: `CLAUDE.md` L50-218 использует 🚦 (21 раз), `AGENTS.md` использует 🧠📚⚙️ (53 emoji) | Конфликт |
| **«Mandatory Action»** marker (TOP: L16, L20, L73, L110): явный лейбл для enforceable шагов | ⚠️ Karaoke использует «**Enforcement**» (AGENTS.md L374, L425, L485, L557) и «**MUST**» | Другой лексический маркер |

**Ключевые наблюдения**:
- В TOP **6 правил** имеют явный маркер «**Rule** + **Failure** + **Mandatory Action** + **Protocol**» = формальный шаблон.
- В Karaoke аналогичный формат только у MUST #0 (Pass 340) и 4 governance-PR (Pass 372-375) — все написаны по пост-мортемам OP #83.
- TOP использует **«Hard Gate»** как именованный концепт. Karaoke — нет.

## 5. Anti-pattern candidates (observations, без вердикта)

- **«Слишком длинный → внимание рассеивается»**:
  - AGENTS.md Karaoke 600 строк vs TOP 117 строк (5.1×).
  - 27 секций в Karaoke AGENTS.md при средней длине **22.2 строки/секцию** — секции достаточно длинные для сканирования.
  - CLAUDE.md Karaoke 237 строк, 16 секций, **14.8 строки/секцию** — компактнее, но добавляет второй источник истины.
  - **TOP 117 строк / 22 секции / 5.3 строки/секцию** — каждая секция ≤ 5 строк, scan-friendly.

- **«Дублирование правил → модель не знает, какое применять»** (из п.2):
  - **R-01 (MUST #0 / Knowledge-first)** — **5 файлов** описывают одно правило с slightly different emphasis:
    - `AGENTS.md` L74 — норма + failure-stop «возврат на /speckit.clarify»
    - `CLAUDE.md` L50 — 10-step checklist «дублирует MUST #0»
    - `constitution.md` L257 — Principle IX + 5 шагов + IX.2 failure-stop «вернуть на /speckit.clarify»
    - `knowledge/README.md` L62-74 — generic protocol «Initialize → Analyze → Mutate → Verify → Purge»
    - `docs/governance/knowledge-first.md` — 158-строчная операционная памятка
  - **R-06 (TOP-10 ловушки)** — дословно копируется в `CLAUDE.md` L123-135 и `architecture-conventions.md` L67-79 (проверено: строки L70-L74 совпадают почти character-by-character, включая порядок и формулировки). **«Докуда ходить» таблица в CLAUDE.md L186-199 повторяет иерархию из AGENTS.md L227-232.**
  - **R-10 (GRADLE_USER_HOME)** — `AGENTS.md` L329 (одна строка в секции «Сборка»), L335-357 (полная секция 22 строки с прецедентом Pass 372), L589 (шаг 1 в Обязательной проверке), `CLAUDE.md` L78 (NON-NEGOTIABLE-блок), L85 (команда), L110 (CI 7/7). **5 локаций**.

- **«Неинструментабельные правила → нет failure-stop»** (из п.3):
  - **«JPA/Hibernate запрещён»** (4 файла, без guard-скрипта) — единственная проверка: ручной code review. Ловушка уже 2 раза была нарушена (см. constitution L86 «NON-NEGOTIABLE»), но нет `tools/check-no-jpa-imports.sh`.
  - **«nginx:stable, не :alpine»** (4 файла, без guard-скрипта) — violation только обнаруживается через runtime fail (bash missing). Нет `tools/check-docker-image-tags.sh`.
  - **«node:22-alpine, не :latest»** (4 файла, без guard-скрипта) — то же.
  - **«MP4/скачивание запрещено упоминать»** (CLAUDE.md L134, L215; оферта) — нет machine-readable проверки, только ручной code review комментариев.
  - **«Без trial в первом раунде»** (CLAUDE.md L148, L217) — нет guard-скрипта, проверяется только через PR review.
  - **«🚦 НЕ делать» в CLAUDE.md** (L208-218) — список из 8 запретов; **ни один** не имеет enforcement-скрипта.

## 6. Additional observations

1. **Эмодзи policy — расхождение внутри Karaoke**:
   - `knowledge/guidelines/architecture-conventions.md` L67 — раздел «TOP-10» без emoji
   - `CLAUDE.md` L50, L76, L106, L123, L139, L155, L173, L184, L202, L208 — **10 секций** начинается с 🚦
   - `AGENTS.md` — emoji в ⛔ (L297), 🚦 (нет), 🎯 (нет), но ⚠️ в changelog-блоке (L3-68)
   - TOP/AGENTS.md L11-12: **«NEVER use emojis in any communication or files»** — но L3, L34, L55, L89, L103 используют 🧠📚🛠🔍⚙️ в заголовках H2.
   - **Конфликт внутри TOP**: rule «no emoji» нарушается самим файлом в 5 местах.

2. **Header-форматирование vs inline rules**:
   - **TOP**: все правила структурированы единообразно — `### Hard Gate: <name>` → `**Rule**: ...` → `**Protocol**: ...` → `**Failure**: ...`. **5 из 8 hard-gates** следуют этому шаблону.
   - **Karaoke**: правила оформлены inconsistently — некоторые как секции с подсекциями (MUST #0: Шаги MUST + Failure-stop + Синхронизация), некоторые как plain paragraph (TOP-10 ловушки: нумерованный список без явных маркеров), некоторые как `### Правило` + `### Команды` (Диагностика на локальной машине L173-219).

3. **Файловая иерархия governance**:
   - **TOP**: 4 файла — `constitution.md` (453) + `AGENTS.md` (117) + `CLAUDE.md` (117, sym) + `AGENTS_LOCAL.md` (122, .gitignore). Local-only файл для машинно-специфичных правил.
   - **Karaoke**: 4 файла — `constitution.md` (457) + `AGENTS.md` (600) + `CLAUDE.md` (237) + `architecture-conventions.md` (79). **Нет local-only файла** — все машинно-специфичные исключения (Pass 282 `nsa-i9`/`dev-pc`) вшиты в AGENTS.md L289-295.

4. **Sync Impact Report (constitution changelog)**:
   - Оба проекта используют HTML-комментарии в начале `constitution.md`. TOP имеет 6 amendment-блоков (1.1.4 → 1.4.1). Karaoke имеет 2 (2.0.0 → 2.1.0 → 2.2.0). **Karaoke changelog более компактен**, но менее детален (TOP указывает template-sync, deferred TODOs).

5. **Перекрёстные ссылки как governance**:
   - `docs/governance/knowledge-first.md` (Karaoke, 158 строк) — **отдельный файл**, который de facto является 6-й копией правила MUST #0. Содержит таблицу «где какое правило живёт» (L51-56). Сам **факт существования** этого файла = признание, что правило разбросано.
   - TOP ничего аналогичного не имеет — single source of truth для каждого правила.

6. **Guard-скрипты vs check-скрипты**:
   - **Karaoke** имеет **20+ `check-*.sh` / `check-*.py` скриптов** в `tools/` (Pass 372-375 + Pass 340-358). Все привязаны к pre-commit или CI.
   - **TOP** имеет **0 guard-скриптов** для governance. Все правила в AGENTS.md/constitution.md — **declarative**, без executable enforcement. Это **самое большое расхождение в зрелости governance** между проектами.

7. **«НЕ дублировать» vs «Mandatory Action sync»**:
   - Karaoke `AGENTS.md` L601 (footer): «НЕ дублировать детали» — но 5 файлов всё равно дублируют MUST #0.
   - TOP `AGENTS.md` L15: «Mandatory Action: sync changes immediately» — но `AGENTS_LOCAL.md` L34: «При конфликте ... локальный файл имеет приоритет, если конфликт не затрагивает принципы конституции». Это создаёт **3-way conflict resolution**: constitution > AGENTS_LOCAL > AGENTS.md.

8. **Объём governance в соотношении к проекту**:
   - Karaoke governance ≈ 600 + 237 + 457 + 79 = **1373 строк** на 1 проект.
   - TOP governance ≈ 117 + 117 + 453 + 122 + 111 = **920 строк** на 1 проект.
   - При этом TOP имеет **больше правил с явным Hard-Gate маркером** на строку (выше плотность сигнала), а Karaoke — больше advisory-правил с failure-stop-формулировками.

---

## Резюме для следующего grilling (тикет #104)

> **Фактчек** (НЕ вердикт): в Karaoke governance на 1 явный hard-gate приходится ~125 строк,
> в TOP — ~15 строк. Только 6 из 15 правил в Karaoke имеют enforcement-скрипт. R-01 (MUST #0)
> живёт в 5 файлах, R-06 (TOP-10 ловушки) — в 2 дословно, R-10 (GRADLE_USER_HOME) — в 5 локациях.
> Ассиметрия AGENTS.md vs CLAUDE.md — намеренная (override #1), но создаёт второй источник истины
> без guard-скрипта. TOP-компактность (1.4× меньше при сопоставимом покрытии правил) за счёт
> формата «Rule/Protocol/Failure» и **0 guard-скриптов** — все правила declarative.

> **Следующий шаг**: тикет #104 (grilling «Scope of governance rewrite» для Karaoke) —
> на основе этих фактов решить, какие секции удалить / переписать / добавить / какие
> правила канонизировать (где живёт «single source of truth» для каждого R-XX).

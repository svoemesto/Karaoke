<!-- description карты в OpenProject — task #100 -->

## Destination

Переписать всю governance-цепочку Karaoke (`AGENTS.md`, `CLAUDE.md`,
`.specify/memory/constitution.md`, `knowledge/README.md#Karaoke-overrides`,
governance-критичные секции `knowledge/guidelines/`) так, чтобы:

1. **AGENTS.md** стал лаконичен (≤ ~200 строк) и сфокусирован на runtime
   directives + hard gates + failure-stop.
2. **CLAUDE.md** остался рекомендательным (для Claude Code), но компактным
   и не дублирующим AGENTS.md (Karaoke-override #1 сохраняется — ассиметрия
   by design).
3. Каждое правило имеет явный **failure-stop** (нарушение → стоп + обоснование).
4. Нет дублирования между файлами (одно правило живёт ровно в одном месте).
5. Структурные метрики достигнуты (см. Success criteria).

Это effort **на много сессий** (Pass 380+). Один тикет ≈ одно решение или
одна фича governance. Карта линейно движется от диагностики к переписыванию.

## Notes

Стоячие предпочтения для каждой сессии, работающей эту карту:

- **Knowledge-first MUST #0** обязателен (см. `knowledge/README.md`,
  AGENTS.md MUST #0). Каждая сессия MUST прочитать `knowledge/README.md` +
  `knowledge/domains/README.md` **до** любых изменений governance.
- **Skills**: каждая сессия MUST подгружать `triage` / `grilling` /
  `wayfinder` / `managing-living-docs` по требованию.
- **Governance-review требования** (CLAUDE.md § Governance-review): любая
  правка `AGENTS.md`, `constitution.md`, `.gitignore`, `Karaoke-overrides` —
  требует semver bump, секции «Governance Impact» в PR, одобрения владельца.
- **OpenProject workflow NON-NEGOTIABLE** (AGENTS.md § Issue-tracker):
  каждый тикет → `claim-issue` → работа → `add-comment --file report.md` →
  `mark-review` → `close-issue`. Auto-hooks Pass 350 (`tracker-bootstrap.sh`,
  `tracker-implement-done.sh`) — optional, но workflow обязателен.
- **Karaoke-override #1 NON-NEGOTIABLE**: AGENTS.md ≠ CLAUDE.md.
  Ассиметрию НЕ чинить. Это зафиксировано в `knowledge/README.md` и НЕ
  пересматривается в этой карте, пока не будет отдельного governance-PR.

## Decisions so far

<!-- индекс закрытых тикетов. Заполняется по мере close-issue -->

- [**Структурное сравнение governance Karaoke vs TOP**](#102)
  (closed Pass 379): hard findings —
  - AGENTS.md (600 строк) vs TOP (117 строк) = ×5.1 разница.
  - В Karaoke **6 из 15 правил** имеют enforcement-скрипт (все post-OP #83).
  - **10 правил дублируются в 2+ файлах** (R-01 MUST #0 = 5 файлов,
    R-06 TOP-10 = 2 дословно, R-10 GRADLE_USER_HOME = 5 локаций).
  - В TOP **6 правил** имеют формат `Rule/Protocol/Failure` (именованный
    маркер «Hard Gate» + «Mandatory Action»).
  - В Karaoke **emoji находятся в конфликте**: rules в одном файле
    запрещают emoji, в другом используют 🚦 21 раз.
  - Самые длинные секции AGENTS.md = 22.2 строки (Karaoke) vs
    5.3 строки (TOP) — scan-friendly формат TOP в 4× компактнее.
  - Details: `specs/_wayfinder-99-governance-rewrite/102-research-resolution.md`.
  - **Action вытекает**: тикет #104 (grilling) теперь может работать
    с конкретным списком R-XX + мест дублирования.

- [**Rule duplication map для governance Karaoke**](#103)
  (closed Pass 379): 50 правил R-01…R-50 inventory + cross-reference table —
  - **12 правил дублируются ≥2 файлов**, severity:
    HIGH (R-01 MUST #0, R-07 JPA), MEDIUM (R-04 nginx, R-05 node, R-06
    TOP-10 дословно, R-10 GRADLE_HOME, R-15 Frontend, R-32 FR-009, R-43
    redirectErrorStream), LOW (R-11 MP4).
  - **Рекомендация по source-of-truth** (для grilling #104):
    - **AGENTS.md**: R-01, R-10 (gradle), R-15 (frontend), R-16 (git), R-17 (tracker), R-19/20 (knowledge SSoT).
    - **constitution.md**: R-07 (JPA, P. II), R-22-29 (Principles I-IX), R-32 (FR-009), R-43 (ADR-0006).
    - **architecture-conventions.md**: R-04 (nginx), R-05 (node), R-06 (TOP-10 ловушек), R-11 (MP4), R-44 (MLT).
    - **knowledge/README.md**: R-50 (Karaoke-overrides).
  - **Top-3 на удаление** (полностью лишние):
    1. `docs/governance/knowledge-first.md` — 6-я копия R-01, удалить L31-44, оставить только enforcement-секцию.
    2. CLAUDE.md «🚦 НЕ делать» (L208-218) — 8 запретов, все дублируют R-16/R-35/R-04/R-05/R-07/R-11/R-36/R-12/R-02.
    3. CLAUDE.md «Git workflow» L155-169 — все правила уже в AGENTS.md (R-16, R-49).
  - **Top-3 на hard-gate** (новые guard-скрипты):
    1. R-07 JPA/Hibernate запрет → `tools/check-no-jpa-imports.sh`.
    2. R-04+R-05 nginx/node теги → `tools/check-docker-image-tags.sh`.
    3. R-11 MP4/скачивание → `tools/check-no-mp4-mentions.sh`.
  - **Реальный content conflict** (не дизайн-намерение): R-39 vs R-40
    (машинно-специфичные исключения `nsa-i9`/`nsa` vs `dev-pc`/`dev`).
    Машина одна, hostname один. Нужно объединить в единую матрицу.
  - Details: `specs/_wayfinder-99-governance-rewrite/103-research-resolution.md`.
  - **Action вытекает**: тикет #104 (grilling) теперь имеет исчерпывающий
    R-XX-список с source-of-truth candidate'ами. Владелец может принять
    решение «что удалить / оставить / добавить hard-gate».

- [**Scope of governance rewrite для Karaoke**](#104)
  (closed Pass 379): grilling-раунд с владельцем, **7 решений из 7 приняты**
  по рекомендациям агента:
  - **Q1 ✅ канон R-01 (MUST #0) = AGENTS.md**; CLAUDE.md L50-73 → cross-ref;
    `docs/governance/knowledge-first.md` → оставить только enforcement L60-89.
  - **Q2 ✅ канон build/docker = architecture-conventions.md**; CLAUDE.md → cross-ref.
  - **Q3 ✅ 3 новых guard-скрипта**: `check-no-jpa-imports.sh`,
    `check-docker-image-tags.sh`, `check-no-mp4-mentions.sh` (R-07, R-04/05, R-11).
  - **Q4 ✅** R-39 vs R-40 **консолидировать** в `AGENTS.md`
    § «Машинно-специфичные исключения» (единая матрица).
  - **Q5 ✅ ограничить emoji** до structure markers (✅/❌/⚠️/🚦) +
    явное правило в AGENTS.md.
  - **Q6 ✅ constitution.md — средний scope** (b): сократить Principles
    до ~10 строк + удалить дубликаты R-04/R-05/R-32/R-43 → cross-ref.
  - **Q7 ✅ AGENTS.md — TOP-стиль плотный, ~150 строк**
    (density 5 строк/правило, формат Rule/Protocol/Failure).
  - **Graduate-нутые task-тикеты** (12 штук, создаются в следующей сессии
    через `tools/tracker.sh create-issue`):
    `[wayfinder:task]` для каждой имплементации.
  - Details: `specs/_wayfinder-99-governance-rewrite/104-grilling-resolution.md`.
  - **Action вытекает**: карта wayfinder завершила чартирование + grilling.
    Следующая сессия — **implementation** через `speckit-*` workflow +
    `git checkout -b NNN-governance-rewrite` + governance-review.

## Not yet specified

> **Состояние**: после закрытия #104 (grilling) — туман в основном рассеян.
> Карта wayfinder завершила чартирование + grilling; следующая фаза — **implementation**
> (`[wayfinder:task]`-тикеты graduate'ятся в новой сессии).
>
> Единственный оставшийся туман — это **технические детали имплементации**,
> которые решаются не wayfinder, а speckit-* pipeline:

- **Точный текст каждого task-тикета**: какие файлы трогать, какие guard-скрипты писать,
  какие cross-ref формулировать. Это будет в `specs/380-governance-rewrite/spec.md`
  после `speckit-specify`.
- **Порядок merges**: тикеты #1-#3 (guard-скрипты) могут идти parallel, #4-#7 (rewrites)
  тоже, #8 (главный rewrite AGENTS.md) — последним. Это решение для `speckit-plan`.
- **CI 7/7** подтверждение после каждого merge: каждый task-тикет обязан пройти
  `tools/check-*.sh` (Pass 372-375) + ktlintCheck + ESLint baseline.
- **Governance-review перед merge** каждого governance-touching change:
  semver bump + секция «Governance Impact» в PR description.

Все остальные graduate'ы переехали в `Decisions so far` (#104) или стали task-тикетами.

## Out of scope

Работа, осознанно выведенная за этот effort. Возвращается только при
перерисовке destination:

- **«Доступ — только онлайн» / `offer.html`** — НЕ трогаем. Не часть
  governance-документов.
- **`docs/architecture-notes-archive.md`** — исторический changelog,
  переписывается в архив отдельно.
- **Генерация PDF-версии governance-документов** — отдельный effort.
- **Unify AGENTS.md и CLAUDE.md в один файл** — запрещено Karaoke-override #1.
- **`AGENTS_LOCAL.md`** в проекте TOP — это специфика TOP (Pass 282 follow-up),
  в Karaoke такого файла нет и не планируется.
- **`autocode`/`autotest` пайплайны** в TOP — не имеют отношения к
  governance-переписыванию Karaoke.

---

## Status

- Карта создана: 2026-09-14 (Pass 379).
- Создатель: ai-agent, по инициативе владельца (задача OpenProject #99).
- **Карта закрыта** (Pass 379, после #104): все decision-тикеты (research + grilling) завершены.
- Следующая фаза: **implementation** — 12 `[wayfinder:task]` тикетов будут созданы
  в новой сессии через `tools/tracker.sh create-issue` и пройдут speckit-* pipeline.
- Метрики успеха (после implementation):
  AGENTS.md ≤ 200 строк (target ~150), R-01 живёт в 1 файле, R-04/05/06/08/11 живут
  в 1 файле (architecture-conventions.md), hard-gate coverage ≥ 18% (9 из 50 правил).

### Status update: Pass 379 follow-up (2026-09-15)

**Step A** ✅ Completed:
- PR #483 (JPA guard, R-07) — MERGEABLE.
- PR #484 (MP4 guard, R-11) — MERGEABLE.
- PR #485 (docker-image-tags guard, R-04, R-05) — MERGEABLE.
- Hard-gate coverage: 6 → 9 из 50 правил (12% → 18%).

**Governance follow-up** ✅ Completed:
- PR #486 (subagent workspace isolation) — MERGEABLE.
- AGENTS.md v2.7.0 → v2.8.0 + constitution.md v2.2.0 → v2.3.0.
- Новый guard `tools/check-subagent-isolation.sh`.

**Step B / C / D / E** ⏳ Pending:
- 9 task-тикетов (#108-#116) готовы к запуску ПОСЛЕ merge Step A + #486.
- После merge #486 — каждый субагент в своём `git worktree`
  (Pass 379 follow-up, новый rule).

### Final status

**4 PR'а готовы к merge** (вам решать):
1. PR #483 — no-jpa-imports guard (R-07).
2. PR #484 — no-mp4-mentions guard (R-11).
3. PR #485 — docker-image-tags guard (R-04, R-05).
4. PR #486 — subagent-isolation guard (governance).

После merge — закрыть #99 (владелец решает, Pass 349).

---
description: "Task list for spec 318 — Prod DB Log Audit (OpenProject #66)"
---

# Tasks: Prod DB Log Audit (OpenProject #66)

**Input**: Design documents from `/specs/318-prod-db-log-audit/`
**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md) | **Data**: [data-model.md](data-model.md)
**Branch**: `318-prod-db-log-audit`
**Tests**: not requested (операционная задача; верификация — ручная по SC-001)

**Organization**: Tasks сгруппированы по user story (P1 → P2 → P3). US2 и US3 —
**условные**: выполняется **ровно одна** в зависимости от вердикта US1.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Создать структуру каталогов, проверить ssh-доступ, настроить `.gitignore` для секретов.

- [ ] T001 Создать каталоги `specs/318-prod-db-log-audit/{scripts,data}` и зафиксировать `.gitignore` для `data/raw.log` (НЕ коммитится) и `data/logs.jsonl` (коммитится sanitized) в `specs/318-prod-db-log-audit/data/.gitignore`
- [ ] T002 [P] Проверить prerequisites локально: `bash --version | head -1` (≥4.4), `jq --version` (≥1.6), `awk --version | head -1` (gawk), `git --version`. Записать вывод в `data/prerequisites.log`
- [ ] T003 [P] Проверить SSH-доступ к `ssh root@188.119.64.111` через `BatchMode=yes`. Сохранить `docker ps --format '{{.Names}}'` и `docker inspect karaoke-db --format '{{.HostConfig.LogConfig.Type}}'` в `data/raw.metadata.json` (поле `log_driver`)

**Checkpoint**: T001–T003 завершены → структура есть, ssh доступен, log driver известен.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Базовые правила категоризации и скелет скриптов. Без них US1 не запустится.

**⚠️ CRITICAL**: Phase 3 (US1) не может начаться без завершения Phase 2.

- [ ] T004 Создать `data/categories.json` со всеми правилами из `data-model.md::Entity 2` (R01 … RN, default=`__unclassified__`). Валидация: `jq . data/categories.json` без ошибок
- [ ] T005 [P] Создать заголовочный файл `scripts/_lib.sh` с общими функциями: `log()`, `die()`, `require_ssh()`, `sanitize_inline()` (см. research R-3). `shellcheck scripts/_lib.sh` без warning'ов
- [ ] T006 [P] Создать `data/sanitize-patterns.txt` — список regex для санитизации (из research R-3): пароли, postgres://user:pass@, Bearer, pat_*, [a-f0-9]{32}
- [ ] T007 Создать тестовый fixture `data/test-fixture.log` (10 строк: Postgres log + ERROR + auto_explain + unclassified). Используется в T013 для smoke-теста

**Checkpoint**: T004–T007 завершены → категоризация готова, sanitize готов, fixture есть.

---

## Phase 3: User Story 1 — Аналитический отчёт по инцидентам недели (Priority: P1) 🎯 MVP

**Goal**: Скачать `docker logs karaoke-db` за 168 ч с прода через SSH,
структурировать, прокатегоризировать, опубликовать `report.md` с вердиктом.

**Independent Test**: `report.md` существует, содержит 9 разделов из
`contracts/report-format.md`, вердикт — одно из `ОТКЛЮЧАТЬ`/`ОСТАВЛЯТЬ`,
`md5sum report.md` стабилен при повторе пайплайна.

### Implementation for US1

- [ ] T010 [US1] Реализовать `scripts/01-fetch-logs.sh`: проверка SSH → определение log driver → `docker logs --since=168h --timestamps karaoke-db` (или `journalctl`) → запись в `data/raw.log` (НЕ в git) + `data/raw.metadata.json`. Запустить на реальном проде
- [ ] T011 [US1] Реализовать `scripts/02-parse-and-categorize.sh`: парсинг timestamp/level → категоризация через `data/categories.json` → выход `data/logs.jsonl` (LogRecord), `data/incidents.jsonl` (IncidentCandidate), `data/metrics.json` (AuditMetrics). Детерминированная сортировка по `(timestamp, source_line_number)`. Запустить на `data/raw.log`
- [ ] T012 [US1] Реализовать `scripts/03-generate-report.sh`: собирает `report.md` строго по контракту `contracts/report-format.md` (9 разделов). Все timestamp'ы из `data/metrics.json::actual_window_*`, не из `$(date)`. Запустить на результатах T011
- [ ] T013 [US1] Smoke-test: прогнать T011+T012 на `data/test-fixture.log` из T007. Проверить, что категории распределились как ожидалось (fixture покрывает все правила R01–RNN). Если падает — фиксить скрипты
- [ ] T014 [US1] SC-002 reproducibility: запустить T011+T012 дважды на одном и том же `data/raw.log`, сверить `md5sum data/logs.jsonl` (должны совпасть). Если не совпали — найти не-детерминированный источник
- [ ] T015 [US1] Sanity-check структуры `report.md`: `test -f report.md`, `wc -l report.md` (≤500), `grep -c '^## ' report.md` (ровно 9), `grep '^## Вердикт:' report.md` (`ОТКЛЮЧАТЬ` или `ОСТАВЛЯТЬ`)
- [ ] T016 [US1] Прочитать `report.md`, **выбрать вердикт** на основе данных. Записать вердикт в задачу #66 через комментарий в OpenProject: «Вердикт: ОТКЛЮЧАТЬ/ОСТАВЛЯТЬ, см. report.md». **Это и есть развилка US2 vs US3**

**Checkpoint**: Phase 3 done = `report.md` опубликован локально, вердикт зафиксирован, задача #66 помечена как «вердикт вынесен». Далее → Phase 4 (если ОТКЛЮЧАТЬ) или Phase 5 (если ОСТАВЛЯТЬ).

---

## Phase 4: User Story 2 — Отключение verbose-логирования (Priority: P2) ⑂ conditional

**Goal**: Применить конкретные изменения конфигурации `karaoke-db` на проде
**только по прямому согласию владельца**, выполнить smoke-проверку ERROR'ов,
сделать замер через 24 ч.

**Independent Test**: docker logs за следующие 24 ч содержит <50 % сообщений
vs. до отключения, ERROR-сообщения по-прежнему появляются,
`docker ps` показывает `karaoke-db` в статусе `Up`,
`curl http://<PROD_HOST>/health` = 200.

**⚠️ Условие входа**: выполняется **только если** вердикт T016 == `ОТКЛЮЧАТЬ`.

### Implementation for US2

- [ ] T020 [US2] Заполнить `contracts/disable-config.md` по результатам T015: Блок A (доминирующие категории), Блок C (команды применения), Блок D (метрики контрольного замера). **Без POSTGRES_PASSWORD в файле** — вместо этого `ssh root@188.119.64.111 "cat /root/.pgpass"` (см. contracts/disable-config.md::Блок C)
- [ ] T021 [US2] **Ждём согласие владельца**. Написать владельцу в чате: «Вердикт ОТКЛЮЧАТЬ. Готов применить: [краткое summary из disable-config.md::Блок C]. Применить? (да/нет/уточнить)». **НЕ запускать** T022 без явного «да»
- [ ] T022 [US2] Применить изменения: `ssh root@188.119.64.111 "docker exec karaoke-db psql -U postgres <<EOF\nALTER SYSTEM SET ...;\nSELECT pg_reload_conf();\nEOF"` (или restart контейнера с новыми env, если ALTER SYSTEM не подходит — определяется в T020). Записать до/после `SHOW ALL` в `data/disable.before.log` / `data/disable.after.log`
- [ ] T023 [US2] Smoke-test ERROR: триггернуть намеренный ERROR через существующий API (например, `curl` к endpoint, возвращающему ошибку), проверить, что ERROR появился в `docker logs karaoke-db` за последнюю минуту. Записать в `data/smoke-test-error.log`
- [ ] T024 [US2] **Через 24 ч** запустить `scripts/05-post-change-metrics.sh` (создаётся в этой задаче): собрать `total_bytes`, `error_count`, `share_pct` доминирующей категории за последние 24 ч. Сравнить с `data/metrics.json` (baseline). Записать в `data/post-change-metrics.json` и **обновить `report.md`** — добавить блок «Контрольный замер (через 24 ч)» в конец

**Checkpoint**: US2 done = verbose-логирование выключено, ERROR'ы пишутся, контрольный замер показывает уменьшение объёма ≥50 %. Если нет — фиксить и повторять.

---

## Phase 5: User Story 3 — Решение «оставлять» и фиксация порога (Priority: P3) ⑂ conditional

**Goal**: Зафиксировать в `report.md` конкретный триггер для будущего отключения.

**Independent Test**: в `report.md` есть блок «Критерии отключения в будущем» с
конкретной датой / событием / метрикой (не «когда-нибудь»).

**⚠️ Условие входа**: выполняется **только если** вердикт T016 == `ОСТАВЛЯТЬ`.

### Implementation for US3

- [ ] T030 [US3] Обновить `report.md` Раздел 6 (Вердикт: ОСТАВЛЯТЬ) — добавить блок «Критерии отключения в будущем» с конкретным триггером. Записать в OpenProject комментарий: «Вердикт ОСТАВЛЯТЬ. Триггер для пересмотра: [конкретная дата / событие / метрика]»
- [ ] T031 [US3] Создать follow-up таск в OpenProject через `tracker.sh create-issue --project-id 1 --type-id 1 --subject "Повторный prod-db-log-audit через 30 дней (после #66)"`. **ID нового таска** получаем из JSON-ответа команды (`jq -r '.id'`). Записать новый ID в `report.md` в раздел «Критерии отключения в будущем» как `OpenProject follow-up: #<id>`. **Due date** в текущем `tracker.sh` не поддерживается — вместо этого явно прописать дату в subject/description таска (например, `…через 30 дней (deadline: 2026-10-08)`)

**Checkpoint**: US3 done = триггер зафиксирован в `report.md`, follow-up таск создан.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Sanitization, публикация, закрытие задачи #66.

- [ ] T040 [P] Реализовать `scripts/04-sanitize-for-commit.sh` (см. research R-3): прогнать `data/logs.jsonl` и `data/raw.log` через sanitize-patterns (T006). Убедиться, что `grep -E 'password=|Bearer |postgres://[^:]+:[^@]+@|pat_[A-Za-z0-9_-]+' data/logs.jsonl` возвращает пусто
- [ ] T041 [P] Pre-commit секрет-проверка: `git ls-files | grep -iE '\.env$|\.key$|\.pem$|do\.env$'` MUST возвращать пусто (Constitution VIII). `data/raw.log` MUST быть в `.gitignore` (см. T001)
- [ ] T042 Закоммитить артефакты в feature-ветку: `git add specs/318-prod-db-log-audit/{spec.md,plan.md,research.md,data-model.md,contracts,quickstart.md,tasks.md,scripts,data/categories.json,data/sanitize-patterns.txt,data/logs.jsonl,data/incidents.jsonl,data/metrics.json,data/raw.metadata.json,report.md,checklists/requirements.md}`. Commit message: `318: prod-db-log-audit report (verdict: <ОТКЛЮЧАТЬ|ОСТАВЛЯТЬ>)`. `git push -u origin 318-prod-db-log-audit`
- [ ] T043 [P] OpenProject: `tracker.sh add-comment 66 --file specs/318-prod-db-log-audit/report.md` затем `tracker.sh mark-review 66`. Проверить, что статус в OpenProject = «In review»
- [ ] T044 [P] Обновить LiveDocs (FR-014): если создался новый per-feature документ (например, `docs/features/prod-log-audit.md`) — добавить запись в `docs/features/README.md`. Если per-feature документ не создавался (one-shot задача) — добавить запись в `docs/architecture-notes.md` (Pass 318)
- [ ] T045 [P] (опционально) Создать PR через `gh pr create --base master --title "318: Prod DB log audit (#66)" --body "OpenProject #66 — см. report.md"`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001 → T002 || T003 (parallel)
- **Phase 2 (Foundational)**: T004 → T005 || T006 (parallel) → T007. **BLOCKS** Phase 3
- **Phase 3 (US1)**: T010 → T011 → T012 → T013 || T014 (parallel sanity) → T015 → T016 (verdict)
- **Phase 4 (US2) — conditional**: T020 → T021 (approval gate) → T022 → T023 → T024
- **Phase 5 (US3) — conditional**: T030 → T031
- **Phase 6 (Polish)**: T040 || T041 (parallel) → T042 → T043 || T044 || T045 (parallel)

### Story Dependencies

- **US1 (P1)**: MUST run first (определяет вердикт)
- **US2 (P2)**: depends on US1 verdict == ОТКЛЮЧАТЬ
- **US3 (P3)**: depends on US1 verdict == ОСТАВЛЯТЬ
- **US2 ⟷ US3**: **mutually exclusive**

### Within Each Phase

- Setup: prerequisites check (T002, T003) — parallel, разные файлы
- Foundational: T004 → T005/T006 → T007 (sequential dependency)
- US1: T010 → T011 → T012 → (T013, T014) → T015 → T016

### Parallel Opportunities

- Phase 1: T002, T003 — parallel (разные файлы: prerequisites.log vs raw.metadata.json)
- Phase 2: T005, T006 — parallel (разные файлы: _lib.sh vs sanitize-patterns.txt)
- US1: T013, T014 — parallel (smoke-test на fixture vs reproducibility на реальных данных)
- Phase 6: T040, T041 — parallel; затем T043, T044, T045 — parallel

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1 (Setup) — T001-T003
2. Complete Phase 2 (Foundational) — T004-T007
3. Complete Phase 3 (US1) — T010-T016
4. **STOP and VALIDATE**: открыть `report.md`, убедиться, что вердикт вынесен
5. **Опубликовать** через Phase 6 — T040-T045

### Conditional Second Phase (если вердикт ОТКЛЮЧАТЬ)

6. (Только если T016 == ОТКЛЮЧАТЬ) Phase 4 (US2) — T020-T024

### Conditional Alternative (если вердикт ОСТАВЛЯТЬ)

6. (Только если T016 == ОСТАВЛЯТЬ) Phase 5 (US3) — T030-T031

### Single-session Strategy

Эта задача **не требует** параллельной работы нескольких разработчиков.
Все задачи последовательны (кроме явно [P]). Все блокировки
(`T021` approval gate, `T024` 24 ч ожидание) — на стороне владельца.

---

## Notes

- **[P] tasks** = разные файлы, нет зависимостей.
- **[Story] label** = трассировка к User Story из spec.md.
- **Каждая user story** завершается **checkpoint**, где владелец может валидировать.
- **Constitution VIII**: T040/T041 — обязательны перед T042.
- **Constitution II**: T021 — обязательный approval gate перед T022.
- **`raw.log` НЕ коммитится** (см. T001 `.gitignore` + T041 проверка).
- **MD-файлы** (spec.md, plan.md, и т.д.) — коммитятся всегда.
- **OpenProject #66** остаётся в `In review` после T043; закрывает её **владелец** после ревью `report.md`.
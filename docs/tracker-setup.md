# Tracker Setup — пошаговое руководство (Plane)

**Версия**: 2.1 (2026-10-28, конвенция «Задача N» — `KARAOKE-N`)
**Связанные документы**: [`knowledge/system/infra/tracker.md`](../knowledge/system/infra/tracker.md),
[`knowledge/system/infra/op-plane-mapping.md`](../knowledge/system/infra/op-plane-mapping.md),
[`knowledge/adr/local-0012-plane-tracker-migration.md`](../knowledge/adr/local-0012-plane-tracker-migration.md),
[`docs/features/tracker-local-integration.md`](features/tracker-local-integration.md)

## Что это

Рабочий трекер проекта Karaoke — **Plane Community Edition 1.4.2** на
infra-сервере (`https://tracker.sm-karaoke.ru`). Владелец заводит задачи в
веб-интерфейсе Plane, AI-агент через CLI (`tools/tracker.sh`) забирает их,
выполняет, публикует отчёт-комментарий и переводит задачу в ревью.

Локальный **OpenProject** остаётся на `nsa-i9` как архив и инструмент для
ручных задач: read-only и выключение не планируются (решение `06` карты
`tracker-migration`). Два трекера сосуществуют, автоматической синхронизации
между ними нет; номера Plane и OpenProject не совпадают, соответствие —
таблица `op-plane-mapping.csv`.

## Трекер и доступ

| Параметр | Значение |
|---|---|
| UI и API | `https://tracker.sm-karaoke.ru` |
| Workspace | `svoemesto` |
| Проект | `Karaoke`, identifier `KARAOKE`, id `6148f3ac-5f36-4f1b-91fc-01fe7cc8f435` |
| Участники | `nsa-svoemesto` (владелец проекта), `ai-agent` (владелец автоматизации) |
| Аутентификация API | заголовок `X-API-Key: <PAT>` |
| Ссылка на задачу | `https://tracker.sm-karaoke.ru/svoemesto/projects/karaoke/issues/<seq>` |

Перенесённые данные (2026-10-27): 297 задач, 638 комментариев, 8 вложений,
39 sub-issues, 12 связей `follows`. Метки типов и состояния перенесены, см.
ниже.

### Переменные окружения

Файл `.env.local-tracker` лежит в корне репозитория и **не** версионируется
(`.gitignore`). Токены в репозиторий и в отчёты не попадают.

| Ключ | Назначение |
|---|---|
| `PLANE_URL` | `https://tracker.sm-karaoke.ru` |
| `PLANE_WORKSPACE` | `svoemesto` |
| `PLANE_AI_TOKEN` | PAT пользователя `ai-agent` (label `karaoke-migration`), рабочий токен агента |
| `PLANE_PROJECT_ID` | id проекта `Karaoke`; необязателен — при отсутствии проект определяется по identifier `KARAOKE` |
| `TRACKER_URL` | оставлен для обратной совместимости (`tools/tracker-poll.sh` строит ссылки) |

Старые `TRACKER_API_TOKEN` и `TRACKER_USER` относятся к OpenProject и
инструментами больше не используются; файл не чистим, чтобы не потерять
доступ к архиву.

### Выпуск PAT

1. Войти в Plane пользователем, от имени которого должен работать агент
   (`ai-agent`).
2. Настройки пользователя → **API tokens** → создать токен, label
   `karaoke-migration`.
3. Положить значение в `PLANE_AI_TOKEN` в `.env.local-tracker`.
4. Проверить: `GET /api/v1/users/me/` возвращает `ai-agent`.

## Проверка доступа

```bash
cd /home/nsa/Karaoke
set -a && . ./.env.local-tracker && set +a

./tools/tracker.sh healthcheck       # GET /users/me/ + GET .../issues/?per_page=1
./tools/tracker.sh list-projects     # проекты workspace svoemesto
./tools/tracker.sh list-issues --assignee ai-agent --status open
```

`healthcheck` отвечает `0`, если Plane доступен и токен принят. Коды ошибок —
в разделе «Коды выхода».

## Задачи и нумерация

Plane нумерует задачи внутри проекта: `KARAOKE-<sequence_id>`, начиная с 1.
Номера Plane **не совпадают** с номерами OpenProject. Команды принимают
идентификатор в трёх формах:

| Форма | Пример | Как разрешается |
|---|---|---|
| номер Plane | `42` | `sequence_id` в проекте по умолчанию |
| полная ссылка | `KARAOKE-42` | проект и `sequence_id` из ссылки |
| номер OpenProject | `op:403` | через `knowledge/system/infra/op-plane-mapping.csv` |

Соответствие «номер OpenProject → номер Plane → uuid» — в таблице
`op-plane-mapping.csv` (297 строк, см. `op-plane-mapping.md`). Правило ссылок:
**новые ссылки — на Plane**, старые `OpenProject #NNN` в текстах не
переписываются.

### Как понимать «Задача N»

Если владелец говорит «Задача 307» или «задача 307 в трекере», это всегда
задача **`KARAOKE-307`** проекта Karaoke в Plane. Правило общее: «Задача N»
означает `KARAOKE-N` при **любом** N (307 — просто пример). Адрес:

```
https://tracker.sm-karaoke.ru/svoemesto/browse/KARAOKE-<N>/
```

Проверка и работа — `./tools/tracker.sh get-issue 307` (или `KARAOKE-307`).

| В речи | Что это | Куда идти |
|---|---|---|
| «Задача 307», «задача 307 в трекере» | `KARAOKE-307` в Plane | `get-issue 307` |
| «OpenProject #307», «op:307» | work package #307 старого трекера | `op-plane-mapping.csv` |
| «spec 307», «спека 307» | каталог `specs/307-*` | файл в репозитории |

Номер берётся из диапазона Plane, а не из OpenProject: после переноса это
разные числа. Если номер не существует в Plane — уточнить у владельца, не
подставлять «похожую» задачу.

## Состояния

В проекте пять состояний. Четыре перенесены из OpenProject, пятое введено для
рабочего цикла (`mark-review`).

| Состояние | Группа Plane | Смысл |
|---|---|---|
| `New` | unstarted (default) | новая задача, исполнителя нет |
| `In progress` | started | в работе (`claim-issue`, `reopen-issue`) |
| `In review` | started | работа готова, ждёт проверки владельца (`mark-review`) |
| `Done` | completed | принято владельцем (`close-issue`) |
| `Cancelled` | cancelled | отклонено, не будет делаться |

Перенесённые задачи в `In review` не попадают: оно появляется только из
`mark-review`. Идентификаторы состояний — в
`.scratch/karaoke/wayfinding/tracker-migration/migration/plane-ids.json`
(локальный артефакт миграции, вне git).

## Метки

Восемь меток: семь типов плюс признак wayfinder.

| Метка | Смысл | Проставлено при переносе |
|---|---|---|
| `type:bug` | дефект | — |
| `type:feature` | новая функциональность | — |
| `type:epic` | крупный переход через несколько доменов | — |
| `type:task` | обычная задача | 294 |
| `type:user-story` | пользовательская история | — |
| `type:milestone` | веха | 3 |
| `type:phase` | фаза | — |
| `wayfinder` | карта решений или её тикет | 126 задач (все с префиксом `[wayfinder:*]`) |

Пять меток типов существуют как словарь для новых задач: в OpenProject было
7 определений типа, фактически использовались два (`Task`, `Milestone`).

## Приоритеты

Plane принимает `urgent|high|medium|low|none`. Все перенесённые задачи —
`medium`: в OpenProject был единственный приоритет `Normal`.

## Команды CLI

`tools/tracker.sh` — единственная точка входа; `tools/tracker-lib.sh` —
библиотека функций, напрямую не вызывается.

| Команда | Что делает |
|---|---|
| `healthcheck` | `GET /users/me/` + `GET .../issues/?per_page=1` |
| `list-projects` | проекты workspace |
| `list-issues [--project-id ID] [--assignee USER] [--status open\|closed\|in-review] [--limit N] [--raw]` | список задач; `--status` отображается на состояния: `open` → `New` + `In progress`, `closed` → `Done` + `Cancelled`, `in-review` → `In review`; `--raw` — TSV для парсинга |
| `get-issue ID` | JSON задачи вместе с комментариями |
| `claim-issue ID` | assignee `ai-agent` + состояние `In progress` |
| `add-comment ID --file FILE` | markdown-файл → `comment_html`, комментарий в задачу |
| `mark-review ID` | состояние `In review` |
| `close-issue ID` | состояние `Done` |
| `reopen-issue ID` | состояние `In progress` |
| `create-issue --project-id ID --type-id TYPE --subject S [--description D]` | создать задачу; `--type-id` — имя метки (`type:task`) или её uuid |

`--assignee` принимает email или display name пользователя (`ai-agent`);
CLI сам разрешает его в uuid участника проекта.

## Коды выхода

Единые для всех подкоманд `tools/tracker.sh`:

| Код | Значение |
|---|---|
| `0` | успех |
| `1` | ошибка использования или валидации (неизвестная подкоманда, нет аргумента, неизвестная метка) |
| `2` | трекер недоступен (сеть, DNS, 5xx после ретраев) |
| `3` | `401` / `403` — токен отсутствует, истёк или отозван |
| `4` | `429` — превышен лимит запросов |
| `5` | прочие `4xx` (например, неизвестный id задачи) |
| `6` | прочие `5xx` |

`tools/tracker-poll.sh` использует свои четыре кода: `0` — есть открытые
задачи, `1` — открытых нет, `2` — трекер недоступен, `3` — токен истёк.

## Рабочий цикл (4 шага)

Обязательный порядок для задачи, у которой есть Issue ID (`AGENTS.md`,
Tier-1 «Plane Workflow»). `<ID>` — номер Plane или `KARAOKE-<номер>`.

1. **Claim** — `./tools/tracker.sh claim-issue <ID>` до первой строки кода:
   задача закрепляется за агентом и переходит в `In progress`.
2. **Add comment** — `./tools/tracker.sh add-comment <ID> --file <report.md>`
   после merge, до ревью: отчёт «что сделано / файлы / проверки» живёт
   комментарием задачи, а не только в PR.
3. **Mark review** — `./tools/tracker.sh mark-review <ID>`: задача уходит в
   `In review`.
4. **Close** — `./tools/tracker.sh close-issue <ID>` после ревью владельца:
   задача уходит в `Done`.

Если владелец вернул задачу на доработку — `reopen-issue <ID>` переводит её
обратно в `In progress`.

## Polling

В начале сессии агент выполняет `tools/tracker-poll.sh`, чтобы увидеть
открытые задачи, задачи в ревью и недавно закрытые:

```bash
cd /home/nsa/Karaoke
set -a && . ./.env.local-tracker && set +a
bash tools/tracker-poll.sh
```

Источник данных — `tracker.sh list-issues --raw` (TSV, колонки через таб),
поэтому `id` / `subject` / `status` разбираются корректно. Без `--raw`
продюсер выравнивает колонки через `column -t` (пробелы), и разбор по табу
даёт одну колонку.

Опции:

- `--json` — JSON для других скриптов; элементы вида
  `{ "id": "42", "subject": "...", "status": "New" }`.
- `--quiet` — только статистика для cron / systemd-timer.
- `--limit N` — ограничить количество задач.
- `--assignee USER` — другой фильтр исполнителя (по умолчанию
  `TRACKER_AGENT_USER`, то есть `ai-agent`).

Ссылки на задачи строятся как
`{PLANE_URL}/{workspace}/projects/{identifier}/issues/{seq}`; для обратной
совместимости база берётся из `TRACKER_URL`, если `PLANE_URL` не задан.

Регулярный опрос (опционально):

```bash
# ~/.local/bin/tracker-poll-cron.sh
#!/usr/bin/env bash
cd /home/nsa/Karaoke
set -a; source .env.local-tracker; set +a
echo "[$(date -Iseconds)] $(bash tools/tracker-poll.sh --quiet 2>&1)" \
  >> ~/.local/share/tracker-poll.log
```

```bash
# crontab -e
*/5 * * * * bash ~/.local/bin/tracker-poll-cron.sh
```

## Спека из задачи

Нетривиальная задача превращается в спеку тем же способом, что и раньше:

```bash
bash tools/tracker-spec-for-issue.sh <ID>
```

Скрипт читает `subject` и `description` через `get-issue`, генерирует
kebab-case slug, находит следующий свободный номер спек, создаёт
`specs/NNN-<slug>/spec.md` и `tasks.md` и добавляет в задачу комментарий со
ссылкой на спеку. Дальше — обычный цикл из четырёх шагов.

Опции: `--force` — перезаписать существующий каталог спеки (осторожно).

Коды выхода: `0` — спека создана, `1` — задача не найдена, `2` — каталог уже
существует, `3` — ошибка `tracker.sh`.

## Wayfinding в Plane

Процесс команды «wayfinder → исполнение» (живые инструкции:
`~/.dsh/romario-team/wayfinding-operations.md`; скилл `wayfinder` включается
вручную) в проекте Karaoke выражается задачами Plane. Локальные
`issues_NNNN.md` здесь **не ведутся**: семантика карты, тикетов,
claim/frontier и тумана сохраняется, меняется только носитель.

### Как выражаются карта и тикеты

| Понятие wayfinder | В Plane |
|---|---|
| Карта усилия (map) | родительская задача проекта `KARAOKE`, тема `[wayfinder:map] <усилие>` |
| Тело карты (Destination / Notes / Decisions so far / Not yet specified / Out of scope) | описание этой задачи |
| Тикет-решение | sub-issue карты: тема `[wayfinder:<type>] <вопрос>`, первая строка описания — `Map: KARAOKE-<seq>` |
| Зависимости (`Blocked by`) | строка `Blocked by: KARAOKE-N, KARAOKE-M` в описании тикета; проверяется чтением этих задач, отдельные relations для зависимостей не заводятся |
| `OPEN` | состояние `New`, исполнителя нет |
| `CLAIMED` | `In progress` + assignee (`claim-issue`) |
| `RESOLVED` | `Done` + комментарий-ответ |

Метка `wayfinder` стоит и на карте, и на её тикетах. Иерархия — штатная
связь parent/child Plane (sub-issues): в перенесённых данных 39 задач имеют
родителя, четыре закрытые карты и открытая карта перенесены вместе с детьми.
Связи `follows` из OpenProject перенесены отдельными relations (12 штук) —
это исторический порядок задач, а не способ задавать зависимости тикетов.

### Операции

```bash
# 1. Карта усилия (одна на усилие; создаёт лид)
bash tools/tracker.sh create-issue --project-id "$PLANE_PROJECT_ID" \
     --type-id type:task --subject "[wayfinder:map] <усилие>" \
     --description "<тело карты>"

# 2. Тикет-решение — один sub-issue на вопрос
#    (первая строка описания: Map: KARAOKE-<seq>; parent задаётся в UI)
bash tools/tracker.sh create-issue --project-id "$PLANE_PROJECT_ID" \
     --type-id type:task --subject "[wayfinder:grilling] <вопрос>" \
     --description "Map: KARAOKE-<seq> ..."

# 3. Claim
bash tools/tracker.sh claim-issue KARAOKE-<seq>

# 4. Frontier: открытые задачи проекта + чтение кандидатов
bash tools/tracker.sh list-issues --status open
bash tools/tracker.sh get-issue KARAOKE-<seq>   # Map, Blocked by, assignee

# 5. Разрешение: ответ комментарием + закрытие
bash tools/tracker.sh add-comment KARAOKE-<seq> --file answer.md
bash tools/tracker.sh close-issue KARAOKE-<seq>
```

Правила: одна сессия — не больше одного тикета; ответ живёт комментарием,
артефакты — ссылками; в `Decisions so far` карты добавляется суть решения и
`KARAOKE-<seq>` тикета; туман и «вне скоупа» — разделами тела карты.

### Переход к исполнению

Классификация остатка работы (большая/маленькая) — **решает владелец**
(`AGENTS.md` § «Hard Gate — Процесс: wayfinder → исполнение»). Маленькая:
владелец выбирает — лид делает сам или через спеку. Большая: issue-задача в
Plane (`create-issue`) → `speckit-specify` → спека уходит владельцу →
**СТОП** → по слову владельца долгоживущий субагент ведёт `speckit-plan →
speckit-tasks → speckit-analyze → speckit-implement`, лид ревьюит каждый шаг
по фактам с диска → после merge штатные 4 шага Plane-workflow (`add-comment`
→ `mark-review` → ревью владельца → `close-issue`).

## Диагностика

| Проблема | Причина и решение |
|---|---|
| `401 Unauthorized` | `PLANE_AI_TOKEN` отсутствует, истёк или отозван. Перевыпустить PAT в Plane и обновить `.env.local-tracker`; код выхода `3`. |
| `403 Forbidden` | Пользователь токена не участник workspace или проекта. Добавить `ai-agent` в проект `Karaoke` (роль Member). |
| `429 Too Many Requests` | Лимит Plane — 60 запросов в минуту на пользователя. CLI делает паузу по `Retry-After` и повторяет; при постоянном 429 — уменьшить частоту polling, не долбить API в цикле. |
| Пустой `list-issues --assignee ai-agent` | Исполнитель не назначен ни одной задаче либо пользователь не участник проекта. Проверить участников проекта и поле `assignees` у задачи. |
| `get-issue 42` открывает чужую задачу | Номер — `sequence_id` **внутри проекта** `KARAOKE`. Проверить, что `PLANE_PROJECT_ID` указывает на проект Karaoke. |
| `op:403` не разрешается | Нет таблицы `knowledge/system/infra/op-plane-mapping.csv` или в ней нет строки. Смотреть `op-plane-mapping.md` § «Как читать». |
| `project not found` | Идентификатор проекта — identifier `KARAOKE` в верхнем регистре; uuid проекта берётся из `PLANE_PROJECT_ID`. |
| Вложение не загружается | Поле вложения в API — `asset`, MIME ограничен белым списком (`text/csv` проходит, `application/csv` — нет). |
| `mark-review` вернул ошибку состояния | Состояние `In review` должно существовать в проекте (группа `started`). Список состояний — `GET .../states/`. |

## OpenProject: локальный архив

OpenProject (`openproject/community:13` + `openproject-db` на `nsa-i9`)
остаётся рабочим инструментом для ручных задач. Read-only и выключение не
планируются; удаление — отдельным решением позже. Контейнеры описаны в
`deploy/tracker-docker-compose.yml`, установка и bootstrap — исторический
`tools/install-tracker.sh`.

Дрейф между трекерами не синхронизируется: задача, заведённая в OpenProject,
не появится в Plane, и наоборот. Для старых номеров действует таблица
соответствия; для новых задач — только Plane.

## Что дальше

- Прочитать [`knowledge/system/infra/tracker.md`](../knowledge/system/infra/tracker.md) —
  состояние инфраструктуры трекера, состав данных, ограничения.
- Прочитать [`knowledge/system/infra/op-plane-mapping.md`](../knowledge/system/infra/op-plane-mapping.md) —
  как пользоваться таблицей соответствия.
- При ручных запросах сверяться с API Plane v1: `https://tracker.sm-karaoke.ru/api/v1/`,
  заголовок `X-API-Key`.

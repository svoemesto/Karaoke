# Настройка Plane-трекера и wayfinder в другом проекте — инструкция для агента

**Версия**: 1.0 (2026-11-06)
**Связанные документы**: [`knowledge/guidelines/runbooks/tracker-setup.md`](tracker-setup.md),
[`knowledge/system/infra/tracker.md`](../../system/infra/tracker.md),
[`knowledge/adr/local-0012-plane-tracker-migration.md`](../../adr/local-0012-plane-tracker-migration.md)

## Кому и зачем

Инструкция для агента, который работает в **другом репозитории** и хочет
связку «Plane-трекер + CLI + wayfinder» — ровно такую, как в проекте Karaoke.
Текст самодостаточен: его можно передать коллеге как файл и выполнять по шагам,
ничего не зная про наш репозиторий.

Все команды и эндпоинты ниже **проверены на живом Plane** (Plane Community
Edition 1.4.2, API v1). Значения по умолчанию даны для нашего проекта
(`KARAOKE`, workspace `svoemesto`) — в своём проекте замените их своими.

## 0. Что получится в итоге

| Слой | Что это |
|---|---|
| Plane | workspace → проект → 5 состояний, метки, участник-агент |
| CLI | `tools/tracker.sh` — единственная точка входа к трекеру |
| Workflow | 4 шага: claim → комментарий-отчёт → mark-review → close |
| Wayfinder | карта усилия и тикеты-решения — **задачи Plane**, не файлы в репозитории |
| Governance | блок правил в `AGENTS.md` проекта + периодический опрос задач |

Слои независимы: сначала Plane и CLI, потом workflow, потом wayfinder.

## 1. Предусловия

1. Развёрнутый Plane (CE 1.4.x или новее) — у коллеги он уже есть.
2. Репозиторий проекта, в котором будет жить агент.
3. На машине агента: `bash`, `curl`, `jq` (CLI использует только их).
4. Агент работает в DSH: скиллы ставятся в `~/.dsh/skills/` или в
   `.agents/skills/` проекта.

Проверить, что Plane отвечает:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<ваш-plane>/api/v1/
```

## 2. Плоскость Plane: состояния, метки, участники

### 2.1 Состояния

Ровно пять, иначе сломается workflow (`In review` — отдельное состояние, а не
комментарий):

| Состояние | Группа Plane | Смысл |
|---|---|---|
| `New` | `unstarted` | новая задача, исполнителя нет |
| `In progress` | `started` | в работе (claim) |
| `In review` | `started` | работа готова, ждёт проверки владельца |
| `Done` | `completed` | принято владельцем (close) |
| `Cancelled` | `cancelled` | отклонено, делать не будем |

Через UI: Project → States. Через API (заголовок `X-API-Key: <PAT>`):

```bash
B="https://<ваш-plane>/api/v1"; W="<workspace>"; P="<project-uuid>"
create_state() {
  curl -s -X POST -H "X-API-Key: $PLANE_AI_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$1\",\"group\":\"$2\"}" "$B/workspaces/$W/projects/$P/states/"
}
create_state "New" unstarted
create_state "In progress" started
create_state "In review" started
create_state "Done" completed
create_state "Cancelled" cancelled
```

### 2.2 Метки

Семь меток типов — словарь для новых задач, плюс `wayfinder` — признак карт
и тикетов wayfinder. Метки принадлежат **проекту**, не workspace.

| Метка | Смысл |
|---|---|
| `type:bug` | дефект |
| `type:feature` | новая функциональность |
| `type:epic` | крупный переход через несколько доменов |
| `type:task` | обычная задача (и тикет wayfinder по умолчанию) |
| `type:user-story` | пользовательская история |
| `type:milestone` | веха |
| `type:phase` | фаза |
| `wayfinder` | карта решения или её тикет |

```bash
for L in type:bug type:feature type:epic type:task type:user-story type:milestone type:phase wayfinder; do
  curl -s -X POST -H "X-API-Key: $PLANE_AI_TOKEN" -H 'Content-Type: application/json' \
    -d "{\"name\":\"$L\"}" "$B/workspaces/$W/projects/$P/labels/" >/dev/null
done
```

Метку `type:governance-violation` (нарушение governance-процедуры) можно
добавить позже — она нужна только если вы будете заводить такие тикеты.

### 2.3 Участники

В проекте должно быть минимум два участника: владелец и агент.

```bash
curl -s -H "X-API-Key: $PLANE_AI_TOKEN" "$B/workspaces/$W/projects/$P/members/"
```

Агента (`ai-agent` или ваше имя) добавляют через UI: Project → Members →
Add member, роль Member. Имя участника нужно CLI: он резолвит `--assignee`
по email или display name.

## 3. Аккаунт агента и PAT

1. Завести пользователя-агента (например `ai-agent`) — можно прямо в UI Plane.
2. Выпустить PAT: Настройки пользователя → API tokens → создать токен
   (label — любой, например `agent`).
3. Добавить пользователя в workspace и в проект (см. 2.3).
4. Проверить токен:

```bash
curl -s -H "X-API-Key: $PLANE_AI_TOKEN" "$B/users/me/"
```

**Заголовок авторизации — именно `X-API-Key`**, не `Authorization: Bearer`.
Токен — персональный агента, общий на всех не делайте: так в журнале видно,
кто именно ходил в трекер.

## 4. Переменные окружения

Файл `.env.local-tracker` в корне репозитория, **не версионируется** (он в
`.gitignore`; проверьте, что это так, и не коммитьте токен никогда).

```bash
PLANE_URL=https://<ваш-plane>          # без trailing slash
PLANE_WORKSPACE=<workspace>             # slug, не отображаемое имя
PLANE_AI_TOKEN=<PAT агента>
PLANE_PROJECT_IDENTIFIER=<ИДЕНТИФИКАТОР>  # напр. KARAOKE; необязателен, если задан PLANE_PROJECT_ID
PLANE_PROJECT_ID=<uuid проекта>          # необязателен: проект ищется по идентификатору
TRACKER_AGENT_USER=ai-agent               # display name участника-агента
```

Токен **никогда** не попадает в репозиторий, отчёты, комментарии в трекере и
команды в чате.

## 5. CLI: что переносить из нашего репозитория

Файлы (в нашем репозитории — `/home/nsa/Karaoke/tools/`):

| Файл | Назначение | Обязателен |
|---|---|---|
| `tracker.sh` | единственная точка входа: все подкоманды | да |
| `tracker-lib.sh` | библиотека функций, напрямую не вызывается | да |
| `tracker-poll.sh` | опрос открытых задач (старт сессии, cron) | желательно |
| `tracker-spec-for-issue.sh` | создать каталог спеки из задачи трекера | по желанию |
| `tracker-smoke-test.sh` | сквозная проверка связки | по желанию |

Что **не переносить**: `tools/install-tracker.sh`, `tools/tracker-bootstrap.sh`,
`tools/tracker-bootstrap-board.sh` — это наследие OpenProject (rails-runner
внутри контейнера `openproject`), в Plane они не работают и только путают.

### 5.1 Что поправить под свой проект

CLI параметризован, поэтому большую часть настроек задают переменные
окружения, а не правкой кода. Но в `tracker-lib.sh` есть две привязки к
нашему проекту — их надо заменить:

1. **Сборка ключа ссылки** (строка с `key: ("KARAOKE-" + ...)`) — жёстко
   зашит идентификатор `KARAOKE`. Замените на свой: иначе ссылки вида
   `KARAOKE-42` в отчётах и комментариях будут вести на чужой проект.
2. **Текст ошибки** «задача KARAOKE-<seq> не найдена» — косметика, но тоже
   стоит поправить.

Плюс проверьте путь журнала: по умолчанию `${REPO_ROOT}/logs/tracker-agent.log`
— каталог `logs/` должен существовать и быть не версионируемым.

### 5.2 Подкоманды

```
tracker.sh healthcheck
tracker.sh list-projects
tracker.sh list-issues [--project-id ID] [--assignee USER] [--status open|closed|in-review] [--limit N] [--raw]
tracker.sh get-issue ID
tracker.sh claim-issue ID
tracker.sh add-comment ID --file FILE
tracker.sh mark-review ID
tracker.sh close-issue ID
tracker.sh reopen-issue ID
tracker.sh create-issue --project-id ID --type-id TYPE --subject S [--description D]
```

`ID` принимается как номер внутри проекта (`42`) и как полная ссылка
(`KARAOKE-42`). `--type-id` — имя метки (`type:task`) или её uuid.
`--assignee` — email или display name.

Коды выхода единые для всех подкоманд:

| Код | Значение |
|---|---|
| 0 | успех |
| 1 | ошибка использования |
| 2 | трекер недоступен (сеть, 5xx) |
| 3 | 401/403 — токен отсутствует, истёк или отозван |
| 4 | 429 — превышен лимит запросов |
| 5 | прочие 4xx (например, несуществующий id задачи) |
| 6 | прочие 5xx |

## 6. Проверка доступа

```bash
cd <репозиторий проекта>
set -a && . ./.env.local-tracker && set +a
./tools/tracker.sh healthcheck
./tools/tracker.sh list-projects
./tools/tracker.sh list-issues --assignee "$TRACKER_AGENT_USER" --status open
```

`healthcheck` должен завершиться кодом 0. Если 3 — токен; если 2 — сеть или
Plane; если 403 — пользователь токена не участник проекта.

## 7. Рабочий цикл: четыре шага

Порядок обязателен. Отчёт о работе — комментарий в задаче, а не только в PR.

| Шаг | Команда | Когда |
|---|---|---|
| 1. Claim | `tracker.sh claim-issue <ID>` | до первой строки кода |
| 2. Add comment | `tracker.sh add-comment <ID> --file report.md` | после merge, до ревью |
| 3. Mark review | `tracker.sh mark-review <ID>` | после комментария |
| 4. Close | `tracker.sh close-issue <ID>` | после ревью владельца |

Возврат на доработку: `tracker.sh reopen-issue <ID>` — задача снова
`In progress`.

Закрепите это в `AGENTS.md` проекта — блоком правил (вставьте свой идентификатор
проекта и свои имена):

```markdown
## Hard Gate — Workflow трекера (Plane)

При Issue ID — обязательно 4 шага; `report.md` — обязательный артефакт.
Трекер — Plane (<url>, workspace <workspace>, проект <ИДЕНТИФИКАТОР>).

1. Claim: `tracker.sh claim-issue <ID>` — до первой строки кода.
2. Add comment: `tracker.sh add-comment <ID> --file specs/<NNN>-<slug>/report.md`.
3. Mark review: `tracker.sh mark-review <ID>`.
4. Close: `tracker.sh close-issue <ID>` — после ревью владельца.
```

## 8. Опрос задач (polling)

В начале сессии агент должен видеть открытые задачи:

```bash
cd <репозиторий проекта>
set -a && . ./.env.local-tracker && set +a
bash tools/tracker-poll.sh
```

Регулярный опрос — cron, раз в 5 минут, вывод в лог:

```bash
# ~/.local/bin/tracker-poll-cron.sh
#!/usr/bin/env bash
cd <репозиторий проекта>
set -a; source .env.local-tracker; set +a
echo "[$(date -Iseconds)] $(bash tools/tracker-poll.sh --quiet 2>&1)" \
  >> ~/.local/share/tracker-poll.log
```

```bash
crontab -e
*/5 * * * * bash ~/.local/bin/tracker-poll-cron.sh
```

## 9. Wayfinder: карта и тикеты как задачи Plane

Скилл `wayfinder` (в нашей установке — `~/.dsh/skills/wayfinder/SKILL.md`)
**не привязан к Plane**: он говорит «карта — это issue, тикеты — дочерние
issues», а физический носитель определяет проектный слой. Поэтому связка
складывается из трёх частей: скилл + соглашение об именовании + команды.

### 9.1 Установить скилл

Положите `SKILL.md` скилла и его каталог `agents/` в `~/.dsh/skills/wayfinder/`
(глобально) или `.agents/skills/wayfinder/` (в проекте). В скилле стоит
`disable-model-invocation: true` — это означает, что скилл **не подхватывается
автоматически**: его включают вручную, когда владелец говорит «построй карту /
wayfinder». Не пытайтесь «сделать его автоматическим» — это осознанное
ограничение.

### 9.2 Проектный слой: что лежит где

У нас за этот слой отвечает `~/.dsh/romario-team/wayfinding-operations.md`
(общий командный файл). Он описывает **файловый** носитель (`.scratch/...`),
поэтому для Plane нужен свой вариант. Минимальный шаблон:

```markdown
# Командный гайдлайн: Wayfinding-операции в Plane (проект <имя>)

Слой задач и слой решений не смешивать: слой решений — карта и тикеты
в Plane, слой задач — обычные задачи Plane проекта <ИДЕНТИФИКАТОР>.

- Карта усилия: родительская задача с темой `[wayfinder:map] <усилие>`.
- Тело карты: описание задачи (Destination / Notes / Decisions so far /
  Not yet specified / Out of scope).
- Тикет-решение: дочерняя задача карты, тема `[wayfinder:<type>] <вопрос>`,
  первая строка описания — `Map: <ИДЕНТИФИКАТОР>-<seq>`.
- Зависимости: строка `Blocked by: <ИДЕНТИФИКАТОР>-N, ...` в описании тикета.
- Claim: `tracker.sh claim-issue <ИДЕНТИФИКАТОР>-<seq>`.
- Frontier: открытые задачи проекта минус CLAIMED, плюс разблокированные.
- Разрешение: ответ комментарием + закрытие; в «Decisions so far» карты —
  строка с сутью решения и ссылкой на тикет.
- Туман: сформулированный вопрос — в тикет; не сформулированный — в раздел
  «Not yet specified» карты.
- Лимит: одна сессия разрешает не больше одного тикета.
```

Метка `wayfinder` ставится и на карту, и на её тикеты.

### 9.3 Соглашение об именовании

| Понятие | В Plane |
|---|---|
| Карта усилия | задача `[wayfinder:map] <усилие>` |
| Тикет-решение | задача `[wayfinder:grilling\|research\|prototype\|task] <вопрос>` |
| Связь карта → тикет | поле `parent` тикета = uuid карты |
| Зависимость тикета | строка `Blocked by: <ИДЕНТИФИКАТОР>-N` в описании |
| Открыт / в работе / решён | `New` / `In progress` + assignee / `Done` + ответ комментарием |

Имя задачи — это её «имя» во всём, что читает человек: в описании карты, в
`Decisions so far`, в отчётах. Поэтому тема должна читаться как вопрос или
как усилие, а не как «Task 12».

### 9.4 Создание карты и тикета (проверенные запросы)

Карта:

```bash
B="https://<ваш-plane>/api/v1"; W="<workspace>"; P="<project-uuid>"
H="X-API-Key: $PLANE_AI_TOKEN"

curl -s -X POST -H "$H" -H 'Content-Type: application/json' -d "$(jq -n \
  --arg name "[wayfinder:map] <усилие>" \
  --arg html "<p>Destination: ...</p><p>Notes: ...</p><p>Decisions so far:</p><p>Not yet specified: ...</p><p>Out of scope: ...</p>" \
  --arg l1 "<uuid метки wayfinder>" --arg l2 "<uuid метки type:task>" \
  '{name:$name, description_html:$html, priority:"medium", labels:[$l1,$l2]}')" \
  "$B/workspaces/$W/projects/$P/issues/"
```

Тикет-ребёнок — ключевое отличие от простого `create-issue`: в payload
добавляется поле `parent` с uuid карты (проверено на живом Plane):

```bash
MAP_UUID="<uuid карты>"
curl -s -X POST -H "$H" -H 'Content-Type: application/json' -d "$(jq -n \
  --arg name "[wayfinder:grilling] <вопрос>" \
  --arg html "<p>Map: <ИДЕНТИФИКАТОР>-<seq карты></p><p>Вопрос: ...</p>" \
  --arg parent "$MAP_UUID" \
  --arg l1 "<uuid метки wayfinder>" --arg l2 "<uuid метки type:task>" \
  '{name:$name, description_html:$html, priority:"medium", labels:[$l1,$l2], parent:$parent}')" \
  "$B/workspaces/$W/projects/$P/issues/"
```

Отдельного эндпоинта `.../issues/<id>/sub-issues/` в Plane 1.4.2 **нет**
(отдаёт 404) — связь задаётся именно полем `parent`.

### 9.5 Claim, Frontier, Разрешение

```bash
# Claim тикета
tracker.sh claim-issue <ИДЕНТИФИКАТОР>-<seq>

# Frontier: открытые задачи проекта + чтение кандидата
tracker.sh list-issues --status open
tracker.sh get-issue <ИДЕНТИФИКАТОР>-<seq>   # Map, Blocked by, assignee

# Разрешение: ответ комментарием, затем закрытие
tracker.sh add-comment <ИДЕНТИФИКАТОР>-<seq> --file answer.md
tracker.sh close-issue <ИДЕНТИФИКАТОР>-<seq>
```

Ответ тикета живёт **комментарием**, артефакты — ссылками, а не вставками.
Закрытый тикет добавляет одну строку в «Decisions so far» карты.

### 9.6 Переход к исполнению

1. Карта закрыта (destination достигнут, открытых тикетов нет) → выходной
   шаг перед концом сессии.
2. Классификация остатка работы: **большая** (правок много, нужен TDD) или
   **маленькая** — решает владелец, не агент.
3. Маленькая: лид делает сам или через спеку — спросить владельца.
4. Большая: задача исполнения в Plane (`create-issue`) → `speckit-specify`
   (вход: destination + decisions so far + контекст с карты) → спека уходит
   владельцу → стоп до слова владельца → по слову владельца субагент ведёт
   `speckit-plan → speckit-tasks → speckit-analyze → speckit-implement`,
   лид ревьюит каждый шаг по фактам с диска → после merge — обычные четыре
   шага workflow.
5. Спека — контракт: изменения требований — только словом владельца.

## 10. Приёмочный тест

Пройдите цепочку целиком на тестовой задаче, прежде чем встраивать в работу:

| # | Действие | Ожидание |
|---|---|---|
| 1 | `tracker.sh healthcheck` | код 0 |
| 2 | `tracker.sh list-projects` | ваш проект в списке |
| 3 | `tracker.sh list-issues --status open` | список задач, формат TSV/таблица |
| 4 | `tracker.sh create-issue --type-id type:task --subject "Тест связки"` | задача создана, номер выдан |
| 5 | `tracker.sh get-issue <ID>` | видно `subject`, `status`, `assignee` |
| 6 | `tracker.sh claim-issue <ID>` | статус `In progress`, исполнитель — агент |
| 7 | `tracker.sh add-comment <ID> --file report.md` | комментарий в задаче |
| 8 | `tracker.sh mark-review <ID>` | статус `In review` |
| 9 | `tracker.sh close-issue <ID>` | статус `Done` |
| 10 | создать карту и тикет-ребёнка (9.4) | у тикета `parent` = uuid карты |
| 11 | `tracker.sh list-issues --status open` | карта и тикет видны, тикет не в списке карты |
| 12 | `tracker.sh reopen-issue <ID>` | задача снова `In progress` (проверка обратного пути) |

Тестовую задачу удалите в UI.

## 11. Ловушки (проверено на живом Plane)

| Симптом | Причина | Что делать |
|---|---|---|
| `PATCH` с полем `subject` не меняет заголовок | у задачи Plane поле называется `name` | использовать `name` |
| 401 при верном токене | заголовок `Authorization: Bearer` | использовать `X-API-Key` |
| 404 на `.../issues/<id>/sub-issues/` | такого эндпоинта в 1.4.2 нет | связь через поле `parent` |
| 400 на `description_html: ""` | Plane не принимает пустое описание | не передавать поле, если описания нет |
| 403 | пользователь токена не участник проекта | добавить в Members |
| 429 | лимит Plane — 60 запросов в минуту на пользователя | снизить частоту опроса; CLI сам ждёт `Retry-After` |
| `get-issue 42` открывает чужую задачу | номер — `sequence_id` внутри проекта | проверить `PLANE_PROJECT_IDENTIFIER` |
| `mark-review` ругается на состояние | состояния `In review` нет в проекте | создать его заранее (2.1); либо один раз выполнить с `TRACKER_CREATE_REVIEW_STATE=1` — тогда CLI создаст состояние сам (по умолчанию не создаёт) |
| `assignee` не находится | CLI резолвит по email или display name | указывать точное имя участника |
| ссылки в отчётах ведут на чужой проект | в коде зашит идентификатор `KARAOKE` | заменить на свой (5.1) |

## 12. Что остаётся на решение владельца

1. **Имя агента** и его учётная запись в Plane (нужна ли отдельная — зависит от
   политики проекта).
2. **Кто закрывает задачи**: только владелец в UI или агент по команде.
3. **Обязательность отчёта-комментария** для мелких задач.
4. **Порядок работы wayfinder** при конфликте с обычными задачами (кто первый).
5. **Правила для «маленьких» задач** — делает лид сам или через спеку.

## 13. Что переносить — сводка

```bash
# из нашего репозитория в репозиторий коллеги
cp /home/nsa/Karaoke/tools/tracker.sh              <проект>/tools/
cp /home/nsa/Karaoke/tools/tracker-lib.sh          <проект>/tools/
cp /home/nsa/Karaoke/tools/tracker-poll.sh         <проект>/tools/
cp /home/nsa/Karaoke/tools/tracker-spec-for-issue.sh <проект>/tools/   # по желанию
cp /home/nsa/Karaoke/tools/tracker-smoke-test.sh   <проект>/tools/     # по желанию

# скилл wayfinder
cp -r /home/nsa/.dsh/skills/wayfinder              <проект>/.agents/skills/wayfinder
```

После копирования:

1. Поправить две привязки к `KARAOKE` в `tracker-lib.sh` (5.1).
2. Создать `.env.local-tracker` в корне репозитория коллеги (4) и убедиться,
   что он в `.gitignore`.
3. Создать `logs/` для журнала CLI и добавить его в `.gitignore`.
4. Прогнать приёмочный тест (10).
5. Вставить блок правил в `AGENTS.md` коллеги (7).
6. Написать проектный слой wayfinder для Plane (9.2) и положить его рядом со
   скиллом (например `.agents/skills/wayfinder/OPERATIONS.md`).

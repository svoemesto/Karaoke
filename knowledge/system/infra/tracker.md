# Component: трекер проекта (Plane)

> **Домен**: system (infrastructure)
> **Компонента**: Plane Community Edition 1.4.2 на infra-сервере
> (`tracker.sm-karaoke.ru`), проект `Karaoke` в workspace `svoemesto`: задачи,
> комментарии, вложения, sub-issues и рабочий цикл агента.

## Назначение

Plane — **рабочий трекер проекта Karaoke**. В нём живут карты решений
wayfinder, тикеты-решения, issue-задачи исполнения и отчёты по ним. Агент
читает и меняет задачи через CLI `tools/tracker.sh`; владелец — через
веб-интерфейс.

Локальный OpenProject на `nsa-i9` остаётся архивом и инструментом для ручных
задач: read-only и выключение не планируются (ADR
[local-0012](../../adr/local-0012-plane-tracker-migration.md), решение `06`
карты `tracker-migration`). Автоматической синхронизации между трекерами нет.

## Интерфейсы и Контракты

| Параметр | Значение |
|---|---|
| URL | `https://tracker.sm-karaoke.ru` |
| Workspace | `svoemesto` |
| Проект | `Karaoke`, identifier `KARAOKE`, id `6148f3ac-5f36-4f1b-91fc-01fe7cc8f435` |
| API | `/api/v1/...`, заголовок `X-API-Key` |
| Лимит | 60 запросов в минуту на пользователя; `429` с `Retry-After` |
| Ссылка на задачу | `https://tracker.sm-karaoke.ru/svoemesto/projects/karaoke/issues/<seq>` |
| Нумерация | `KARAOKE-<sequence_id>`, внутри проекта, с 1; с номерами OpenProject не совпадает |

**Состояния** (5): `New` (unstarted, default), `In progress` (started),
`In review` (started, рабочее — только из `mark-review`), `Done` (completed),
`Cancelled` (cancelled).

**Метки** (8): `type:bug`, `type:feature`, `type:epic`, `type:task`,
`type:user-story`, `type:milestone`, `type:phase`, `wayfinder`. При переносе
проставлены `type:task` (294 задачи), `type:milestone` (3) и `wayfinder`
(126 задач с префиксом `[wayfinder:*]`); остальные существуют как словарь.

**Приоритет**: все перенесённые задачи — `medium` (в OpenProject был только
`Normal`).

**Идентификаторы в CLI**: `42` — номер Plane; `KARAOKE-42` — полная ссылка;
`op:403` — номер OpenProject, разрешается через
[`op-plane-mapping.csv`](op-plane-mapping.csv).

Команды, их семантика, коды выхода и пошаговые сценарии — в
[`docs/tracker-setup.md`](../../../docs/tracker-setup.md) (единственный
источник деталей, здесь не дублируются).

## Логика и Алгоритмы

### Как понимать «Задача N»

«Задача N» и «задача N в трекере» у владельца означают задачу **`KARAOKE-N`**
этого проекта при любом N (307 — пример, а не особый номер):
`https://tracker.sm-karaoke.ru/svoemesto/browse/KARAOKE-N/`.
Открывается через `tools/tracker.sh get-issue N`.

Различать три сущности с одинаковыми числами:

- `KARAOKE-N` — задача Plane (рабочая);
- `op:N` / `OpenProject #N` — work package старого трекера, разрешается через
  `knowledge/system/infra/op-plane-mapping.csv`;
- `specs/NNN-*` — каталог спеки, это не задача трекера.

Ошибка «понял N как OpenProject» приводит к работе не над той задачей.

### Состав данных после переноса (2026-10-27)

| Что | Сколько |
|---|---|
| Задачи | 297 |
| Комментарии | 638 (у 274 задач) |
| Вложения | 8 файлов, 2 135 046 Б, у 4 задач |
| Sub-issues | 39 (4 закрытые карты wayfinder и открытая карта с детьми) |
| Связи `follows` | 12 |
| Статусы источника | `Closed` 280, `Rejected` 10, `New` 5, `In progress` 2 |
| Типы источника | `Task` 294, `Milestone` 3 |

Маппинг статусов и типов зафиксирован в решениях `02` и `03` карты
`tracker-migration`; таблица соответствия номеров —
[`op-plane-mapping.md`](op-plane-mapping.md).

### Рабочий цикл

`claim-issue` (assignee `ai-agent` + `In progress`) → работа → `add-comment`
(отчёт-комментарий) → `mark-review` (`In review`) → ревью владельца →
`close-issue` (`Done`). Возврат на доработку — `reopen-issue`
(`In progress`). Обязательность шагов задаёт `AGENTS.md`, Tier-1 «Plane
Workflow».

### Размещение и обслуживание

- Plane — compose-стек внутри LXD-контейнера `infra` на infra-сервере
  (см. [`external-infra.md`](external-infra.md)); TLS — host-nginx dub2000.
- PostgreSQL и вложения: БД на SSD, вложения — на `/srv/infra`.
- Бэкап — ежедневный `pg_dump` Plane в 03:30 UTC вместе с остальными сервисами
  (ADR `local-0013`), хранение 7 дней на сервере и 14 дней на `nsa-i9`.

### Учётные записи и токены

- `nsa-svoemesto` — владелец проекта, работает в UI.
- `ai-agent` — учётная запись автоматизации, PAT с label `karaoke-migration`;
  токен лежит в `.env.local-tracker` (вне git) как `PLANE_AI_TOKEN`.
- API-запросы агента идут от `ai-agent`; авторство исторических комментариев
  сохранено префиксом в тексте (решение `07`).

### Wayfinder в Plane

Карта усилия — родительская задача с темой `[wayfinder:map] <усилие>`, тикеты —
её sub-issues с темой `[wayfinder:<type>] <вопрос>` и первой строкой описания
`Map: KARAOKE-<seq>`; метка `wayfinder` стоит на карте и тикетах. Статусы:
`OPEN` → `New` без исполнителя, `CLAIMED` → `In progress` + assignee,
`RESOLVED` → `Done` + комментарий-ответ. Зависимости — строками
`Blocked by: KARAOKE-N` в описании (решение `04`).

## Зависимости

- → **infra-сервер** ([`external-infra.md`](external-infra.md)): контейнер
  `infra`, host-nginx, TLS, `/srv/infra`, бэкапы.
- → **`tools/tracker.sh`**, `tools/tracker-lib.sh`, `tools/tracker-poll.sh`,
  `tools/check-spec-issue-link.py` — клиенты Plane; детали и коды выхода —
  [`docs/tracker-setup.md`](../../../docs/tracker-setup.md).
- → **[`op-plane-mapping.csv`](op-plane-mapping.csv)** — разрешение номеров
  OpenProject (`op:<NNN>`).
- ← **OpenProject** на `nsa-i9` — источник перенесённых данных и ручной
  локальный инструмент; связи в графе нет, только историческая.

## Известные ловушки

1. **Лимит 60 запросов в минуту** — обход лимита циклом даёт `429`; нужен
   `Retry-After` и пауза, а не немедленный повтор.
2. **Identifier и uuid** — в API проект адресуется uuid (`PLANE_PROJECT_ID`),
   а identifier `KARAOKE` — для ссылок и человеко-читаемых запросов; в нижнем
   регистре identifier не находится.
3. **Вложения** — поле записи называется `asset`, MIME ограничен белым
   списком (`application/csv` отклоняется, `text/csv` проходит), лимит 5 МБ.
4. **Номера не совпадают** — `KARAOKE-42` и `OpenProject #42` — разные задачи;
   старый номер без префикса `op:` разрешается неверно.
5. **Smoke-тесты** — `tools/tracker-smoke-test.sh` создаёт и удаляет служебную
   задачу (`smoke-test-...`), поэтому «текущее число задач» на мгновение больше
   297; сверять данные нужно по таблице соответствия, а не по счётчику.
6. **Два трекера без синхронизации** — задача, заведённая в OpenProject, не
   появится в Plane; дрейф не устраняется автоматически.
7. **Коды выхода трекера нельзя читать как «всё хорошо»** (BR-601) —
   `tools/tracker.sh` возвращает **2**, если трекер недоступен, и **3**, если
   истёк токен; stderr трекера не смешивается с данными, а код передаётся
   наружу. Иначе poll-сводка выглядит как «все задачи закрыты», хотя трекер
   просто не ответил. `tools/tracker-poll.sh` разбирает `--json` из
   `list-issues --raw` (колонки разделены табом) → `id` / `subject` / `status`.
8. **Секреты трекера — не в argv** (BR-605, BR-552) — API-токен уходит в curl
   через stdin (`curl --config -`, `header = "Authorization: …"`), пароль БД —
   через одноразовый `.pgpass` внутри контейнера; в `ps -eo args` секрета нет.
   Проверяется guard'ом `tools/check-secret-in-argv.sh`.
7. **`mark-review` без состояния `In review`** — команда падает на валидации;
   состояние должно существовать в проекте (группа `started`).
8. **Пагинация списков** — ответ содержит `results`, `count`,
   `total_results`, `next_cursor`; `per_page=1` даёт полный счётчик без выкачки
   всех задач.

## Связанные ADR | Related ADRs

- [`local-0012-plane-tracker-migration.md`](../../adr/local-0012-plane-tracker-migration.md) —
  Plane как трекер, перенос с OpenProject, сосуществование трекеров.
- [`local-0013-infra-backups-and-monitoring.md`](../../adr/local-0013-infra-backups-and-monitoring.md) —
  бэкапы и мониторинг infra-сервера, включая Plane.

## Связь с другими компонентами

- **[external-infra.md](external-infra.md)** — сервер, контейнер `infra`,
  nginx и TLS для `tracker.sm-karaoke.ru`.
- **[ci-tools.md](ci-tools.md)** — CI-раннер на `nsa-i9`; `check-spec-issue-link.py`
  проверяет привязку спек к задачам Plane.
- **[`docs/tracker-setup.md`](../../../docs/tracker-setup.md)** — руководство
  пользователя и агента: доступ, команды, состояния, диагностика.
- **[`op-plane-mapping.md`](op-plane-mapping.md)** — таблица соответствия
  номеров OpenProject и Plane.

## Changelog

- **2026-10-27**: Initial (T5 карты `tracker-migration`). Plane — рабочий
  трекер проекта; OpenProject — локальный архив для ручных задач.

- **2026-10-28**: добавлена конвенция «Задача N» — это `KARAOKE-N` в Plane при
  любом N; различение с `op:N` и `specs/NNN-*`.

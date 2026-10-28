# Phase 1 — Data Model: снимок CI и его инварианты

Модель одна для всех потребителей: host собирает **снимок** (`Snapshot`),
клиент рисует его, `forgejo_ci_status` отдаёт его же, `forgejo_ci_wait` отдаёт
его часть. Одна схема — один источник правды — не расходимся между панелью и
агентом.

## Сущности

### Run (прогон)

| Поле | Тип | Источник | Правило |
|---|---|---|---|
| `id` | integer | `runs[].id` | внутренний идентификатор; для `wait` и дедупа уведомлений |
| `number` | integer | `runs[].index_in_repo` | человекочитаемый номер; **из него строится ссылка** |
| `ref` | string | `prettyref` | `master`, `#6` — как есть |
| `event` | string | `event` | `push`, `pull_request`, `workflow_dispatch`, `schedule`, `""` |
| `status` | enum | `status` | `waiting` \| `running` \| `success` \| `failure` \| `cancelled` \| иное как есть |
| `durationSec` | integer \| null | `duration / 1e9` | только у завершённых; у ждущих `null` |
| `createdAt` / `updatedAt` | ISO string | `created` / `updated` | — |
| `url` | string | `html_url` | если пусто — собирается из `number` |
| `fromCache` | boolean | вычисляется | `true`, если прогон уже не отдаётся API (пурж), а взят из буфера |
| `jobs` | RunJobs \| null | `run_number` из `actions/tasks` | счётчики задач этого прогона |
| `etaSec` / `etaBasis` | integer \| null / `history` \| `progress` \| null | вычисляется | остаток только с основой |

Валидация: `id` — целое > 0; `number` — целое ≥ 0; `status` — непустая строка;
`url` — абсолютный `http(s)`; `durationSec` — целое ≥ 0 или `null`.

### RunJobs (счётчики задач прогона)

| Поле | Тип | Источник |
|---|---|---|
| `running` | integer | задачи прогона в статусе `running` |
| `waiting` | integer | задачи прогона в статусе `waiting` |
| `done` | integer | задачи в `success`/`failure`/`cancelled` |
| `total` | integer \| null | число джобов в workflow-файле; null — файл недоступен |

Группировка — по `run_number` из `actions/tasks` (эндпоинт отдаёт задачи многих
прогонов сразу). `jobs: null` — по прогону строк нет. На экране числитель —
`done + running`, но не больше `total`: в API бывают повторы по `attempt`.

**Инвариант**: у прогона в терминальном статусе (`success`/`failure`/`cancelled`/
`unknown`) `running` и `waiting` **всегда нулевые**. Forgejo держит устаревшие
строки задач у завершённых прогонов часами (наблюдалось 19 «бегущих» задач у
упавших #33 и #46), и без этого правила человек читает «19 из 27» у упавшего
прогона как «ещё бежит». `done` считает только терминальные статусы задач.

### ETA прогона

`etaSec`/`etaBasis` — только у активного прогона и только с основой:
`history` — медиана `durationSec` двух и более завершённых прогонов минус
прошедшее время; `progress` — при `done ≥ 3`, `elapsed ≥ 60 с` и известном
`total`: `elapsed / done × (total − done − running)`. Иначе `null` — выдуманное
время запрещено.

### RunnerState (вычисляемое состояние раннера)

Не хранится в API — выводится из снимка:

| Значение | Условие |
|---|---|
| `offline` | `actions/runners` вернул пустой список (или запрос упал, но есть прошлый успешный ответ с пустым списком) |
| `running` | есть прогон `running` **или** в задачах есть `running` |
| `queue-stuck` | есть прогоны `waiting` **и** `now − lastActivityAt > queueStuckAfterMs` (10 мин) |
| `idle` | активности нет, но `now − lastActivityAt ≤ queueStuckAfterMs` |
| `stale` | данные старше `staleAfterMs` (45 с) — перекрывает все предыдущие |

`lastActivityAt = max(runs[].updatedAt, tasks[].runStartedAt)`; если активности
не было ни разу — `null` (и состояние `idle`, если список прогонов пуст).

**Инвариант**: `active` из `actions/runners` наружу **не выходит** — это
регистрация, а не работа (факт: раннер `active`, три прогона висят в `waiting`).

### Task (задача/джоб)

| Поле | Тип | Источник |
|---|---|---|
| `name` | string | `tasks[].name` |
| `status` | string | `tasks[].status` |
| `runStartedAt` | ISO string \| null | `tasks[].run_started_at` |

Только последний прогон; пагинация API не работает, поэтому берём то, что
пришло, и не обещаем полноту.

### Snapshot (снимок)

```json
{
  "schemaVersion": 1,
  "generatedAt": "<ISO>",
  "stale": false,
  "lastOkAt": "<ISO>|null",
  "error": null,
  "repo": { "owner": "svoemesto", "name": "Karaoke", "url": "https://git.sm-karaoke.ru/svoemesto/Karaoke" },
  "runner": {
    "name": "nsa-i9", "version": "v6.4.0", "state": "running",
    "lastActivityAt": "<ISO>|null",
    "queue": { "running": 12, "waiting": 98 }
  },
  "runs": [ { "id": 399, "number": 33, "ref": "master", "event": "push",
              "status": "failure", "durationSec": 643,
              "createdAt": "<ISO>", "updatedAt": "<ISO>",
              "url": "https://…/actions/runs/33", "fromCache": false } ],
  "jobs": { "running": 12, "waiting": 98, "names": ["Docs (structure + offline links)"] }
}
```

**Инварианты снимка**

1. `runs` — не более `historyLimit` (по умолчанию 20, 5..100), порядок: свежие
   сверху (по `id` убыв.). Витрина (панель, popover, инструмент по умолчанию) —
   первые `runsLimit` (5): снимок шире показа, иначе серия отмен вытесняет
   реальный исход из окна и индикатор врёт.
2. `runs` — объединение ответа API и кольцевого буфера; прогоны, которых API
   больше не отдаёт, получают `fromCache: true` (пурж — норма, не ошибка).
3. `jobs` — `null`, если последний прогон не `running`/`waiting` (иначе панель
   покажет «бегут 19 задач» у провалившегося прогона — проверенный факт).
4. `stale: true` ⟺ `now − lastOkAt > staleAfterMs` **или** последний опрос
   упал; при `stale` поля остаются от последнего успешного снимка.
5. `error` — перечислимое поле: `null` | `"token-missing"` | `"forbidden"` |
   `"network"` | `"http-4xx"` | `"http-5xx"`. `token-missing` (ссылка на секрет
   не резолвится) и `forbidden` (Forgejo ответил 403 на запрос с токеном) —
   **разные** состояния, панель показывает разные тексты (FR-015): иначе
   человек идёт чинить «Forgejo», когда чинить надо токен. При `token-missing`
   `lastOkAt` сохраняется, `runs` пуст при первом старте.
6. Токен и заголовки `Authorization` **не представимы** в модели: белый список
   полей собирается явно, а не копированием ответа API (тест
   `secret.test.mjs` сериализует снимок и ищет значение токена).
7. `schemaVersion` растёт при изменении формы; клиент рисует только известную
   версию, на неизвестной — плашка «плагин и хост разной версии».

### TokenRef (ссылка на секрет)

- `tokenRef` — строка, дефолт `FORGEJO_CI_TOKEN`, валидируется паттерном
  `/^[A-Za-z_][A-Za-z0-9_]*$/`;
- значение резолвится перед каждым опросом и живёт только в локальной
  переменной на время запроса; в снимок, конфиг, лог и ответ не попадает.

## Состояния и переходы

### Прогон (для уведомлений и `wait`)

```text
waiting ──▶ running ──▶ success | failure | cancelled
   │            │
   └────────────┴──▶ (пурж: прогон исчезает из API → fromCache, из буфера)

терминальные = { success, failure, cancelled }
```

- Уведомление — только на переходе `waiting|running → terminal`, замеченном
  клиентом между двумя опросами; прогон, уже терминальный при первом опросе,
  уведомления не даёт (FR-013).
- `forgejo_ci_wait` завершается на терминальном статусе или по таймауту.

### Раннер

```text
(нет данных) ──▶ stale ──▶ running ──▶ idle ──▶ queue-stuck ──▶ idle …
                    ▲                      │
                    └──── Forgejo недоступен (40 с без успеха)
```

## Кольцевой буфер прогонов

- Ёмкость `runsLimit` (5); при вставке нового — вытесняется самый старый по `id`.
- Обновление существующего `id` **не** вставляет дубль: поля заменяются
  (у прогона может появиться `durationSec`).
- **Известное ограничение**: после перезапуска DSH история прогонов пуста, а
  Forgejo хранит историю недолго — панель покажет только то, что успеет увидеть
  текущий процесс. Эта же фраза — в README плагина (раздел «Известные
  ограничения»).
- Буфер пополняется только успешными опросами: при `stale` снимок не «затирает»
  историю пустым ответом.

# Контракты: маршрут состояния и два tool'а

Три интерфейса, один снимок. Если меняется форма снимка — меняются все три
одновременно (`schemaVersion` растёт).

## 1. `GET /forgejo-ci/state` (host → браузер)

- Регистрация: `webServer.register({kind: 'exact', path: '/forgejo-ci/state', handler})`
  внутри `ctx.effect` (снимается при выгрузке плагина).
- Метод: только `GET`; `POST/PUT/…` → `405`, тело `{"error":"method-not-allowed"}`.
- Заголовки ответа: `Content-Type: application/json; charset=utf-8`,
  `Cache-Control: no-store`.
- Код ответа: всегда `200`, даже при недоступном Forgejo — состояние деградации
  передаётся полями `stale`/`error` (панель не должна различать «нет данных» и
  «сломался» по HTTP-коду).
- Тело: ровно [state.schema.json](state.schema.json), без обёрток и без полей
  «на будущее».
- Время ответа: из кеша, без сетевого вызова; цель < 100 мс.
- Секрет: маршрут не принимает заголовков авторизации и не возвращает ничего,
  что не описано схемой (белый список полей).

## 2. `forgejo_ci_status`

Параметры (JSON Schema, `parameters`):

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "runs": {
      "type": "integer",
      "minimum": 1,
      "maximum": 20,
      "description": "Сколько последних прогонов вернуть. По умолчанию — runsLimit (5)."
    }
  }
}
```

Ответ: тот же объект, что `GET /forgejo-ci/state`, с `runs`, обрезанным до
запрошенного числа (порядок сохраняется). `stale`, `lastOkAt`, `error` — всегда
на месте (FR-008).

Поведение:

- читает **кеш**, в сеть не ходит; цель < 2 с (фактически миллисекунды);
- при `stale` не падает и не возвращает ошибку — отдаёт последний снимок с
  пометкой;
- при `error: "token-missing"` — отдаёт снимок, где `runs` пуст, а `error`
  объясняет причину; текст ответа не содержит подсказок вида «проверьте
  переменную с токеном» со значением.

`output.schema` — `state.schema.json`; `output.render` — короткий текст для
карточки: «5 прогонов · последний #33 failure (10 мин 43 с) · раннер nsa-i9
running», плюс `stale`-пометка, если она есть.

## 3. `forgejo_ci_wait`

Параметры:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "runId": {
      "type": "integer",
      "minimum": 1,
      "description": "Id прогона (поле runs[].id в снимке). Не задан — следим за самым новым прогоном на момент вызова."
    },
    "timeoutSec": {
      "type": "integer",
      "minimum": 10,
      "maximum": 600,
      "description": "Сколько ждать. По умолчанию 300."
    }
  }
}
```

Ответ:

```json
{
  "run": { "…поля Run из data-model.md…" },
  "timedOut": false,
  "waitedSec": 42,
  "stale": false,
  "lastOkAt": "<ISO>|null"
}
```

Поведение:

- ждёт смены статуса, опрашивая кеш раз в 1 с (сеть не трогает — кеш обновляет
  таймер host-половины);
- прогон уже терминален → ответ сразу, `waitedSec: 0`;
- истёк `timeoutSec` → `timedOut: true` + текущий `run` (не ошибка);
- `timeoutMs` определения — `620000`, чтобы собственный таймаут (600 с) всегда
  срабатывал раньше внешнего;
- отмена вызова (`AbortSignal`) → ожидание прекращается, таймер снимается.

Ошибки (структурированные, `isError: true`):

| Код | Когда | Тело |
|---|---|---|
| `unknown-run` | `runId` нет в кеше | `{"error":"unknown-run","runId":42,"knownRunIds":[399]}` |
| `no-runs` | в кеше нет ни одного прогона (пустой репозиторий или `token-missing`) | `{"error":"no-runs","reason":"token-missing"\|"empty"}` |
| `timeout-too-long` | `timeoutSec > 600` | `{"error":"timeout-too-long","max":600}` |

## Общие требования к обоим tool'ам

1. Описания — английский, 1–3 предложения, из которых модель понимает, что
   `status` дешёвый, а `wait` экономит цикл опросов.
2. Ни один ответ не содержит: значения токена, заголовка `Authorization`,
   URL с параметрами авторизации, тела ответа Forgejo целиком.
3. `isConcurrencySafe: () => true` — оба вызова только читают кеш.
4. Регистрация — внутри `ctx.effect`, снятие при выгрузке плагина (FR-018).

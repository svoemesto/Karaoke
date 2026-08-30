# Contract: `POST /api/getfreetimeslots`

> Контракт не меняется в рамках этой спеки — документ служит для фиксации
> текущего поведения, на которое опирается UI-фикс datalist-маскировки.

## Endpoint

```
POST /api/getfreetimeslots
```

Без параметров запроса. Возвращает JSON-массив строк.

## Контракт

### Request

```http
POST /api/getfreetimeslots HTTP/1.1
Host: <KARAOKE_APP_HOST>:<PORT>
Content-Type: application/json
Content-Length: 0
```

Тело запроса: пустое или `{}`.

### Response

**Status**: `200 OK`

**Content-Type**: `application/json`

**Body**: `string[]` — JSON-массив строк формата `dd.MM.yy HH:mm`,
например:

```json
[
  "30.08.26 10:00",
  "30.08.26 11:00",
  "30.08.26 12:00",
  "30.08.26 13:00",
  "30.08.26 14:00",
  "30.08.26 15:00",
  "30.08.26 16:00",
  "30.08.26 17:00",
  "30.08.26 18:00",
  "30.08.26 19:00",
  "30.08.26 20:00",
  "30.08.26 21:00",
  "30.08.26 22:00"
]
```

### Гарантии (per спека 156)

1. Длина массива — ровно 13 элементов (если серверная БД доступна).
2. Каждый элемент — строка формата `dd.MM.yy HH:mm` (lowercase `yy`, 2 цифры года).
3. Все элементы строго в будущем относительно серверного `LocalDateTime.now()`.
4. По одному элементу на каждый час с 10:00 до 22:00 включительно.
5. Для часов, по которым ещё нет ни одной записи в `tbl_songs`, элемент всё
   равно присутствует (кандидат — сегодня/завтра по правилам спеки 156).

### Failure modes

| Сценарий | Поведение | Источник |
|---|---|---|
| БД недоступна | Возвращает `[]` (пустой массив), лог `[Timestamp] Невозможно установить соединение с базой данных` | `Utils.kt:4800-4804` |
| SQL-ошибка | Возвращает `[]`, `e.printStackTrace()` | `Utils.kt:4816-4818` |
| HTTP-ошибка (network) | Vuex-action ловит и логирует; UI показывает пустой datalist | `store.js:2759-2767` |

## Кто вызывает

- **`store.js::actions.getFreeTimeSlots`** (`store.js:2759-2767`) — Vuex-action,
  загружает `freeTimeSlots` через `promisedXMLHttpRequest`.
- **`store.js::getters.getFreeTimeSlots`** (`store.js:252-254`) — Vuex-getter,
  отдаёт `state.freeTimeSlots`.
- **`SongEdit.vue::computed.freeTimeSlots`** (`SongEdit.vue:2830-2832`) —
  проксирует Vuex-getter в шаблон.
- **`<datalist id="list_free_time_slots">`** (`SongEdit.vue:41-43`) —
  рендерится на основе `freeTimeSlots`.

## Что НЕ меняется в этой спеке

- Endpoint path, method, request/response формат — **без изменений**.
- Реализация в `ApiController.kt:6681-6683` (`@PostMapping("/getfreetimeslots")`)
  — **без изменений**.
- Реализация `Utils.kt::getFreeTimeSlots()` — **без изменений** (см.
  спеку [`156-publish-slots-range`](../156-publish-slots-range/spec.md)).

## Что МЕНЯЕТСЯ в этой спеке (frontend)

Контракт API не меняется, но **UX-потребитель** контракта
(`<input list="list_free_time_slots">` в `SongEdit.vue`) получает:

- `name="song_date_field"` — уникальное имя поля для отвязки от браузерной
  истории автозаполнения;
- `autocomplete="off"` — подавление собственного автокомплита браузера, чтобы
  datalist со слотами публикации показывался первым.

Это влияет только на UX-рендеринг datalist в браузере; данные, получаемые
через API, идентичны.
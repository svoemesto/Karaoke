# Contracts: backend↔frontend имена, меняющиеся этой задачей

Единственный вид «контракта» в этой задаче — переименование существующих
полей/параметров/ключей без изменения формы данных, типов или бизнес-логики.
Ниже — «было → стало» для каждого контракта, пересекающего границу
backend↔frontend (FR-010…FR-012, FR-015).

## Контракт 1: `GET`-ответ со списком health-report (`HealthReportDTO`)

**Потребитель**: `webvue3/src/components/Common/HealthReport/store.js`,
`HealthReportTableBody.vue`, `HealthReportTableHeader.vue`.

| Поле JSON — было | Поле JSON — стало | Тип |
|---|---|---|
| `settingsId` | `songId` | `number` (Long) |
| `settingsFileName` | `songFileName` | `string` |

Остальные поля `HealthReportDTO` (`description`, `healthReportTypeName`,
`healthReportStatusName`, `color`, `canResolve`, `problemText`,
`solutionText`) не меняются.

**Проверка после реализации**: открыть админку → раздел Health Report →
убедиться, что таблица рендерится (значит, `store.js` и Vue-компоненты
читают новый ключ), и что кнопка «repair» (использует `songId` для запроса
повторного скана) работает.

## Контракт 2: `POST /changesettingsstatus`

**Потребитель**: `karaoke-app/src/main/resources/static/settings_context.js`.

| Параметр запроса — было | Параметр запроса — стало | Тип |
|---|---|---|
| `settingsId` | `songId` | `Long` (обязательный) |
| `statusId` | `statusId` (не меняется) | `Long` |

**Проверка после реализации**: открыть legacy-страницу, использующую
`settings_context.js`, сменить статус песни, убедиться, что запрос уходит
успешно (200, не 400 «missing required parameter»).

## Контракт 3: `POST /songs_update` (два метода)

**Потребитель**: Thymeleaf-шаблоны `songs.html`, `songs2.html`,
`area_center_column.html`.

Все 54 form-параметра (по 27 в каждом из двух методов) с префиксом
`settings_` переименовываются в префикс `song_`, сохраняя суффикс без
изменений, например:

| Было | Стало |
|---|---|
| `settings_id` | `song_id` |
| `settings_songName` | `song_songName` |
| `settings_author` | `song_author` |
| `settings_fileName` | `song_fileName` |
| `settings_idVk`, `settings_idDzenLyrics`, … | `song_idVk`, `song_idDzenLyrics`, … |

Полный список — см. `data-model.md`, Категория 4. Имена `<input name="...">`
в трёх шаблонах должны быть переименованы 1:1 с параметрами контроллера.

**Проверка после реализации**: открыть legacy-страницу редактирования
списка песен (`songs.html`/`songs2.html`), изменить одно из полей (например,
`author`), сохранить, убедиться, что изменение применилось (перезагрузить
страницу и увидеть новое значение).

## Контракт 4: SSE-событие обновления health-report

**Отправитель**: `SseNotification.kt` (`healthReports(...)`).
**Потребитель**: `webvue3/src/components/Songs/store.js` (обработчик
пользовательских SSE-событий).

| Ключ данных события — было | Ключ данных события — стало |
|---|---|
| `settingsId` | `songId` |

Остальная форма события (`healthReportDtoList` и т.п.) не меняется.

**Проверка после реализации**: в админке запустить действие, вызывающее
health-report scan одной песни (например, «repair»), убедиться, что UI
обновляется без перезагрузки страницы (значит, SSE-событие дошло и было
корректно распознано по новому ключу).

## Контракты, НЕ входящие в эту задачу

- Физическая схема БД (колонка `settings_id` и другие объекты, см.
  `data-model.md`, Категория 6) — не меняется.
- `SyncTarget.key = "settings"` (`sync/SyncTarget.kt`) — не меняется
  (используется в несохранённом в git `Karaoke.properties` на машине
  администратора).
- Любые контракты `karaoke-public` — модуль вне области задачи (FR-014).

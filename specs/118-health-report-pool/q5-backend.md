## Question

Добавить backend-фильтр к существующему эндпоинту `POST /processes/countwaiting`
(либо завести новый эндпоинт с другим именем), чтобы можно было получить
количество WAITING-процессов **только для lane `THREAD_LANE_HEALTH_REPORT = 1`**.

## Зачем

UI-бейдж в `ProcessWorker.vue` (тикет #123) должен показывать размер пула
**именно HealthReport-заданий**, а не общее число WAITING по всем lane'ам
(которое включает рендер-процессы, demucs и т.д. — для админки Песен это
шум).

## Текущее состояние

- Эндпоинт: `POST /processes/countwaiting`
  → `ApiController.kt:2106-2108`, возвращает `Long` (общее число WAITING).
- SQL сейчас: `select count(*) from tbl_processes where process_status = 'WAITING'
  and process_command <> 'tail'`.
- `KaraokeProcess.kt:505-538` — `getCountWaiting(threadId: Int? = null)`
  уже принимает опциональный фильтр. Нужно просто прокинуть его из контроллера.
- Список процессов с фильтром уже есть:
  `GET /api/admin/processes?thread_id=1`
  (`KaraokeProcessAdminController.kt:32,45`).

## Что сделать

1. Расширить `POST /processes/countwaiting` опциональным параметром
   `thread_id: Int?` (либо новым эндпоинтом, если нужно сохранить обратную
   совместимость).
2. Внутри прокинуть в `getCountWaiting(threadId = thread_id)`.
3. Убедиться, что SSE-канал `PROCESS_COUNT_WAITING` (`KaraokeProcessWorker.kt:801-817`)
   **продолжает** отдавать общий счётчик (для существующего бейджа
   KaraokeProcess). Новый фильтр — **только** для прямого REST-вызова.
4. Добавить unit/integration-тест на фильтр (если в проекте есть
   convention для контроллеров).

## Зависимости

- Карта #119.
- Зависит от — ничего (можно делать параллельно с #122).

Тип: [wayfinder:task]
Блокирует: #123 (UI-бейдж использует этот endpoint)
Заблокирован: —
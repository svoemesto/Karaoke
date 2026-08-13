# Contracts: выбор статуса песни при апруве задания (5 или 6)

**Feature**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Два эндпоинта получают **аддитивные** изменения — ни маршрут, ни метод, ни
обязательные параметры, ни базовые HTTP-семантики не меняются. Существующие
потребители продолжают работать без правок.

| Файл | Эндпоинт | Что меняется |
|---|---|---|
| [approve-endpoint.md](./approve-endpoint.md) | `POST /api/songeditor/approve` | необязательный `?idStatus=`; гейт рендера/sync по факту; расширенные сообщения лога и ответа |
| [byid-endpoint.md](./byid-endpoint.md) | `POST /api/songeditor/byId` | новое поле `idStatus` (статус ПЕСНИ) в ответе |

## Общие инварианты обоих эндпоинтов (проверяются в quickstart.md)

1. Все 4 известных исхода approve (`assignment_not_found`, `draft_not_found`,
   `song_not_found`, `bad_markers`, `save_failed`) — **как сейчас**, коды ошибок
   обратно совместимы (см. [specs/094-fix-approve-news-failure/contracts/approve-endpoint.md](../../094-fix-approve-news-failure/contracts/approve-endpoint.md)).
2. HTTP 200 на все штатные исходы (никакого 5xx из-за необработанного
   исключения внутри бизнес-логики — inherited from specs/094).
3. `id_status` песни при апруве никогда не понижается и никогда не выходит за
   `{5, 6}` (data-model.md INV-1, INV-2).
4. Идемпотентность повторного клика — `status: "already_approved"` — срабатывает
   **до** валидации `idStatus` (т.е. даже запрос с `idStatus=5` на уже
   одобренное задание НЕ изменит ничего, кроме логирования).
5. Существующий клиент (без `idStatus` в запросе) получает **полностью
   идентичное сегодняшнему** поведение — это обязательный критерий SC-003.
6. Push одобренной песни на SERVER (`updateRemoteSongFromLocalDatabase(song.id)`)
   происходит при любом `idStatus` — иначе одобренная разметка не попадёт
   в прод (research D-3).

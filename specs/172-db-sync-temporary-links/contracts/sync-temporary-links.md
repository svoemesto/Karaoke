# Contract: Sync API для временных ссылок

## Existing endpoints

Специальные endpoints не добавляются. Временная ссылка появляется в существующих контрактах после регистрации в `SyncRegistry`.

### `GET /api/sync/entities`

Возвращает список сущностей синхронизации. Для новой сущности должен присутствовать объект:

- `key`: `sharelinks`
- `displayName`: `Временные ссылки`
- `oneClickDirection`: `SERVER_TO_LOCAL`
- флаги операций `insert/update/delete/move` для направлений `push` и `pull`

UI использует эти данные для строки сущности и переключателей разрешений.

### `POST /api/sync/oneclick`

Запускает синхронизацию разрешённых сущностей в их `oneClickDirection`. Для `sharelinks` направление всегда определяется как `SERVER_TO_LOCAL` и не требует параметра от клиента.

Успешный результат содержит итог операции для временных ссылок: добавленные, изменённые, удалённые, перемещённые записи и ошибки. В результате и логах нельзя передавать `token_hash`, `active_session_token_hash`, `active_session_browser_hash` и другие секретные/непрозрачные значения.

### `POST /api/sync/run`

Параметры:

- `key=sharelinks`
- `direction=SERVER_TO_LOCAL` или явно выбранное `LOCAL_TO_SERVER`

Направление должно быть проверено по существующим operation flags до выполнения. Недоступная операция возвращает текущий стандартный отказ sync API, без частичного успешного результата.

### `POST /api/sync/setflag`

Параметры:

- `key=sharelinks`
- `direction=LOCAL_TO_SERVER` или `SERVER_TO_LOCAL`
- `operation=INSERT|UPDATE|DELETE|MOVE`
- `value=true|false`

После изменения UI получает обновлённое описание сущности. Новые флаги не должны включаться автоматически при установке приложения.

## Compatibility

Существующие клиенты, не знающие о `sharelinks`, продолжают работать: endpoint списка сущностей остаётся расширяемым, а one-click использует registry на сервере.

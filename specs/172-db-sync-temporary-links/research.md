# Research: Временные ссылки в синхронизации БД

## Decision: Синхронизировать только `tbl_song_share_links`

- **Rationale**: Это доменная запись временной ссылки: владелец, песня, token hash, активность, срок действия и отзыв. `tbl_song_share_sessions` содержит короткоживущие lease/session и аудит использования; перенос таких строк между окружениями может восстановить устаревшие сессии и нарушить ограничения одновременного доступа.
- **Alternatives considered**: синхронизировать обе таблицы; отклонено из-за runtime-состояния сессий и внешних IP/user-agent hash.

## Decision: Использовать существующий generic `SyncTarget`

- **Rationale**: `SyncRegistry`, `GenericKaraokeDbTableSyncTarget`, `SyncOperation`, `/api/sync/entities`, `/api/sync/run` и `/api/sync/oneclick` уже поддерживают одинаковый lifecycle для таблиц с `KaraokeDbTable` и `recordhash`. Это минимизирует риск расхождения поведения и не требует нового API.
- **Alternatives considered**: специальный контроллер и отдельный sync service; отклонено как дублирование generic движка.

## Decision: One-click direction — `SERVER_TO_LOCAL`, operation flags disabled by default

- **Rationale**: Это явно задано пользовательским требованием для направления. Существующая модель проекта по умолчанию выключает sync-разрешения, поэтому добавление записи не должно автоматически разрешить destructive операции. Конкретные pull-флаги включаются конфигурацией так же, как у других сущностей.
- **Alternatives considered**: включить все pull-операции сразу; отклонено из-за риска неожиданных удалений и принципа безопасного opt-in.

## Decision: Секреты не выдавать в UI и логах

- **Rationale**: В таблице есть `token_hash`, а спецификация требует сохранения секрета при переносе, но не его раскрытия. UI-краткая метка должна использовать только id/owner/song/status/expiry.
- **Alternatives considered**: показывать token hash для диагностики; отклонено по принципу секретов и git/runtime hygiene.

## Decision: Проверка через compile/lint и ручной dual-DB сценарий

- **Rationale**: В проекте нет CI-safe полного integration suite; существующие тесты требуют окружение. Поэтому план должен сочетать статические проверки с воспроизводимой проверкой новой сущности на двух БД.
- **Alternatives considered**: полагаться только на интеграционные тесты; отклонено, так как они требуют credentials/network/browser.

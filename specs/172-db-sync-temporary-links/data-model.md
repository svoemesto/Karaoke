# Data Model: Временные ссылки в синхронизации БД

## SongShareLink / `tbl_song_share_links`

Синхронизируемая доменная запись временного доступа к песне.

| Поле | Назначение | Правила |
|---|---|---|
| `id` | Стабильный идентификатор ссылки | Сохраняется при переносе; используется как sync key |
| `owner_site_user_id` | Владелец ссылки | Сохраняется; должен ссылаться на существующего пользователя |
| `song_id` | Целевая песня | Сохраняется; должен ссылаться на существующую песню |
| `token_hash` | Непрозрачный идентификатор ссылки | Переносится без раскрытия; не показывается в summary/logs |
| `active` | Признак активности | Переносится как текущее состояние |
| `expires_at` | Срок действия | Переносится без изменения значения |
| `created_at` | Время создания | Не изменяется повторной синхронизацией |
| `revoked_at` | Время отзыва | Nullable; переносится при отзыве |
| `revoke_reason` | Причина отзыва | Nullable; переносится при отзыве |
| `first_used_at` | Первое использование | Nullable; переносится как состояние ссылки |
| `last_used_at` | Последнее использование | Nullable; переносится как состояние ссылки |
| `active_session_token_hash` | Текущий lease token hash | Сохраняется непрозрачно; не выводится наружу |
| `active_session_lease_until` | Окончание lease | Переносится как текущее состояние, но session table не синхронизируется |
| `active_session_browser_hash` | Непрозрачный browser hash | Не логируется и не отображается |
| `sessions_total` | Счётчик сессий | Переносится без повторного инкремента |
| `rejected_concurrent` | Счётчик отклонённых параллельных сессий | Переносится без повторного инкремента |
| `recordhash` | Канонический hash строки | Обновляется существующим DB-триггером и используется generic diff |

## Relationships

- `SongShareLink.owner_site_user_id` → владелец в `tbl_site_users`.
- `SongShareLink.song_id` → песня в `tbl_songs`.
- `tbl_song_share_sessions.share_link_id` → ссылка, но таблица сессий исключена из `SyncRegistry`.

## Sync behavior

- Registry key: `sharelinks`.
- Table: `tbl_song_share_links`.
- One-click direction: `SERVER_TO_LOCAL`.
- Operations: `INSERT`, `UPDATE`, `DELETE`, `MOVE`, каждая отдельно разрешается для `push` и `pull`.
- Default safety: новые operation flags имеют `false`, если конфигурация явно не задаёт иное.
- Idempotency: повторный diff по одинаковому `id` и `recordhash` не создаёт новую строку и не меняет существующую.
- Integrity: перенос ссылки не должен менять `id`, owner, song, expiry или token-related hashes.

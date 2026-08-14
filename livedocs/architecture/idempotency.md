---
status: Active
slug: idempotency
type: topic
related:
  - ../L3-components.md
  - ../dual-db-access.md
  - ../../features/182-editor-self-assign-tasks.md
  - ../../features/164-complete-guest-share-link.md
---

# Idempotency Patterns (API + Operation)

> Кросс-cut паттерн: как сделать API-операции идемпотентными (повторный
> вызов = тот же результат, без побочных эффектов).

## Что показывает

Конвенции проекта по идемпотентности. **Почти все** POST API в Karaoke
имеют разные стратегии идемпотентности. Здесь — каталог.

**Когда читать**:
- Пишете новый POST-endpoint (нужно решить стратегию).
- Отлаживаете race condition в существующем endpoint.
- Дизайните retry на клиенте.

## Стратегии

### 1. `Idempotency-Key` header (стандартная)

Стандарт Stripe-like: клиент отправляет уникальный ключ в header:

```
POST /api/foo
Idempotency-Key: 6e6f7469-6e67-7569-642d-6b657965
Content-Type: application/json
{ ... }
```

Сервер сохраняет `Idempotency-Key → response` и при повторении возвращает
тот же ответ без повторного выполнения операции.

```sql
CREATE TABLE tbl_idempotency_keys (
    key TEXT PRIMARY KEY,
    endpoint TEXT NOT NULL,         -- какой endpoint, e.g. POST /api/share/{id}/create
    user_id BIGINT,
    status_code INT NOT NULL,       -- 200, 201, 409, ...
    response_body JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);
```

**Применимо в Karaoke**: ПОТЕНЦИАЛЬНО для всех финансовых операций (покупка
подписки, оплата) — Pass N+.

### 2. Operation-level UNIQUE constraint + ON CONFLICT DO NOTHING

Использовать UNIQUE-индекс на уровне БД + `INSERT ... ON CONFLICT DO NOTHING`:

```sql
ALTER TABLE tbl_song_assignments
    ADD CONSTRAINT song_assignments_unique
    UNIQUE (song_id, assignee_id);
```

```sql
INSERT INTO tbl_song_assignments (song_id, assignee_id, ...)
VALUES (...)
ON CONFLICT (song_id, assignee_id) DO NOTHING
RETURNING id;
```

Если `RETURNING id` — вернулся → создано. Если нет → idempotent (уже было).

**Пример**: фича [182-editor-self-assign-tasks.md](../../features/182-editor-self-assign-tasks.md),
end-point `POST /api/public/songeditor/assign-self` — UNIQUE по
`(song_id, assignee_id)` + кнопка «Взять в работу» (UI идемпотентна: повторный
клик = 200 OK + `idempotent: true`).

### 3. Optimistic Concurrency (см. [concurrent-editing.md](concurrent-editing.md))

Перед операцией проверить `record.version` через `If-Match`. Не strict
idempotency, но защищает от случайной перезаписи.

### 4. Lease-based (TTL для ресурсов)

Для share-link playback sessions — lease с `active_session_lease_until`:

```
POST /api/share/claim { secret, browserHash }
→ server creates tbl_song_share_sessions, lease=60s
→ POST /api/share/heartbeat (каждые 30s) → lease++ 
→ POST /api/share/release → finished_at=now()
```

Один пользователь = одна активная сессия (лимит 2 устройств). Lease expired →
авто-отзыв через sweeper (см. [164-complete-guest-share-link.md](../../features/164-complete-guest-share-link.md)).

### 5. Side-effect-free GET + state machine для POST (heavy operation)

`POST` начинает асинхронную задачу, `GET /api/jobs/{jobId}` — статус.
Все retry-friendly через `jobId` (UUID). Удобно для долгих render-операций.

**Не реализовано в Karaoke** (всё через очередь + UE 30s polling),
но pattern задокументирован для будущего.

## Паттерны по эндпоинтам

| Endpoint | Стратегия | Где смотреть |
|----------|-----------|--------------|
| `POST /api/public/songeditor/assign-self` | #2 (UNIQUE + idempotent field) | [features/182](../../features/182-editor-self-assign-tasks.md) |
| `POST /api/public/share/claim` | #4 (lease-based) | [features/164](../../features/164-complete-guest-share-link.md), [167](../../features/167-fix-share-claim-500.md) |
| `POST /api/public/share/{id}/create` | #2 (UNIQUE на link) | то же |
| `POST /api/public/songeditor/approve` | Частично #3 (`@See` регрессии в [094](../../features/094-fix-approve-news-failure.md)) | — |
| `POST /songs_update` (legacy Thymeleaf) | **НЕТ идемпотентности** (legacy, override всё) | технический долг |
| `POST /api/utils/backfillpublishflags` | #2 (бэкфилл идемпотентен по `news`) | [features/125](../../features/125-news-flags-backfix.md) |
| Покупка подписки (будущее) | #1 (`Idempotency-Key`) | Pass N+ |

## Что должно идемпотентно

- ✅ Любой `POST` от пользователя (клик, форма).
- ✅ Любой `POST` от бота/scheduler (повторный запуск после падения).
- ✅ Любой `POST` от sync `LOCAL → SERVER` (повтор транзакции).
- ❌ `DELETE` обычно идемпотентен по дизайну HTTP.
- ❌ `PUT` (полный update) — **опасно**, нужна защита через `If-Match`.

## Антипаттерны

- ❌ **«Повторю ещё раз, не помешает»** — без идемпотентности это ДВОЙНЫЕ
  effects (двойная покупка, двойная публикация).
- ❌ **«Используем time как идемпотентный ключ»** — NTP drift, дубли в миллисекундах.
- ❌ **«Запомнили в памяти, что уже сделано»** — теряется при restart.
- ❌ **«Просто try/catch на стороне сервиса»** — race condition между двумя
  одновременными запросами.

## Что нужно для Pass 17+

1. **Внедрить `Idempotency-Key` header** для платежей (Pass N+).
2. **Добавить UNIQUE constraints** везде, где POST может дублировать
   (аналогично [182](../../features/182-editor-self-assign-tasks.md)).
3. **Добавить `tbl_idempotency_keys`** сервис + middleware.

## Связанные LiveDocs

- [concurrent-editing.md](concurrent-editing.md) — `If-Match` для защиты от
  race condition (смежно, не strict idempotency).
- [dual-db-access.md](dual-db-access.md) — JDBC retry (важно учитывать при
  idempotency).
- [features/182-editor-self-assign-tasks.md](../../features/182-editor-self-assign-tasks.md) — пример #2.
- [features/164-complete-guest-share-link.md](../../features/164-complete-guest-share-link.md) — пример #4.

## Код

- `karaoke-web/.../controllers/*Controller.kt` — примеры.
- Frontend: `Idempotency-Key` генерация (UUID на клиенте).

## История

- Создан: 2026-08-14
- Последнее обновление: 2026-08-14
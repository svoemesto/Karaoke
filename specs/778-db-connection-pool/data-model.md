# Data Model: Ограниченный пул соединений к БД (Phase 1)

**Feature**: `specs/778-db-connection-pool/spec.md` | **План**: [plan.md](./plan.md)

Сущности пула — **внутренние**, не данные предметной области: они не хранятся в БД, не мигрируются и не видны API. Модель описывает состояние в памяти процесса.

## Сущности

### DbPool — ограниченный пул к одной базе данных

| Поле | Тип | Описание |
|---|---|---|
| `target` | `String` | Роль БД: `LOCAL` / `SERVER` (для различения в журнале) |
| `url` | `String` | JDBC-URL **как есть** (с `sslmode`/`sslcert`/`sslkey`/`sslrootcert` для SERVER) |
| `username` / `password` | `String` | Учётные данные БД (из существующих полей `KaraokeConnection`) |
| `maxPoolSize` | `Int` | Потолок одновременных физических каналов (FR-001) |
| `minIdle` | `Int` | Нижняя граница прогретых каналов |
| `acquireTimeoutMs` | `Long` | Сколько ждать свободный слот (Q3=A: 5000) |
| `validationTimeoutMs` | `Long` | Проверка живости канала при выдаче |
| `keepaliveMs` | `Long` | Пинг простаивающих каналов (туннель) |
| `maxLifetimeMs` | `Long` | Абсолютный срок жизни канала |
| `provider` | `PoolProvider` | Источник каналов (HikariCP — в проде; подмена — в тестах) |

**Связи**: владеет каналами; выдаёт handle'ы; ведёт `PoolOwnerRegistry`.

**Правила валидации** (при создании, FR-007/FR-010):
- `maxPoolSize ≥ 1` и `≤` суммарного лимита БД минус резерв;
- сумма `maxPoolSize` всех пулов процесса `< 300` (лимит БД) — иначе отказ на старте с объяснением;
- `acquireTimeoutMs > 0`, `keepaliveMs ≥ 0`, `maxLifetimeMs > keepaliveMs`;
- `minIdle ≤ maxPoolSize`.

### PooledHandle — аренда физического канала

| Поле | Тип | Описание |
|---|---|---|
| `channel` | `java.sql.Connection` | Физический канал, выданный пулом (**не** владелец — только ссылка) |
| `owner` | `Thread` | Поток, взявший handle (владелец аренды) |
| `borrowedAtMs` | `Long` | Момент выдачи |
| `lastUsedMs` | `AtomicLong` | Последнее использование (наблюдаемость) |
| `released` | `AtomicBoolean` | Handle уже возвращён в пул (защита от двойного возврата) |

**Поведение**:
- `close()` → если `released` уже `true`, ничего; иначе `released = true`, `provider.release(channel)`, удаление из реестра владельцев. **Физический канал не закрывается** — он возвращается в пул (FR-002).
- Все прочие методы `java.sql.Connection` → делегируются в `channel` (через прокси, как `wrapForBrokenDetection` в BR-274).
- `isClosed()` → `released || channel.isClosed`.

### PoolOwnerRecord — запись реестра владельцев

| Поле | Тип | Описание |
|---|---|---|
| `owner` | `Thread` | Поток-хозяин handle'а |
| `borrowedAtMs` | `Long` | Момент выдачи |
| `lastUsedMs` | `AtomicLong` | Последнее использование |

**Хранилище**: `ConcurrentHashMap<PooledHandle, PoolOwnerRecord>` в `PoolOwnerRegistry` — потокобезопасно, без внешней синхронизации.

### PoolProvider — источник физических каналов (интерфейс)

| Метод | Смысл |
|---|---|
| `borrow(): java.sql.Connection` | Выдать канал (блокируется до `acquireTimeoutMs`); при исчерпании — исключение |
| `release(channel)` | Вернуть канал в пул |
| `totalConnections(): Int` | Всего физических каналов (наблюдаемость, SC-001) |
| `activeConnections(): Int` | Занято арендами |
| `idleConnections(): Int` | Свободны |
| `waitingThreads(): Int` | Ждут слот (для события `DB-POOL-002`) |

**Реализации**: `HikariPoolProvider` (прод) и тестовые подмены (без сети).

## Состояния handle'а

```
                 borrow()
   [свободен в пуле] ─────────────────▶ [арендован: owner=P, channel=C]
          ▲                                        │
          │                                        │ close() → release(C)
          │                                        ▼
          └──────────────────────────────── [освобождён: released=true]
                                                   │
                                                   │ рейпер (owner мёртв)
                                                   ▼
                                            [освобождён: released=true]
```

**Инварианты**:
1. Канал в пуле **одновременно арендован не более чем одним** handle'ом.
2. `released == true` ⇒ канал либо в пуле, либо уже отдан другому арендатору ⇒ handle'ом пользоваться нельзя.
3. Handle, взятый живым потоком, **не возвращается** в пул рейпером (FR-012) — только сам поток (`closeThreadConnection()`) или конец его жизни.
4. Handle, чей владелец мёртв, **возвращается** в пул рейпером (FR-003) — не позднее целевого интервала (SC-005).

## Переходы

| Переход | Инициатор | Условие | Действие |
|---|---|---|---|
| Выдача | поток P | `getConnection()` | `provider.borrow()` → новый handle → `owner = P` → в реестр → `ThreadLocal(P).set(handle)` |
| Повторное обращение | поток P | `getConnection()` | тот же handle из `ThreadLocal` (ThreadLocal-кеш сохранён, FR-005); обновляется `lastUsedMs` |
| Добровольный возврат | поток P | `closeThreadConnection()` | `handle.close()` → канал в пул; handle из реестра и из ThreadLocal |
| Возврат по смерти | рейпер | `owner.isAlive() == false` | `handle.close()` → канал в пул; handle из реестра |
| Осиротение | рейпер | `owner` жив, `lastUsed` старше `idle-ms` | handle помечается осиротевшим; канал **не** отдаётся (владелец может вернуться) |
| Переоткрытие после ошибки | поток P | `SQLException` → `markConnectionBroken()` | текущий handle освобождается, `getConnection()` берёт новый (BR-274, specs/236) |
| Исчерпание | пул | все слоты заняты > `acquireTimeoutMs` | событие `DB-POOL-002` (`target`, `waiting`, `busy`); быстрый отказ приложения (FR-008, Q3=A) |

## Связи с существующими сущностями

| Существующая | Как меняется |
|---|---|
| `KaraokeConnection` | `ThreadLocal` хранит `PooledHandle` вместо физического соединения; реестр `openConnections` расширяется владельцем; `reapIdleConnections()` в пуловом режиме возвращает handle'ы мёртвых владельцев |
| `Connection.local()/remote()` | Фабрики создают `DbPool` для своей роли (лениво); `virtual()` — без пула |
| `LocalConnectionReaper` (app и web) | В пуловом режиме возвращает handle'ы; в режиме без пула — прежнее поведение (FR-013) |
| `openConnections` (реестр) | В пуловом режиме — часть `PoolOwnerRegistry` (handle → владелец) |
| `markConnectionBroken()` / `wrapForBrokenDetection` | Сохраняются как есть (BR-274) — работают поверх handle'а |

## Данные, которых фича НЕ трогает

- Схема и данные PostgreSQL — без миграций (FR-014).
- `Karaoke.properties`, файлы MinIO, конфигурация nginx.
- Публичные контракты API.

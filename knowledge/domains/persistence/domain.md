---
id: domain-persistence
title: "Domain: Persistence (KaraokeDbTable + DB I/O)"
status: Active
slug: persistence
related:
  - ../processing/domain.md
  - ../health/domain.md
  - ../../adr/0001-raw-jdbc.md
---

# Domain: Persistence (KaraokeDbTable + DB I/O)

> Bounded context для абстракции БД-персистентности. Все 30 DB-сущностей
> karaoke-app реализуют интерфейс `KaraokeDbTable`. Прецедент
> создания — P2 Knowledge-аудита (Pass 341).

## Обзор контекста (Bounded Context)

**KaraokeDbTable** — единая абстракция для всех персистентных
сущностей Karaoke. Это не ORM (нет JPA/Hibernate), а **лёгкая
reflection-based обёртка**:

1. **Reflection-based mapping** — `@KaraokeDbTableField` аннотации
   на полях → маппинг на колонки БД.
2. **Diff-based save** — `save()` загружает текущее состояние из БД,
   сравнивает с in-memory, генерирует UPDATE только для изменённых
   колонок.
3. **SSE-уведомления** — после `save()` рассылается `recordChange`
   всем UI-вкладкам.
4. **Sync-готовность** — наличие колонки `recordhash` (md5) позволяет
   участвовать в two-DB sync.

**Граница**: контекст НЕ отвечает за:

- Сами таблицы и поля (это другие домены).
- Two-DB sync как процесс (→ [processing/two-db-sync](../processing/components/two-db-sync.md)).
- SQL-миграции (отдельный процесс).

## Ubiquitous Language | Единый язык

| Термин | Определение | Где в коде |
| --- | --- | | **`KaraokeDbTable`** | Интерфейс, который реализуют все DB-сущности | `model/KaraokeDbTable.kt:62` |
| **`@KaraokeDbTableField`** | Аннотация: имя колонки, `isId`, и т.п. | `model/KaraokeDbTableField.kt` |
| **`getTableName()`** | Имя таблицы (`"tbl_songs"`, `"tbl_authors"`, ...) | `KaraokeDbTable.kt:72` |
| **`save()`** | Сохранить in-memory → БД (INSERT или UPDATE с diff) | `KaraokeDbTable.kt:95` |
| **`toDTO()`** | Сериализация для API/SSE | `KaraokeDbTable.kt:203` |
| **`loadList(args, tableName, database)`** | Загрузить список записей с фильтрами | `KaraokeDbTable.kt:441` |
| **`loadById`** | Загрузить одну запись по id | `KaraokeDbTable.kt:579` |
| **`loadByIds`** | Загрузить несколько записей (пакетно) | `KaraokeDbTable.kt:600` |
| **`createDbInstance`** | INSERT + RETURNING id | `KaraokeDbTable.kt:622` |
| **`getDiff(new, old)`** | Field-level diff между двумя сущностями | `KaraokeDbTable.kt:762` |
| **`getListHashes`** | Получить `(id, recordhash)` для sync | `KaraokeDbTable.kt:688` |
| **`delete(id, database)`** | DELETE по id | `KaraokeDbTable.kt:788` |
| **`invalidateSchemaCache`** | Сбросить reflection-cache (для тестов) | `KaraokeDbTable.kt:425` |

## Архитектура

### Reflection-based mapping

Каждая сущность (например, `Song`) объявляет поля с аннотацией:

```kotlin
class Song(...) : KaraokeDbTable {
    override var id: Long = 0
    @KaraokeDbTableField(name = "song_file_name") var fileName: String = ""
    @KaraokeDbTableField(name = "song_year") var year: Int = 0
    // ...
}
```

При загрузке `loadList` использует reflection для:

1. Получения всех `@KaraokeDbTableField` полей.
2. Генерации SQL `SELECT <columns> FROM <table> WHERE <args>`.
3. Маппинга `ResultSet` → поля Kotlin.

**Кеш схемы**: `invalidateSchemaCache` — сбрасывает кеш reflection.
Используется в тестах.

### `save()` — diff-based UPDATE

Алгоритм:

```
1. Если id == 0L → INSERT (createDbInstance).
2. Иначе:
   - loadById текущего состояния из БД.
   - getDiff(this, savedEntity) — field-level diff.
   - Если diff пустой → ничего не делать.
   - Генерируется UPDATE только для изменённых колонок.
   - После успешного UPDATE → SSE recordChange.
```

**NB**: `save()` **молча проглатывает** UNIQUE-конфликты и другие
SQLException. Для сущностей с UNIQUE-индексом проверяйте конфликт ДО
`save()` в контроллере.

### `getDiff`

Сравнивает поле-за-полем между двумя экземплярами одной сущности.
Возвращает `List<RecordDiff>` — каждое изменение как
`(name, oldValue, newValue)`.

### `loadList(args, ...)`

Аргументы — Map<String, String> для фильтрации (`song_id=123`,
`is_active=true`). Генерирует `WHERE` clause динамически.

### `recordhash`-колонка

Каждая таблица в `SyncRegistry.all` MUST иметь:

- Колонку `recordhash VARCHAR(32)` (md5 от канонизированной строки).
- SQL-триггер `tg_<table>_recordhash`, поддерживающий колонку на
  INSERT/UPDATE/DELETE.

Без триггера sync не увидит diff.

### SSE после save

`save()` рассылает `SseNotification.recordChange(...)` через
`SseNotificationService.send(...)`. UI (`webvue3`) обновляет
представление без polling.

## Архитектурные решения

### Решение 1: Не используем JPA/Hibernate

Сырой JDBC + reflection. См. Constitution Principle II.

Рациональ: 18k+ записей на проде, O(n²) сравнения занимали 3+ минуты,
O(n) — секунды.

### Решение 2: Diff-based UPDATE

Генерируем UPDATE только для изменённых колонок → меньше шанс на
race condition в multi-user среде, меньше нагрузки на БД.

### Решение 3: Recordhash для sync

md5 от канонизированной строки. Позволяет O(n) diff через
`associateBy { it.id }`.

### Решение 4: SSE после save

UI обновляется без polling → экономия трафика и времени.

## Domain Invariants

1. **`save()` MUST быть идемпотентным** в части «diff is empty → no-op»
   (повторный save без изменений не генерирует UPDATE).
2. **`loadList` MUST использовать `WHERE id IN (..)`** для batch-операций,
   не по одной записи в цикле (Constitution II).
3. **`recordhash`-триггер MUST быть на каждой sync-таблице**.
5. **Nullable-колонки БД → nullable Kotlin-поля**. Иначе `loadList` бросает NPE на SQL NULL (см. ловушки в KDoc).

## Hot paths

- **`Song.loadList(...)`** — на странице «Песни» webvue3
  загружается с фильтрами. SELECT может быть тяжёлым (18k+ записей).
- **`Song.save()`** — на любой правке метаданных песни → UPDATE +
  SSE.
- **`getListHashes`** — на старте sync. Полный список `(id, recordhash)`.
  SELECT быстрый (только 2 колонки).

## Ловушки (из KDoc и DEVELOPER-опыта)

1. **`loadList` + SQL NULL**: nullable-колонка в БД → non-null поле
   в Kotlin → NPE при чтении. **Решение**: nullable-поля.
2. **`save()` проглатывает UNIQUE-конфликты**: проверяйте конфликт
   ДО save в контроллере (см. CONSTITUTION_PATTERN).
4. **`getDiff` field order** — **FIXED in Pass 451** ✅:
   `KaraokeDbTable.getDiff` теперь сортирует по
   `KaraokeDbTableField.name` для детерминированного UPDATE column
   order. Без сортировки порядок зависел от reflection
   (`kClassEntityA.members`) — JVM НЕ гарантирует порядок, что
   приводило к нестабильному `recordhash` и race в two-DB sync.

## Связь с другими компонентами

- **Two-DB sync** ([processing/two-db-sync](../processing/components/two-db-sync.md)):
  использует `getListHashes` для быстрого diff.
- **Async Process Queue** ([processing/async-process-queue](../processing/components/async-process-queue.md)):
  `KaraokeProcess.save()` обновляет статус.
- **SSE** ([sse domain](../sse/domain.md)):
  `recordChange` события после save.
- **HealthReport** ([health domain](../health/domain.md)):
  `Song.save()` для смены `idStatus`.

## Известные TODO

- [ ] **Какие сущности НЕ реализуют `KaraokeDbTable`**: проверка
      списка (предположительно все 30, см. P0 отчёт).
- [ ] **Конкретные сущности с UNIQUE-индексом**: какие из них есть,
      какой паттерн проверки конфликта.
- [ ] **`KaraokeDbTableField`** — где определён, все ли поля аннотированы.
- [ ] **Schema cache invalidation** — как тесты работают с этим.
- [ ] **Логирование при ошибках save**: куда, в каком формате.
- [ ] **Connection leak** — `KaraokeConnection.getConnection()` без
      close (см. KDoc `KaraokeProcessThread`).

## Код (физическая реализация)

- `karaoke-app/.../model/KaraokeDbTable.kt` (~840 строк — большой
  файл)
- `karaoke-app/.../model/KaraokeDbTableField.kt`
- `karaoke-app/.../model/KaraokeDbTableDto.kt`
- `karaoke-app/.../Connection.kt` (local/remote/virtual factory)
- `karaoke-app/.../KaraokeConnection.kt` (обёртка над JDBC)
- `karaoke-app/.../KaraokeProperties.kt` (через getString/getLong/...)

## Changelog

- **Pass 341 P2** (2026-09-09): Initial. Автор: agent (Karaoke).
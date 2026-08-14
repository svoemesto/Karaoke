# ADR-0001: Сырой JDBC без JPA/Hibernate для доступа к БД

* **Status**: Accepted
* **Date**: 2026-07-20 (фаза 001, ratified в Constitution § II)
* **Deciders**: команда Karaoke

## Context

Проект Karaoke работает с **PostgreSQL 16** напрямую, без ORM. Изначально
(2023-2024) был сделан сознательный выбор против Spring Data JPA / Hibernate
по нескольким причинам:

1. **Контроль над SQL**. Команда пишет SQL вручную — это важно для фичей вроде
   «Синхронизация LOCAL↔SERVER» и `recordhash`-триггеров, которые плохо ложатся
   на JPA/Hibernate ORM (грязный кэш, lazy loading, N+1).
2. **Производительность**. Запросы к БД — в горячем пути (sync, очередь задач,
   `SiteAdmin.vue`, статистика). На проде ~18k записей `tbl_settings`; O(n²) в
   сравнении рекордов = 3+ минуты; O(n) = секунды.
3. **Прямой контроль над `java.sql.Connection`** (cycle lifecycle, isolation,
   закрытие). Для очереди задач с `ProcessBuilder` и перезапусками критично.
4. **Минимизация зависимостей**. Spring Boot уже даёт JdbcTemplate, который
   предоставляет ровно то, что нужно (без ORM overhead).

## Decision

Мы используем **сырой JDBC** через:

- `org.springframework.jdbc.core.JdbcTemplate` для большинства запросов;
- `KaraokeConnection` (singleton) — фасад над `Connection.local()/remote()/virtual()`;
- `recordhash` — md5 от канонизированной строки таблицы, вычисляется триггерами в БД.

**Запрещено**: Spring Data JPA, Hibernate, Exposed, любой другой ORM.
**Причина запрета зафиксирована** в Constitution § II (NON-NEGOTIABLE).

### Паттерн «O(n) not O(n²)»

Все массовые сравнения — через `associateBy { it.id }`:

```kotlin
// ✅ ПРАВИЛЬНО (O(n))
val localById = localList.associateBy { it.id }
val serverById = serverList.associateBy { it.id }
localById.keys.intersect(serverById.keys).forEach { id ->
  if (localById[id]!!.recordhash != serverById[id]!!.recordhash) {
    pushToServer(localById[id]!!)
  }
}

// ❌ НЕПРАВИЛЬНО (O(n²) — 3+ минуты на 18k записей!)
localList.forEach { local ->
  serverList.none { it.id == local.id && it.recordhash == local.recordhash }
}
```

### Пакетная загрузка, не по одной

```kotlin
// ✅ ПРАВИЛЬНО — 1 запрос
jdbcTemplate.queryForObject(
  "SELECT * FROM tbl_settings WHERE id IN ($idsCsv)",
  rowMapper
)

// ❌ НЕПРАВИЛЬНО — N+1 запросов
ids.forEach { id ->
  jdbcTemplate.queryForObject("SELECT * FROM tbl_settings WHERE id = ?", ...)
}
```

## Consequences

**Положительные**:
- Полный контроль над SQL и execution plans (`EXPLAIN ANALYZE` используется часто).
- Нет проблем с N+1, dirty cache, lazy loading.
- Проще отлаживать performance (`pg_stat_statements` показывает каждый запрос).
- Меньше зависимостей → меньше supply-chain risk.
- `recordhash`-триггеры в SQL дают O(n) diff между БД.

**Отрицательные**:
- Больше boilerplate (маппинг `ResultSet → DTO` руками).
- Нет автогенерации запросов.
- Разработчик должен знать SQL/PostgreSQL.
- Миграции — чистый SQL, без Flyway/Liquibase (хотя можно добавить).

**Нейтральные**:
- Требуется дисциплина: каждый запрос в `try-with-resources` (или с явным
  `close()` в `finally`), чтобы не утекали Connection (см. `specs/091-fix-connection-leak`,
  LiveDoc `091`).

## Alternatives Considered

- **Spring Data JPA + Hibernate**: отвергнут — грязный кэш, сложность отладки
  sync, плохая производительность на 18k записях.
- **Exposed (JetBrains)**: рассматривался — Kotlin-native, но не давал выигрыша
  поверх JdbcTemplate и добавлял зависимость.
- **JOOQ**: рассматривался — type-safe SQL, но полезен только при наличии
  сотен разных запросов; у нас их порядка 50 уникальных, окупаемости нет.
- **Raw `DriverManager` без Spring**: отвергнут — теряем DI, AOP, тестируемость.
- **Hibernate с явным flush/clear**: гипотетически сработало бы, но добавляет
  существенный complexity без явного выигрыша.

## Ссылки

- [Constitution § II](.specify/memory/constitution.md) — NON-NEGOTIABLE принцип.
- [livedocs/architecture/data-sync.md](../data-sync.md) — `recordhash` и
  `SyncRegistry` (практическое применение принципа).
- [livedocs/features/087-fix-shared-db-connection.md](../../features/087-fix-shared-db-connection.md) —
  ThreadLocal изоляция Connection (последствие фикса 087).
- [livedocs/features/091-fix-connection-leak.md](../../features/091-fix-connection-leak.md) —
  утечка JDBC от одноразовых потоков (последствие фикса 087).
- [Constitution § III](.specify/memory/constitution.md) — обязательное участие
  в `SyncRegistry` для двусторонней синхронизации.
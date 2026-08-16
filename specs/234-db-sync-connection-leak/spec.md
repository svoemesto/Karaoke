# Feature Specification: Устранить утечку JDBC-соединений при «Синхронизации БД в 1 клик»

**Feature Branch**: `234-db-sync-connection-leak`
**Created**: 2026-08-16
**Status**: Draft
**Input**: User description: "Локально на админской машине при 'Синхронизации БД в 1 клик' вот такие ошибки в консоли, раньше такого не наблюдалось. Каскад сообщений 'KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already'."

## Контекст и текущее состояние

### Симптом (наблюдение пользователя, 2026-08-16)

На админской машине (локальный запуск `karaoke-app` в Docker) при нажатии
кнопки «Синхронизация БД в 1 клик» в логе `karaoke-app` появляется каскад
сообщений вида:

```
[2026-08-16 08:25:30.517622783] Устанавливаем связь с базой данный LOCAL...
KaraokeConnection getConnection Exception: FATAL: sorry, too many clients already
[2026-08-16 08:25:30.522809349] Невозможно установить связь с базой данный LOCAL
[2026-08-16 08:25:30.523051257] Устанавливаем связь с базой данный SERVER...
... (повторяется ~10 раз подряд для одного клика)
```

Каскад воспроизводится **на каждом клике** «Синхронизации БД в 1 клик» —
10 последовательных пар «попытка LOCAL → FATAL → попытка SERVER → успех».
Раньше (до появления 18+ сущностей в `SyncRegistry.all`) такого не было.

### Корневая причина

1. **`POST /api/sync/oneclick` (`ApiController.kt:5284-5317`)** для каждого
   `SyncTarget` из `SyncRegistry.all` (на текущий момент **18 сущностей**)
   вызывает `runEntitySync(target.key, target.oneClickDirection)`.
2. **`runEntitySync` (`Utils.kt:629-647`)** создаёт **новую пару**
   `Connection` через `Connection.local()` + `Connection.remote()`
   (см. KDoc `Connection.kt:60-98`: статические фабрики **всегда**
   возвращают `new Connection(...)`).
3. **`KaraokeConnection.getConnection()` (`KaraokeConnection.kt:36-47`)** для
   каждого свежего инстанса `Connection` открывает **новое физическое
   JDBC-соединение** через `DriverManager.getConnection(...)` и кладёт его
   в `ThreadLocal<java.sql.Connection?>` (см. спеку `087-fix-shared-db-connection`).
4. **Один HTTP-запрос `POST /api/sync/oneclick`** = 18 сущностей × 2 БД
   (`local` + `remote`) = **36 свежих `Connection`-инстансов**, каждый из
   которых на своём первом `getConnection()` открывает **отдельное физическое
   JDBC-соединение к Postgres** и кладёт в свой `ThreadLocal`.
5. **ThreadLocal никогда не освобождается** в долгоживущем Tomcat-потоке:
   - `KaraokeConnection.closeThreadConnection()` (`KaraokeConnection.kt:64-73`)
     существует, но KDoc явно запрещает вызывать его из переиспользуемых
     потоков (Tomcat worker pool).
   - Объект `Connection` — `local` val `threadLocalConnection` живёт, пока
     жив сам `Connection`-инстанс.
6. **Пул Tomcat-потоков в `karaoke-app`** = ~200 (дефолт). После 5 кликов
   «Синхронизации БД в 1 клик» каждый Tomcat-поток может нести по
   36 свежих JDBC-соединений в своих `ThreadLocal`-ах разных `Connection`-инстансов.
   `pg_stat_activity` быстро упирается в `max_connections = 100` (дефолт
   Postgres в `deploy/karaoke-db/`).

### Почему раньше работало

- До спеки `087-fix-shared-db-connection` (см. `karaoke-app/.../KaraokeConnection.kt:6-23`)
  использовался **общий `@Volatile` connection** в каждом `Connection`-инстансе —
  то есть 1 инстанс Connection держал ровно 1 физический канал. 36 инстансов
  Connection давали 36 каналов — столько же, сколько сейчас.
- С ростом `SyncRegistry.all` от единичных таблиц (Songs) до 18 (с добавлением
  ShareLinks, Subscriptions, SiteChatMessages и т.п. в последних фичах
  `172-db-sync-temporary-links`, `171-admin-subscriptions-history`,
  `176-authors-new-albums-badge`) каскад стал воспроизводиться стабильно
  на каждом клике — раньше 1-2 таблицы ещё укладывались в лимит, теперь нет.

### Что НЕ покрыто этой частичной правкой

1. **`Connection.local()/remote()` возвращают НОВЫЙ объект** на каждый
   вызов — паттерн «новый `Connection` → новый `ThreadLocal` → новый
   `DriverManager.getConnection`» сохраняется. Спека `091-fix-connection-leak`
   добавила `closeThreadConnection()`, но это решение для **одноразовых**
   потоков (KDoc явно запрещает для переиспользуемых).
2. **`getConnection()` маскирует сбой под `println + null`** — пользователь
   не понимает, что происходит (на консоль сыпется каскад сообщений, а
   `SyncOneClickResultDto` всё равно возвращается с `skipped=false` для
   тех target'ов, где удалось подключиться только к SERVER).
3. **Нет логирования уровня `log.warn`** для `too many clients` — только
   `println` через `KaraokeConnection.kt:43`. В прод-логах это
   неструктурированный stdout, без trace/correlation.
4. **Нет connection pool** — `DriverManager.getConnection()` напрямую,
   никакого HikariCP. Эта задача **отдельно** (см. спеку
   `174-fix-stats-connection-leak`/FR-007 — HikariCP вынесен).

### Соседние спеки (контекст)

- `specs/087-fix-shared-db-connection/` — общий `connection` →
  `ThreadLocal<Connection>`. Текущее решение.
- `specs/091-fix-connection-leak/` — `closeThreadConnection()` для
  одноразовых потоков (KaraokeProcessThread).
- `specs/174-fix-stats-connection-leak/` — аналогичная проблема для дашборда
  «Статистика», решённая через frontend lazy load + TTL-кеш (HikariCP
  отложен в отдельную задачу).
- `specs/172-db-sync-temporary-links/` — добавление ShareLinksSyncTarget
  в `SyncRegistry.all` (1 из последних сущностей, раздувших список).
- `archive/docs/features/dual-db-sync.md` — общее описание sync LOCAL↔SERVER.

## Clarifications

### Session 2026-08-16

Все 3 ключевых вопроса закрыты решениями пользователя (см. чеклист
`checklists/requirements.md`):

- **Q1 — основной механизм фикса**: **Singleton `Connection.local()/remote()`**.
  Два долгоживущих инстанса `Connection` на весь процесс `karaoke-app`
  (отдельные для `local` и `remote`), их `ThreadLocal` кеширует физические
  JDBC-соединения между вызовами `getConnection()` на одном Tomcat-потоке.
  HikariCP НЕ подключается в этой спеке.

- **Q2 — scope фикса**: **Только Connection**. Все SyncTarget и контроллеры
  сразу получают корректное поведение без отдельных правок — поведение
  меняется везде единообразно через смену реализации фабрик.

- **Q3 — обработка `too many clients`**: **Только логирование через SLF4J**
  (`log.warn` с полями `target=local|remote`, `thread`, `cause`).
  `getConnection()` остаётся возвращать `Connection?`, не бросает
  исключение и не меняет контракт. Существующие 174+ вызовов
  `getConnection()` НЕ трогаем.

> Все `[NEEDS CLARIFICATION]` маркеры в `### Requirements` ниже заменены
> на конкретные формулировки.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — «Синхронизация БД в 1 клик» работает без каскада «too many clients» (Priority: P1)

Администратор на админской машине нажимает кнопку «Синхронизация БД в 1 клик»
на странице синхронизации (`SyncAdminView.vue` в `webvue3`). Синхронизация
проходит **без каскада сообщений** `KaraokeConnection getConnection Exception:
FATAL: sorry, too many clients already` в логе `karaoke-app`.

**Why this priority**: Прямой симптом, который привёл к задаче. Без фикса
синхронизация «в 1 клик» неработоспособна на стандартном `max_connections=100`,
а любые параллельные действия (дашборд «Статистика», sync) усугубляют
проблему.

**Independent Test**: 10 раз подряд нажать «Синхронизация БД в 1 клик»
после чистого старта `karaoke-app`. Проверить `docker logs karaoke-app
--since 5m | grep -c "too many clients"` — должно быть **0**. Параллельно
открыть «Статистику» и убедиться, что нет регрессии (см. спеку 174).

**Acceptance Scenarios**:

1. **Given** Postgres `max_connections=100`, чистый старт `karaoke-app`,
   **When** администратор нажимает «Синхронизация БД в 1 клик»,
   **Then** в логе `karaoke-app` **нет ни одного** сообщения
   `KaraokeConnection getConnection Exception: FATAL: sorry, too many
   clients already` за эту операцию.
2. **Given** Postgres `max_connections=100`, **When** администратор нажимает
   «Синхронизация БД в 1 клик» 10 раз подряд, **Then** `pg_stat_activity`
   показывает **≤10** одновременных соединений к Postgres от `karaoke-app`
   (по `application_name='karaoke-app'`).
3. **Given** параллельно открыт дашборд «Статистика» (спека 174),
   **When** администратор нажимает «Синхронизация БД в 1 клик», **Then**
   оба сценария работают без `too many clients` (нет регрессии на 174).

---

### User Story 2 — При реальной перегрузке БД пользователь видит понятный warn в логе (Priority: P2)

Когда Postgres действительно отказывает в новом соединении (`too many
clients`), в логе `karaoke-app` появляется **структурированный warn** с
контекстом (какая БД, какой поток, какая причина), а не голый `println`.
Текущий код (`KaraokeConnection.kt:43`) остаётся как fallback, но
дополнительно логирует через SLF4J.

**Why this priority**: Улучшает диагностику инцидентов, но не убирает саму
проблему перегрузки (это — скоуп спеки 174 / отдельного HikariCP-фикса).

**Independent Test**: Искусственно занять 99 соединений через `psql`
(`SELECT pg_sleep(60) FROM generate_series(1,99);`), нажать «Синхронизация
БД в 1 клик» — в `docker logs karaoke-app` должна появиться запись вида
`WARN  KaraokeConnection too many clients target=local thread=http-nio-8080-exec-3`
с реальным именем потока, а не голый `println`.

**Acceptance Scenarios**:

1. **Given** Postgres отказывает (`too many clients`), **When** любой
   код в `karaoke-app` вызывает `getConnection()` для `local` или `remote`,
   **Then** в логе `karaoke-app` появляется **ровно один**
   `log.warn` с полями `target`, `thread`, `cause` (в дополнение к
   существующему `println` — для обратной совместимости).
2. **Given** лог содержит `WARN KaraokeConnection too many clients`,
   **When** инженер ищет инцидент, **Then** в JSON-парсере логов
   (если включён) видно `target=local|remote`, `thread=<name>`,
   `cause=org.postgresql.util.PSQLException: FATAL: ...`.

---

### User Story 3 — Существующие 174+ вызовов `getConnection()` продолжают работать (Priority: P1)

После перехода `Connection.local()/remote()` на singleton-инстансы
**все остальные места проекта** (174+ вызовов `getConnection()` в
`ApiController`, `SongEditorController`, `SponsrSyncController`,
`StemJobsAdminController`, `PromoController`, `PublicSettingsController`,
`Utils.kt`, `StatsController` и др.) **продолжают работать** без изменений.
Рефакторинг `Connection` не должен ломать контракт
`getConnection(): java.sql.Connection?`.

**Why this priority**: Любая поломка контракта сломает ВСЕ контроллеры
и очереди. Это инвариант.

**Independent Test**: Прогнать существующие ручные сценарии:
- открыть «Статистику» → графики загружаются (FR-006 спеки 174);
- создать/обновить/удалить песню в админ-редакторе → сохраняется в БД;
- запустить синхронизацию по одной сущности (например, только `songs`)
  → результат идентичен текущему;
- дождаться выполнения фоновой задачи из `KaraokeProcessQueue` → нет
  `SocketTimeoutException` от конкурентного использования одного канала.

**Acceptance Scenarios**:

1. **Given** singleton `Connection.local()/remote()` в `karaoke-app`,
   **When** любой существующий код вызывает `db.getConnection()` (где
   `db` получен через `Connection.local()/remote()` или `WORKING_DATABASE`),
   **Then** возвращается валидный `java.sql.Connection?` с тем же контрактом,
   что и до фикса.
2. **Given** два параллельных HTTP-запроса на разных Tomcat-потоках,
   **When** оба вызывают `db.getConnection()` для **одного и того же**
   singleton-инстанса `Connection`, **Then** каждый получает **свой**
   физический JDBC-канал (из своего `ThreadLocal`) — нет конкурентного
   использования одного соединения (см. спеку `087-fix-shared-db-connection`).

---

### Edge Cases

- **Что если Postgres вообще недоступен (не `too many clients`, а
  connection refused)** — поведение не меняется: `getConnection()` бросает
  исключение, фабрика возвращает `null`, вызывающий код получает `null`,
  как и раньше. Никакой регрессии.
- **Что если Tomcat перезапускает потоки (graceful shutdown)** —
  `ThreadLocal` очищается GC при смерти потока (слабая ссылка внутри
  ThreadLocal). Никакой утечки в долгосрочной перспективе.
- **Что если `karaoke-app` запущен не в Docker (локальный dev-pc)** —
  `APP_WORK_IN_CONTAINER=false` → URL `jdbc:postgresql://localhost:8832/karaoke`.
  Singleton работает идентично. Никаких отдельных правок для dev-pc.
- **Что если в `karaoke-web` есть свой `Connection` (`karaoke-web/.../Connection.kt`)** —
  симметричный фикс (singleton-фабрики в `karaoke-web`) обязателен, иначе
  останется та же утечка в `webvue3`-эндпоинтах (новости, шаблоны,
  словари — см. `withDb { ... }` паттерн в `NewsController`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `Connection.Companion.local()` (`karaoke-app/.../Connection.kt:79`)
  MUST возвращать **тот же singleton-инстанс** `Connection` на повторные
  вызовы (а не `new Connection(...)` каждый раз). Аналогично для
  `Connection.Companion.remote()` (строки 92-98). Поведение `getConnection()`
  внутри singleton-инстанса **не меняется** — `ThreadLocal` кеширует
  соединение на поток.

- **FR-002**: `Connection.Companion.virtual()` (`Connection.kt:107-114`)
  SHOULD остаться без изменений (помечен `@Suppress("unused")`, в проде
  не вызывается; менять поведение «на всякий случай» — лишний риск).

- **FR-003**: Singleton-инстансы `Connection.local`/`Connection.remote`
  MUST быть thread-safe (double-checked locking через `@Volatile` +
  `synchronized` либо `LazyThreadSafetyMode.SYNCHRONIZED` Kotlin
  `lazy {}`). Два параллельных HTTP-запроса на старте приложения не
  должны создать два инстанса.

- **FR-004**: `KaraokeConnection.getConnection()` (`KaraokeConnection.kt:36-47`)
  MUST в дополнение к существующему `println(...)` логировать сбой
  `org.postgresql.util.PSQLException` через **SLF4J `log.warn`** с
  полями `target = "${this.name}"` (`name = "LOCAL"`/`"SERVER"`/`"VIRTUAL"`),
  `thread = "${Thread.currentThread().name}"`, `cause = "${e.message}"`.
  Поведение `println` сохраняется для обратной совместимости с логами,
  где SLF4J ещё не настроен.

- **FR-005**: `KaraokeConnection` MUST получить `private val log =
  LoggerFactory.getLogger(KaraokeConnection::class.java)` (через существующий
  SLF4J-конфиг — без новых зависимостей). Логирование должно работать
  в обоих режимах запуска (Docker и dev-pc).

- **FR-006**: Существующая сигнатура `fun getConnection(): java.sql.Connection?`
  MUST сохраниться без изменений (174+ вызывающих мест не трогаем).
  Возвращаемый тип остаётся nullable — контракт `Connection?` сохранён.

- **FR-007**: Существующий `KaraokeConnection.closeThreadConnection()`
  (`KaraokeConnection.kt:64-73`) MUST продолжать работать по контракту
  спеки `091-fix-connection-leak` (для одноразовых потоков). Рефакторинг
  фабрик НЕ должен сломать этот метод.

- **FR-008**: `karaoke-web/.../Connection.kt` MUST получить **симметричный
  фикс** — singleton-фабрики `local()`/`remote()` для `karaoke-web`,
  чтобы эндпоинты `webvue3` (новости, шаблоны, словари через `withDb`)
  тоже не плодили соединения. Поведение `getConnection()` идентично
  `karaoke-app`-варианту.

- **FR-009**: Существующие приватные `withDb { ... }` хелперы в
  `NewsController`/`DictionariesController`/`SponsrSyncController`/
  `SiteUsersController`/`ChatController` MAY остаться как есть —
  после FR-001 они всё равно будут переиспользовать singleton-инстанс
  (но лишний `close()` уже не повредит — идемпотентная операция для
  закрытого соединения). Опционально можно удалить их `finally { close() }`
  в рамках чистки — НЕ блокирует FR-001..FR-008.

- **FR-010**: В `Connection.kt` KDoc MUST быть обновлён: указать, что
  фабрики возвращают **singleton** (а не «новый инстанс на каждый вызов»),
  и что `getConnection()` кеширует соединение по потоку через `ThreadLocal`
  (см. спек `087-fix-shared-db-connection`).

- **FR-011**: Существующие unit/integration тесты (если есть — `karaoke-app/src/test`)
  MUST продолжать проходить без изменений (если тесты не используют
  `Connection.local()/remote()` напрямую — скорее всего не используют).
  Ручной smoke-тест «открыть → синхронизировать → редактировать песню →
  запустить фоновую задачу → повторить 10 раз» должен проходить.

- **FR-012**: Документация MUST обновляться в `archive/docs/features/dual-db-sync.md`:
  - добавить секцию «Singleton Connection-фабрики» с описанием нового
    поведения;
  - обновить секцию «Известные ловушки» с ловушкой «новый `Connection`
    на каждый вызов → утечка `ThreadLocal` → `too many clients`».

- **FR-013**: **HikariCP / connection pool НЕ включается** в эту спеку
  (см. спеку `174-fix-stats-connection-leak`/FR-007 — отдельная задача
  после замеров нагрузки). Singleton на основе `DriverManager` +
  `ThreadLocal` — компромиссное решение, достаточное для текущего
  `max_connections=100`.

### Key Entities *(include if feature involves data)*

- **`Connection.Companion.local`** — изменённая статическая фабрика.
  Внутри: `private val LOCAL_INSTANCE: Connection by lazy
  (LazyThreadSafetyMode.SYNCHRONIZED) { Connection(name="LOCAL",
  url=connectionLocalUrl(), username=USERNAME, password=PASSWORD) }`.
  Возвращает `LOCAL_INSTANCE` (не новый объект). См. `Connection.kt:79`.

- **`Connection.Companion.remote`** — изменённая статическая фабрика.
  Внутри: `private val REMOTE_INSTANCE: Connection by lazy
  (LazyThreadSafetyMode.SYNCHRONIZED) { Connection(name="SERVER",
  url=connectionRemoteUrl(), username=DB_SERVER_POSTGRES_USER,
  password=DB_SERVER_POSTGRES_PASSWORD) }`.
  Возвращает `REMOTE_INSTANCE`. См. `Connection.kt:92-98`.

- **`KaraokeConnection.log`** — новое поле `private val log =
  LoggerFactory.getLogger(KaraokeConnection::class.java)` в
  `karaoke-app/.../KaraokeConnection.kt`. Используется в `getConnection()`
  и `closeThreadConnection()` для логирования сбоев через SLF4J.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: При 10 кликах «Синхронизация БД в 1 клик» подряд на
  чистом старте `karaoke-app` в `docker logs karaoke-app` за последние
  5 минут **0** сообщений `FATAL: sorry, too many clients already`.
  Проверка: `docker logs karaoke-app --since 5m | grep -c "too many
  clients"` → `0`.

- **SC-002**: При 10 кликах «Синхронизация БД в 1 клик» подряд
  `pg_stat_activity` показывает **≤10** одновременных соединений к
  Postgres от `application_name='karaoke-app'`. Проверка:
  `SELECT count(*) FROM pg_stat_activity WHERE application_name=
  'karaoke-app';` → `≤10`. Сейчас (до фикса) — растёт линейно до 100+.

- **SC-003**: При искусственной перегрузке БД (`pg_terminate_backend`
  до `max_connections` или `ALTER SYSTEM SET max_connections=5;
  SELECT pg_reload_conf();`) **все вызовы** `getConnection()` для
  `local` и `remote` логируют `WARN KaraokeConnection too many
  clients target=local|remote thread=<name> cause=<message>` через
  SLF4J. Проверка: `docker logs karaoke-app 2>&1 | grep "WARN
  KaraokeConnection too many clients"` — структурированные записи
  присутствуют (а не голый `println`).

- **SC-004**: Существующие функциональные сценарии — открыть
  «Статистику» (спека 174), создать/обновить песню в редакторе,
  запустить синхронизацию по одной сущности, дождаться фоновой
  задачи очереди — проходят **без регрессий**. Проверка: ручной
  smoke-тест в `quickstart.md` спеки (этап `/speckit.plan`).

- **SC-005**: При 5 одновременных HTTP-запросах к разным эндпоинтам
  `karaoke-app` (синхронизация + статистика + редактор + фоновый
  воркер + `/api/songs/...`) `pg_stat_activity` показывает **≤30**
  соединений от `karaoke-app`. Сейчас (до фикса) — легко превышает
  100. Проверка: ручная симуляция нагрузки + `pg_stat_activity`.

## Assumptions

- Пользователь работает на `dev-pc` под OS-пользователем `dev` — это даёт
  право агенту пересобирать/перезапускать локальные контейнеры
  `karaoke-app`, `karaoke-web`, `karaoke-db` без отдельного согласия
  (см. Constitution, Principle VIII / AGENTS.md «Ограничения агента →
  Разрешено», п. 6). На любой другой машине пересборка — только по прямому
  согласию пользователя.
- Текущий Postgres `max_connections=100` (дефолт) не меняется в рамках
  этой спеки. Singleton-решение даёт ~10-кратное снижение потребления
  соединений, чего должно быть достаточно для админки «в 1 клик».
  Если окажется мало — отдельная задача `XXX-hikaricp-pool` (см.
  спеку `174-fix-stats-connection-leak`/FR-007).
- HikariCP / пул соединений НЕ подключается в этой спеке. Singleton
  на основе `DriverManager` + `ThreadLocal` — минимальный, достаточный
  фикс.
- Singleton НЕ означает «один физический канал на весь процесс» —
  это запрещено спекой `087-fix-shared-db-connection` (конкурентное
  использование одного JDBC-канала приводит к `SocketTimeoutException`).
  Singleton = «один инстанс `Connection` (а значит, один `ThreadLocal`)
  на процесс»; внутри — `ThreadLocal` кеширует по одному каналу
  **на поток**, как и сейчас.
- Существующий `println` в `getConnection()`/`closeThreadConnection()`
  сохраняется для обратной совместимости с логами, где SLF4J ещё не
  настроен. Добавляется только `log.warn` поверх `println`.
- Симметричный фикс в `karaoke-web/.../Connection.kt` обязателен
  (FR-008) — иначе останется та же утечка в `webvue3`-эндпоинтах.
- Существующий паттерн `withDb { ... }` (закрытие соединения после
  запроса) в `NewsController`/`DictionariesController`/`SponsrSyncController`
  MAY остаться без изменений — после FR-001 singleton он станет
  избыточным, но не сломает логику (закрытие закрытого соединения —
  no-op). Опциональная чистка — отдельная задача, НЕ блокирует эту.

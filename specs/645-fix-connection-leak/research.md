# Research: Устранение утечки соединений с БД (BR-627)

**Дата**: 2026-10-26 | **Спека**: [spec.md](spec.md) | **План**: [plan.md](plan.md)

## Установленные факты (проверено на коде в ветке `648-fix-connection-leak-impl`)

| # | Факт | Как проверено |
|---|---|---|
| F-1 | В прикладном коде (`karaoke-app/src/main`, `karaoke-web/src/main`) ровно **4** вызова `kotlin.concurrent.thread`: `ExportAlignmentDataset.kt:58`, `StorageCacheWarm.kt:59`, `CacheEtagSizeBackfill.kt:75`, `controllers/SongEditorController.kt:556` | `grep -rnP '(?<![\w.])thread\s*\{\|(?<![\w.])thread\s*\((?!\s*\))' --include='*.kt' karaoke-app/src/main karaoke-web/src/main` |
| F-2 | Все 4 — в файлах, обращающихся к БД; `ExportAlignmentDataset` и `SongEditorController` **не закрывают** соединение вовсе, `StorageCacheWarm` и `CacheEtagSizeBackfill` закрывают сами через `use { }`, но запущены в обход канонического механизма | чтение файлов |
| F-3 | Канонический механизм `startOneShotThread(database, name, onFinally) { }` закрывает соединение потока в `finally` при любом исходе | `karaoke-app/.../services/OneShotThread.kt` |
| F-4 | `WORKING_DATABASE = Connection.local()`; фабрики `Connection.local()/remote()/virtual()` — singleton'ы, т.е. `Connection.local()` и `WORKING_DATABASE` — **один и тот же** экземпляр `KaraokeConnection` | `Constants.kt:216`, `Connection.kt:124` |
| F-5 | `updateRemoteDatabaseFromLocalDatabase()` (тело потока в `SongEditorController`) открывает соединения в **двух** экземплярах: `Connection.local()` и `Connection.remote()` | `Utils.kt:1168-1177`, `Utils.kt:1245,1253` |
| F-6 | Поток главного цикла очереди (`KaraokeProcessWorker:749`) в ветке sync-batch тоже ходит в `Connection.remote()` | `KaraokeProcessWorker.kt:1282,1375` |
| F-7 | `java.lang.Thread` встречается в прикладном коде в 6 местах: 2 JVM shutdown hook'а (`HealthReport.kt:129`, `StorageMetadataCache.kt:121`), долгоживущий consumer-поток (`TelegramUpdatesConsumer.kt:65`), фабрика потока пула (`StatBySong.kt:156`), канонический `Thread(` в `OneShotThread.kt:46`, поток-читатель вывода процесса (`MediaInfo.kt:100`, `PlayerMp4MuxService.kt:202`) | `grep` + чтение файлов |
| F-8 | В `karaoke-web` и `karaoke-db` сырых `kotlin.concurrent.thread` нет | F-1 |
| F-9 | Корутинных диспетчеров (`Dispatchers.IO/Default`) в прикладном коде нет; пулы — фиксированного размера или одноразовые | `grep -rn "Dispatchers\." karaoke-app/src/main karaoke-web/src/main` → пусто |

## Решения

### D-1. Объём фикса — точечный (вариант A из Q1 владельца)

**Decision**: перевести 4 места на `startOneShotThread` + расширить проверку;
переход на ограниченный пул соединений (HikariCP) — вне объёма.

**Rationale**: FR-010 прямо запрещает перевод на пул в рамках задачи; ADR
local-0009, п. 5 «Альтернатива 3», отклоняет пул как требующий отдельной спеки
с замерами. Утечка, кроме того, наблюдалась не в проде, а на локальной БД во
время длинных прогонов тестов — то есть вектор ограничен и измерен.

**Alternatives considered**: (B) фикс + пул в этой же задаче — отклонено (FR-010,
ADР local-0009); (C) только пул — отклонено (оставляет утечку в 4 местах).

### D-2. `startOneShotThread` должен закрывать соединения всех БД, к которым пошёл поток

**Decision**: аддитивно расширить `startOneShotThread` параметром
`alsoClose: List<KaraokeConnection> = emptyList()` — список дополнительных
экземпляров `KaraokeConnection`, чьи thread-local соединения закрываются в том
же `finally`.

**Rationale**: F-5 и F-6 показывают, что реальные фоновые потоки ходят в ДВЕ БД.
У каждого экземпляра `KaraokeConnection` — свой `ThreadLocal<java.sql.Connection?>`,
поэтому `closeThreadConnection()` одного экземпляра не закрывает соединение
другого. Без `alsoClose` фикс был бы неполным: соединение к SERVER продолжало бы
висеть — тот же класс дефекта (FR-001 требует закрытия соединения потока при
любом исходе).

**Alternatives considered**: (а) `finally { Connection.remote().closeThreadConnection() }`
внутри тела потока — отклонено: закрытие перестаёт быть гарантией канонического
механизма, его можно забыть (ровно тот дефект, который устраняется); (б) общий
реестр всех экземпляров `KaraokeConnection` — отклонено: затрагивает весь код
работы с БД в двух модулях (вне объёма, как и пул).

**Следствие для SSoT**: внутренний API `startOneShotThread` в
`knowledge/domains/persistence/domain.md` дополняется описанием нового
параметра; ADR local-0009 — поправкой (append-only, без переписывания).

### D-3. Проверка обнаруживает файлы, обращающиеся к БД, динамически

**Decision**: «файл обращается к БД», если его код (без комментариев) содержит
хотя бы один маркер:

| Маркер | Что означает |
|---|---|
| `getConnection(` | прямое получение JDBC-соединения |
| `prepareStatement(` / `createStatement(` / `executeQuery(` / `executeUpdate(` | прямой SQL |
| `Connection.local(` / `Connection.remote(` / `Connection.virtual(` | фабрика соединений |
| `WORKING_DATABASE` | синглтон рабочей БД |
| `KaraokeConnection` | тип соединения |
| `KaraokeDbTable` | базовая сущность БД |
| `database =` / `database:` | передача экземпляра БД в вызов |

**Rationale**: FR-003 требует охвата 100% файлов прикладного кода, обращающихся
к БД (SC-003), а фиксированный перечень — исходный дефект. Эвристика покрывает
прямые и косвенные обращения: пересчёт по коду даёт 162 файла из 420 `.kt`
(`karaoke-app` + `karaoke-web`); два файла, которые ловятся только маркером
`database =` (`monitor/checks/LaneStalledCheck.kt`,
`monitor/checks/RenderQueueStalledCheck.kt`), действительно работают с БД.

**Alternatives considered**: (а) фиксированный перечень — отклонено (FR-003);
(б) поиск по `git ls-files` + анализ AST — отклонено: в проекте нет
AST-инструментария в тестах, а зависимость от внешнего тулинга в `test` усложняет
сборку; (в) маркеры по `import com.svoemesto.karaokeapp.KaraokeConnection` —
отклонено: `karaoke-web` импортирует `KaraokeConnection` из `karaoke-app`, но
модуль-независимый текстовый разбор устойчивее.

### D-4. Что считается «сырым фоновым потоком» в файле с обращением к БД

**Decision**: нарушение — это:

1. `kotlin.concurrent.thread` в любой форме (`thread {`, `thread(`, `kotlin.concurrent.thread {`);
2. `java.lang.Thread { … }` (SAM-конструктор) — **кроме** случая JVM
   shutdown hook'а (`addShutdownHook(Thread { … })`).

Не является нарушением: `Thread { … }`/`Thread(…)` в файле **без** обращений к БД
(US2, acceptance scenario 2), `Thread(…)` с non-SAM-аргументами (фабрика потока
пула, consumer-поток), JVM shutdown hook, а также канонический `Thread(` внутри
`OneShotThread.kt`.

**Rationale**: FR-002 называет конкретную конструкцию — `kotlin.concurrent.thread`.
Kotlin регистрозависим, поэтому `thread` (нижний регистр) и `Thread` (верхний) —
разные идентификаторы: `thread {` — это stdlib-функция, `Thread {` — SAM-конструктор
`java.lang.Thread`. Судя по F-7, в файлах с обращением к БД `Thread {` встречается
ровно в двух местах, и оба — shutdown hook'и: это не фоновые рабочие потоки, они
не ходят в БД и живут до конца JVM. Исключение для `addShutdownHook` сделано
явным, а не «списком файлов», чтобы правило не превратилось в белый список.

**Граница (осознанное ограничение)**: `Thread(…)` c SAM-лямбдой в скобках
(`Thread({ … }, "name")`) и `Thread(runnable, "name")` конструкциями не
отлавливаются — статически не отличить «одноразовый рабочий поток» от «фабрики
потока пула» без разбора тела. Таких мест в прикладном коде сейчас нет; они
перечислены в KDoc проверки как известная граница.

### D-5. Проверка живёт в `karaoke-app/src/test`, а не как shell-guard в `tools/`

**Decision**: расширить существующий JUnit-тест
`karaoke-app/src/test/.../services/OneShotThreadWiringTest.kt` и вынести детектор
в отдельный файл тестовых исходников `RawThreadDbGuard.kt` (в тестовых
исходниках — чтобы не тащить в прод-код статический анализатор).

**Rationale**: спека FR-003 требует «автоматическую проверку», а лид прямо
указал `OneShotThreadWiringTest` как точку расширения; проверка должна
падать вместе с тестами модуля (`./gradlew :karaoke-app:test`), которые и так
входят в контрактный прогон. Регистрация нового shell-guard'а по R-47.3
(скрипт + CI-джоб + pre-commit-хука + `--self-test` с негативной фикстурой) —
отдельная работа, не требуемая ни одним FR; её добавление раздуло бы диф
и создало бы второй, параллельный механизм контроля того же правила (R-48).

**Alternatives considered**: shell-guard `tools/check-raw-threads.sh` — отклонено
по причинам выше (дублирование + регистрация целиком, вне объёма спеки).

### D-6. Негативная фикстура «до/после»

**Decision**: проверка должна доказать три вещи:

1. **После правки** — реальные файлы прикладного кода без обращений к БД не
   считаются нарушением, а реальные DB-файлы не содержат сырых потоков
   (позитивный прогон по репозиторию).
2. **До правки** — та же проверка на копии реального DB-файла с внедрённым
   сырым `thread {` **падает** и указывает файл и строку (негативная фикстура
   «до/после» из FR-006).
3. **Различение** (FR-004) — синтетический файл с сырым потоком, но **без**
   обращений к БД, нарушением не считается.

**Rationale**: без негативной фикстуры проверка может молча ничего не проверять
(ровно тот класс дефекта, который чинится: «проверка ловит такой код только в
одном контроллере»). Фикстура строится на **реальном** файле (например,
`StorageCacheWarm.kt`), чтобы «до/после» относилось к тому же коду, а не к
синтетике.

### D-7. Регламент запуска тестов (FR-009) — в `knowledge/system/infra/ci-tools.md`

**Decision**: канонический текст регламента — в `ci-tools.md` (компонента
«контур проверок»: там уже описан контрактный прогон перед PR, R-47);
`knowledge/domains/persistence/domain.md` даёт ссылку на него; краткое
упоминание — в `DEVELOPMENT.md` рядом с командами прогона тестов.

**Rationale**: FR-009 требует, чтобы регламент запуска тестов называл (а) что
тестовые потоки тоже оставляют соединения, и (б) требуемый запас по лимиту
соединений (`max_connections=300`). Каноническое место для правил прогона —
`knowledge/system/infra/ci-tools.md`; `persistence/domain.md` — место, где
читатель ищет контракт соединения (SC-005: «в одном месте»).

**Содержание регламента**: перед длинным прогоном backend-тестов —
убедиться, что лимит соединений БД имеет требуемый запас
(`max_connections=300`, переопределяется `DB_MAX_CONNECTIONS`), потому что
тестовые потоки открывают соединения так же, как фоновые операции, и при
недостаточном лимите прогон падает с `sorry, too many clients already` (а тест
падает не по своей причине). Проверка состояния:
`SELECT count(*) FROM pg_stat_activity;` — сравнить с `max_connections`.

### D-8. Раскладка SSoT (FR-012)

| Файл SSoT | Что меняется | FR |
|---|---|---|
| `knowledge/domains/persistence/domain.md` | контракт соединения: новая проверка (динамический охват), параметр `alsoClose`, ссылка на регламент тестов | FR-001, FR-003, FR-007, FR-009, FR-012 |
| `knowledge/system/infra/deploy-overview.md` | способ проверки числа занятых соединений (`SELECT count(*) FROM pg_stat_activity;`) рядом с признаком исчерпания | FR-008 |
| `knowledge/system/infra/ci-tools.md` | регламент запуска тестов: тестовые потоки оставляют соединения; требуемый запас по лимиту | FR-009 |
| `knowledge/adr/local-0009-thread-local-connection-lifecycle.md` | поправка (append-only): факт закрытия 4 мест + аддитивный параметр `alsoClose`; признанное ограничение и компенсирующая мера остаются | FR-007, FR-012 |
| `docs/features/fix-connection-leak.md` | per-feature документ (Constitution VI, FR-009) | Constitution VI |

Карта `.ssot-map.yml` правил для этих файлов не содержит — новых правил не
требуется (ни один из изменяемых файлов кода под неё не подпадает); правки
SSoT делаются по FR-012 спеки и по Constitution IX.

## Открытые вопросы

| # | Вопрос | Решение |
|---|---|---|
| Q-1 | Что делать с `java.lang.Thread { }` в DB-файлах? | Не трогать (D-4): это либо shutdown hook, либо долгоживущий поток; контракт для них — другой тип потока (ADR local-0009) |
| Q-2 | Закрывать ли соединение к SERVER в потоке очереди (`KaraokeProcessWorker`)? | Да, через `alsoClose` — тот же класс утечки, правка в одну строку (F-6) |
| Q-3 | Нужен ли отдельный shell-guard в `tools/`? | Нет (D-5) — проверка покрыта JUnit-тестом модуля; при необходимости — отдельная задача |
| Q-4 | Менять ли `println` на SLF4J в затронутых файлах? | Нет: это предсуществующий долг вне контракта спеки; новый код `println` не добавляется |

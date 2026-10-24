# OpenProject #368 — PKG-RES-THREADLOCAL (волна C): пять записей одного корня — жизненный цикл JDBC-соединения потока

- **Ветка**: `577-pkg-res-threadlocal` (worktree `.worktrees/waveC-threadlocal`, от master `4b5f16a7`)
- **Пакет**: `PKG-RES-THREADLOCAL`, порции P-04/P-08/P-09/P-30/P-36/P-37 аудита #336
- **Записи**: BR-006 (S2), BR-027 (S3), BR-038 (S3), BR-060 (S2), BR-116 (S3) — все S2/S3, цикл короткий (без отдельной спеки — ТЗ = карточки аудита)
- **Дата**: 2026-10-24

---

## 1. Итог

| BR | Файл | Было | Стало |
|---|---|---|---|
| BR-006 | `karaoke-web/.../services/EventsBuffer.kt:198` (`executeBatch`) и `:233` (`executeSingle`) | `connection.close()` в `finally` — соединение из `WORKING_DATABASE.getConnection()` (thread-local кеш) закрывалось в переиспользуемом потоке (Tomcat / `@Scheduled`) → кеш разрушался, следующий запрос открывал новое физическое соединение (churn до N коннектов/сек на публичном `POST /registerevent`, риск исчерпания `max_connections`) | `close()` убран; соединение остаётся в thread-local кеше потока; statements закрываются через `use { }`. Добавлен шов `database: KaraokeConnection = WORKING_DATABASE` (методы стали `internal`) | 
| BR-027 | `karaoke-web/.../controllers/PublicSettingsWebController.kt:67` (`handleSetProperty`) | `finally { connection?.close() }` — тот же дефект в HTTP-потоке Tomcat; комментарий в коде утверждал, что соединение «закрываем сами» | `finally` с `close()` удалён; соединение остаётся в кеше потока; KDoc объясняет контракт |
| BR-038 | `karaoke-app/.../KaraokeProcessWorker.kt:733` (поток главного цикла `doStart`) | Поток очереди **одноразовый** (создаётся заново на каждый `start()`, умирает при остановке), держит свой thread-local канал к LOCAL-БД и **не закрывает** его → каждый цикл stop/start оставлял открытый физический канал до финализации сборщиком мусора | Поток запускается через `startOneShotThread(database, "karaoke-process-worker")` — соединение потока закрывается в `finally` при любом исходе. Прежний KDoc `closeThreadConnection`, запрещавший вызов из `doStart()`, исправлен: запрет был ошибочен (поток переиспользовать некому) |
| BR-060 | `karaoke-app/.../controllers/ApiController.kt` (7 endpoint'ов: 4915, 5693, 5752, 5809, 5855, 6570, 6594) + связанные локации `SyncAudioDescendants.kt:173` (воркер очереди) и `Utils.kt` (4 фоновые задачи: 97, 265, 415, 534) | 12 одноразовых фоновых потоков `thread { … }` без `closeThreadConnection()` → после каждого клика по кнопке админки оставался открытый канал к PostgreSQL | Все переведены на `startOneShotThread(database, "<имя>")` — закрытие в `finally`; в `autoAssignOriginalAll` `rs`/`ps` закрываются через `use { }` (было — вне `finally`) |
| BR-116 | `karaoke-app/.../controllers/ChatController.kt:39` (`withDb`) | `finally { db.getConnection()?.close() }` — тот же дефект в HTTP-потоке; устаревший комментарий «resolveDb() открывает новое физическое соединение на каждый вызов — без close() пул Postgres исчерпывается» описывал состояние до specs/234 | `withDb` не закрывает соединение и **не открывает** его только ради закрытия; комментарий приведён к фактическому контракту; добавлен шов `db` для тестов |

**Корень (общий для всех пяти)**: контракт `KaraokeConnection.closeThreadConnection()` был
применён «наоборот». Физическое соединение кэшируется в `ThreadLocal` потока
(`KaraokeConnection.kt:48`, specs/087; фабрики `Connection.local()/remote()` — singleton'ы,
specs/234). Отсюда два взаимоисключающих требования:

- **одноразовый поток** → закрывать ОБЯЗАТЕЛЬНО (иначе канал живёт до финализации GC; при
  `max_connections` — отказ БД для всего приложения, прецедент из KDoc `Connection.kt`);
- **переиспользуемый поток** (пул Tomcat, `@Scheduled`) → закрывать НЕЛЬЗЯ (churn физических
  соединений, риск исчерпания `max_connections`, рост latency).

Контракт зафиксирован в [local-ADR-0009](../../knowledge/adr/local-0009-thread-local-connection-lifecycle.md)
и в SSoT домена `persistence`.

**Изменённые/созданные файлы** (9 правок + 8 новых + 2 служебных):

```
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeConnection.kt      (open-шов + исправленный KDoc контракта)
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt   (BR-038: поток очереди + post-hook рендера)
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/SyncAudioDescendants.kt   (BR-060: воркер очереди)
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt                   (BR-060: 4 фоновые задачи)
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt (BR-060: 7 endpoint'ов)
M karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ChatController.kt (BR-116: withDb)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebController.kt (BR-027)
M karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/EventsBuffer.kt   (BR-006)
A karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt   (канонический хелпер startOneShotThread)
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/FakeKaraokeConnection.kt   (поддельное соединение для тестов)
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/OneShotThreadTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/OneShotThreadWiringTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/controllers/ChatControllerThreadLocalTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/controllers/ApiControllerOneShotThreadTest.kt
A karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/services/EventsBufferThreadLocalTest.kt
M karaoke-web/src/test/kotlin/com/svoemesto/karaokeweb/controllers/PublicSettingsWebControllerTest.kt (+1 регресс-тест)
M knowledge/adr/README.md
A knowledge/adr/local-0009-thread-local-connection-lifecycle.md
M knowledge/domains/persistence/domain.md
M knowledge/domains/processing/components/async-process-queue.md
M specs/336-code-audit/registry.csv                                  (статус 5 записей: TODO → исправлено)
A specs/368-pkg-res-threadlocal/report.md                            (этот отчёт)
```

---

## 2. Разбор по коду (что именно было не так)

1. `KaraokeConnection.getConnection()` возвращает **одно и то же** физическое соединение
   повторно для потока (кеш в `ThreadLocal`), а `closeThreadConnection()` закрывает его и
   **убирает** из `ThreadLocal`. Поэтому `close()` в HTTP-потоке = «убить кеш потока»,
   а **не** «вернуть соединение в пул» (пула в проекте нет — ADR-0001, сырой JDBC).
2. Прежний KDoc `closeThreadConnection` запрещал вызов из «главного цикла
   `KaraokeProcessWorker.doStart()`» — но этот поток создаётся заново на каждый `start()`
   и умирает при остановке очереди (`KaraokeProcessWorker.kt:691–747`), то есть он
   одноразовый и переиспользовать его соединение некому. Запрет закреплял утечку (BR-038).
3. Образец правильного поведения в проекте уже был — `KaraokeProcessThread.run`
   (`KaraokeProcessWorker.kt:483–485`): `finally { karaokeProcess.database.closeThreadConnection() }`.
   Он и стал основой для канонического хелпера.
4. `ApiController` — единственный модуль, где `closeThreadConnection()` не вызывался ни разу
   (проверено grep'ом по всему `karaoke-app` до правки: единственный корректный вызов был в
   `KaraokeProcessWorker`).

**Канонический способ закрытия** — новый хелпер
`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/OneShotThread.kt`:

```kotlin
fun startOneShotThread(
    database: KaraokeConnection,
    name: String? = null,
    block: () -> Unit,
): Thread =
    Thread(
        {
            try {
                block()
            } finally {
                database.closeThreadConnection()
            }
        },
        name,
    ).apply {
        isDaemon = true
        start()
    }
```

---

## 3. Регрессионные тесты «до/после»

### 3.1. Новые/изменённые тесты

| Тест | Что ловит | BR |
|---|---|---|
| `OneShotThreadTest` (3 теста) | `startOneShotThread` закрывает соединение потока — и при успехе, и при исключении внутри тела; поток — daemon и завершается | BR-038, BR-060 |
| `OneShotThreadWiringTest` (2 теста) | В аудируемых файлах (`ApiController.kt`, `KaraokeProcessWorker.kt`, `SyncAudioDescendants.kt`, `Utils.kt`) нет сырых `thread { … }`; поток очереди идёт через `startOneShotThread` | BR-038, BR-060 |
| `ApiControllerOneShotThreadTest` (1 тест) | Фоновая задача контроллера закрывает thread-local соединение потока (ожидание до 15 с) | BR-060 |
| `ChatControllerThreadLocalTest` (2 теста) | `withDb` НЕ закрывает соединение переиспользуемого потока и НЕ открывает его только ради закрытия | BR-116 |
| `EventsBufferThreadLocalTest` (2 теста) | Буфер событий (sync- и batch-вставка) НЕ закрывает thread-local соединение | BR-006 |
| `PublicSettingsWebControllerTest` (+1 тест) | Запись настройки НЕ закрывает thread-local соединение | BR-027 |

### 3.2. «До» — дефекты возвращены в код (git checkout + точечное возвращение `close()`/`thread {}`), тесты запущены на «до»-состоянии

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test \
    --tests "*OneShotThreadWiringTest" --tests "*ChatControllerThreadLocalTest" --tests "*ApiControllerOneShotThreadTest"

ApiControllerOneShotThreadTest > фоновая задача контроллера закрывает thread-local соединение потока() FAILED
    org.opentest4j.AssertionFailedError at ApiControllerOneShotThreadTest.kt:81
ChatControllerThreadLocalTest > withDb не открывает и не закрывает соединение, если тело к БД не ходило() FAILED
    org.opentest4j.AssertionFailedError at ChatControllerThreadLocalTest.kt:46
ChatControllerThreadLocalTest > withDb не закрывает thread-local соединение переиспользуемого потока() FAILED
    org.mockito.exceptions.verification.NeverWantedButInvoked at ChatControllerThreadLocalTest.kt:34
OneShotThreadWiringTest > в аудируемых файлах нет сырых одноразовых потоков() FAILED
    org.opentest4j.AssertionFailedError at OneShotThreadWiringTest.kt:62
5 tests completed, 4 failed
> Task :karaoke-app:test FAILED

$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test \
    --tests "*EventsBufferThreadLocalTest" --tests "*PublicSettingsWebControllerTest"

PublicSettingsWebControllerTest > запись не закрывает thread-local соединение переиспользуемого потока() FAILED
    org.mockito.exceptions.verification.NeverWantedButInvoked at PublicSettingsWebControllerTest.kt:206
EventsBufferThreadLocalTest > sync-вставка не закрывает thread-local соединение потока() FAILED
    org.mockito.exceptions.verification.NeverWantedButInvoked at EventsBufferThreadLocalTest.kt:72
EventsBufferThreadLocalTest > batch-вставка не закрывает thread-local соединение потока() FAILED
    org.mockito.exceptions.verification.NeverWantedButInvoked at EventsBufferThreadLocalTest.kt:82
18 tests completed, 3 failed
> Task :karaoke-web:test FAILED
```

Итого «до»: **7 падающих тестов** — по каждой из пяти записей BR есть падающая проверка.

### 3.3. «После» — те же тесты на исправленном коде

```
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test
BUILD SUCCESSFUL in 42s

karaoke-app: classes=55 tests=456 skipped=0 failures=0 errors=0
karaoke-web: classes=14 tests=82  skipped=0 failures=0 errors=0
```

---

## 4. Проверки

| Проверка | Команда | Результат |
|---|---|---|
| Тесты | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-web:test :karaoke-app:test` | **BUILD SUCCESSFUL** — 456 + 82 теста, 0 failures, 0 errors, 0 skipped |
| Линтер | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` | **BUILD SUCCESSFUL** (в т.ч. `ktlintMainSourceSetCheck`/`ktlintTestSourceSetCheck` обоих модулей) |
| Knowledge-структура | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` (frontmatter 16/16) |
| Ссылки в доках | `python3 tools/check-doc-references.py` | `OK: битых ссылок, несуществующих путей и эндпоинтов нет.` (4115 ссылок, 2235 путей, 306 эндпоинтов) |
| Стиль доков | `python3 tools/lint-knowledge.py` | `All documentation passes architectural style and structural checks.` |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | `✅ Все commits в ветке 577-pkg-res-threadlocal уникальны (нет race condition)` (worktrees активны: 5) |
| SSoT-влияние | `python3 tools/check-ssot-impact.py` | `[3/3] Checked 0 rules; 0 violation(s)` — `OK: all matched SSoT-impact rules satisfied.` (9 значимых файлов; ни один не попадает под правило `.ssot-map.yml`) |
| Pre-commit хуки | `PRE_COMMIT_HOME=<worktree>/.tmp-precommit GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle pre-commit run` | **все хуки Passed**: gradle-user-home guard, docker-config guard, container-restart guard, frontend-build guard, docker-image-tags guard, no-jpa-imports guard, no-mp4-mentions guard, subagent-isolation guard, ktlint, lychee (verify documentation links), check documentation references, check @Value placeholders, SongEdit field coverage, Endpoint field coverage, Block direct commits to master/main |

---

## 5. Knowledge-first: что прочитано и какие grep'ы были

Прочитано: `knowledge/README.md`, `knowledge/domains/README.md`,
`knowledge/domains/persistence/domain.md` (домен `KaraokeConnection`/`KaraokeDbTable`),
`knowledge/adr/0001-raw-jdbc.md`, `knowledge/adr/README.md`, `knowledge/templates/{domain,component,adr}.md`,
`knowledge/domains/processing/components/async-process-queue.md` (поток очереди `KaraokeProcessWorker`).

Grep'ы по `knowledge/` (состояние master `4b5f16a7`, до правки):

| # | Запрос | Результат |
|---|---|---|
| 1 | `git grep -n "closeThreadConnection" 4b5f16a7 -- knowledge/` | **1 совпадение** — `knowledge/domains/persistence/domain.md:193` (однострочное упоминание, без контракта «когда вызывать») |
| 2 | `git grep -lniE "threadlocal\|thread-local" 4b5f16a7 -- 'knowledge/*.md'` | 5 файлов: `adr/0001-raw-jdbc.md`, `adr/local-0005-structured-logging-karaoke-app.md`, `domains/catalog/components/audio-descendant-sync.md`, `domains/persistence/domain.md`, `domains/sse/domain.md` — **контракта жизненного цикла соединения нет ни в одном** |
| 3 | `git grep -niE "connection leak\|утечк.*соединен" 4b5f16a7 -- knowledge/` | 2 совпадения: `domains/stats/domain.md:156` (не наш случай), `system/infra/specs-catalog-detailed.md:42` (спека 087) |

Вывод: знаний о контракте «когда закрывать thread-local соединение» в базе не было —
правило зафиксировано в этом пакете (local-ADR-0009 + раздел в `persistence/domain.md`).

---

## 6. Что осталось за владельцем

1. **Family `withDb` в 16 контроллерах `karaoke-app`** — тот же дефект, что и BR-116, но
   вне порции P-08 и вне реестра (`TariffsController`, `SiteUsersController`,
   `SponsrSyncController`, `StatsController`, `StemJobsAdminController`,
   `SubscriptionsController`, `SitePlaylistsController`, `SongEditorController`,
   `NewsController`, `NewsTemplateController`, `PublicSettingsController`,
   `DictionariesController`, `PromoController`, `ListeningHistoryController`,
   `ShareLinksAdminController`), плюс `StorageMetadataCache.refreshAll` (`conn.close()` в
   `finally`). Файлы пересекаются с пакетами #375/#396 — правка оставлена владельцу, чтобы
   не создавать конфликт в чужих ветках. Объём механически тот же, что у BR-116.
2. **Остальные одноразовые потоки вне BR-060** — `ExportAlignmentDataset.kt:58` (ход в БД),
   `SongEditorController.kt:499`, `StorageCacheWarm.kt:59`, `CacheEtagSizeBackfill.kt:74`
   (у них свои BR в других пакетах: #364/#373/#398). В `KaraokeProcessWorker.kt:452`
   (post-hook рендера) — исправлено в этом пакете (тот же файл, что BR-038).
3. **Регрессионный тест BR-038 — инвариант, а не поведение.** `KaraokeProcessWorker.doStart()`
   в юнит-тесте не запускается: главный цикл делает живые сетевые запросы
   (`checkLastAlbumYm`) и retry с backoff до 60 с. Поэтому для BR-38 покрытие двухуровневое:
   поведенческий тест хелпера (`OneShotThreadTest`) + инвариант «в аудируемых файлах нет
   сырых `thread { }`» (`OneShotThreadWiringTest`). Если владелец хочет именно поведенческий
   тест потока очереди — нужен интеграционный тест с живой БД (отдельная задача).
4. **Без перехода на пул соединений.** Альтернатива «HikariCP вместо ThreadLocal-кеша»
   рассмотрена в local-ADR-0009 и отклонена как отдельная задача (затрагивает всю работу с
   БД в обоих модулях, нужны замеры). До её решения ThreadLocal-контракт остаётся каноническим.
5. **Трекер OpenProject.** Issue #368 остался в состоянии `New / unassigned` — claim/comment/
   mark-review/close не выполнялись (bookkeeping ведёт лид волны; `push`/`PR` запрещены задачей).

---

## 7. Коммиты

| Хэш | Сообщение |
|---|---|
| `f9f1ea5f` | `fix(persistence): закрытие thread-local JDBC-соединений (аудит #336, BR-006/027/038/060/116)` |
| `git log -2` (HEAD) | `docs(336): отчёт по PKG-RES-THREADLOCAL (OpenProject #368) + реестр 5 записей` — коммит с самим отчётом: его хэш меняется при каждом `amend` (self-reference), смотрите `git log -2` |

Коммит в `master` не делается; `push`/`PR` не выполняются (запрещено задачей).

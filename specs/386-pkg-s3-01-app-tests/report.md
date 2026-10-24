# OpenProject #386 — PKG-S3-01 (волна B): TEST/karaoke-app, 17 записей BR

- **Ветка**: `566-pkg-s3-01-app-tests` (worktree `.worktrees/waveB-app-tests`, от master `6cbaad02`)
- **Пакет**: `PKG-S3-01`, порция P-30/P-31/P-32 аудита #336
- **Класс всех записей**: TEST, severity S3 (тесты не выше S3)
- **Цикл**: короткий (без отдельной спеки — ТЗ = карточки аудита)
- **Дата**: 2026-10-24

---

## 1. Итог

| BR | Файл | Было | Стало |
|---|---|---|---|
| BR-532 | `karaoke-app/src/test/kotlin/com/svoemesto/PlaywrightTests.kt` | 5 ручных Playwright-сценариев в общем наборе `test`, `@Disabled`, `readLine()`, жёсткий `/usr/bin/yandex-browser`, права файла состояния — по umask | Файл перенесён в **новый source set `manualTest`** (`karaoke-app/src/manualTest/…`), `@Disabled` снят, `readLine()` заменён ожиданием условия с таймаутом, путь к браузеру — из system-property/env, файл состояния получает права `600` явно. Обвязка — `AuthStateFile` (`src/test`), покрыта обычным тестом `AuthStateFileTest` |
| BR-561 | `…/services/HealthReportBatchPoolTest.kt:204–224` | Тест `lastSentQueueSize zero after drain` проверял обратное (`assertEquals(1L, …)`), инвариант «0 после drain» не проверялся нигде | Тест переписан: `lastSentQueueSize обнуляется после drain` — выполняется тот же шаг, что и в worker-loop (`takeNext()` + `sendPoolCountMessage(queueSize())`), и проверяется `0` |
| BR-562 | `…/services/HealthReportBatchPoolTest.kt:38` | `private lateinit var pool` не инициализировался ни в одном тесте → `::pool.isInitialized` всегда `false` → ветка очистки **не выполнялась никогда**; неиспользуемый `realExecutor` | `private var pool: HealthReportBatchPool?` + список `createdExecutors` (оба executor'а конструктора); `tearDown` гасит `executor`, `waitingExecutor` и оба constructor-пула; `realExecutor` удалён |
| BR-563 | `…/services/StorageCircuitBreakerTest.kt:108–145` | Probe выдавался **до** старта потоков → все 20 потоков видели HALF_OPEN и всегда получали FastFail; конкурентный single-probe не проверялся | 20 потоков стартуют одновременно **из OPEN** (`startLatch`), probe заранее не выдан; ассерты `probeCount == 1`, `fastFailCount == threadCount - 1`, `state == HALF_OPEN` |
| BR-564 | `…/services/StorageCircuitBreakerTest.kt:138–139` (+ `HealthReportBatchPoolTest.kt:166–167`, `HealthReportWaitingPoolTest.kt:136–137`) | `shutdownNow()/shutdown()` выполнялись **после** ассерта → при падении non-daemon-потоки оставались живыми до конца JVM | `try { … } finally { executors.shutdownNow() }` во всех трёх местах |
| BR-565 | `…/services/cleanup/DemoMp4CleanupTest.kt:60–75` | Тест «демо удаляется и когда публикация провалилась» — дословный дубль соседнего; признак исхода публикации в API не передаётся, FR-001/FR-005 не проверялись | Тест моделирует сценарий целиком: `stageClassesAfterPublish(tgSuccess=false, vkSuccess=false)` → демо удаляется, картинки (PUBLICATION_PNG) остаются |
| BR-566 | `…/services/cleanup/DemoMp4CleanupTest.kt:202–218` | Журнал (FR-012) не читался вообще: «файл удаляется, но запись не пишется» оставалось незамеченным | Тест читает TSV-журнал из `tempDir/logs`: `HEADER`, число строк, путь, размер, класс, `result == "deleted"`, утвердивший |
| BR-567 | `…/services/cleanup/ArtifactCleanupRegistryTest.kt:20–23, 35` | Жёстко зашиты числа-снимки («17 классов», «2 198 файлов») — обновление инвентаризации роняло тест без изменения поведения | Инварианты вместо снимка: у каждого класса есть вердикт/название/пояснение/матчеры (кроме `MANUAL_FILES`), каждый класс находится по своему `id`; `expectedFileCount/expectedBytes` — «> 0» вместо числа |
| BR-568 | `…/services/VkPreviewWarmupClientTest.kt:209–216` | Имя обещало «with connect error», текст ошибки не проверялся — `io:`/`timeout` дали бы тот же результат | Добавлены `assertNotNull(result.error)` и `assertTrue(result.error.startsWith("connect:"))` |
| BR-569 | `…/services/StorageTimeoutInterruptTest.kt:86–129` | Глобальный хук `Hooks.onErrorDropped` + фиксированное окно `Thread.sleep(500)`: чужая ошибка в JVM роняла тест, «опоздавшая» ошибка (после 500 мс) оставалась незамеченной | Проверка конкретного результата: прерывание действительно произошло (`wasInterrupted`), прерванная задача **дождана** завершения (`CountDownLatch`, до 15 с), из списка dropped берётся только ошибка **нашего** теста (по маркеру в сообщении) |
| BR-570 | `…/services/StorageCircuitBreakerTest.kt:310–347` (и `:209`) | Окна по стенным часам: `elapsedMs in 800..2500` (нижняя граница меньше самого таймаута, верхняя зависит от загрузки) и `Thread.sleep(4_500)` для watchdog'а | Порядок событий вместо длительности: «fallback вернулся **до** того, как loader закончил»; watchdog — ожидание состояния через Awaitility (до 20 с) вместо фиксированного сна |
| BR-587 | `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SearchLastAlbumVkTest.kt` | `@Disabled`, **ни одного assert** (только `println`), живой VK + браузер, затирание глобалей `DB_*` без восстановления | Перенесён в `src/manualTest`; глобалы восстанавливаются в `finally`; результат проверяется (`assertNotNull` + `assertTrue(!isBlank)`) |
| BR-588 | `…/AlbumCoverFinderScrapersTest.kt:75–82` | Тест читал **живой** `/sm-karaoke/system/Karaoke.properties` (и создавал его по системному пути), а утверждения («список непуст», «нет пустых токенов») верны при **любой** конфигурации | Значение настройки подставляется явно в глобальную карту свойств (с восстановлением и заглушкой, чтобы карта не была пустой и `loadPropertiesMap()` не создавал живой файл); проверка `defaultValue` вынесена в отдельное утверждение |
| BR-589 | `…/HealthReportWaitingPoolTest.kt:193–197` | Статическая ссылка `HealthReport.healthReportBatchPool` не восстанавливалась → протекала в следующие тесты JVM | Снимок/восстановление в `@BeforeEach`/`@AfterEach`; добавлен тест `attachHealthReportBatchPool не оставляет статическое состояние` (`@Order(100)`, после подмены `@Order(90)`) |
| BR-590 | `…/model/WhisperMarkerAlignerSpecTagsTest.kt:27–35` | `@BeforeAll` мутировал глобалы приложения и **не восстанавливал** их — значения жили до конца test-JVM | `AppGlobalsSnapshot` (capture/apply/restore) + `@AfterAll` c восстановлением; тест `глобальные настройки приложения восстанавливаются после подмены` фиксирует контракт восстановления |
| BR-591 | `…/KaraokeProcessCleanupTest.kt:81–105` | Проверка «файлы остались» опиралась на то, что процесс **не root** (под root `CAP_DAC_OVERRIDE` → файл удаляется → ложное падение) | Разделено: `cleanupFailsOpenOnLockedFile` (fail-open через `assertDoesNotThrow`, независим от окружения) и `cleanupKeepsFilesInReadOnlyDirectory` (под `Assumptions.assumeTrue` по **фактической** пробе, а не по имени пользователя) |
| BR-592 | `…/SanitizePathTest.kt:120–141` (+ `:143–159`) | Глобальный уровень логгера менялся на `INFO` и не возвращался — терялся для всех последующих тестов JVM | `withLoggerLevel(...)` — подмена с восстановлением в `finally` в обоих тестах; тест `уровень логгера восстанавливается после проверок FR-014` фиксирует контракт |

**Изменённые/созданные файлы** (13 файлов пакета + 3 служебных):

```
M karaoke-app/build.gradle.kts                                  (новый source set manualTest + задача manualTest)
R karaoke-app/src/test/kotlin/com/svoemesto/PlaywrightTests.kt
  → karaoke-app/src/manualTest/kotlin/com/svoemesto/PlaywrightTests.kt
R karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SearchLastAlbumVkTest.kt
  → karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/SearchLastAlbumVkTest.kt
A karaoke-app/src/manualTest/kotlin/com/svoemesto/PlaywrightTests.kt
A karaoke-app/src/manualTest/kotlin/com/svoemesto/karaokeapp/SearchLastAlbumVkTest.kt
A karaoke-app/src/test/kotlin/com/svoemesto/manual/AuthStateFile.kt
A karaoke-app/src/test/kotlin/com/svoemesto/manual/AuthStateFileTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/AlbumCoverFinderScrapersTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/HealthReportWaitingPoolTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/KaraokeProcessCleanupTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/SanitizePathTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/model/WhisperMarkerAlignerSpecTagsTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/HealthReportBatchPoolTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageCircuitBreakerTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/StorageTimeoutInterruptTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/VkPreviewWarmupClientTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/ArtifactCleanupRegistryTest.kt
M karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/services/cleanup/DemoMp4CleanupTest.kt
M specs/336-code-audit/registry.csv                              (status: TODO → исправлено, 17 записей)
M knowledge/guidelines/code-style.md                             (конвенция: ручные сценарии → source set manualTest)
M DEVELOPMENT.md                                                 (как запускать ручные сценарии)
```

**Кода приложения не менялось** — все правки в `src/test`, `src/manualTest` и `build.gradle.kts`
(исключение — добавление source set'а, затребованное карточкой BR-532).

---

## 2. Проверка «до/после»

Метод: ломаем прод-поведение (минимальный патч) либо снимаем восстановление состояния —
и смотрим, что **новый** тест падает. Откат — копированием файла (git checkout не применялся:
иначе задеты незакоммиченные правки тестов).

Команда во всех случаях:

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test --tests "<класс>" --rerun
```

### BR-532 — права файла состояния авторизации

Патч: `AuthStateFile.setOwnerOnlyPermissions` выставляет `rw` + group/others read вместо `600`.

```
AuthStateFileTest > файл состояния авторизации создаётся с правами только владельца() FAILED
    org.opentest4j.AssertionFailedError at AuthStateFileTest.kt:32
5 tests completed, 1 failed
BUILD FAILED
```

Плюс — ручные сценарии больше не в общем наборе:

```
$ python3 - <<'EOF'  # сводка по build/test-results/test
manual classes in test set: []
EOF
```

### BR-561 — счётчик SSE-бейджа не обнуляется

Патч: `HealthReportBatchPool.sendPoolCountMessage` не обрабатывает `count == 0`.

```
HealthReportBatchPoolTest > lastSentQueueSize обнуляется после drain() FAILED
    org.opentest4j.AssertionFailedError at HealthReportBatchPoolTest.kt:240
13 tests completed, 1 failed
BUILD FAILED
```

Прежний тест с тем же патчем оставался зелёным: он проверял `assertEquals(1L, …)`.

### BR-562 — очистка ресурсов в `tearDown`

Пробник (временный `@AfterAll`, проверяет `waitingExecutor.isShutdown`):

```
# с текущим (новым) кодом
BUILD SUCCESSFUL
# со «старой» схемой (lateinit-поле не инициализировалось → очистки нет)
HealthReportBatchPoolTest > executionError FAILED
    org.opentest4j.AssertionFailedError at HealthReportBatchPoolTest.kt:-1
14 tests completed, 1 failed
BUILD FAILED
```

### BR-563 — конкурентный single-probe

Патч: атомарный `compareAndSet(OPEN, HALF_OPEN)` заменён на неатомарную
проверку+установку (`state.get() == OPEN` → `Thread.sleep(50)` → `state.set(HALF_OPEN)`),
т. е. все потоки успевают прочитать OPEN и каждый получает probe.

```
StorageCircuitBreakerTest > concurrent acquire allows only one probe() FAILED
    org.opentest4j.AssertionFailedError at StorageCircuitBreakerTest.kt:149
16 tests completed, 1 failed
BUILD FAILED
```

Прежний тест с тем же патчем оставался зелёным: probe выдавался заранее, состояние
уже было HALF_OPEN, и все 20 потоков закономерно получали FastFail
(`probeCount == 0`, `fastFailCount == 20`).

### BR-564 — утечка потоков при падении ассерта

Пробник (временный тест, не коммитится) — сравнивает старую и новую схемы:

```
ZZThreadPoolLeakProbeTest > старая схема оставляет пул живым, новая — гасит() — PASS
BUILD SUCCESSFUL
```

(старая схема: `shutdown` после ассерта → `isShutdown == false`; новая: `finally` → `isShutdown == true`).

### BR-565 — картинки чистятся при провале публикации

Патч: `stageClassesAfterPublish` добавляет `PUBLICATION_PNG` всегда.

```
DemoMp4CleanupTest > демо удаляется, а картинки остаются, когда публикация провалилась() FAILED
    org.opentest4j.AssertionFailedError at DemoMp4CleanupTest.kt:82
DemoMp4CleanupTest > картинки удаляются только при успехе обеих площадок() FAILED
10 tests completed, 2 failed
BUILD FAILED
```

Прежний (дублирующий) тест с тем же патчем оставался зелёным.

### BR-566 — удаление не фиксируется в журнале

Патч: `journal.appendDeleted(...)` закомментирован.

```
DemoMp4CleanupTest > картинки удаляются и попадают в журнал при двойном успехе() FAILED
DemoMp4CleanupTest > удаление демо фиксируется в журнале() FAILED
10 tests completed, 2 failed
BUILD FAILED
```

### BR-567 — класс реестра объявлен неполно

Патч: у `MANUAL_FILES` пустой набор вердиктов.

```
ArtifactCleanupRegistryTest > каждый класс реестра объявлен полностью() FAILED
    org.opentest4j.AssertionFailedError at ArtifactCleanupRegistryTest.kt:26
14 tests completed, 2 failed
BUILD FAILED
```

### BR-568 — классификация сетевой ошибки

Патч: ветка `ConnectException` отдаёт `"io: …"` вместо `"connect: …"`.

```
VkPreviewWarmupClientTest > connect to closed port returns FAILED with connect error() FAILED
    org.opentest4j.AssertionFailedError at VkPreviewWarmupClientTest.kt:221
12 tests completed, 1 failed
BUILD FAILED
```

### BR-569 — «опоздавшая» dropped-ошибка

Патч: `runBlockingMinioOrNull` пробрасывает interrupt-обёртку вместо `null`.

```
StorageTimeoutInterruptTest > decorate timeout does not produce onErrorDropped() FAILED
    org.opentest4j.AssertionFailedError at StorageTimeoutInterruptTest.kt:150
9 tests completed, 2 failed
BUILD FAILED
```

Прежний тест с тем же патчем оставался зелёным: он ждал 500 мс, а прерывание приходит
на 2-й секунде (таймаут `timeoutSeconds = 2`) — «опоздавшая» ошибка попадала в
`onErrorDropped` уже после проверки.

### BR-570 — таймаут и watchdog

Патч 1: `watchdogTick()` ничего не делает.

```
StorageCircuitBreakerTest > watchdog reopens stuck HALF_OPEN() FAILED
16 tests completed, 1 failed
BUILD FAILED
```

Патч 2: `.timeout(Duration.ofSeconds(timeoutSeconds))` убран из `decorate`.

```
StorageCircuitBreakerTest > decorate timeout returns empty() FAILED
StorageCircuitBreakerTest > decorate blocking loader times out at timeoutSeconds() FAILED
16 tests completed, 2 failed
BUILD FAILED
```

### BR-587 — ручной сценарий VK

Проверка «до/после» — по вынесению из набора `test` (см. BR-532) и по восстановлению
глобалей: сценарий теперь в `src/manualTest` и не может «затереть» конфигурацию БД
у остальных тестов JVM. Утверждения `assertNotNull(result)` / `assertTrue(result.isNotBlank)`
делают сценарий падающим при пустом результате.

### BR-588 — разбор настройки скрапперов

Патч: `parseAlbumCoverScrapers` всегда возвращает дефолт.

```
AlbumCoverFinderScrapersTest > albumCoverSearchScrapersList читает явно подставленное значение настройки() FAILED
    org.opentest4j.AssertionFailedError at AlbumCoverFinderScrapersTest.kt:82
10 tests completed, 4 failed
BUILD FAILED
```

Прежний тест (`albumCoverSearchScrapersList читает настройку без пустых токенов`) с тем же
патчем оставался зелёным: «список непуст» и «нет пустых токенов» выполняются при любой конфигурации.

### BR-589 — протекание статической ссылки

Патч: строка восстановления `HealthReport.healthReportBatchPool = globalPoolBeforeTest` закомментирована.

```
HealthReportWaitingPoolTest > attachHealthReportBatchPool не оставляет статическое состояние() FAILED
    org.opentest4j.AssertionFailedError at HealthReportWaitingPoolTest.kt:244
17 tests completed, 1 failed
BUILD FAILED
```

### BR-590 — протекание глобальных настроек

Патч: восстановление `DB_LOCAL_POSTGRES_USER` в `AppGlobalsSnapshot.restore()` снято.

```
WhisperMarkerAlignerSpecTagsTest > глобальные настройки приложения восстанавливаются после подмены() FAILED
    org.opentest4j.AssertionFailedError at WhisperMarkerAlignerSpecTagsTest.kt:68
9 tests completed, 1 failed
BUILD FAILED
```

### BR-591 — fail-open наружу

Патч: оба `catch` в `cleanupOldLogsIn` пробрасывают исключение наружу.

```
KaraokeProcessCleanupTest > cleanupFailsOpenOnLockedFile() FAILED
    org.opentest4j.AssertionFailedError at KaraokeProcessCleanupTest.kt:102
KaraokeProcessCleanupTest > cleanupKeepsFilesInReadOnlyDirectory() FAILED
5 tests completed, 2 failed
BUILD FAILED
```

### BR-592 — утечка уровня логгера

Патч: `logger.level = original` в `finally` закомментирован.

```
SanitizePathTest > уровень логгера восстанавливается после проверок FR-014() FAILED
    org.opentest4j.AssertionFailedError at SanitizePathTest.kt:178
41 tests completed, 1 failed
BUILD FAILED
```

---

## 3. Результаты проверок

| Проверка | Команда | Результат |
|---|---|---|
| Юнит-тесты | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew :karaoke-app:test` | **BUILD SUCCESSFUL**, 448 тестов, 0 failures, 0 errors, 0 skipped (было 443/0/0/**6 skipped** — 6 ручных `@Disabled` ушли в `manualTest`, +11 новых тестов) |
| Компиляция main | `:karaoke-app:compileKotlin` | код приложения не менялся; задача проходит (выполняется в составе `test`) |
| Компиляция manualTest | `:karaoke-app:compileManualTestKotlin` | **BUILD SUCCESSFUL** |
| Линтер | `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew ktlintCheck` | **BUILD SUCCESSFUL** (в т.ч. `ktlintManualTestSourceSetCheck`) |
| Knowledge-структура | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| Ссылки в доках | `python3 tools/check-doc-references.py` | `OK: битых ссылок, несуществующих путей и эндпоинтов нет.` (4102 ссылки, 2236 путей, 306 эндпоинтов) |
| Изоляция субагентов | `bash tools/check-subagent-isolation.sh` | exit 0 (предупреждение: «ветка не содержит новых commits относительно master» — до коммита) |
| SSoT-влияние | `python3 tools/check-ssot-impact.py` | exit 0 |
| Pre-commit хуки | `git commit` | проходят (см. раздел 6) |

---

## 4. Knowledge-first: что прочитано и какие grep'ы были

Прочитано: `AGENTS.md`, `knowledge/README.md`, `knowledge/domains/README.md`,
`knowledge/guidelines/code-style.md` (§ «Тесты»), ADR `local-0001-karaoke-properties-defaults.md`,
`knowledge/domains/health/components/health-report-batch-pool.md`,
`knowledge/domains/storage/components/storage-api-client.md`.

Grep'ы по `knowledge/` (результаты — в задании агента):

| # | Запрос | Результат |
|---|---|---|
| 1 | `grep -rn -i 'test' knowledge/ --include='*.md' -l` | 20 файлов (health, storage, integration, processing, karaoke-web, publishing, stats) |
| 2 | `grep -rn -iE '\b(mock\|assert)\b' knowledge/` | 0 совпадений — знаний о mock/assert в базе нет |
| 3 | `grep -rn -iE 'junit' knowledge/` | 2 совпадения: `knowledge/domains/processing/components/key-bpm-from-file.md:177`, `knowledge/guidelines/code-style.md:36` |
| 4 | `grep -rn -iE '\b(random\|seed)\b' knowledge/` | 1 совпадение (`karaoke-web/components/services-overview.md:112` — не про тесты) |
| 5 | `grep -rn -iE 'thread\.sleep\|sleep\(' knowledge/` | 3 совпадения: `health-report-batch-pool.md:129` (прод), `adr/local-0002-save-exception-handling.md:58,61` (прод) |
| 6 | `grep -rn -iE 'awaitility\|await\(' knowledge/` | **0 совпадений** — Awaitility в базе знаний не описан (использован как факт: `org.awaitility:awaitility:4.2.2` входит в `spring-boot-starter-test`, подтверждено `./gradlew :karaoke-app:dependencies --configuration testRuntimeClasspath`) |

Релевантные документы по итогам: `knowledge/guidelines/code-style.md` (§ «Тесты» — обновлён:
ручные сценарии вынесены в source set `manualTest`), `knowledge/domains/health/components/health-report-batch-pool.md`
(подменяемые executor'ы в тестах), `knowledge/domains/storage/components/storage-api-client.md`
(FSM circuit breaker, watchdog), ADR `local-0001` (дефолты `KaraokeProperties` — оттуда
взят `defaultValue` для BR-588).

---

## 5. Что осталось за владельцем

1. **BR-561 — инвариант «0 после drain» проверяется по шагу worker-loop, а не самим циклом.**
   Сам `workerLoop()` зовёт `HealthReport.recomputeAndBroadcast` (обращение в БД/MinIO), поэтому
   в unit-тесте не запускается. Тест выполняет ровно тот шаг, который обновляет счётчик.
   Если инвариант критичен — нужен интеграционный тест worker-loop (отдельная задача).
2. **BR-589 — «штатного» сброса статической ссылки в проде нет.** `attachHealthReportBatchPool(pool)`
   принимает не-null (`HealthReport.kt:148`), сброса «в null» в API нет; в тесте значение
   ставится напрямую (`HealthReport.healthReportBatchPool = …`). Если владелец хочет
   симметричный API — нужен отдельный метод (например, `detachHealthReportBatchPool()`).
3. **BR-590 — `lateinit`-глобалы приложения нельзя «расинициализировать».** Если до класса
   они не были инициализированы, после класса останутся тестовые значения (`"test"`/false).
   Это не хуже прежнего поведения (раньше они оставались подменёнными всегда), но полное
   восстановление требует смены модели (например, `Config`-объект вместо глобалей).
4. **BR-532 — окно в правах файла состояния.** Playwright `storageState()` сам создаёт файл
   (права по umask), и `chmod 600` применяется сразу после — между созданием и chmod файл
   доступен по umask хоста. Если это неприемлемо: писать во временный файл с правами 600 и
   переименовывать (atomic move), либо менять umask процесса.
5. **BR-532 — задача `manualTest` вне `check`/`build` и вне CI.** Сценарии не запускаются
   автоматически; нужен регламент ручного прогона (у кого, с каким окружением).
6. **BR-591 — под root проверка «файлы остались» пропускается** (`Assumptions.assumeTrue`).
   В CI-образе под root она не выполнится; если проверка обязательна — нужен прогон от
   не-root пользователя (или эмуляция запрета удаления без reliance на права ФС).
7. **BR-567 — сверка реестра с `data-model.md` автоматизировать нельзя** (документ, не код).
   Сейчас проверяется полнота объявления каждого класса; расхождение с документом
   останется незамеченным — это вопрос к владельцу инвентаризации.

---

## 6. Коммиты

| Хэш | Сообщение |
|---|---|
| `e779ed58f8ada3bd8708a532175c2d69dcb135c7` | `test(karaoke-app): TEST-подсиление 17 записей BR-532, BR-561..BR-570, BR-587..BR-592 (PKG-S3-01, OpenProject #386)` |

Коммит в `master` не делается; `push`/`PR` не выполняются (запрещено задачей).

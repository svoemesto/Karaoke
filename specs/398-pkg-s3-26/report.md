# PKG-S3-26 (OpenProject #398) — karaoke-app: DATA, ERR, PERF, RACE, RULE, SEC — 19 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-26` (`specs/336-code-audit/backlog.md`, блок `### PKG-S3-26`).
- **Ветка**: `626-pkg-s3-26` от `origin/master` `d5fbab3f` (merge PR #722, реестр волны E).
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-26`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-01.md`, `P-02.md`, `P-03.md`,
  `P-07b.md`, `P-29.md`, `P-34.md`; порции определены по `registry.csv` (`work_package = 398`).
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод, БД и
  `specs/336-code-audit/registry.csv` не трогались; деплоя не было; push/PR не делались.
- **Режим**: пакет собран одним проходом (19 записей), регрессия — двумя классами тестов.

## Сводка

| Блок | BR | Статус |
|---|---|---|
| Health-отчёты и ремонт | BR-280…BR-287 (8) | ✅ исправлены |
| Настройки, словари, крипта | BR-276, BR-289, BR-290, BR-291, BR-292, BR-294 (6) | ✅ исправлены |
| Хранилище, обложки, гигиена | BR-288, BR-293, BR-526, BR-531 (4) | ✅ исправлены |
| — | закрытых ранее чужими пакетами записей нет | — |

**Все 19 записей пакета закрыты.** Ни одна запись не оказалась «уже исправлена другим
пакетом»: фактическое состояние кода проверено по каждой строке реестра до правки
(см. «Проверка фактического состояния»).

---

## Проверка фактического состояния (до правки)

Для каждой из 19 записей прочитан актуальный код (номера строк в бэклоге — от снимка
аудита `1977838a`; ниже — строки текущей ветки). Ни одна запись не была помечена
«исправлено ранее»: все 19 дефектов присутствовали в коде.

| BR | Где смотрел | Что было в коде на начало пакета |
|---|---|---|
| BR-276 | `Karaoke.kt` (сеттер `voices`) | `KaraokeProperties.set("flashColor", getStringFromVoices(value))` — копипаста |
| BR-277 | `Utils.kt` (`actionToDeleteFileAndFolderIfFolderEmpty`) | `File(pathToFile).exists()` + `rm -f` |
| BR-280 | `HealthReport.kt` (`actionsLocalStorage`/`actionsRemoteStorage`/`actionsLocalFileSystem`) | по одному `KaraokeProcess.loadList(...)` на пару «тип × локация» |
| BR-281 | `HealthReport.kt` (`cachedFileInfo`) | `catch (e: Exception) { null }` без лога; вызывающий `localInfo == null || …` |
| BR-282 | `HealthReport.kt` (`startRepairAll`) | `recomputeAndBroadcast` до `submit` и вне `try` |
| BR-283 | `HealthReport.kt` (`actionsRemoteStorage`) | `catch (e: Exception) { println("Ошибка при удалении …") }` (2 места) |
| BR-284 | `HealthReport.kt` (`executeResolvable`, `repairExecutor.submit`) | без `catch`; в задаче пула только `finally` |
| BR-285 | `HealthReport.kt` + `ApiController.kt` (`executeHealthReportActions`) | `?.executeSolutionActions()` в потоке Tomcat |
| BR-286 | `HealthReport.kt` (`symlinkFolderReports`) | `println(...)` + `return@forEach` |
| BR-287 | `HealthReport.kt` (`recalculatePlayerReadiness`) | `var failed = 0` … `return total` |
| BR-288 | `CacheEtagSizeBackfill.kt` (`loadRowsNeedingBackfill`) | `catch → return result` (пустой список) и `?: return result` при null-соединении |
| BR-289 | `Extentions.kt` (`String.censored`) | `"…$uncensored…".toRegex()` без `Regex.escape` |
| BR-290 | `Functions.kt` (`getVoices`) | `tmpLines.maxOf { it.lineDurationMs() }` → деление на 0 |
| BR-291 | `Crypto.kt` | `ex.printStackTrace()` + `return null` в обеих операциях; длины ключа/IV не проверяются |
| BR-292 | `Converter.kt` (`getVoicesFromString`) | `partsVoiceFields[1]`, `parts[0]`/`parts[1]` без проверки; `Color(...)` вне `try` |
| BR-293 | `AlbumCoverFinder.kt` (`findYandexAlbumCovers`) | `fetchYandexArtistAlbumsHtml(...)` без `try` |
| BR-294 | `Extentions.kt` + `Utils.kt` (`rescanAllCensoredNames`) | `CensoredWordsDictionary(...).dict` + `toRegex()` на каждый вызов, в цикле по песням |
| BR-526 | `karaoke-app/src/main/resources/test.xml` | файл на месте (463 строки, ПДн-подобные поля), потребителей 0 |
| BR-531 | `karaoke-app/src/test/kotlin/Правильный.xml` (+ зеркала `Неправильный.xml`, `1`, `2.xml`) | файлы на месте (1 887 + 1 975 + 1 916 + 1 359 строк), потребителей 0 |

---

## По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-276 | `karaoke-app/.../Karaoke.kt:664-668` | DATA | Сеттер `Karaoke.voices` писал строку голосов в ключ **`flashColor`** (копипаста с соседнего свойства): голоса не сохранялись (геттер читал прежнее значение), а настройка цвета флеша затиралась нецветовой строкой (`getColorFromString` вернул бы белый и напечатал предупреждение) | Ключ **`"voices"`**; добавлен round-trip тест «запись → чтение» (`Converter.getStringFromVoices` → `getVoicesFromString`) | контрактная (1) + поведенческая (1) |
| BR-277 | `karaoke-app/.../Utils.kt:4828-4853` | ERR | `actionToDeleteFileAndFolderIfFolderEmpty` определял существование через `File(path).exists()`; для **битого** симлинка это `false` (Java следует по ссылке) → действие «Удалить файл» для проблемы «Битая символическая ссылка» (`HealthReport.symlinkFolderReports`, `canResolve=true`) было no-op: ссылка оставалась, проблема возвращалась на каждом прогоне | Existence = `File.exists() \|\| Files.isSymbolicLink(path)`; удаление — `Files.deleteIfExists(path)` (единственный корректный способ для битой ссылки; `rm -f` заменён) | контрактная (1) + поведенческая (1) |
| BR-280 | `karaoke-app/.../HealthReport.kt:770-781` (LOCAL), `:1180-1192` (REMOTE), `:489-491`/`:505-507` (LOCAL_FILESYSTEM) | PERF | На каждую пару «тип файла × локация» — свой `KaraokeProcess.loadList` (`SELECT tbl_processes.*`): ~18 запросов на песню, тысячи на страницу «Песни» (ADR-0001: пакетная загрузка, не по одной) | Задания песни читаются **одним** `loadList(args = mapOf("song_id" to …))` в `getHealthReportList` и раздаются в `actions*` параметром `songProcesses`; отбор — `filterSongProcesses(...)` в памяти (те же правила: `song_id`, `process_status`, `process_type`, `thread_id`, `process_args LIKE`) | контрактная (1) + поведенческая (1) |
| BR-281 | `karaoke-app/.../HealthReport.kt:196-211` (`cachedFileInfo`), `:800-805` (LOCAL), `:1207-1212` (REMOTE) | ERR | `cachedFileInfo` ловил любое исключение и возвращал `null` **без лога**; `null` означал сразу три состояния (нет строки кеша / «объекта нет» (`size<0`) / «вызов не удался»), а вызывающий трактовал `null` как «файл актуален» (`localInfo == null \|\| …`) → при недоступном MinIO отчёт был `OK` | Типизированный `StorageFileInfoProbe` (`Known` / `Missing` / `Unknown`) + вердикт `fileActualityVerdict` → `FileActualityVerdict` (`ACTUAL` / `NOT_ACTUAL` / `DISK_VANISHED` / `UNKNOWN`). «Не удалось узнать» → `WARNING` + `storageStateUnknown = true` (флаги готовности не сбрасываются), `WARN` с throwable в `catch` | контрактная (1) + поведенческая (1) |
| BR-282 | `karaoke-app/.../HealthReport.kt:2814-2816` | RACE | `startRepairAll` брал guard (`attemptEnterRepair`), затем **до** `repairExecutor.submit` вызывал `recomputeAndBroadcast` вне `try`. Исключение (сбой БД в `saveToDbLocked`, ошибка `SNS.send`) оставляло `repairInFlight[songId] = true` навсегда → «Исправить всё» и каскад для песни молча умирали до рестарта приложения | Guard берётся и снимается в `try/catch/finally` вокруг **всего** тела (включая синхронный `recomputeAndBroadcast` и `submit`) | контрактная (1) |
| BR-283 | `karaoke-app/.../HealthReport.kt:1229-1237`, `:1442-1451` | ERR | Ошибка удаления объекта из REMOTE гасилась `catch { println(…) }`: лямбда завершалась «нормально», остальные действия выполнялись, UI показывал «исправлено», а объект оставался в MinIO | `deleteRemoteFileOrThrow`: SLF4J `ERROR` (`infra.health.report`) с throwable **и** проброс исключения в repair-цикл, где действие помечается неуспешным | контрактная (1) + поведенческая (1) |
| BR-284 | `karaoke-app/.../HealthReport.kt:2791-2795` (`executeResolvable`), `:2820-2830` (`repairExecutor.submit`) | ERR | Лямбды выполнялись подряд без обработки исключений: исключение одного действия обрывало **весь** каскад (остальные не выполнялись, финальный `recomputeAndBroadcast` и SSE не уходили, пользователь не получал ни ошибки, ни обновления); в задаче пула был только `finally` | Каждое действие — в своём `try/catch` (лог с throwable); `executeResolvable` возвращает число неуспешных действий; в задаче пула — `catch` с логом и SSE `type=error` (`reportRepairFailure`) | контрактная (1) + поведенческая (1) |
| BR-285 | `karaoke-app/.../HealthReport.kt:944-951`, `1335-1341` (в `solutionActions`) + `controllers/ApiController.kt:8357-8381` | RULE | `solutionActions` содержат блокирующие операции MinIO (`downloadFile` — десятки МБ, `.block()`), а `POST /api/song/executeHealthReportActions` выполнял их **синхронно в потоке Tomcat** — нарушение инварианта storage-домена № 4 («MinIO-операции НЕ выполняются в HTTP-handler-треде») | Действие уходит в `repairExecutor` (`HealthReport.executeHealthReportActionsAsync`), эндпоинт отвечает **`202 Accepted`**, фактический результат — по SSE (`HEALTH_REPORTS` + сообщение об ошибке) | контрактная (1) |
| BR-286 | `karaoke-app/.../HealthReport.kt:2529-2535` (`symlinkFolderReports`) | ERR | Ошибка чтения каталога симлинков гасилась `println` + `return@forEach`: каталог выпадал из аудита, отчёт выглядел здоровым, `OK` был неотличим от «не проверили» | `WARNING`-запись без действия («Каталог ссылок не прочитан: …») + SLF4J `WARN` с throwable (`infra.health.report`) | контрактная (1) |
| BR-287 | `karaoke-app/.../HealthReport.kt:2677-2699` + `controllers/ApiController.kt:6052-6068` | ERR | `recalculatePlayerReadiness` считал `failed`, но возвращал `total` → SSE всегда показывал «Проверено песен: N», даже если сотни песен упали; причина — только `println` без стека | Возвращается `ReadinessRecalcResult(processed, failed)`; SSE: при `failed > 0` — `type=error` с обоими числами; ошибка логируется через SLF4J с throwable | контрактная (1) + поведенческая (1) |
| BR-288 | `karaoke-app/.../CacheEtagSizeBackfill.kt:169-192` | ERR | Ошибка выборки из БД (и `null`-соединение) превращалась в пустой список → оператор видел SSE `info` «Обработано 0 строк … завершено» вместо ошибки; «пропуски» в подсказке не появлялись, потому что `skipped == 0` | `loadRowsNeedingBackfill` **пробрасывает** сбой (`SQLException` при отсутствии соединения, исключение БД) — вызывающий шлёт SSE `error` и не печатает «завершено» | контрактная (1) |
| BR-289 | `karaoke-app/.../Extentions.kt:337-340` (`String.censored`) | ERR | Значение словаря «Censored» подставлялось в regex **как есть**: `х*й` превращалось в шаблон и маскировало «й» в любом слове (искажённый `song_name_censored` уходил в БД), а значение с незакрытой скобкой роняло `toRegex()` (`PatternSyntaxException`) → падало сохранение песни (`Song.saveToDb`) и массовое пересканирование | `Regex.escape` при сборке шаблона; пустые и неразбираемые значения отбрасываются с записью в лог; правила собираются один раз (`CensoredDictionaryRules.buildRules`) | контрактная (1) + поведенческая (3) |
| BR-290 | `karaoke-app/.../Functions.kt:243-246`, `:284`, `:316` | ERR | `maxDuration` считался по **всем** линиям, включая нулевые (`newLine`/`emptyLine` и комментарии дают `lineStartMs == lineEndMs`); голос без единой непустой по времени линии → `maxDuration == 0` → `ArithmeticException: / by zero` (создание проекта падало: 500 / `ERROR` в очереди) | Максимум считается только по линиям с **положительной** длительностью; деление выполняется только при `maxDuration > 0` (иначе пустые строки не вставляются) | контрактная (1) + поведенческая (1) |
| BR-291 | `karaoke-app/.../Crypto.kt:83-86`, `:97-99` | ERR | Обе операции при любом исключении делали `ex.printStackTrace()` в stdout и возвращали `null`: ни логгера, ни контекста, ни признака ошибки у вызывающего (`null` неотличим от «значения нет»); длина ключа/IV не проверялась → неверный `CRYPTO_AES_KEY` тихо превращал шифрование в `null`, а вызывающий отправлял в удалённую БД **пустую строку** | SLF4J `log.error("crypto: encrypt/decrypt failed op=…")` с throwable (без значений и ключей); `validateKeyMaterial` (ключ 16/24/32 байта, IV 16 байт) бросает `CryptoConfigurationException`; проверка конфигурации — при старте (`KaraokeAppService.init`, категория `infra.crypto.config`); операции при неверной конфигурации **отказываются** работать (fail-closed) | контрактная (1) + поведенческая (1) |
| BR-292 | `karaoke-app/.../Converter.kt:30-32`, `:41-42`, `:71-72`, `:171-173` | ERR | Парсер конфигурационной строки `voices` индексировал результат `split` без проверки размера (`partsVoiceFields[1]`, `parts[0]`/`parts[1]`) — `IndexOutOfBoundsException` **вне** `try`; `Color(...)`/`Font(...)` строились вне `try` — значение из БД вида `r=300` давало `IllegalArgumentException`. Настройка `voices` записывается через `POST /api/properties/setproperty` без валидации | После `split` — `getOrNull` (нет поля → дефолт + лог `infra.converter`); `Color`/`Font` строятся внутри `try` с fallback (`safeColor`) | контрактная (1) + поведенческая (2) |
| BR-293 | `karaoke-app/.../AlbumCoverFinder.kt:213` (`findYandexAlbumCovers`) | ERR | Яндекс-ветка не имела границы ошибок: `Playwright.create()` / `launchPersistentContext(USER_DATA_DIR)` / `page.navigate(...)` бросали, исключение проходило в контроллер → 500; фолбэк (SearXNG/fourget) выполнялся только для `NotFound`, т.е. при любом сбое поиск обложки был невозможен | Яндекс-ветка обёрнута в `try/catch`: сбой логируется (`infra.cover`) и даёт `AlbumCoverSearchOutcome.NotFound` — фолбэк работает при любом сбое | контрактная (1) |
| BR-294 | `karaoke-app/.../Extentions.kt:235-251`, `Utils.kt:586` (`rescanAllCensoredNames`) | PERF | `censored()` на каждый вызов заново читал весь словарь из БД (`SELECT` по `tbl_dictionaries`) и компилировал по два regex на каждое слово; в пакетном `rescanAllCensoredNames` вызов шёл в цикле по всем песням → N SELECT'ов и N×M компиляций (на 18k песен — десятки тысяч лишних запросов) | Словарь читается **один раз на пакет** (`CensoredDictionaryRules.load`), шаблоны компилируются один раз (`buildRules`), в цикле применяются готовые правила (`text.censored(rules)`) | контрактная (1) + поведенческая (1) |
| BR-526 | `karaoke-app/src/main/resources/test.xml:1-463` | SEC | В `src/main/resources` (боевой classpath) лежал образец запроса ЕГРН из чужого проекта с ПДн-подобными полями (ФИО, телефон, e-mail, СНИЛС, адрес с квартирой). Потребителя не было (0 вхождений «test.xml» в коде/конфигах/Dockerfile), но файл упаковывался в `BOOT-INF/classes/` каждого боевого jar и лежал в публичном репозитории; guard'а на такие фикстуры нет | Файл **удалён**; регрессионный тест ищет в `src/main/resources` любые XML/JSON с полями ПДн (`snils`, `phoneNumber`, `passport`, `EGRNRequest`) | поведенческая (1) |
| BR-531 | `karaoke-app/src/test/kotlin/Правильный.xml:1-1887` (+ зеркала `Неправильный.xml`, `1`, `2.xml`) | SEC | В каталоге тестовых исходников лежали реальные MLT-проекты с абсолютными путями `~/Documents/Караоке/…` (пути к stem-файлам, `done_projects`, название песни автора) — внутренние данные автора в публичном репозитории и в classpath тестов; потребителей нет | Файлы **удалены** (все четыре — один корень «личные пути в репозитории», лид аудита объединил локации в BR-531); регрессионный тест ищет в `src/test` любые фикстуры с `~/Documents/`, `/home/`, `C:\Users\` | поведенческая (1) |


---

## «До / после»: команда и вывод

Проверка «до» — регрессионные тесты на **старом** коде: изменённые исходники откачены
через `git stash push -- karaoke-app/src/main karaoke-web/src/main` (тесты остаются), после
чего тесты прогнаны.

### Контрактные проверки (компилируются и на старом коде)

Класс `PkgS326SourceContractsTest` **не ссылается на новые API** — только на текст
исходников, поэтому компилируется и на старом коде и служит настоящим доказательством
«до/после».

```bash
git stash push -m "s3-26-before-check" -- karaoke-app/src/main karaoke-web/src/main
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.PkgS326SourceContractsTest'
git stash pop
```

Вывод «до» (16 тестов, **16 упали** — каждая запись имеет наблюдаемый «до»):

```
PkgS326SourceContractsTest > BR-276 сеттер voices пишет в ключ voices а не flashColor() FAILED
PkgS326SourceContractsTest > BR-280 задания песни загружаются одним запросом а не по одному на тип файла() FAILED
PkgS326SourceContractsTest > BR-281 неизвестность состояния хранилища больше не значит файл актуален() FAILED
PkgS326SourceContractsTest > BR-282 guard ремонта снимается даже если пересчёт упал() FAILED
PkgS326SourceContractsTest > BR-283 ошибка удаления из удалённого хранилища не гасится() FAILED
PkgS326SourceContractsTest > BR-284 каждое действие ремонта выполняется в своём try-catch() FAILED
PkgS326SourceContractsTest > BR-285 одиночное действие отчёта не выполняется в HTTP-потоке() FAILED
PkgS326SourceContractsTest > BR-286 непрчитанный каталог ссылок даёт WARNING а не тихий пропуск() FAILED
PkgS326SourceContractsTest > BR-287 пересчёт готовности возвращает и число ошибок() FAILED
PkgS326SourceContractsTest > BR-288 ошибка выборки строк кеша не выглядит как пустой успех() FAILED
PkgS326SourceContractsTest > BR-289 значение словаря экранируется перед подстановкой в шаблон() FAILED
PkgS326SourceContractsTest > BR-290 деление на максимальную длительность защищено от нуля() FAILED
PkgS326SourceContractsTest > BR-291 ошибки крипты логируются через SLF4J а не в stdout() FAILED
PkgS326SourceContractsTest > BR-292 разбор настроек голосов проверяет размер после split() FAILED
PkgS326SourceContractsTest > BR-293 сбой Яндекс-ветки приводит к NotFound а не к 500() FAILED
PkgS326SourceContractsTest > BR-294 пакетное пересканирование читает словарь один раз() FAILED

16 tests completed, 16 failed
```

### Поведенческие проверки

Класс `PkgS326BehaviorTest` проверяет поведение там, где это возможно без БД/браузера.
Для записей, чья правка ввела новый API (`StorageFileInfoProbe`, `CensoredDictionaryRules`,
`CryptoConfigurationException`, `ReadinessRecalcResult`), класс **не компилируется** на
старом коде — поэтому «до» для них проверено точечно: откатывался ровно тот файл,
который менялся, и запускался соответствующий тест.

| BR | Как проверено «до» | Вывод «до» |
|---|---|---|
| BR-277 | откат `Utils.kt` | `BR-277 битая символическая ссылка удаляется действием repair() FAILED` |
| BR-290 | откат `Functions.kt` | `BR-290 голос без непустых по времени линий не роняет создание проекта() FAILED` (ArithmeticException) |
| BR-292 | откат `Converter.kt` | `BR-292 усечённая настройка голосов не роняет разбор() FAILED` + `BR-292 некорректный цвет из настройки не роняет разбор() FAILED` |
| BR-526 | возврат `src/main/resources/test.xml` | `BR-526 в prod-resources нет чужих XML-фикстур с полями ПДн() FAILED` |
| BR-531 | возврат `Правильный.xml`/`Неправильный.xml`/`1`/`2.xml` | `BR-531 в репозитории нет тестовых MLT-фикстур с локальными путями() FAILED` |
| BR-276, BR-280, BR-281, BR-283, BR-284, BR-287, BR-289, BR-291, BR-294 | контрактные проверки выше (они и есть «до» для этих записей) | 16/16 FAILED |

Вывод «после» (обе части):

```bash
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.PkgS326*'
```

```
BUILD SUCCESSFUL in 30s     # 33 теста, 0 падений (17 поведенческих + 16 контрактных)
```

### Почему для части записей выбран статический guard

Обоснование (требование ТЗ «для S3-мелочей допустим статический guard, если поведенческий
тест невозможен»):

| BR | Почему поведенческий тест невозможен в юнит-контуре |
|---|---|
| BR-282 | Нужен `Song` из БД и искусственный сбой `recomputeAndBroadcast` (сбой БД в `saveToDbLocked` / ошибка `SNS.send`) — воспроизводится только в интеграционном окружении с живой БД |
| BR-285 | Требуется замер времени потока Tomcat и реальный `downloadFile` из MinIO — то есть развёрнутый стенд |
| BR-286 | Требуется каталог симлинков с отозванными правами (`AccessDeniedException`) — поведение ФС под конкретными uid/gid не воспроизводится в юнит-тесте |
| BR-288 | Требуется недоступная БД в момент выборки (pool исчерпан / рестарт `karaoke-db`) |
| BR-293 | Требуется Chromium/Playwright и заблокированный persistent-профиль Яндекс.Музыки |

Для этих пяти записей контрактная проверка фиксирует ровно то свойство, которое было
сломано (обязательный `try/catch`/`finally`, запрет `println` в `catch`, типизированный
результат, `202 Accepted` вместо синхронного выполнения).

---

## Побочные эффекты (что меняется для пользователя и для системы)

1. **Контракт `POST /api/song/executeHealthReportActions` изменился**: было `200` с пустым
   телом и синхронным выполнением действий, стало `202 Accepted` + выполнение в фоне.
   Клиент (`webvue3/src/components/Common/HealthReport/store.js`) вызывает действие и
   сразу перезапрашивает список — теперь список может прийти **до** применения действия;
   актуальное состояние придёт по SSE `HEALTH_REPORTS` (обработчик в `webvue3/src/App.vue`
   уже есть и обновляет стор). Отдельного изменения фронта не потребовалось, но это
   **поведенческое изменение тайминга в UI** — отмечено здесь и в «Известных ловушках».
2. **`GET`/`POST /api/utils/recalcplayerreadiness`** теперь может вернуть SSE
   `type=error` (а не `info`) — если часть песен не пересчиталась. Фронт показывает
   сообщение об ошибке, но само число «проверено» корректно разделено.
3. **«Объекта нет» в хранилище теперь означает «неактуально»** (delete + upload), а не
   «всё хорошо». Это может породить одноразовую перезаливку объекта, который был удалён
   из MinIO вручную, — это и есть правильное поведение (BR-281), но стоит знать при
   анализе журнала ремонта.
4. **Частые `downloadFile` из health-report больше не занимают поток Tomcat** — при
   параллельных кликах админка больше не получит исчерпание пула запросов.
5. **Сбор отчёта по песне стал дешевле**: ~18 SQL-запросов → 1 на песню (BR-280).
6. **Значения словаря «Censored» с метасимволами теперь ищутся буквально.** Если в
   словаре лежало значение-регулярное выражение (например, `х*й` в смысле «х, хх, ххх…»),
   после правки оно маскирует **ровно** «х*й». Проверить словарь в проде перед
   использованием — за владельцем.
7. **Зашифрованные значения перестают «тихо превращаться в пустую строку»**: при неверной
   длине `CRYPTO_AES_KEY`/`CRYPTO_AES_IV` операции синхронизации теперь падают с
   `CryptoConfigurationException` (раньше — уходила пустая строка, и удалённая сторона
   отвечала «не удалось расшифровать»).
8. **Крипта проверяется при старте приложения** (`KaraokeAppService.init`): при неверной
   длине ключа/IV в лог уходит `ERROR` (`infra.crypto.config`), приложение **стартует**,
   но операции шифрования отказываются работать. Полный отказ при старте (fail-fast)
   сознательно не сделан — см. «За владельцем».

---

## За владельцем

1. **Полный отказ приложения на старте при неверной конфигурации крипты (fail-fast).**
   Сейчас — «стартует, но операции отказываются» (fail-closed на уровне операции).
   Fail-fast (не поднимать контейнер) не сделан: опечатка в env превратилась бы в
   неподнимающийся контейнер. Решение — за владельцем.
2. **Словарь «Censored» в проде.** После BR-289 значения с метасимволами ищутся буквально.
   Нужна проверка словаря (и пересканирование названий
   `POST /api/utils/rescanallcensorednames`) — за владельцем (данные не трогал).
3. **Миграция `CRYPTO_AES_KEY` / `CRYPTO_AES_IV` с legacy-значений** (в `deploy/` эти
   переменные не заданы — используются захардкоженные legacy-ключ и IV). BR-291 этого не
   менял (это отдельный корень BR-011) — за владельцем.
4. **UI-тайминг одиночного «выполнить действие»** (п. 1 «Побочные эффекты»): если
   владелец хочет, чтобы список отчётов обновлялся строго после применения действия,
   нужен явный `status=applied` в SSE — сейчас обновление приходит общим каналом
   `HEALTH_REPORTS`. За владельцем.
5. **Зеркало BR-288 в `ExportAlignmentDataset.kt`** (тот же корень «ошибка выборки →
   ложный успех», но файл вне пакета). Не трогал — за владельцем (или за пакетом #397,
   где лежат остальные записи `Utils.kt`/`P-07`).
6. **Очистка уже опубликованных фикстур.** Файлы `test.xml` и `Правильный.xml` удалены
   из репозитория, но они остаются в истории git и в уже собранных боевых jar-файлах.
   Публичный репозиторий `github.com/svoemesto/Karaoke` — перезапись истории **не**
   выполнялась (запрещено); решение об очистке истории — за владельцем.

---

## Проверки (CI-набор)

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=… ./gradlew --max-workers=2 … :karaoke-app:test :karaoke-web:test` | ✅ `BUILD SUCCESSFUL in 1m 47s` (оба модуля; новые тесты `PkgS326*` — 33, все зелёные) |
| `… ./gradlew … ktlintCheck` | ✅ `BUILD SUCCESSFUL in 33s` (ktlint + ktlintCheck по main/test) |
| `bash tools/check-knowledge-structure.sh` | ✅ `OK: knowledge/ structure valid (9/9 checks passed)` |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | ✅ `All documentation passes architectural style and structural checks.` |
| `python3 tools/check-doc-references.py` | ✅ 4335 ссылок / 2650 путей / 353 эндпоинта — 0 проблем (54 записи whitelist, из них 3 новые — на удалённые файлы) |
| `python3 tools/check-recordhash-consistency.py` | ✅ `OK recordhash-контракт …` |
| `bash tools/check-no-mp4-mentions.sh` | ✅ `OK: all 139 MP4 mentions are in baseline (grandfathered)` |
| `bash tools/check-feature-doc.sh docs/features/*.md` | ✅ `Все документы валидны` (включая новый `docs/features/pkg-s3-26.md`) |
| `bash tools/check-subagent-isolation.sh` | ✅ ветка `626-pkg-s3-26` в отдельном worktree `/home/nsa/Karaoke/.worktrees/waveE-s3-26` (предупреждение «не содержит новых commits относительно master» — на момент прогона коммит ещё не был сделан) |
| `bash tools/check-file-permissions.sh` | ✅ `OK: chmod с правом записи для всех нет (покрыто baseline: 100 совпадений)` |
| `bash tools/check-no-legacy-tbl-settings.sh` | ✅ `OK: 1757 файлов проверено, нарушений нет` |
| `python3 tools/check-ssot-impact.py` | ✅ `OK: all matched SSoT-impact rules satisfied` (29 изменённых файлов, 13 значимых, 0 нарушений) |
| `cd webvue3 && npm run lint` / `npm run test:unit` | ✅ eslint — 0 ошибок; `node --test` — 227 тестов, 227 pass, 0 fail |
| `cd karaoke-public && npm run lint` / `npm run test:unit` | ✅ eslint — 0 ошибок; `node --test` — 106 тестов, 106 pass, 0 fail |

Фронтенд не менялся (в пакете нет записей по `webvue3`/`karaoke-public`); проверки
прогнаны для полноты CI-набора.

---

## Знание (SSoT) и per-feature документ

- `knowledge/domains/health/components/health-report.md` — контракты BR-280…BR-288 и
  раздел «Ловушки (пакет `PKG-S3-26`)».
- `knowledge/domains/storage/components/storage-flow.md` — BR-281 («не знаю» ≠ «актуален»).
- `knowledge/domains/catalog/components/song-entity.md` — BR-289/BR-294 (правила цензурирования).
- `knowledge/domains/processing/components/two-db-sync.md` — BR-291 (конфигурация AES).
- `knowledge/domains/rendering/components/mlt-generator.md` — BR-290/BR-292 (разбор настроек, `maxDuration`).
- `knowledge/domains/integration/components/external-api-clients.md` — BR-293 (сбой источника обложек).
- `knowledge/domains/monitoring/components/log-categories.md` — новые категории
  `infra.health.report`, `infra.converter`, `infra.crypto.config`.
- `knowledge/guidelines/architecture-conventions.md` — BR-526/BR-531 (гигиена репозитория).
- `docs/features/pkg-s3-26.md` — per-feature документ (FR-009) с разделом «Известные ловушки».

---

## Хэши артефактов

Ключевые файлы правки (SHA-256, рабочее дерево после коммита `7cefdd40`):

| Файл | SHA-256 |
|---|---|
| `karaoke-app/.../HealthReport.kt` | `d7fbde772c3bccfc5450685df53b23b674584f9ea6f5df700a4fdd43da9fbd5d` |
| `karaoke-app/.../Extentions.kt` | `921d9e6c17f60dbd7e244dff3d947c3e3b57a7f60d3aa75a1673a56aa4c3856c` |
| `karaoke-app/.../Crypto.kt` | `6d720d87485d63525b6f9e5757db679a04f7b4a84e6750f9815f6a38f92e45b1` |
| `karaoke-app/.../Converter.kt` | `f8cd2ef668151dd260457b245f0e124eba04442118d96d72454a020b5cf95d1c` |
| `karaoke-app/.../Functions.kt` | `5948b41345ad916e49c091438bdc13c45c2028e5d7a7ab0a4058ce04354b096a` |
| `karaoke-app/.../AlbumCoverFinder.kt` | `61ae46a0c4ca954dbc28fced88e6aa1b981f424cc75f201319b3b843e44675a6` |
| `karaoke-app/.../CacheEtagSizeBackfill.kt` | `928f0833fa60063ff010d7f564d828814c7d9436ef51da2fc4c5425f01c97750` |
| `karaoke-app/.../Karaoke.kt` | `a550bcb0b5587418eea683cf4626f5e33a34a237c68439ae1e62b61d23106360` |
| `karaoke-app/.../Utils.kt` | `32d36a811f99a83e9f91d44f7f98093acbbeb16888d74d80390ebd6feaf58db0` |
| `karaoke-app/.../controllers/ApiController.kt` | `61fc266a46683db5cdaf7bc658c3f6904673ca1bd8a011307acc36ca467cc875` |
| `karaoke-app/.../services/KaraokeAppService.kt` | `2d705e52d1c3760a4ef40643a6fe873444f2740c6022d993e880d34973fb554c` |
| `karaoke-app/src/test/.../audit/PkgS326BehaviorTest.kt` | `11ad5be65d4662b16f824c048ab99538a9c0e5f69026340664f1cbd6b7501609` |
| `karaoke-app/src/test/.../audit/PkgS326SourceContractsTest.kt` | `57fa456a80c10949602a9ea96e579673cb0672f86a72e10a9426caf4da5d2151` |
| `docs/features/pkg-s3-26.md` | `22568c0702b739848da24f760d127b352abfbc371a126309046cda877568e0fa` |

Коммит пакета: `7cefdd40` («PKG-S3-26 (OpenProject #398): 19 записей аудита #336 в karaoke-app»),
ветка `626-pkg-s3-26` от `origin/master` `d5fbab3f`. Push и PR **не** выполнялись
(по условию задачи).

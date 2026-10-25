# PKG-S3-23 (OpenProject #395) — karaoke-app: BUILD, DATA, ERR, LEAK, PERF, RACE — 39 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-23` (`specs/336-code-audit/backlog.md`).
- **Ветка**: `615-pkg-s3-23` от `origin/master` `e42a573d`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-23`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-08.md`, `P-19.md`, `P-21.md`,
  `P-22.md`, `P-23.md`, `P-24.md`, `P-25.md`; порции определены по `registry.csv`.
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод,
  БД и `specs/336-code-audit/registry.csv` не трогались; деплоя не было; push/PR не делались.
- **Работа партиями**: по 10–13 записей, коммит после каждой партии.

## Сводка

| Партия | BR | Статус |
|---|---|---|
| 1 | BR-117…BR-122, BR-129…BR-131, BR-134…BR-137 (13) | ✅ исправлены |
| 2 | BR-144…BR-153 (10) | ✅ исправлены |
| 3 | BR-155…BR-160, BR-163…BR-166 (10) | ⏳ в работе |
| 4 | BR-167…BR-173 (6) | ⏳ в работе |

---

## Партия 1 — BR-117…BR-122, BR-129…BR-131, BR-134…BR-137 (13 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-117 | `karaoke-app/.../UtilsPictures.kt:650-655` | ERR | ошибка записи картинки ловилась `catch (e: Exception) { println(e.message) }` → функция возвращала `Unit` «как успех», endpoint отдавал SSE «прошло успешно», битый файл обнаруживался только на публикации | `writePictureOrThrow()`: SLF4J `infra.pictures` (путь + стек) + `PictureWriteException` наружу; `ImageIO.write == false` — тоже ошибка | `PkgS323Batch1BehaviorTest` (2) + контрактная (1) |
| BR-118 | `karaoke-app/.../controllers/ShareLinksAdminController.kt:145-146, 170-171` | ERR | `SQLException` → `printStackTrace()` (stdout) и **200** с пустым списком; дата фильтра подставлялась в SQL «как есть» | `shareLinksDateFilter()` — валидация ДО SQL, 400 с именем поля; ошибка БД → SLF4J `infra.admin.sharelinks` + `503 sharelinks.digest.unavailable` | `PkgS323Batch1BehaviorTest` (2) + контрактная (1) |
| BR-119 | `karaoke-app/.../controllers/NewsController.kt:64-68` | ERR | `page`/`pageSize` без clamp → отрицательный OFFSET (Postgres отвергает) и `pageSize=Int.MAX_VALUE`; `News.loadAll/countAll` глотали `SQLException` → 200 с пустым списком | clamp `page.coerceAtLeast(0)`, `pageSize.coerceIn(1, MAX_PAGE_SIZE=200)`; `News.loadAll/countAll` бросают `SQLException` с кодом `news.*.unavailable`; контроллер → 503 | контрактная (3) + поведенческая (1) |
| BR-120 | `karaoke-app/.../controllers/PromoController.kt:90-91` | ERR | `Timestamp.valueOf(...)` без `try/catch` → `IllegalArgumentException` → 500 без описания поля | `parsePromoBoundary()` — как `NewsController.parsePublishAt`, но 400 с описанием поля; пустая строка = «границу не трогать» | `PkgS323Batch1BehaviorTest` (1) |
| BR-121 | `karaoke-app/.../YandexLyricsSearchOutcome.kt:59-75` | PERF | один поиск запускал Chromium ДВАЖДЫ («прогрев» + поиск); параллельный доступ к `USER_DATA_DIR` → падение из-за блокировки профиля Chromium | один контекст на операцию (прогрев сохранён в нём же); общий `YANDEX_PROFILE_LOCK` (`ReentrantLock`) для всех автоматических операций с профилем | контрактная (2) |
| BR-122 | `karaoke-app/.../WaveformCompare.kt:129-132` | LEAK | `ffmpeg` без таймаута: `inputStream.readBytes()` + `waitFor()` — вечное ожидание, поток Tomcat не возвращается, temp-файл не удаляется | `waitFor(FFMPEG_TIMEOUT_SECONDS=120, SECONDS)` + `destroyForcibly()`; вывод ffmpeg в temp-файл (нет stdout-трубы → процесс не встанет на полном буфере); оба temp-файла удаляются в `finally` | контрактная (1) |
| BR-129 | `karaoke-app/.../model/SongAssignmentDraft.kt:157-172` | ERR | `PreparedStatement` закрывался только в успешной ветке; ошибка → `println` + `false`, а вызывающие **всё равно удаляли назначение** (оставался ровно тот осиротевший черновик, ради которого метод написан) | `use { }` (закрытие на любой ветке); SLF4J `infra.editor.draft`; вызывающие не удаляют назначение при `draft_cleanup_failed` | контрактная (2) |
| BR-130 | `karaoke-app/.../model/SongVoice.kt:67` | ERR | `getLines().maxOf` / `chords.maxOf` / `maxBy` / `getLines().last()` на пустом списке → `NoSuchElementException` ронял генерацию MLT песни целиком | ранний выход в `actuateChilds` при пустом списке строк + WARN; `maxOfOrNull`/`maxByOrNull`/`lastOrNull` | контрактная (1) |
| BR-131 | `karaoke-app/.../model/SongArtifactRenamer.kt:72-80` | ERR | `renameTo == false` и «цель уже существует» ТИХО пропускались; песня сохранялась с новым `fileName` при части артефактов под старым именем | `RenameOutcome` (renamed + failures); WARN `infra.catalog.rename`; `Song.renameAllArtifacts` возвращает неудачи → `SongUpdateMapper` даёт `fileNameRenameError` с перечнем файлов | контрактная (1) |
| BR-134 | `karaoke-app/.../monitor/checks/StemJobsStuckCheck.kt:22-27` | ERR | исключение глоталось и подменялось `0` («проблем нет») — оператор не видел, что проверка не смогла ответить | исключение логируется (SLF4J `infra.monitor.check`) и **пробрасывается** → тик делает WARNING `check.<Name>.failure`. Те же два зеркала (`SubmittedAssignmentsCheck`, `UnreadChatMessagesCheck`) исправлены тем же приёмом — они названы в той же находке P-21-03 | контрактная (1) |
| BR-135 | `karaoke-app/.../monitor/MonitoringService.kt:40-53` | RACE | `fixedRate = 60_000` без single-flight: при недоступном проде тик > 60 с и следующий стартовал параллельно (дубли curl/ssh, гонка `firstFailureAt`, откат снапшота) | `fixedDelay` + `AtomicBoolean.compareAndSet` guard + WARN «предыдущий тик ещё выполняется — текущий пропущен» | контрактная (1) |
| BR-136 | `karaoke-app/.../services/AdminTaskService.kt:41-42` | LEAK | `tasks` пополнялся и НИКОГДА не чистился (каждая запись хранила `errors` bulk'а до конца жизни процесса); пул потоков не закрывался | TTL `TASK_TTL_MS = 1 ч` + `purgeExpiredTasks()` при старте новой задачи; `@PreDestroy` → `executor.shutdown()` | контрактная (1) |
| BR-137 | `karaoke-app/.../services/AlignmentServiceClient.kt:73-81` | ERR | любой сбой alignment-сервиса глотался `catch (_: Exception) { null }` без записи в лог; новый `OkHttpClient` на каждый вызов (churn пула/диспетчера) | SLF4J `infra.alignment` (Throwable) для исключения и для неуспешного HTTP-кода; кэшируемый `OkHttpClient` (пересоздаётся при смене `callTimeout`) | контрактная (1) |

### «До / после»: команда и вывод

Проверка «до» — контрактный тест на **старом** коде (главные исходники откачены через
`git stash`, тесты оставлены). Класс компилируется и на старом коде — падают все 16
утверждений, то есть каждая запись партии имеет наблюдаемый «до».

```bash
# ДО: главные исходники откачены (git stash), тесты — новые
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch1SourceContractsTest"
# было: 16 tests completed, 16 failed
#   BR-117 в UtilsPictures больше нет println в catch записи картинки FAILED
#   BR-118 в дайджесте share-ссылок не осталось printStackTrace FAILED
#   BR-119 сбой БД в списке новостей не выглядит как пустой список FAILED
#   BR-119 в контроллере новостей пагинация клампится FAILED
#   BR-119 в News больше нет println про SQLException FAILED
#   BR-121 поиск текста запускает Chromium ОДИН раз FAILED
#   BR-121 профиль Яндекс Музыки сериализирован общим локом FAILED
#   BR-122 ffmpeg запускается с таймаутом и без вечного readBytes FAILED
#   BR-129 очистка черновика закрывает statement и логирует ошибку FAILED
#   BR-129 вызывающий не удаляет назначение, если черновик не очищен FAILED
#   BR-130 пустой голос не роняет раскладку FAILED
#   BR-131 неудача переименования артефакта попадает в результат FAILED
#   BR-134 проверка мониторинга не подменяет ошибку нулём FAILED
#   BR-135 тик мониторинга не пересекается с самим собой FAILED
#   BR-136 реестр admin-задач ограничен по TTL, пул закрывается FAILED
#   BR-137 клиент forced-alignment переиспользуется, ошибка логируется FAILED

# ПОСЛЕ: код исправлен
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch1*"
# стало: BUILD SUCCESSFUL (21 тест: 16 контрактных + 5 поведенческих)
```

Поведенческие тесты партии 1 (на новых API, введённых правкой) — в отдельном классе
`PkgS323Batch1BehaviorTest`: на старом коде он не компилируется (этих API не было).

### Изменённые файлы (партия 1)

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`:
`UtilsPictures.kt`, `YandexLyricsSearchOutcome.kt`, `AlbumCoverFinder.kt`, `Utils.kt`,
`UtilsPlaywright.kt`, `WaveformCompare.kt`, `model/News.kt`, `model/Song.kt`,
`model/SongArtifactRenamer.kt`, `model/SongAssignmentDraft.kt`, `model/SongVoice.kt`,
`controllers/NewsController.kt`, `controllers/PromoController.kt`,
`controllers/ShareLinksAdminController.kt`, `controllers/SongEditorController.kt`,
`controllers/SongUpdateMapper.kt`, `monitor/MonitoringService.kt`,
`monitor/checks/{StemJobsStuckCheck,SubmittedAssignmentsCheck,UnreadChatMessagesCheck}.kt`,
`services/AdminTaskService.kt`, `services/AlignmentServiceClient.kt`;
`karaoke-web/.../controllers/PublicSongEditorController.kt`.

Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS323Batch1SourceContractsTest.kt`,
`PkgS323Batch1BehaviorTest.kt`; обновлён `model/RenameAllArtifactsTest.kt` (новый тип
`RenameOutcome`).

### Решения за владельцем

- **BR-121**: ручная авторизация Яндекс.Музыки (`createNewAuthContext2`) под
  `YANDEX_PROFILE_LOCK` намеренно **не** берётся: она ждёт человека минутами и
  заблокировала бы автоматику на всё это время. Правило «не запускать автоматику, пока
  идёт авторизация» осталось в `UtilsPlaywright` (`authInProgress`). Если владелец хочет
  строгую сериализацию «авторизация vs автоматика» — это отдельная правка состояния
  профиля.
- **BR-136**: TTL завершённой admin-задачи выбран 1 час (в ТЗ — «например, `finishedAt + 1 ч`»).
  Значение `TASK_TTL_MS` — константа, меняется одной строкой.

---

## Партия 2 — BR-144…BR-153 (10 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-144 | `karaoke-app/.../services/StorageMetadataCache.kt:325-341` | DATA | `getFileExists` писал в **вечный** кеш любое значение loader'а; `fileExists` возвращает `false` и при отсутствии объекта, и при недоступном хранилище (fast-fail/таймаут) → один таймаут MinIO навсегда закреплял `exists=false` | `StorageApiClient.fileExistsReliable(): Boolean?` (null = «не ответили»); `getFileExists`/`getFileExistsAsync` принимают `() -> Boolean?` и **не кешируют** null; REMOTE-загрузчики health-report переведены на надёжный вариант | контрактная (2) |
| BR-145 | `karaoke-app/.../services/StorageCircuitBreaker.kt:249-259` | ERR | `recordSuccess()` обнулял `failureCount` только при `HALF_OPEN→CLOSED`; успех в `CLOSED` счётчик не трогал — порог «N consecutive failures» превращался в «N сбоев за всё время» | обнуление `failureCount` на **любом** успехе в `CLOSED` | поведенческая (2, в т.ч. контрольная на «серия подряд всё ещё открывает circuit») |
| BR-146 | `karaoke-app/.../services/TelegramAutoPublishScheduler.kt:149-151` (+ `:71-73`) | ERR | три загрузчика кандидатов ловили `SQLException` пустым блоком → сбой неотличим от «кандидатов нет»; `printStackTrace()` уходил в stdout | загрузчики возвращают `null` при сбое, фазы тика прерываются (`false`); SLF4J `infra.telegram.publish` (SQLState + Throwable) | контрактная (1) |
| BR-147 | `karaoke-app/.../services/SseNotificationService.kt:217-219` | RACE | `putIfAbsent` + повторное разыменование `emitters[key]!!`: рассылка могла удалить ключ между операциями → NPE → 500 на `/api/subscribe` | `emitters.computeIfAbsent(key) { mutableListOf() }` — список берётся один раз; ключ возвращается в мапу, если рассылка его успела удалить | контрактная (1) |
| BR-149 | `karaoke-app/.../services/VkAutoPublishService.kt:243-249` | ERR | `createProcess` возвращает `0` и при «уже выполняется» (single-flight), и при реальном отказе; оба случая давали `SEND_FAILED` + ложную ошибку → пока рендер шёл, тик затирал `RENDERING`, и `onRenderCompleted` не вызывался никогда | `hasActiveRenderProcess()`: при активном `RENDER_MP4_DEMO` состояние остаётся `RENDERING`, ошибка не пишется; отказ → `SEND_FAILED` как раньше | контрактная (1) |
| BR-150 | `karaoke-app/.../services/VkAutoPublishService.kt:279-292` (+ `publishTextOnly`) | RACE | под локом перечитывался только `idVk`, а флаг `newsPremiumVkSent` проверялся по объекту, загруженному ДО лока → второй параллельный вызов публиковал **дубль** PREMIUM-поста | под локом перечитывается песня ЦЕЛИКОМ; флаг проверяется по свежей записи (`freshSong?.newsPremiumVkSent`) | контрактная (1) |
| BR-151 | `karaoke-app/.../services/VkAutoPublishScheduler.kt:55-59` | ERR | весь тик в `catch (Exception) {}` без логирования; сбой на одной песне обрывал цикл — остальные новости тика не публиковались | `log.error("VkAutoPublishScheduler.tick failed", e)`; обработка каждой новости в `runCatching` (сбой одной не блокирует остальные) | контрактная (1) |
| BR-152 | `karaoke-app/.../services/VkAutoPublishScheduler.kt:181-182` (+ `:216-217`, `:254-255`) и `VkAutoPublishService.kt:550-551` | ERR | четыре `catch (SQLException) {}` глотали ошибки БД; `findAirNewsForSong` отдавал `null` → AIR-пост публиковался с пустым `{newsBody}` | загрузчики возвращают `null` при сбое и тик прерывается; `findAirNewsForSong` бросает `VkNewsLookupException` → `publishToVk` пишет `SEND_FAILED` с причиной, пост **не** уходит с потерянным телом новости | контрактная (1) |
| BR-153 | `karaoke-app/.../services/VkApiClient.kt:481-528` | ERR | `proxy.send(...)` стоял вне `try` — сырое `IOException`/`HttpTimeoutException` пролетало мимо `tryPhotosPath`/`publishFile` (ловят только `VkNetworkException`) и роняло весь тик | прокси-ветка оборачивает сбой в `VkNetworkException` (симметрично прямой ветке) + fallback на прямой запрос | контрактная (1) |

### «До / после»: команда и вывод

```bash
# ДО: главные исходники откачены (git stash), тесты — новые
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch2SourceContractsTest"
# было: 11 tests completed, 10 failed
#   BR-144 недостоверный ответ хранилища не кешируется FAILED
#   BR-144 health-report спрашивает надёжный ответ хранилища FAILED
#   BR-145 успех в CLOSED обнуляет счётчик сбоев FAILED
#   BR-146 загрузчики кандидатов Telegram не подменяют сбой БД пустым списком FAILED
#   BR-147 подписка SSE не разыменовывает список эмиттеров повторно FAILED
#   BR-149 уже выполняющийся рендер не превращается в ошибку публикации FAILED
#   BR-150 флаг идемпотентности PREMIUM проверяется по свежей песне под локом FAILED
#   BR-151 тик VK-публикации логирует исключение и изолирует сбой одной новости FAILED
#   BR-152 сбой БД в VK-публикации не выглядит как пустой результат FAILED
#   BR-153 сбой прокси оборачивается в VkNetworkException FAILED
#   (11-й тест — контрольный «серия сбоев подряд всё ещё открывает circuit» — на старом
#    коде проходит, он фиксирует, что исправление не сломало исходный контракт)

# ПОСЛЕ
$ ... :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch2*"
# стало: BUILD SUCCESSFUL (11 тестов)
```

### Изменённые файлы (партия 2)

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`: `HealthReport.kt`,
`services/{StorageApiClient,StorageCircuitBreaker,StorageMetadataCache,SseNotificationService,TelegramAutoPublishScheduler,VkApiClient,VkAutoPublishScheduler,VkAutoPublishService}.kt`;
`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/StorageApiClientWeb.kt`
(второй реализатор интерфейса `StorageApiClient` — новый метод `fileExistsReliable`).

Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS323Batch2SourceContractsTest.kt`.

### Решения за владельцем

- **BR-144**: `fileExists` (старое поведение «недоступно = false») сохранён для
  остальных вызывающих; для кеша метаданных введён `fileExistsReliable`. Если владелец
  хочет, чтобы недоступное хранилище ВООБЩЕ не давало «файла нет» в UI, это отдельная
  правка health-report (там `false` трактуется как «файла нет»).
- **BR-149**: «уже выполняется» определяется по наличию активного задания
  `RENDER_MP4_DEMO` (WAITING/WORKING/CREATING) в `tbl_processes`. Если владелец хочет
  различать и «поставлено в очередь мной» vs «поставлено ранее», нужен отдельный
  идентификатор задания.

## Партия 3 — BR-155…BR-160, BR-163…BR-166

_в работе_

## Партия 4 — BR-167…BR-173

_в работе_

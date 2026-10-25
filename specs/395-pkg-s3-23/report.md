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
| 3 | BR-155…BR-160, BR-163…BR-166 (10) | ✅ исправлены |
| 4 | BR-167…BR-173 (6) | ✅ исправлены |

**Итог партий:** 39/39 записей исправлены, 5 коммитов (4 партии + SSoT/документ).

---

## SSoT и per-feature документ (FR-009)

- **Новый компонент SSoT**: `knowledge/domains/monitoring/components/error-semantics.md` —
  сквозной инвариант «сбой ≠ штатный результат»: канонические пары
  «штатный результат / сбой», механизмы различения в коде (трёхзначный
  `Boolean?`, `sealed class Outcome`, `null` вместо пустого списка, типизированные
  исключения, транзакция, журнал как часть результата), где зафиксированы
  регрессионные проверки. Зарегистрирован в
  `knowledge/domains/monitoring/domain.md` § «Структура компонентов (C4 L3)».
- **Новые SLF4J-категории** (18 шт.) зарегистрированы в
  `knowledge/domains/monitoring/components/log-categories.md` — по правилу самой
  компоненты («Регистрация новой категории должна быть зафиксирована в таблице
  выше»): `infra.pictures`, `infra.admin.sharelinks`, `infra.admin.news`,
  `infra.admin.tasks`, `infra.editor.draft`, `infra.catalog.rename`,
  `infra.monitor.check`, `infra.monitor.tick`, `infra.alignment`,
  `infra.telegram.publish`, `infra.telegram.polling`, `infra.vk.publish`,
  `infra.whisper.asr`, `infra.premium.publish`, `infra.cache.storage.local`,
  `infra.cleanup`, `infra.sponsr.sync`, `infra.waveform.compare`.
- **Инварианты по доменам** добавлены в `knowledge/domains/*/domain.md`:
  `storage` (BR-144, BR-145, BR-163, BR-164), `integration` (BR-149…BR-153,
  BR-157, BR-158, BR-159, BR-160), `health` (BR-169…BR-171), `persistence`
  (BR-168), `sse` (BR-147), `catalog` (BR-130, BR-131), `editorial` (BR-129),
  `monitoring` (BR-134…BR-136), `processing` (BR-117, BR-121, BR-122).
- **Per-feature документ (FR-009)**: `docs/features/pkg-s3-23.md` — 6 обязательных
  секций (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`,
  `## Известные ловушки`, `## Ссылки`) и 16 ловушек.

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

## Партия 3 — BR-155…BR-160, BR-163…BR-166 (10 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-155 | `karaoke-app/.../services/VkAutoPublishService.kt:40` | LEAK | `songLocks: ConcurrentHashMap<Long, Any>` — per-song локи никогда не удалялись, карта росла с числом песен | striped-локи фиксированного размера (`SONG_LOCK_STRIPES = 256`), индекс через `Math.floorMod` — память не растёт | контрактная (1) |
| BR-156 | `karaoke-app/.../services/VkAutoPublishScheduler.kt:187-219` | PERF | `loadNewsCandidates` читал ВСЮ историю air-новостей (без LIMIT), и для каждой — полная `Song.loadFromDbById`; уже опубликованные песни отсекались ПОСЛЕ загрузки | один SQL с `LEFT JOIN tbl_songs` (подтягивает `id_vk`, в т.ч. по ссылке `/song?id=<id>`), `LIMIT` (`newsCandidateLimit = 200`); опубликованные отсекаются ещё в SQL | контрактная (1) |
| BR-157 | `karaoke-app/.../services/VkAutoPublishScheduler.kt:43-44` | ERR | слот rate-limit занимался ДО попытки; `SCHEDULED`/`RENDERING` израсходовали лимит без поста — после трёх тиков бот замолкал до конца часа | `reserveRateLimitSlot()` резервирует, `releaseRateLimitSlot()` возвращает слот, если поста не было (`PUBLISHED` — единственное состояние, где слот остаётся израсходованным) | контрактная (1) |
| BR-158 | `karaoke-app/.../services/TelegramUpdatesConsumer.kt:64-67` (+ `:50-57`) | ERR | `deleteWebhook()` вне `try` — сетевая ошибка убивала поток, а `isWork` оставался `true` (повторный `start()` молча ничего не делал до рестарта); `start()` — check-then-act без синхронизации | `deleteWebhook` в `try/catch` с логом; `finally { isWork = false; started.set(false) }`; `start()` — атомарный `AtomicBoolean.compareAndSet` | контрактная (1) |
| BR-159 | `karaoke-app/.../services/WhisperAsrService.kt:113-121` | ERR | любой сбой ASR (HTTP-код, пустое тело, битый JSON, таймаут) → `null` → `whisper_unavailable`, неотличимо от «сервис не настроен»; `InterruptedException` глушился | `transcribeDetailed(): Outcome` (`Transcribed`/`Failed` с причиной `asr.not_configured`/`asr.http_500`/`asr.empty_body`/`asr.invalid_response`/`asr.interrupted`); `SongEditorController` возвращает `reason` | контрактная (1) |
| BR-160 | `karaoke-app/.../services/VkIdTokenRefreshScheduler.kt:44` | ERR | cron раз в час + порог 30 мин → OAuth в 31–59-ю минуту давал «пропуск» до следующего тика, токен успевал истечь | тик каждые 10 минут (`0 */10 * * * *`) при пороге 30 мин — «проскочить» токен не может | контрактная (1) |
| BR-163 | `karaoke-app/.../services/KaraokeStorageService.kt:347-363` | LEAK | `file.inputStream()` не закрывался НИКОГДА (minio-java переданный поток не закрывает) — дескрипторы копились в массовых загрузках | `file.inputStream().use { … }` — закрытие гарантировано на любой ветке | контрактная (1) |
| BR-164 | `karaoke-app/.../services/KaraokeStorageService.kt:499-529` (+ `getFileStat`) | ERR | любой `ErrorResponseException` (403/500/…) трактовался как «объекта нет»; `getFileStat` глотал любой `MinioException` → `exists=false` в кеше | «нет объекта» — только `NoSuchKey`/`NoSuchBucket`/`ResourceNotFound`; остальные коды и сбои MinIO → исключение + WARN `infra.cache.storage.local` (и breaker их видит) | контрактная (1) |
| BR-165 | `karaoke-app/.../services/PremiumAutoPublishScheduler.kt:397-398` (+ `:435-436`, `:462-463`) | ERR | три SQL-выборки глотали `SQLException` пустым блоком; `printStackTrace()` уходил в stderr | загрузчики возвращают `null` при сбое, фазы тика прерываются; SLF4J `infra.premium.publish` (SQLState + Throwable) | контрактная (1) |
| BR-166 | `karaoke-app/.../services/PremiumAutoPublishScheduler.kt:231-233` | ERR | тик реагировал только на `send_failed`; `SCHEDULED` («контент не готов») игнорировался → песня НАВСЕГДА в очереди: каждые 30 с полная загрузка песни, без лога и без терминального FAILED | `handleChannelOutcome()` разбирает код явно: `send_failed` и `scheduled` → `handleFailure` (счётчик + причина), остальное → WARN без расхода попытки | контрактная (1) |

### «До / после»: команда и вывод

```bash
# ДО: главные исходники откачены (git stash), тесты — новые
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch3SourceContractsTest"
# было: 10 tests completed, 10 failed  (BR-155…BR-166 — все десять)

# ПОСЛЕ
$ ... :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323*"
# стало: BUILD SUCCESSFUL (42 теста: 16 + 5 + 11 + 10)
```

### Изменённые файлы (партия 3)

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`: `controllers/SongEditorController.kt`,
`services/{KaraokeStorageService,PremiumAutoPublishScheduler,TelegramUpdatesConsumer,VkAutoPublishScheduler,VkAutoPublishService,VkIdTokenRefreshScheduler,WhisperAsrService}.kt`.

Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS323Batch3SourceContractsTest.kt`.

### Решения за владельцем

- **BR-156**: `LIMIT` = 200 кандидатов за тик (тик раз в 60 с). Если у оператора
  больше 200 неопубликованных air-новостей подряд, хвост очереди уйдёт на следующие
  тики — это осознанный размен (стоимость тика ограничена). Значение — константа
  `newsCandidateLimit`.
- **BR-160**: тик обновления VK ID-токена стал чаще (каждые 10 минут вместо часа).
  Частота refresh'а VK ID ограничена самим VK; при 6 попытках в час риск не выше, чем
  был (до правки тик всё равно не срабатывал в «окне»).
- **BR-163**: поток в `uploadFile(path)` теперь закрывается. Владелец потоком на
  стороне вызывающего (перегрузка `uploadFile(InputStream)`) остаётся прежним — её
  закрывают вызывающие (`Pictures.kt`, `Utils.kt`, `StorageController.kt`).

## Партия 4 — BR-167…BR-173 (6 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-167 | `karaoke-app/.../services/SponsrSyncService.kt:152-161` | DATA | цикл продления премиума без изоляции: исключение на одном подписчике обрывало батч — остальные не получали `sponsr_premium_until` (частичный батч, без транзакции и без следа в логах) | обработка одного пользователя в `try/catch` с логом (`siteUserId`, причина); список неудачных возвращается в результате и в `messages`; `success = failed.isEmpty()` | контрактная (1) |
| BR-168 | `karaoke-app/.../services/KaraokeProcessAdminService.kt:347-363` (+ bulk `:639-652`, delete) | DATA | UPDATE процесса и его audit-запись — две независимые autocommit-транзакции: падение INSERT'а в `tbl_processes_audit` давало 500 при УЖЕ применённой правке, и история правки терялась | хелпер `inTransaction()` (`autoCommit=false` → `commit`/`rollback`, `autoCommit` возвращается в `finally`, т.к. соединение кэшируется в потоке); edit/delete/bulk — в одной транзакции | контрактная (1) |
| BR-169 | `karaoke-app/.../services/cleanup/ArtifactCleanupPlanner.kt:145-147` | ERR | сбой обхода дерева ловился широким `catch` и печатался в stdout: отчёт содержал лишь часть файлов, а `apply` удалял эту часть и завершался `DONE` | факт прерывания попадает в `CleanupPlan.warnings` с маркером `WALK_INTERRUPTED_MARKER`; `apply` отклоняет такой отчёт (`409 incomplete_plan`) | контрактная (1) |
| BR-170 | `karaoke-app/.../services/cleanup/ArtifactCleanupService.kt:186-189` | ERR | результат `journal.appendDeleted`/`appendFailed` игнорировался: сбой записи в журнал не прерывал удаление — файл удалялся, счётчик рос, прогон завершался `DONE` (вопреки FR-012) | `append == false` → флаг `journalBroken`, прогон прерывается и переводится в `FAILED`; ошибка пишется в `errors` и в лог `infra.cleanup` | контрактная (1) |
| BR-171 | `karaoke-app/.../services/cleanup/ArtifactCleanupService.kt:141-142` | RACE | `runId = r-<дата>-<classId>` — у всех прогонов одного класса за день идентификатор совпадал; два `apply` писали в одну запись `runs[runId]`, и `status?runId=` отдавал счётчики последнего | к `runId` добавляется короткий UUID прогона (`r-<дата>-<class>-<8 hex>`) | контрактная (1) |
| BR-172 | `karaoke-app/.../textfiledictionary/TextFileDictionary.kt:24-25` | ERR | действие выбиралось reflection'ом по имени метода и всегда вызывалось с одним аргументом: любой метод другой арности → `IllegalArgumentException` → 500; через запрос был доступен вызов любого публичного метода | явный `when (dictAction)` по фиксированному набору `ALLOWED_ACTIONS` (`add`, `remove`); неизвестное действие → `false` (контроллер уже сообщает «не выполнено») | контрактная (1) |
| BR-173 | `karaoke-app/src/main/resources/application.yml:67-69` | BUILD | блок `app.max-file-size`/`max-request-size` не читался ни одним классом, но выглядел как настройка лимита загрузки и расходился с настоящим (`spring.servlet.multipart`) | блок `app:` удалён — в файле один источник истины; в комментарии зафиксировано, где настоящий лимит | контрактная (1) |

### «До / после»: команда и вывод

```bash
# ДО: главные исходники откачены (git stash), тесты — новые
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323Batch4SourceContractsTest"
# было: 7 tests completed, 7 failed  (BR-167…BR-173 — все семь)

# ПОСЛЕ
$ ... :karaoke-app:test --tests "com.svoemesto.karaokeapp.audit.PkgS323*"
# стало: BUILD SUCCESSFUL (49 тестов: 16 + 5 + 11 + 10 + 7)
```

### Изменённые файлы (партия 4)

`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`: `services/SponsrSyncService.kt`,
`services/KaraokeProcessAdminService.kt`, `services/cleanup/{ArtifactCleanupPlanner,ArtifactCleanupService}.kt`,
`textfiledictionary/TextFileDictionary.kt`; `karaoke-app/src/main/resources/application.yml`.

Тесты: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS323Batch4SourceContractsTest.kt`.

### Решения за владельцем

- **BR-168**: транзакция использует кэшированное в потоке соединение (`KaraokeConnection`),
  поэтому `autoCommit` включается обратно в `finally`. Если владелец хочет отдельное
  соединение на транзакцию — это правка `KaraokeConnection`, а не сервиса.
- **BR-170**: после сбоя записи в журнал файл УЖЕ удалён (удаление необратимо), поэтому
  прогон помечается `FAILED`, а не откатывается. Альтернатива «писать в журнал ДО
  удаления» меняет порядок операций и контракт FR-012 — решение за владельцем.
- **BR-173**: в `karaoke-web/src/main/resources/application.yml` остались те же мёртвые
  ключи `app.max-file-size`/`app.max-request-size` (рядом с живым `app.public-site-url`).
  Они не входят ни в одну запись реестра — фикс оставлен «за владельцем».


---

## Проверки CI (финальный прогон, после всех партий)

Полный набор — ровно тот, что указан в ТЗ пакета:

```bash
$ GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
    :karaoke-app:test :karaoke-web:test
# BUILD SUCCESSFUL (karaoke-app: 624 теста, karaoke-web: без изменений)

$ ... ktlintCheck
# BUILD SUCCESSFUL

$ bash tools/check-knowledge-structure.sh
# OK: knowledge/ structure valid (9/9 checks passed)

$ python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt
# All documentation passes architectural style and structural checks.

$ python3 tools/check-doc-references.py
# Проверено: ссылок 4313, путей 2549, эндпоинтов 334; whitelist: 51 записей.
# OK: битых ссылок, несуществующих путей и эндпоинтов нет.

$ python3 tools/check-recordhash-consistency.py
# OK recordhash-контракт: NEW-префикс в триггерах, канонические файлы == модели
# (+бэфилл), миграции tbl_site_users == канонической формулы, tbl_songs_sync
# содержит все колонки INSERT.

$ bash tools/check-no-mp4-mentions.sh
# ✅ OK: all 139 MP4 mentions are in baseline (grandfathered)

$ bash tools/check-feature-doc.sh docs/features/*.md
# ==> Все документы валидны

$ bash tools/check-subagent-isolation.sh
# ✅ Все commits в ветке 615-pkg-s3-23 уникальны (нет race condition)
```

**Фронтенд.** `webvue3/` и `karaoke-public/` не затронуты
(`git diff --stat e42a573d..HEAD -- webvue3 karaoke-public` — пусто), поэтому
`npm run lint` / `npm run test:unit` не запускались: в worktree нет `node_modules`,
а установка зависимостей требует сети.

**Что НЕ делалось** (по запретам ТЗ): деплой не выполнялся; контейнеры, прод и БД
не трогались; `specs/336-code-audit/registry.csv` не изменён (проверено
`git diff --stat e42a573d..HEAD -- specs/336-code-audit/registry.csv` — пусто);
push и PR не делались; коммитов в `master` нет; история не переписывалась;
`SKIP=` не использовался.

## Коммиты пакета

| Коммит | Содержание |
|---|---|
| `008b5539` | Партия 1: BR-117…BR-137 (13 записей) |
| `a58bbf48` | Партия 2: BR-144…BR-153 (10 записей) |
| `fb3fce7f` | Партия 3: BR-155…BR-160, BR-163…BR-166 (10 записей) |
| `52b364a5` | Партия 4: BR-167…BR-173 (6 записей) |
| `9bee74c3` | SSoT + per-feature документ (FR-009) |

## Решения за владельцем (сводно)

1. **BR-121** — ручная авторизация Яндекс.Музыки (`createNewAuthContext2`) под
   `YANDEX_PROFILE_LOCK` намеренно **не** берётся: она ждёт человека минутами и
   заблокировала бы автоматику на всё это время. Строгая сериализация
   «авторизация vs автоматика» — отдельная правка состояния профиля.
2. **BR-144** — `fileExists` (старое «недоступно = false») сохранён для остальных
   вызывающих; для кеша метаданных введён `fileExistsReliable`. Если недоступное
   хранилище не должно давать «файла нет» в UI — это отдельная правка health-report.
3. **BR-149** — «уже выполняется» определяется по наличию активного задания
   `RENDER_MP4_DEMO` в `tbl_processes`. Различать «поставлено мной» и «поставлено
   ранее» без отдельного идентификатора задания нельзя.
4. **BR-156** — `LIMIT` = 200 кандидатов за тик (тик раз в 60 с). Хвост очереди
   уйдёт на следующие тики — осознанный размен.
5. **BR-160** — тик обновления VK ID-токена стал чаще (каждые 10 минут вместо
   часа); частота refresh'а ограничена самим VK.
6. **BR-163** — поток в `uploadFile(path)` закрывается на стороне сервиса; во
   владении потоком у вызывающих (`uploadFile(InputStream)`) ничего не изменилось.
7. **BR-168** — транзакция использует кэшированное в потоке соединение
   (`KaraokeConnection`), `autoCommit` возвращается в `finally`. Отдельное
   соединение на транзакцию — правка `KaraokeConnection`.
8. **BR-170** — после сбоя записи в журнал файл УЖЕ удалён (необратимо), поэтому
   прогон помечается `FAILED`, а не откатывается. Вариант «писать в журнал ДО
   удаления» меняет порядок операций и контракт FR-012 — решение за владельцем.
9. **BR-173** — в `karaoke-web/src/main/resources/application.yml` остались те же
   мёртвые ключи `app.max-file-size`/`app.max-request-size` (рядом с живым
   `app.public-site-url`). Они не входят ни в одну запись реестра — фикс оставлен
   за владельцем.
10. **BR-136** — TTL завершённой admin-задачи = 1 час (значение ТЗ
    «например, `finishedAt + 1 ч`»), константа `TASK_TTL_MS`.

# PKG-S3-24 (OpenProject #396) — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC — 40 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-24` (`specs/336-code-audit/backlog.md`, блок `### PKG-S3-24`).
- **Ветка**: `623-pkg-s3-24` от `origin/master` `b2c4b06f` (merge PR #717, `PKG-S3-23`).
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-24`.
- **Источник правды**: карточки `specs/336-code-audit/findings/P-15.md`, `P-16.md`, `P-17.md`,
  `P-20.md`, `P-25.md`, `P-26.md`, `P-27.md`, `P-28.md`; порции определены по `registry.csv`.
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры, прод, БД и
  `specs/336-code-audit/registry.csv` не трогались; деплоя не было; push/PR не делались.
- **Работа партиями**: по 10 записей, коммит после каждой партии.

## Сводка

| Партия | BR | Статус |
|---|---|---|
| 1 | BR-174, BR-177…BR-181, BR-183, BR-184, BR-186, BR-187 (10) | ✅ исправлены |
| 2 | BR-189…BR-194, BR-196…BR-199 (10) | ✅ исправлены |
| 3 | BR-202…BR-204, BR-207…BR-212, BR-215 (10) | ✅ исправлены |
| — | BR-182, BR-185, BR-188, BR-195, BR-200, BR-201, BR-205, BR-206, BR-213, BR-214 (10) | ✅ закрыты волнами A–D (см. «Уже исправленные волнами A–D») |

---

## Партия 1 — BR-174, BR-177…BR-181, BR-183, BR-184, BR-186, BR-187 (10 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-174 | `services/cleanup/ArtifactCleanupPlanner.kt:132-133` + `ArtifactCleanupService.kt:126` + `CleanupController.kt:52` | PERF | построение отчёта сухого прогона — синхронный обход трёх корней (183 448 файлов на машине владельца) прямо в потоке HTTP-запроса; `apply` перед постановкой удаления в executor повторял весь обход ради отпечатка → один щелчок «применить» = **два** полных обхода; на каждый файл — 3–4 stat-вызова, для совпавших — второй `sizeOf` | `POST /api/cleanup/plan` → `202` + `planId`, обход идёт в executor'е `artifact-cleanup` (`ArtifactCleanupService.planAsync`), отчёт забирается `GET /api/cleanup/plan/{planId}` (`READY`/`PLANNING`/`FAILED`); сверка состава в `apply` перенесена в фоновый прогон — при несовпадении прогон → `FAILED`, **ничего не удаляется** (fail-closed); `sizeOf` на файл считается один раз (`matchSize`) | контрактная (2) + поведенческая (1) |
| BR-177 | `model/News.kt:283-290` | COMPAT | `loadPublishedSince` — курсор по `id` (`id > ?`), а порядок и отсечение по `publish_at` (`ORDER BY publish_at DESC LIMIT 50`); клиент (`karaoke-public/src/composables/useNewsUnreadCount.js:144-151`) пишет в `localStorage` **максимум `id`** из выданного → при >50 новых строк отрезанные новости терялись навсегда (бедж недосчитывал) | сортировка согласована с курсором: `ORDER BY id ASC` — выдаётся непрерывный префикс непрочитанных строк, `max(id)` клиента = последняя выданная строка | контрактная (1) |
| BR-178 | `model/News.kt:249-252, 265-268, 311-314, 441-443` | ERR | четыре метода публичного чтения гасили `SQLException` в `println` по `e.message` и возвращали «пустой успех»: пустая лента, `total=0`, пустой бейдж, `existsAnnouncement=false`; публичный endpoint отдавал `200` при живой БД, причина не попадала ни в логи, ни в ответ | SLF4J (`infra.admin.news`) с объектом исключения + `throw e` (как уже сделано для `loadAll`/`countAll` в BR-119); нет соединения — тоже `SQLException` (`news.*.unavailable`); контроллер `karaoke-web/.../PublicNewsController.kt` → `503` + `{"error":"news.list.unavailable"}` | контрактная (1) |
| BR-179 | `model/Picture.kt:311` | ERR | `ImageIO.read(File(params.pathToFile))` без проверок: путь по умолчанию `""` → `IIOException("Can't read input file!")`, отсутствующий файл → `IIOException`, неподдерживаемый формат → `ImageIO.read` возвращает `null` → NPE в `resizeBufferedImage`; исключение глотал вызывающий `catch` — картинка просто не создавалась | `readImageOrThrow()`: пустой путь → `picture.image_path_blank`, нет файла → `picture.image_file_missing`, нечитаемый формат → `picture.image_unreadable` (явная ошибка вместо NPE/IIOException) | контрактная (1) + поведенческая (2) |
| BR-180 | `model/Picture.kt:453-467` (+ `132-135`) | PERF | `getVariantsOfLines` рекурсивно перечислял **все** разбиения текста (`2^(n-1)` вариантов для `n` слов), и каждый полностью измерялся (подбор кегля с шагом 1 px на каждой строке) → 15 слов = 16 384 варианта, 20 слов = 524 288 (минуты CPU в потоке рендера, риск OOM всего `karaoke-app`); `multiLines()` не вызывал `dispose()` у `Graphics2D` | динамика по позициям разрыва: `selectBestSplit()` — для каждого числа строк `k` считается лучшее разбиение (максимальный минимальный кегль, затем минимальная ширина самой широкой строки с этим кеглем), `O(n²·maxLines)` замеров; кегль по-прежнему считается по `h / k`, правило выбора то же; замеры идут на одном переиспользуемом `Graphics2D` (1×1) вместо `BufferedImage(w,h)` на каждый замер; пределы `MAX_SPLIT_LINES=10`, `MAX_SPLIT_WORDS=24`; `Graphics2D` освобождается в `finally` | контрактная (1) + поведенческая (1, таймаут) |
| BR-181 | `model/Pictures.kt:272-289` + `model/KaraokeDbTable.kt:642-643` | ERR | заявленная защита от гонки в `createNewPicture` не срабатывала: `createDbInstance` ловит `Exception` **внутри себя** и не пробрасывает его → ветка `catch` не выполнялась никогда, метод уходил в `return null` (у проигравшей песни не появлялась обложка, в MinIO оставался осиротевший объект); «тихий null» неотличим от штатного результата | `null` из `createDbInstance` — тоже «возможно, запись создана соседним потоком»: перечитывание по имени; если и там пусто — `ERROR` в журнал `infra.pictures`; `createDbInstance` логирует `SQLState` и различает гонку (`23505`, `INFO`) и прочие ошибки (`ERROR`) | контрактная (2) |
| BR-183 | `model/MltNode.kt:35-50` (+ `mlt/mko/MkoScroller.kt:169,171`, `MkoSplashStart.kt:260`, `MkoSongText.kt:94`, `model/SongVoiceLineElement*.kt:100,172`, `model/Song.kt:3420…3597`, `Extentions.kt:170`) | DATA | `toString()` собирал XML **без экранирования** (KDoc обещал экранирование, кода не было) → `&` в названии песни/автора/альбома (564 записи в каталоге) давал невалидный `.mlt`; ручные подмены в вызывающих (`replace("&", "&amp;")` / `"&amp;amp;"`) были разными и в разных местах, часть — с двойным экранированием (в интерфейсе показывалось «&amp;») | единое место экранирования — `MltNode.toString()`: значения атрибутов и текстовое содержимое экранируются `escapeXml()` (`&`, `<`, `>`, `"`, `'`); ручные `replace` из вызывающих убраны (20 мест); `xmldata()` больше не экранирует `<` вручную — это делает сериализация | контрактная (2) + поведенческая (2) |
| BR-184 | `model/KaraokeDbTable.kt:459-463` + `model/Author.kt:307-319` + `model/Album.kt:633-646, 692-705` + `Utils.kt:3631-3646` | ERR | `loadList` превращал любой сбой БД в `emptyList()`, неотличимый от «строк нет»; пути create-or-get (`Album.findOrCreateForSongImport`, `Album.findOrCreateForSongImportRaw`, `Utils.getAuthorForRequest`) на такой «пустой» выборке **создавали второго автора** с тем же именем — справочник авторов тихо размножался | `loadList(..., throwOnError = true)` — SQL-ошибка/отсутствие соединения пробрасываются как `SQLException`; `Author.getAuthorByName(..., throwOnError = true)`; в create-or-get сбой чтения → `ERROR` в журнал и `null` (сущность **не** создаётся). UNIQUE-индекс на `tbl_authors.author` — **за владельцем** (нужна миграция + дедупликация, см. «За владельцем») | контрактная (1) |
| BR-186 | `model/Author.kt:118-160` + `controllers/ApiController.kt:6636-6645` | PERF | `Author.toDTO()` на каждого автора делал отдельный `Pictures.loadList(..., LIMIT 1)` — N+1 (число SELECT'ов = числу авторов: 126 авторов → ~126 запросов) | `Author.toDTOsWithPictures()` — картинки всех авторов читаются **одним** запросом `WHERE picture_name IN (…)` (`Pictures.getPicturesByNames`) и раздаются из карты; `toDTO()` (legacy, одиночный автор) сохранён | контрактная (1) |
| BR-187 | `model/KaraokeDbTable.kt:305-338` (и ещё 20 мест) | ERR | ошибки SQL в базовом слое логировались `printStackTrace()`/`println()` в stdout — без уровня, логгера, MDC и стека (ADR `local-0005` требует SLF4J) | все `printStackTrace()`/`println()` в `KaraokeDbTable.kt` заменены на SLF4J: `log.error` (недоступность БД, сбой вставки), `log.warn` (сбой чтения/закрытия ресурсов), `log.debug` (рутинные сообщения); stack trace сохраняется (объект исключения передаётся в логгер) | контрактная (1) |

### «До / после»: команда и вывод

Проверка «до» — регрессионные тесты на **старом** коде: изменённые исходники откачены через
`git stash push -- karaoke-app/src/main karaoke-web/src/main` (тесты оставлены), после чего тесты
прогнаны. Класс компилируется и на старом коде — падают **все 19** утверждений, то есть каждая
запись партии имеет наблюдаемый «до».

```bash
git stash push -m "s3-24-batch1-before-check" -- karaoke-app/src/main karaoke-web/src/main
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.PkgS324Batch1*'
git stash pop
```

Вывод «до» (19 тестов, 19 упали):

```
PkgS324Batch1BehaviorTest > BR-174 отчёт чистки строится в фоновом потоке() FAILED
PkgS324Batch1BehaviorTest > BR-180 длинное название разбивается на строки быстро() FAILED
PkgS324Batch1BehaviorTest > BR-183 XML с амперсандом в атрибуте и тексте остаётся валидным() FAILED
PkgS324Batch1BehaviorTest > BR-183 вложенный xmldata не ломает документ() FAILED
PkgS324Batch1BehaviorTest > BR-179 отсутствующий файл картинки даёт понятную ошибку() FAILED
PkgS324Batch1BehaviorTest > BR-179 пустой путь к картинке даёт понятную ошибку() FAILED
PkgS324Batch1SourceContractsTest > BR-184 сбой чтения отличается от отсутствия данных() FAILED
PkgS324Batch1SourceContractsTest > BR-174 размер файла в отчёте считается один раз() FAILED
PkgS324Batch1SourceContractsTest > BR-177 курсор новостей согласован с сортировкой() FAILED
PkgS324Batch1SourceContractsTest > BR-181 createDbInstance различает гонку и ошибку() FAILED
PkgS324Batch1SourceContractsTest > BR-181 проигравший гонку за картинкой перечитывает запись по имени() FAILED
PkgS324Batch1SourceContractsTest > BR-174 отчёт чистки строится вне HTTP-потока() FAILED
PkgS324Batch1SourceContractsTest > BR-178 сбой чтения новостей не выглядит как пустая лента() FAILED
PkgS324Batch1SourceContractsTest > BR-183 ручные замены амперсанда убраны из вызывающих() FAILED
PkgS324Batch1SourceContractsTest > BR-183 XML экранируется в одном месте() FAILED
PkgS324Batch1SourceContractsTest > BR-180 перебор разбиений заменён динамикой() FAILED
PkgS324Batch1SourceContractsTest > BR-179 путь к картинке проверяется явно() FAILED
PkgS324Batch1SourceContractsTest > BR-187 базовый слой БД логирует через SLF4J() FAILED
PkgS324Batch1SourceContractsTest > BR-186 картинки авторов грузятся одним запросом() FAILED
19 tests completed, 19 failed
```

Показательные сообщения «до»:

```
BR-183: амперсанд должен быть экранирован: <producer id="p0" title="Sex & Violence <live>">Глеб
BR-183 (xmldata): org.xml.sax.SAXParseException; lineNumber: 1; columnNumber: 31;
          The processing instruction target matching "[xX][mM][lL]" is not allowed.
BR-180: java.util.concurrent.TimeoutException: BR-180 длинное название разбивается на строки быстро() timed out after 60 seconds
BR-179: Unexpected exception type thrown, expected: <java.lang.IllegalStateException> but was: <javax.imageio.IIOException>
BR-187: printStackTrace заменён на SLF4J (BR-187) ==> expected: <false> but was: <true>
```

Вывод «после» (тот же запуск на исправленном коде):

```
BUILD SUCCESSFUL
```

Регрессионные тесты партии 1:

- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS324Batch1SourceContractsTest.kt` — 13 контрактных тестов (по 1–2 на запись);
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS324Batch1BehaviorTest.kt` — 6 поведенческих тестов (BR-174, BR-179 ×2, BR-180, BR-183 ×2).

**Почему для BR-177/BR-178 нет поведенческого теста**: обе записи — SQL-путь к живой БД
(`tbl_news`), а пакет запрещает запись в БД и поднятие контейнеров. Доказательство «до/после»
даёт контрактный тест по тексту исходника (SQL-конструкция `ORDER BY id ASC`, отсутствие
`println`, наличие `throw e` в каждом из четырёх методов) — он падает на старом коде и проходит
после. Для BR-184/BR-186/BR-187/BR-181 поведенческий тест без БД невозможен по той же причине
( JDBC-путь), кроме BR-181, где проверяется ветвление «null → перечитывание» (контрактный тест).

### Побочные эффекты правок (важно для ревью)

1. **BR-183 меняет вид текста в MLT.** Раньше значения подставлялись «как есть» (XML невалиден),
   а часть вызывающих подменяла `&` на `&amp;amp;` — в интерфейсе это показывалось как «&amp;».
   Теперь экранирование одно и правильное: «Sex & Violence» отображается как «Sex & Violence».
   Ручные `.replace` в `model/Song.kt:2669, 2676, 2690, 2753, 2810` **оставлены** — это сырые
   XML-строки (не `MltNode`), там экранирование по-прежнему нужно.
2. **BR-178 меняет код ответа публичной ленты**: `200` → `503` при сбое БД. Клиент
   (`karaoke-public`) уже умеет обрабатывать сетевую ошибку (`catch` в `pollOnce` оставляет
   бейдж как есть) — «новостей нет» больше не показывается.
3. **BR-177 меняет порядок выдачи `/api/public/news/since`**: строки идут по возрастанию `id`
   (непрерывный префикс непрочитанных). Для бейджа/тоста порядок не важен — важен `count`.
4. **BR-174 меняет контракт `POST /api/cleanup/plan`**: `200` (отчёт) → `202` (`planId`), плюс
   новый `GET /api/cleanup/plan/{planId}`. Фронтендов/скриптов, вызывающих этот эндпоинт, в
   репозитории нет; контрактный документ `specs/544-song-artifact-cleanup/contracts/cleanup-api.md`
   **не редактировался** (чужой issue) — синхронизация контракта — за владельцем (см. ниже).
   Кроме того, `apply` при несовпадении состава теперь сообщает `FAILED` прогоном
   (`errors[0] = "fingerprint_mismatch: …"`), а не HTTP `409`; проверка присланного клиентом
   отпечатка осталась синхронной (`409`).
5. **BR-186** меняет только порядок/состав запросов; формат ответа дайджеста авторов прежний.

### За владельцем

| Вопрос | Почему за владельцем | Что сделано в коде meanwhile |
|---|---|---|
| UNIQUE-индекс на `tbl_authors.author` (BR-184) | требует миграции + дедупликации существующих дублей (запись в БД запрещена пакетом, объём дублей не измерен) | на уровне кода запрещено создавать дубль при сбое чтения (`throwOnError`) |
| Синхронизация контракта чистки `specs/544-song-artifact-cleanup/contracts/cleanup-api.md` под `202 + planId` (BR-174) | контракт принадлежит другому issue (#321), править его в этом пакете нельзя | контракт описан в `docs/features/pkg-s3-24.md` § «Известные ловушки» |
| Точность переноса проверки состава из `apply` (HTTP `409` → `FAILED` прогон) | меняется наблюдаемое поведение админ-эндпоинта | поведение fail-closed: при несовпадении **ничего не удаляется** |
| `MAX_SPLIT_WORDS = 24` / `MAX_SPLIT_LINES = 10` (BR-180) | границы влияют на вид длинных названий (нужен взгляд владельца на реальные названия) | разбиение не выполняется сверх 24 слов (однострочная раскладка) — вместо минут CPU |

---

## Партия 2 — BR-189…BR-194, BR-196…BR-199 (10 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-189 | `model/Publication.kt:1471-1495` | DATA | сетка публикаций — «одна песня на часовой слот», но уникальность `(publish_date, publish_time)` не обеспечена: `when (song.time)` присваивал слот **безусловно** (вторая песня молча затирала первую), а у `when` не было `else` — песня со временем вне 10:00–23:00 исчезала из выдачи без следа | слот выбирается через свойство (`Publication::publish10`…): перед записью проверяется занятость слота — при конфликте в журнал пишется WARN с id обеих песен (первая песня не затирается); `else` → WARN «время вне сетки». Частичный UNIQUE-индекс в БД — **за владельцем** (нужна миграция + дедупликация) | контрактная (1) |
| BR-190 | `model/Publication.kt:1357` (+ `:1642`) | COMPAT | модель читала дату «с» из ключа `filter_date_from`, а контроллеры кладут `publish_date_from` → нижняя граница фильтра молча игнорировалась (список с самой ранней даты) | модель читает оба ключа: `args["filter_date_from"] ?: args["publish_date_from"]` (и симметрично для «по») — оба контроллера и legacy-страница работают | контрактная (1) |
| BR-191 | `model/Publication.kt:1455, 1476` + `controllers/MainController.kt` | ERR | формат `publish_date` не проверялся нигде: `split(".")[2]` без проверки длины → `IndexOutOfBoundsException`, `SimpleDateFormat.parse` → `ParseException`; одно неверное значение рушило **всю** страницу публикаций; при записи формат принимался свободным текстом | `parsePublishDate()` — регулярка `dd.MM.yy` + строгий разбор (`isLenient=false`, `31.02.26` → null); границы фильтра и даты песен разбираются **один раз**, песня с неверной датой пропускается с WARN; `publicationIdFromDate()` вместо `split`; на записи — `Publication.validatePublishDateTime()` → `400` с понятным текстом (`MainController.songsUpdate`/`songs2Update`) | контрактная (1) + поведенческая (1) |
| BR-192 | `model/SearchResult.kt:352-360`, `model/SearchAsync.kt:197-205` | PERF | удаление результатов поиска — N+1 DELETE (по строке в цикле), результат каждого `delete` отбрасывался; сбой на середине оставлял часть результатов привязанной к удалённой песне, а вызывающий отвечал «успех» | один `DELETE … WHERE id = ANY(?)` (`KaraokeDbTable.deleteIn`) для обоих семейств; расхождение «хотели удалить N, удалили M» → WARN `infra.search`; методы возвращают число удалённых строк | контрактная (1) |
| BR-193 | `model/SiteChatMessage.kt:147-310` (6 методов) + контроллеры чата | ERR | шесть методов гасили `SQLException` в `println` по `e.message` и возвращали «пустой успех»: пустая история чата, `total=0`, бейдж непрочитанных `0`, тихий no-op при пометке прочитанным | SLF4J (`infra.chat`) с объектом исключения; счётчики возвращают признак ошибки `COUNT_ERROR = -1` (сбой ≠ «сообщений нет»), `markThreadRead` возвращает `Boolean`; чтение истории/тредов пробрасывает `SQLException`; контроллеры (`ChatController`, `PublicChatController`) → `503` + `{"error":"chat.*.unavailable"}`; монитор `UnreadChatMessagesCheck` → WARNING вместо «всё прочитано» | контрактная (2) |
| BR-194 | `model/SiteUser.kt:106-119` | ERR | флаг `welcomeMessageSent = true` и `save()` выполнялись независимо от результата `createNew(...)` (возвращал `null` при сбое) → приветствие не доходило, а флаг оставался `true` — повторной попытки не было бы никогда | флаг ставится **только после** успешного создания сообщения; `save()` — с проверкой (при сбое флаг в памяти возвращается в `false`, исключение пробрасывается) | контрактная (1) |
| BR-196 | `model/StatBySong.kt:268, 656, 1196` (+ `:522`) | PERF | `limit`/`offset` подставлялись в SQL «как есть»: `?pageSize=2000000000` вычитывал всю `tbl_events` (250k+ строк) в heap, а `offset = (page-1)*pageSize` переполнялся в отрицательный Int → SQL-ошибка, проглоченная методом (клиент получал `200` с пустой страницей) | единый `normalizeStatsPaging(limit, offset)` → `limit.coerceIn(1, 1000)`, `offset.coerceAtLeast(0)`; применён во всех четырёх местах (включая `getStatBySong`, где раньше нормализовался только `limit`) | контрактная (1) + поведенческая (1) |
| BR-197 | `model/Uuids.kt:153-156` + `Uuids.kt:7-18` | PERF | на каждый ключ MLT-модели — отдельный `SELECT … WHERE id = ?` (тысячи round-trip'ов на песню), для новых — ещё и отдельный `INSERT` | кеш пар `(id, uuid)` на процесс: одна загрузка `SELECT id, uuid FROM tbl_uuids` + раздача из памяти; новые ключи пишутся в кеш сразу после вставки (с перечитыванием из БД, если строку создал другой процесс — иначе uuid в MLT не совпадал бы с uuid в БД) | контрактная (1) |
| BR-198 | `model/StemJob.kt:266-281` + `karaoke-web/.../PublicStemJobController.kt:92-103` | RACE | лимит очереди (`MAX_ACTIVE_JOBS_PER_USER = 5`) — check-then-act: два параллельных `POST /create` оба видели `activeCount = 4` и создавали по заданию (6+ активных); при `SQLException` счётчик подменялся нулём — недоступность БД **полностью отключала** проверку лимита | `StemJob.createIfUnderLimit()` — проверка и вставка в одной транзакции под `pg_advisory_xact_lock(site_user_id)`; `countActiveByUser` при сбое возвращает `COUNT_ERROR = -1` → задание **не создаётся** (fail-closed), контроллер → `503 stemjobs.unavailable`; при лимите — `429 queue_limit_reached` | контрактная (1) |
| BR-199 | `model/Uuids.kt:30-33` | ERR | SQL `UPDATE tbl_uuids SET uuid = ?, WHERE id = ?` — лишняя запятая делала запрос синтаксически невалидным (любой вызов бросал `SQLException`); `PreparedStatement` не закрывался; ошибка уходила молча в UI-поток | `UPDATE tbl_uuids SET uuid = ? WHERE id = ?`, statement — в `use {}`, ошибка логируется через SLF4J (`infra.uuids`) с `sqlState` и стеком | контрактная (1) |

### «До / после»: команда и вывод

```bash
git stash push -m "s3-24-batch2-before-check" -- karaoke-app/src/main karaoke-web/src/main
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.PkgS324Batch2*'
git stash pop
```

Вывод «до» (12 тестов, 12 упали):

```
PkgS324Batch2SourceContractsTest > BR-189 конфликт публикаций не затирается молча() FAILED
PkgS324Batch2SourceContractsTest > BR-191 дата публикации разбирается безопасно() FAILED
PkgS324Batch2SourceContractsTest > BR-199 update uuid синтаксически валиден() FAILED
PkgS324Batch2SourceContractsTest > BR-192 результаты поиска удаляются одним запросом() FAILED
PkgS324Batch2SourceContractsTest > BR-193 счётчик чата помечает ошибку отдельным значением() FAILED
PkgS324Batch2SourceContractsTest > BR-193 сбой чата не выглядит как пустой чат() FAILED
PkgS324Batch2SourceContractsTest > BR-191 неверная дата не разбирается() FAILED
PkgS324Batch2SourceContractsTest > BR-197 uuid читаются пакетно() FAILED
PkgS324Batch2SourceContractsTest > BR-194 флаг приветствия ставится только после успеха() FAILED
PkgS324Batch2SourceContractsTest > BR-196 пагинация статистики нормализована() FAILED
PkgS324Batch2SourceContractsTest > BR-198 лимит очереди заданий атомарен() FAILED
PkgS324Batch2SourceContractsTest > BR-190 ключ фильтра даты совпадает с контроллерами() FAILED
12 tests completed, 12 failed
```

Показательные сообщения «до»:

```
BR-193: println в модели чата заменён на SLF4J (BR-193) ==> expected: <false> but was: <true>
BR-196: нужен единый helper нормализации пагинации (BR-196) ==> expected: <true> but was: <false>
BR-191: нужен публичный разбор даты публикации ==> expected: not <null>
```

Вывод «после» — `BUILD SUCCESSFUL` (12/12 проходят).

Регрессионный тест партии 2: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS324Batch2SourceContractsTest.kt`
(12 тестов; новые API проверяются через рефлексию — чтобы класс собирался и на старом коде).

### Побочные эффекты правок (важно для ревью)

1. **BR-193 меняет коды ответов чата**: `200` → `503` при сбое БД (`chat.messages.unavailable`,
   `chat.count.unavailable`, `chat.mark_read.unavailable`, `chat.threads.unavailable`); счётчики
   чата на публичной стороне возвращают `-1` (признак ошибки) вместо `0`.
2. **BR-191** теперь отклоняет сохранение песни с датой/временем вне формата (`dd.MM.yy` / `HH:MM`)
   — `400` с текстом ошибки. В БД уже есть legacy-значения; они не мигрируются (миграции
   запрещены пакетом), но больше не роняют страницу публикаций — просто пропускаются с WARN.
3. **BR-189** при конфликте слота первая песня остаётся в сетке (раньше её затирала вторая),
   а вторая не показывается — и это видно в журнале. Само ограничение на уровне БД (UNIQUE) —
   за владельцем.
4. **BR-198** при недоступной БД задание **не создаётся** (fail-closed), клиент получает `503`.
5. **BR-197** кеш uuid живёт в процессе: после перезапуска он перечитывается из БД.

### За владельцем (партия 2)

| Вопрос | Почему за владельцем | Что сделано в коде meanwhile |
|---|---|---|
| Частичный UNIQUE-индекс на `(publish_date, publish_time)` (BR-189) | требует миграции и дедупликации существующих дублей (запись в БД запрещена пакетом) | конфликт слота больше не затирается молча — WARN с id песен |
| Дедупликация авторов + UNIQUE на `tbl_authors.author` (BR-184, партия 1) | то же | дубль не создаётся при сбое чтения (`throwOnError`) |
| Гонка «два параллельных премиум-события → два приветственных сообщения» (BR-194) | требует CAS-обновления в БД (`UPDATE … WHERE welcome_message_sent = false`) в одной транзакции с вставкой сообщения | флаг ставится только после успешного создания и сохранения; дубль остаётся возможен при параллельных событиях (S3) |

---

## Партия 3 — BR-202…BR-204, BR-207…BR-212, BR-215 (10 записей, шаблоны `karaoke-app`)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-202 | `templates/main.html:296` | COMPAT | `<datalist id="list_authors">` читал `${songAuthors}`, а `MainController.main()` кладёт в модель `authors` → автодополнение автора на главной было пустым | `th:each="author:${authors}"` | контрактная (1) |
| BR-203 | `templates/editsubs.html:434, 447, 690, 838` | SEC | пользовательский текст (label маркера, хвост текста, текст песни) вставлялся через `innerHTML` без экранирования: метка вида `<img src=x onerror=…>` исполнялась в браузере админа (метки пишутся через `savesourcemarkers`) | `escapeHtmlText()` (textContent → innerHTML) для label и хвоста текста; текст песни в `<textarea>` — через `.value` (иначе HTML-сущности декодировались и портили текст) | контрактная (1) |
| BR-204 | `templates/editsubs.html:365-373` | ERR | скорость бралась из несуществующего свойства `checked` у `<button>`, а `return` внутри `forEach` выходил из колбэка, а не из функции → всегда возвращалось `1.0` (в разметке активна `0.75`), редактор субтитров стартовал на неверной скорости | признак выбора — класс `active`; обычный цикл с `return` | контрактная (1) |
| BR-207 | `templates/songs.html:673-690`, `:496` | ERR | «Сохранить» без выбранной строки → `TypeError` на `currentRow.cells`; строка перекрашивалась **до** запроса, и при ошибке (500 из-за отсутствующих полей формы) экран показывал «сохранённые» значения, которых в БД нет; ошибка не показывалась | проверка `if (!id || !currentRow)`; DOM обновляется только в `success` (после 2xx); `error`-обработчик с текстом причины; `updateTableRowFromFormElements` защищён проверкой `currentRow` | контрактная (1) |
| BR-208 | `templates/songs.html:1084-1100` + `MainController.doDeleteSong` | RACE | в `success` удалялся **глобальный** `currentRow` (он меняется при каждом клике по строке): если во время запроса выбрать другую песню, из DOM исчезала она, а удалённая оставалась на экране; сервер всегда отдавал `0`, поэтому клиент считал удаление успешным даже когда песни не было | строка запоминается до запроса (`var row = currentRow`) и удаляется именно она; сервер возвращает число удалённых строк (`1`/`0`), клиент проверяет результат и показывает ошибку при `0`; добавлен `error`-обработчик | контрактная (1) |
| BR-209 | `templates/songs.html:2177-2181` | COMPAT | оба `<datalist>` читали `${songAuthors}`/`${songAlbums}`, а модель содержит `authors`/`albums` → автодополнение автора и альбома не работало | `th:each="author:${authors}"`, `th:each="album:${albums}"` | контрактная (1) |
| BR-210 | `templates/songs.html:723-730` | COMPAT | Telegram-фильтры TL/TK/TC были в форме, но не попадали в URL → фильтр молча не применялся | три фильтра читаются из DOM и передаются как `flag_telegram_lyrics`/`flag_telegram_karaoke`/`flag_telegram_chords` | контрактная (1) |
| BR-211 | `templates/songs.html:88-100`, `:1732-1736` | COMPAT | подписка на STOMP `SockJS('/api/message')` — эндпоинта в `karaoke-app` нет (брокер удалён в 2024-08-15) → live-обновлений не было с 2024 года; payload читался по несуществующим ключам `recordChangeId`/`recordChangeTableName`/`recordChangeDiffs` и по старому имени таблицы `tbl_settings` | переход на актуальный канал SSE `GET /api/subscribe` (тот же, что в webvue3): payload `{type:"recordChange", data:{recordId,tableName,diffs}}`, имя таблицы — `tbl_songs`; добавлены `onerror` (обрыв соединения виден) и безопасный разбор JSON | контрактная (1) |
| BR-212 | `templates/songs.html` (34 места) | COMPAT | `navigator.clipboard.writeText` доступен только в secure-контексте, а админка `karaoke-app` слушается по HTTP (`http://<host>:8898`) → `navigator.clipboard` = `undefined`, каждая кнопка копирования бросала TypeError | `copyToClipboardSafely()`: Clipboard API → при отказе/отсутствии fallback на существующий `copyToClipboard` (execCommand); все 34 вызова переведены на helper | контрактная (1) |
| BR-215 | `templates/songs2.html:579-618` | COMPAT | то же, что BR-210, для страницы `songs2`: Telegram-фильтры не попадали в URL | три фильтра читаются из DOM и передаются в URL | контрактная (1) |

### «До / после»: команда и вывод

```bash
git stash push -m "s3-24-batch3-before-check" -- karaoke-app/src/main karaoke-web/src/main
GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.PkgS324Batch3*'
git stash pop
```

Вывод «до» (10 тестов, 10 упали): BR-202, BR-203, BR-204, BR-207, BR-208, BR-209, BR-210,
BR-211, BR-212, BR-215 — `10 tests completed, 10 failed`.

Вывод «после» — `BUILD SUCCESSFUL` (10/10 проходят; полный прогон пакета — 750 тестов).

Регрессионный тест партии 3: `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS324Batch3SourceContractsTest.kt`
(10 тестов; проверяются тексты шаблонов — класс собирается и на старом коде).

### Побочные эффекты правок (важно для ревью)

1. **BR-211 переводит страницу `/songs` на SSE.** Раньше STOMP-подписка не работала с 2024-08-15,
   теперь live-обновления приходят по `GET /api/subscribe` (тот же канал, что в webvue3).
   События приходят по всем таблицам; страница реагирует только на `tbl_songs`.
2. **BR-208 меняет ответ `GET /song/{id}/delete`**: было всегда `0`, стало `1`/`0`. Клиент
   `songs.html` проверяет результат. Других потребителей эндпоинта в репозитории нет.
3. **BR-203** экранирует label маркеров и хвост текста в редакторе субтитров — в тексте больше
   не работают HTML-теги (раньше работали — это был вектор XSS).
4. **BR-210/BR-215** включают Telegram-фильтры в URL — после правки фильтр применяется
   (раньше страница выглядела отфильтрованной, а данные не фильтровались).

### За владельцем (партия 3)

| Вопрос | Почему за владельцем | Что сделано в коде meanwhile |
|---|---|---|
| Проверка результата удаления песни на других legacy-страницах | `GET /song/{id}/delete` — GET-мутация, общий для нескольких страниц | ответ стал `1`/`0`, клиент проверяет |
| Полный перенос legacy-страниц `/songs`, `/songs2` на webvue3 | большой объём (BR-225 в аудите) | точечные фиксы сделаны, страницы работают |

---

## Уже исправленные волнами A–D (входят в 40 записей пакета, но закрыты ранее)

| BR | Где исправлено | Проверка |
|---|---|---|
| BR-182 | `specs/383-pkg-sec-sqli/report.md` (SQL-инъекция в `Author.getWhereList`) | контрактная в пакете #383 |
| BR-185 | `specs/385-pkg-err-stats-zero/report.md` (`MonetizationStats` не отдаёт нули при сбое БД) | поведенческая в пакете #385 |
| BR-188 | `specs/375-pkg-s2-12/report.md` (`Publication.getUnPublicationList` — вечный цикл) | контрактная в пакете #375 |
| BR-195 | `specs/375-pkg-s2-12/report.md` (`StatsCacheKey.params` — параметры в ключ кеша) | контрактная в пакете #375 |
| BR-200 | `specs/375-pkg-s2-12/report.md` (пагинация публикаций) | контрактная в пакете #375 |
| BR-201 | `specs/375-pkg-s2-12/report.md` (`processes.html` — STOMP-брокер) | контрактная в пакете #375 |
| BR-205 | `specs/375-pkg-s2-12/report.md` (`songs.html` — поля формы vs параметры контроллера) | контрактная в пакете #375 |
| BR-206 | `specs/375-pkg-s2-12/report.md` (`songs.html` — рендер всего списка) | контрактная в пакете #375 |
| BR-213 | `specs/375-pkg-s2-12/report.md` (`songs2.html` — `song_resultVersion`) | контрактная в пакете #375 |
| BR-214 | `specs/375-pkg-s2-12/report.md` (`songs2.html` — пагинация) | контрактная в пакете #375 |

Правки этих записей в этом пакете **не дублируются** — заново закрывать их нельзя, реестр
(`registry.csv`) их не меняет (вне правки пакета).

---

## Финальные проверки (CI-набор)

Выполнено в worktree `/home/nsa/Karaoke/.worktrees/waveE-s3-24`, ветка `623-pkg-s3-24`
(база `b2c4b06f`).

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test` | BUILD SUCCESSFUL (750 тестов, 0 падений) |
| `ktlintCheck` | BUILD SUCCESSFUL |
| `bash tools/check-knowledge-structure.sh` | OK (9/9) |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | OK |
| `python3 tools/check-doc-references.py` | OK (ссылок 4332, путей 2607, эндпоинтов 353) |
| `python3 tools/check-recordhash-consistency.py` | OK |
| `bash tools/check-no-mp4-mentions.sh` | OK (139 упоминаний — в baseline) |
| `bash tools/check-feature-doc.sh docs/features/*.md` | OK (все документы валидны) |
| `bash tools/check-subagent-isolation.sh` | OK |
| `bash tools/check-file-permissions.sh` | OK (baseline: 100 совпадений) |
| `python3 tools/check-ssot-impact.py` | OK (0 нарушений) |

**Фронтенд (`webvue3`, `karaoke-public`):** файлы фронтенда этим пакетом **не менялись**
(`git diff origin/master -- webvue3 karaoke-public` — пусто), поэтому их проверки
(`npm run lint`, `npm run test:unit`) дают **предсуществующие** падения, не связанные с
пакетом:

- `webvue3`: `npm run lint` → `ESLint couldn't find an eslint.config.(js|mjs|cjs) file`
  (ESLint 10.5.0 не поддерживает `.eslintignore`, flat-конфига в репозитории нет);
  `npm run test:unit` → падает `src/player/__tests__/karaoke-player-s2-18.test.js`;
- `karaoke-public`: `npm run lint` — ошибка конфигурации ESLint (та же причина),
  `npm run test:unit` — 0 падений.

Исправление фронтовой тулчейки — вне периметра пакета `PKG-S3-24` (фронтенд ведут пакеты
`PKG-S3-31`…`PKG-S3-33`).

## Коммиты

| Коммит | Содержание |
|---|---|
| `4abfd195` | партия 1: BR-174…BR-187 (10 записей) |
| `930be882` | партия 2: BR-189…BR-199 (10 записей) |
| `f3982127` | партия 3: BR-202…BR-215 (10 записей) |
| `1c1c0e79` | SSoT (`knowledge/`) + per-feature документ (`docs/features/pkg-s3-24.md`) |

## Итог

- **Закрыто 30/40 записей пакета** (партии 1–3); ещё **10 записей закрыты волнами A–D**
  (BR-182, BR-185, BR-188, BR-195, BR-200, BR-201, BR-205, BR-206, BR-213, BR-214) —
  их правки не дублировались, ссылки на пакеты приведены выше.
- **Регрессия**: 3 класса, 41 тест (`PkgS324Batch1*` — 19, `PkgS324Batch2*` — 12,
  `PkgS324Batch3*` — 10). На старом коде падают **все** тесты каждой партии
  (проверено через `git stash` изменённых исходников), после правки — проходят.
- **Регрессионные тесты, которые не удалось сделать поведенческими** (SQL-путь к живой БД,
  запись в БД и запуск контейнеров пакетом запрещены): BR-177, BR-178, BR-184, BR-186,
  BR-187, BR-189, BR-190, BR-192, BR-193 (частично), BR-194, BR-196 (частично),
  BR-197, BR-198, BR-199 — для них доказательство «до/после» даёт контрактный тест по
  тексту исходника (падает на старом коде). Поведенческими сделаны BR-174, BR-179, BR-180,
  BR-183, BR-191 (разбор даты), BR-193 (признак ошибки счётчика), BR-196 (нормализация).

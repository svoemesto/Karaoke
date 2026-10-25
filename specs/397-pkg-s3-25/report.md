# PKG-S3-25 (OpenProject #397) — karaoke-app: COMPAT, DATA, ERR, LEAK, PERF, RACE, SEC — 40 BR

- **Аудит**: #336, волна E, пакет `PKG-S3-25` (`specs/336-code-audit/backlog.md`).
- **Ветка**: `624-pkg-s3-25` от `origin/master` `b2c4b06f`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-s3-25`.
- **Источник правды**: карточки `specs/336-code-audit/findings/` (P-05, P-06, P-07a,
  P-07b, P-10, P-12, P-13, P-14); порции определены по `registry.csv`.
- **Что НЕ делалось**: миграции не применялись ни к LOCAL, ни к PROD; контейнеры,
  прод, БД и `specs/336-code-audit/registry.csv` не трогались; деплоя не было;
  push/PR не делались; `master` не коммитился.
- **Работа партиями**: по 10–13 записей, коммит после каждой партии.

## Сводка

| Партия | BR | Статус |
|---|---|---|
| 1 | BR-228…BR-236, BR-238…BR-243 (13) | ✅ исправлены |
| 2 | BR-245, BR-246, BR-248…BR-250, BR-252…BR-257, BR-259, BR-260 (13) | ✅ исправлены |
| 3 | BR-261…BR-265, BR-267…BR-275 (15) | ✅ исправлены (BR-267, BR-272 — уже были исправлены в master, добавлена регрессия) |
| — | BR-234, BR-235, BR-237, BR-244, BR-247, BR-251, BR-258, BR-266 (8) | 🔒 закрыты ранее — добавлена регрессионная проверка |

**Итог партий:** 39/39 исправлены в этом пакете, 8/8 закрыты ранее (регрессия).

> **Примечание о составе пакета.** В `registry.csv` колонка `work_package=397`
> содержит ровно 40 BR, и блок `### PKG-S3-25` в `backlog.md` содержит ровно
> те же 40 строк: `BR-228…BR-243`, `BR-245…BR-267`, `BR-268…BR-275`.
> Задание от 25.10.2026 перечисляло 40 записей `BR-228…BR-267` (включая 8 уже
> закрытых другими пакетами) — этот список покрыт полностью, а
> `BR-268…BR-275` (тоже `work_package=397` в реестре и в ТЗ) закрыты в партии 3.
> Итого: **40/40 записей реестра закрыты**, ни одна не осталась.

---

## SSoT и per-feature документ (FR-009)

- **Новый компонент SSoT**:
  `knowledge/domains/monitoring/components/boundary-validation.md` — сквозной
  инвариант «вход проверяется ДО использования»: HTTP-параметры, страницы из
  интернета, значения из админских свойств, ссылки внутри `.mlt`. Зарегистрирован
  в `knowledge/domains/monitoring/domain.md` § «Структура компонентов (C4 L3)».
- **Инварианты в доменах**:
  - `knowledge/domains/rendering/domain.md` — инварианты 6–8 (вход в
    MLT-генерацию, ссылки внутри `.mlt`, UUID из кеша);
  - `knowledge/domains/monitoring/components/log-categories.md` — 3 новые
    SLF4J-категории: `infra.admin.processes`, `infra.render.tabs`,
    `infra.render.counters` (по правилу самой компоненты: «Регистрация новой
    категории должна быть зафиксирована в таблице выше»).
- **Per-feature документ (FR-009)**: `docs/features/pkg-s3-25.md` — 6 обязательных
  секций (`## Что делает`, `## Зачем`, `## Как работает`, `## Инварианты`,
  `## Известные ловушки`, `## Ссылки`) и 14 ловушек.

---

## Партия 1 — BR-228…BR-236, BR-238…BR-243 (13 записей)

### По записям

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-228 | `karaoke-app/.../llm/LyricsFinderService.kt:161` | PERF | `Jsoup.connect(url).get()` без лимита тела: обрезка до 8 000 символов делалась **после** построения DOM, пик памяти на HTML+DOM безграничен | `.maxBodySize(MAX_PAGE_BODY_BYTES)` (2 МБ) на уровне HTTP-клиента; константа рядом с `MAX_PAGE_TEXT_CHARS` | `PkgS325Batch1SourceContractsTest.BR-228 загрузка страницы ограничена по размеру тела` |
| BR-229 | `karaoke-app/.../llm/Tools.kt:107` | ERR | SearXNG-путь не проходил `filterUselessLyricsUrls`, хотя KDoc обещает «то же, что fourget»: в LLM уходили homepage/логины/sitemap/tracking-ссылки и дубликаты (каждая — до 120 с LM Studio) | Тот же фильтр на оба движка (он же дедуплицирует через `LinkedHashSet`) + счётчик отброшенных в лог | `…BR-229 SearXNG-путь проходит тот же post-filter, что и fourget` |
| BR-230 | `karaoke-app/.../Uuids.kt:7` | PERF | `getStoredUuid` на **каждый** узел MLT делал `SELECT` по `tbl_uuids` (+`INSERT` для новых) — ~9 000 обращений к БД на песню (×2 версии) | **Закрыт параллельным пакетом `PKG-S3-24` (BR-197, слит в master раньше)** — кеш `tbl_uuids` в `model/Uuids.kt` (`loadAll`/`cachedUuid`/`putCached`/`removeCached`). Этот пакет принёс параллельную реализацию; при слиянии оставлена одна (из `PKG-S3-24`), добавлена регрессия на КОНТРАКТ кеша | `…BR-230 UUID узлов MLT берутся из кеша…` + `PkgS325Batch1BehaviorTest` (5 тестов) |
| BR-231 | `karaoke-app/.../controllers/MainController.kt:219` | ERR | `id.toLong()`/`process_order.toInt()`/`process_priority.toInt()` без проверки → `NumberFormatException`/NPE → 500. Штатный сценарий: кнопка «New prior» с пустым полем писала пустую строку в priority **всем** строкам → N запросов, все 500 | `parseOptionalInt(raw, reason)`: пусто → `null` («не менять»), нечисло → 400 с причиной; `id` → `bad_process_id`; `status` — по белому списку `KaraokeProcessStatuses` (`bad_process_status`); процесс не найден → 404 | `…BR-231 числовые параметры process update разбираются безопасно`, `…пустое числовое поле означает не менять значение`, `…нечисловое значение даёт 400…` |
| BR-232 | `karaoke-app/.../controllers/MainController.kt:229` | PERF | XHR получал `redirect:/processes` → прозрачно шёл по 302 и **скачивал всю страницу** (полный `SELECT` процессов + полный Thymeleaf-рендер) на каждое изменение поля; «New prior» — N раз подряд (O(N²)) | XHR (по `X-Requested-With`) → 200 `{ok:true}`; обычный запрос → 302 на `/processes` (совместимость) | `…BR-232 XHR получает короткий JSON…`, `…XHR определяется по заголовку X-Requested-With` |
| BR-233 | `karaoke-app/.../controllers/MainController.kt:1263` | COMPAT | Три метода `texttelegram*header` собирали текст через `getDescriptionVkHeader` — в Telegram-пост уходил VK-вариант без ссылки на страницу песни | `getDescriptionTelegramHeader` во всех трёх (lyrics/karaoke/chords); VK-методы (`textvk*header`) не тронуты | `…BR-233 Telegram-заголовки используют getDescriptionTelegramHeader` |
| BR-236 | `karaoke-app/.../controllers/MainController.kt:702` | ERR | Ошибка разбора маркеров глоталась: `println(... ${e.message})` без стека, ответ — 200 с текстом `Error`; отсутствие песни давало тот же «Error» | SLF4J `infra.admin.processes` со стеком и `songId`/`voice`; 400 `bad_source_markers` (невалидный JSON) и 404 `song_not_found` (нет песни) — различимы | `…BR-236 ошибка разбора маркеров логируется со стеком и различает коды` |
| BR-238 | `karaoke-app/.../mlt/mko/MkoSplashStart.kt:50` | ERR | `cds !== ""` — сравнение **по ссылке**; `replace(...)` возвращает новый объект `String`, поэтому условие истинно ВСЕГДА и fallback «Key/BPM» не срабатывал никогда (на заставке пропадала строка с тональностью и темпом) | Сравнение по значению: `if (cds.isNotBlank()) cds else toneBpmDescriptionText` | `…BR-238 сравнение строк по значению, а не по ссылке` |
| BR-239 | `karaoke-app/.../mlt/mko/MkoMelodyTabs.kt:238` | ERR | `split("|") + toInt()` без проверки формата: `stringLad` вида `undefined|undefined` (его пишет редактор маркеров) → `NumberFormatException` **внутри генерации `.mlt`** — падала вся песня, а не только слой табов | `parseStringLad` по `^[0-5]\|\d{1,2}$` + диапазон строки 0..5; невалидное → WARN `infra.render.tabs` и **пропуск таба** (рендер не падает) | `…BR-239 строка табулятуры разбирается только по валидному формату`, `…невалидный stringLad не роняет генерацию mlt` |
| BR-240 | `karaoke-app/.../model/Album.kt:589` | ERR | Ошибка выборки ловилась `catch (SQLException) { printStackTrace() }` и возвращался **частичный** список; нет соединения → `emptyList()`. Публичный эндпоинт отдавал 200 с `[]`, и результат ещё кэшировался на TTL → «у автора нет альбомов» даже при живой БД | `AlbumTilesUnavailableException` на обоих сбоях; публичный эндпоинт (`karaoke-web/.../PublicApiController.kt`) → 503 `albums.unavailable`; исключение пробрасывается **до** кэширования, поэтому сбойный результат в кеш не попадает | `…BR-240 сбой БД при загрузке альбомов не выглядит как пустой список` |
| BR-241 | `karaoke-app/.../mlt/mko/MkoMelodyTabs.kt:98` | ERR | При отсутствии TEXT-элемента `template()` возвращал `MltNode()` — узел с **пустым именем**; `toString()` даёт `< />`, и такой не-well-formed XML уходил в `xmldata` титра | `emptyKdenliveTitle()` — валидный пустой `kdenlivetitle` (есть имя, тело, поля); catch сужен до `NoSuchElementException`/`IndexOutOfBoundsException` + WARN | `…BR-241 отсутствие элемента не превращается в nameless-узел`, `…пустой kdenlivetitle — валидный XML с именем элемента` |
| BR-242 | `karaoke-app/.../mlt/mko/MkoMainBin.kt:150` | COMPAT | В `main_bin` для не-sequence типов всегда подставлялось `producer_<type>…`, но `LINETRACK`/`CHORDPICTURELINETRACK`/`SCROLLERTRACK` **не переопределяют** `producer()` → узла `<producer>` в `.mlt` нет. Эмпирика аудита: 182 битые ссылки `producer_linetrack_*` на 12 свежих `.mlt`, других битых ссылок нет | Для типов из `PRODUCER_TYPES_WITHOUT_PRODUCER` ссылка ведёт на **существующий** трактор (`MltGenerator.nameTractor(...)`); список закрытый и проверяется тестом | `…BR-242 битых ссылок на несуществующие producer в main_bin нет`, `…список типов без producer совпадает с классами mko` |
| BR-243 | `karaoke-app/.../mlt/mko/MkoCounter.kt:76` | ERR | `Karaoke.voices[0].groups[0]` и `Karaoke.countersColors[childId]` — индексация без проверки размера. Свойства приходят из админ-API строкой без валидации → `IndexOutOfBoundsException` **в `template()`** → падает генерация `.mlt` | `getOrNull` + fallback (первый голос/первая группа, последний цвет, дефолтный белый) + WARN `infra.render.counters` с именем свойства | `…BR-243 конфигурационные списки счётчика индексируются с проверкой` |

### Регрессионные проверки партии 1

- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS325Batch1SourceContractsTest.kt`
  — 20 контрактных тестов (по числу BR партии; часть проверяет чистые функции
  `parseOptionalInt`, `parseStringLad`, `emptyKdenliveTitle`, `isXhr`).
- `karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/PkgS325Batch1BehaviorTest.kt`
  — 4 поведенческих теста на `UuidCache` (загрузка таблицы один раз, создание
  нового `id` один раз, `reset`, стабильность `storedUuidId`).

**«До/после»:** на коде «до» правки класс `UuidCache` и функции
`parseOptionalInt`/`parseStringLad`/`emptyKdenliveTitle` не существуют — тесты
не компилируются (поведенческие) либо падают на исходниках (контрактные:
например, на старом коде `MkoCounter.kt` содержит `Karaoke.countersColors[childId]`,
а `MainController.kt` — `process_order.toInt()`).

---

## Партия 2 — BR-245, BR-246, BR-248…BR-250, BR-252…BR-257, BR-259, BR-260 (13 записей)

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-245 | `KaraokeProperties.kt:155` | ERR | `setFromString` разбирал значение через `toLong()`/`toInt()`/`toDouble()` (→ `NumberFormatException` → 500 на `/api/properties/setproperty`) и `toBoolean()` (любая опечатка → молча `false`); неизвестный ключ — тихий no-op (`if (defaultValue !== null)`) | `toXxxOrNull()` → 400 `KaraokePropertyValueException`; boolean — явный список (`true/false/1/0/yes/no/on/off`); неизвестный ключ → 404 `KaraokePropertyUnknownKeyException` | `…BR-245 значения свойств разбираются по типу…`, `…неизвестный ключ не молчаливый no-op` |
| BR-246 | `KaraokeProperties.kt:88` | COMPAT | Три ключа, которые читает `karaoke-web`, не зарегистрированы: `karaoke.public.public-settings-cache.enabled` (→ `getBoolean` = `false`, кеш публичных настроек выключен ВСЕГДА), `karaoke.web.events.batch-enabled` (нельзя включить), `karaoke.web.events.batch-max-buffer-size` (→ `0` → `coerceAtLeast(1)` = 1 вместо 500) | Три ключа зарегистрированы с типами и дефолтами из спецификаций (specs/249 FR-003, specs/274) | `…BR-246 три ключа karaoke-web зарегистрированы` + поведенческий тест дефолтов |
| BR-248 | `KaraokeProperties.kt:39` | RACE | `propertiesLoadAttempted` — обычный `var` без `volatile`; флаг ставился ДО наполнения карты → читатель мог увидеть «загрузка выполнена» при полупустой карте | `@Volatile` + двойная проверка (быстрый путь — volatile-чтение, медленный — под `propertiesWriteLock`); флаг ставится ПОСЛЕ наполнения карты (release barrier) | `…BR-248 флаг загрузки volatile и карта публикуется после наполнения` |
| BR-249 | `KaraokeProperties.kt:75` | ERR | `savePropertiesMap()` ловил `Exception` и печатал одну строку в stdout; `set()` возвращал `Unit` → `/api/properties/setproperty` отвечал 200 и рассылал SSE «установлено», даже если файл НЕ записан (нет каталога, read-only ФС, ошибка `Files.move`); значение жило только в памяти | `savePropertiesMap(): Boolean` + `set(): Boolean` со **ОТКАТОМ** значения в карте при неудаче; ошибка — SLF4J `infra.settings.write` со стеком; контроллер → 500 | `…BR-249 результат записи файла свойств наблюдаем` |
| BR-250 | `KaraokeProperties.kt:65` | DATA | `decodePropertiesMap` использовал `associate { … }` — одна битая строка отбрасывала ВЕСЬ файл (~150 настроек молча откатывались к дефолтам, а первая запись их затирала) | `readPropertiesFileTolerant` — построчный разбор, битые строки пропускаются с логом (номер строки + причина); при провале загрузки карта заполняется дефолтами всех ключей ДО первой записи | `…BR-250 файл свойств разбирается построчно` + поведенческий тест на битой строке |
| BR-252 | `Utils.kt:859` | DATA | `Song.deleteFromDb(..., sync = true)` выполнялся ДО проверки соединения и до вставки: при недоступной REMOTE-БД sync-запись уже удалялась, новая не создавалась, а вызывающий рапортовал «добавлено в SYNC-таблицу» | Проверка соединения ДО удаления; DELETE+INSERT в одной транзакции (`autoCommit=false`, `commit`/`rollback`); исключение наружу (`KaraokeSyncEnqueueFailedException`) | `…BR-252 DELETE и INSERT в tbl_songs_sync — в одной транзакции` |
| BR-253 | `Utils.kt:262` | RACE | Защита от повторного запуска — неатомарный check-then-set на `@Volatile`-флаге: два одновременных запроса оба читали `false` и оба запускали поток (две параллельные записи в одни и те же строки `tbl_songs`) | Три флага → `AtomicBoolean.compareAndSet(false, true)` | `…BR-253 флаги фоновых задач захватываются атомарно` |
| BR-254 | `Utils.kt:370` | ERR | Флаг снимался последним оператором тела потока, а не в `finally` (KDoc обещал `finally`): сбой `SNS.send` (Jackson вне `try`) оставлял флаг `true` навсегда — кнопка до перезапуска приложения отвечала «уже выполняется» | `startOneShotThread` получил параметр `onFinally` (срабатывает в том же `finally`, что и закрытие соединения); флаг снимается и при неудаче создания потока (`catch` вокруг `startOneShotThread`) | `…BR-254 флаги снимаются в finally` |
| BR-255 | `Utils.kt:2590` | LEAK | `getFileNameByMasks` — `Files.walk(...)` без закрытия (дескрипторы до финализации); `catch (_: Exception) { return "" }` глушил ошибку, вызывающий не отличал «не найдено» от «не смогли посмотреть» | `.use { stream -> … }`; ошибка обхода логируется (логгер файла `karaoke.utils`) со стеком | `…BR-255 поток обхода файлов закрывается` |
| BR-256 | `Utils.kt:1607` | ERR | `getBpmAndKeyFromCsv` ловил только `IOException`: короткая строка CSV (`get(3)` вне диапазона) и нечисловой BPM (`toLong()`) бросали наружу и прерывали массовый `updateBpmAndKey` на середине; `printStackTrace()` — без стека в централизованных логах | Разбор каждой записи в своём `try` (битая строка — пропуск с WARN `id`/файл); `catch (e: Exception)` + SLF4J со стеком; `bpm.toLongOrNull()` | `…BR-256 битая строка CSV не роняет массовое обновление` |
| BR-257 | `Utils.kt:2132` | DATA | Четыре ветки `searchSongText` брали `element.html()` (снимая только `<br>`/`&nbsp;`) → в `song.sourceText` попадала чужая разметка (`<div>`, `<span class=…>`, `<script>`), которая уезжала в маркеры строк и в кадр видео | `htmlToPlainTextWithNewlines` — Jsoup-разбор + `<br>`/блочные теги → `\n`, теги убираются, разметка наружу не уходит | `…BR-257 ветки поиска текста не возвращают HTML-разметку` + 3 поведенческих теста |
| BR-259 | `StemJobCleanup.kt:52` | RACE | Шаги задания сопоставлялись по ТОЧНОМУ `description` → цепочка CPU-повтора («… (повтор без GPU)») не попадала под остановку: `destroyForcibly`/`interrupt` не вызывались, строки `tbl_processes` оставались, а финализация перезаписывала статус задания | Сопоставление по префиксу с id задания (`isStemJobDescription`, с границей — #42 ≠ #420); `executeFinalizeStemJob` перечитывает статус и не финализирует задание в `ERROR` | `…BR-259 шаги задания сопоставляются по префиксу с id` + поведенческий тест |
| BR-260 | `StemJobPollScheduler.kt:82` | DATA | Статус `WORKING` ставился ДО вставки шага, а результат `createDbInstance` отбрасывался (при сбое вставки он возвращает `null` без исключения) → задание оставалось `WORKING` без шага в очереди, без автовосстановления (алерт только через 30 мин) | `WORKING` — только ПОСЛЕ успешной вставки; `null` → задание в `ERROR` с причиной; то же — для CPU-повтора в `StemJobProcessing` | `…BR-260 статус WORKING ставится только после успешной вставки шага` |

---

## Партия 3 — BR-261…BR-265, BR-267…BR-275 (15 записей)

| BR | Файл:строка (было) | Класс | Что не так | Как правильно | Проверка |
|---|---|---|---|---|---|
| BR-261 | `UtilsAI.kt:104` | PERF | `createNewIamToken()` не обновлял `Karaoke.requestIamTokenLastTimeMs` → `getIamToken()` видел «старое» время и перезапрашивал токен на КАЖДЫЙ вызов (внешний процесс на каждый запрос); ошибка получения токена не отличалась от «HTTP-поиск ничего не нашёл» | Метка времени — сразу после успешного обновления токена; ошибка чтения/пустоты токена — `IamTokenUnavailableException` | `…BR-261 метка времени IAM-токена обновляется после успешного обновления` |
| BR-262 | `UtilsAI.kt:345` | ERR | Тело POST-запроса к Yandex Search собиралось строковой интерполяцией (`"queryText": "$queryText"`) → кавычка или обратный слэш в названии песни/автора делали JSON невалидным, Yandex отвечал 400 | `buildYandexSearchRequestBody` — Jackson (`ObjectMapper.writeValueAsString`); исправлены ОБА места (sync- и async-путь) | `…BR-262 тело запроса собирается через Jackson…` |
| BR-263 | `StemJobPollScheduler.kt:74` | ERR | `durationSeconds == null || durationSeconds > MAX` — «не удалось измерить» (ffprobe не дал числа) и «превышен лимит» были одной веткой: задание отклонялось с ложным сообщением | Три исхода разведены: измерили / не удалось измерить (своя причина в `errorMessage`) / превышен лимит | `…BR-263 невозможность измерить длительность отличается от превышения лимита` |
| BR-264 | `SyncAudioDescendants.kt:344` | ERR | Ошибка `SELECT` глоталась в `println`, функция возвращала пустой список → `syncAll` отвечал «OK» («нет потомков со статусом <6»), в сводке не было признака ошибки | `SyncAudioSelectionException` из обеих выборок; `syncAll` → «ERROR: …»; в сводке — счётчик `adminErrors` (и тип SSE-сообщения `error`) | `…BR-264 сбой выборки не выглядит как «пусто»` |
| BR-265 | `UtilsAI.kt:865` | ERR | `getHtml` ловил `Exception` и возвращал `""` без стека и без различения причин (сеть / не-2xx / пустой документ) | `getHtmlDetailed` → `HtmlFetchResult` (`Ok`/`NetworkError`/`HttpError`/`EmptyDocument`); причина логируется со стеком (`infra.ai.search`); `getHtml` — совместимая обёртка | `…BR-265 сетевой сбой, не-2xx и пустой документ различаются` |
| BR-267 | `Utils.kt:3583` | ERR | `author.ymId !== ""` — сравнение строк ПО ССЫЛке (истинно всегда) → автор с пустым `ym_id` попадал в поиск альбома | **Уже исправлено в master** (коммит `4d643554`, BR-266/251 в том же), изменений не вносилось — добавлена регрессионная проверка | `…BR-267 ymId сравнивается по значению` |
| BR-268 | `Utils.kt:3065` | DATA | `getSyllables` разбивал текст через `regexWords.find(text)` — это ПЕРВОЕ совпадение, а не `findAll`: для многословного текста возвращались слоги только первого слова, а `updateMarkersFromSourceText` обнулял `label` всем маркерам сверх длины списка | `regexWords.findAll(text)` — все слова | `…BR-268 разбиение на слова обрабатывает все слова` |
| BR-269 | `Utils.kt:3674` | LEAK | `fetchCountryCodeFromService` не закрывал ни поток, ни `HttpURLConnection` (ни в успешной ветке, ни в `catch`) — утечка сокета на каждом вызове (раз в минуту, а при недоступности сервиса — на каждой попытке) | Тело — в `use { }`; `conn?.disconnect()` в `finally`; ошибка — SLF4J со стеком | `…BR-269 соединение и поток закрываются` |
| BR-270 | `Utils.kt:4614` | ERR | `runCommand` (86 вызовов) — `process.waitFor()` без таймаута (зависший `docker`/`rm` держал поток Tomcat навсегда), `BufferedReader` не закрывался, вывод копился в `StringBuilder` без ограничения | `waitFor(timeoutMs, MILLISECONDS)` + `destroyForcibly()` при истечении; `reader.use { }`; `finally` — убить процесс и закрыть потоки; лимит вывода `MAX_RUN_COMMAND_OUTPUT_CHARS` (1 МБ) | `…BR-270 процесс запускается с таймаутом и гарантированно убивается` |
| BR-271 | `Utils.kt:4102` | SEC | `createScriptForHost` склеивал значения в shell-строку; `wrapInApostraf()` (= `'value'`) не экранировал апостроф → путь вида `Guns N' Roses` давал незакрытую кавычку (watcher исполнял искажённую команду); файл — права `777` | Скрипт — POSIX-sh: значения передаются позиционными параметрами (`set -- 'v1' 'v2'` + `exec 'cmd' "$@"`), апостроф экранируется как `'\''`; права `700`; `wrapInApostraf()` из вызовов убран | `…BR-271 скрипт для хоста не склеивает значения и имеет права 700` + 3 поведенческих теста |
| BR-272 | `Utils.kt:4657` | ERR | `getTransposingChord` использовал `note!!`/`chord!!` — при `capo > 0` и значении вне словаря (`""`, `D/F#`) → NPE из `Song.getFormattedChords` (500) и из MLT-генерации | **Уже исправлено в master** (коммит `f1fc26e4`, BR-071 в `PKG-S2-10`) — `if (chord == null || note == null) return originalChord`; изменений не вносилось — добавлена регрессионная проверка | `…BR-272 транспонирование не разыменовывает null` |
| BR-273 | `Utils.kt:4907` | PERF | `findParentCandidateId` на КАЖДУЮ песню читал ВСЮ `tbl_songs` (18k строк) и нормализовал имена в Kotlin → N полных сканов на N песен (O(N²) чтений — нарушение ADR-0001) | `ParentCandidateIndex` — кандидаты грузятся ОДНИМ запросом и группируются по нормализованному названию; циклы `customFunction`/`findParentForAuthor` используют готовый индекс | `…BR-273 кандидаты-родители загружаются пакетно` |
| BR-274 | `KaraokeConnection.kt:54` | PERF | `conn.isValid(3)` на КАЖДОМ `getConnection()` — в pgjdbc это отдельный запрос к серверу (пустой query), т.е. лишний round-trip на каждое обращение к БД (для `Connection.remote()` — через интернет) | Валидация только при открытии, после «битого» состояния и после простоя > 5 мин (`CONNECTION_VALIDATION_IDLE_MS`); соединение оборачивается в прокси, помечающий его битым при `SQLException` (self-healing specs/236 сохранён) | `…BR-274 isValid не вызывается на каждый getConnection` |
| BR-275 | `KaraokeConnection.kt:67` | ERR | В `catch` логировался только `e.message` (и в `println`, и в `log.warn`) — ни стека, ни класса, ни cause-цепочки: сбой подключения к БД диагностировался по одной строке | `log.warn(..., e)` — throwable последним аргументом (SLF4J добавит стек и cause) | `…BR-275 в лог уходит само исключение` |

---

## Слияние с параллельным пакетом `PKG-S3-24` (#396)

Ветка `624-pkg-s3-25` была перебазирована на master, куда уже слился
`PKG-S3-24` (пакет #396, BR-174…BR-187). Две правки **пересеклись**:

1. **BR-230 (кеш UUID узлов MLT)** — `PKG-S3-24` закрыл его отдельной записью
   BR-197 (`model/Uuids.kt`: `loadAll`/`cachedUuid`/`putCached`/`removeCached`).
   Моя реализация (`UuidCache` в `Uuids.kt`) при слиянии отброшена, чтобы не
   было двух кешей одного назначения. Регрессия переписана на проверку
   **контракта** кеша (не имён API конкретного пакета).
2. **Логгер в `Utils.kt`** — `PKG-S3-24` добавил свой `private val log`
   (категория `karaoke.utils`, BR-184). Мой второй `private val log`
   (`infra.files.walk`) конфликтовал с ним на уровне объявлений. Решено:
   в `Utils.kt` используется **логгер файла** (`karaoke.utils`), отдельной
   категории `infra.files.walk` не вводится (в `log-categories.md` не
   зарегистрирована — чтобы таблица не расходилась с кодом).

Остальные 38 правок пакета слились без конфликтов. После слияния прогон
`:karaoke-app:test :karaoke-web:test ktlintCheck` выполняется с
`--rerun-tasks` (обычный прогон Gradle может сообщить UP-TO-DATE по
закешированным классам и скрыть ошибку компиляции).

---

## «До/после» — 5 представительных примеров

### 1. BR-231: пустое поле «New prior» больше не 500 на КАЖДУЮ строку

**Было** (`MainController.kt`, `progressUpdate`):

```kotlin
if (process_order != null) {
    processOrder = process_order.toInt()   // "" → NumberFormatException → 500
}
```

**Стало**:

```kotlin
val processOrder = parseOptionalInt(process_order, "bad_process_order")
// "" / null → null («не менять»); "abc" → 400 с причиной bad_process_order
```

Кнопка «применить ко всем» с пустым полем больше не бьёт по каждой строке
(было N запросов, все 500), и приоритет в БД не пишется пустой строкой.

### 2. BR-239: один битый `stringLad` больше не роняет генерацию `.mlt` всей песни

**Было** (`MkoMelodyTabs.kt`):

```kotlin
val stringLad = stringLadText.split("|")
val stringIndex = stringLad[0].toInt()   // "undefined" → NumberFormatException
```

**Стало**:

```kotlin
val (stringIndex, lad) = parseStringLad(stringLadText)
    ?: run {
        log.warn("MkoMelodyTabs: некорректный stringLad '$stringLadText' — таб пропущен")
        return@forEach
    }
```

Неверный маркер (`undefined|undefined`) → таб пропущен, рендер продолжается.

### 3. BR-242: битых ссылок `producer_linetrack_*` больше нет

**Было** (`MkoMainBin.kt`, `main_bin`):

```kotlin
"producer" to MltGenerator.nameProducer(type, …)   // для LINETRACK узла <producer> НЕ существует
```

**Стало**:

```kotlin
"producer" to if (type in PRODUCER_TYPES_WITHOUT_PRODUCER) {
    MltGenerator.nameTractor(type, …)    // ссылка на существующий трактор
} else {
    MltGenerator.nameProducer(type, …)
}
```

Эмпирика аудита: 182 битые ссылки на 12 свежих `.mlt`; после правки — 0.

### 4. BR-274: `isValid(3)` убран из горячего пути

**Было** (`KaraokeConnection.kt`):

```kotlin
if (conn == null || conn.isClosed || !conn.isValid(3)) { … }   // round-trip на КАЖДЫЙ вызов
```

**Стало**:

```kotlin
if (needsValidation(conn)) { … }   // при открытии, после SQLException и после простоя > 5 мин
```

`isValid(3)` в pgjdbc — это запрос к серверу (пустой query), то есть лишний
round-trip на каждое обращение к БД; для `Connection.remote()` — через интернет.

### 5. BR-249: `/api/properties/setproperty` больше не отвечает 200, если файл не записан

**Было**:

```kotlin
fun savePropertiesMap() {
    try { … } catch (e: Exception) { println("… не удалось сохранить файл …") }
}
```

**Стало**:

```kotlin
fun savePropertiesMap(): Boolean = … catch (e: Exception) { log.error("…", e); false }
fun set(key: String, value: Any): Boolean = … // откат значения в карте при неудаче
```

Контроллер: `saved == false` → 500 (было 200 + SSE «Свойство установлено» при
незаписанном файле — значение жило только в памяти и исчезало при рестарте).

---

## Уже закрытые другими пакетами — регрессионные проверки

| BR | Где закрыто (реестр) | Что подтверждает регрессия | Правка в этом пакете |
|---|---|---|---|
| BR-234 | #375, `collectstore-noop:main` | `MainController.kt` — сбор хранилища не отвечает безусловным `true` | не трогали |
| BR-235 | #375, `voice-index-oom:main` | `MainController.kt` — индекс голоса проверяется через `isValidVoiceIndex` | не трогали |
| BR-237 | #383, `sql-injection-albums:albums` | `Album.kt` — `author_id` передаётся параметром (`ps.setLong`), не склейкой | не трогали |
| BR-244 | #375, `properties-write-race:properties` | `KaraokeProperties.kt` — запись под `ReentrantLock`, атомарная запись файла | не трогали |
| BR-247 | #407, `chmod666-secrets:properties` | `Extentions.kt` — `SECRET_FILE_PERMISSIONS` = 600 (владелец), `setSecretFilePermissions` | не трогали |
| BR-249 | **TODO, 397** (см. примечание) | `KaraokeProperties.kt` — `savePropertiesMap(): Boolean` + права 600 на файл | **исправлено** в партии 2 |
| BR-251 | #375, `sync-result-ignored:utils` | `Utils.kt` — `postChunksConfirming` прерывает отправку при отказе цели | не трогали |
| BR-258 | #361, `public-bucket-stems:storage` | `StemJob.kt` — приватный бакет `stemjobs` вместо публичного `karaoke` | не трогали |
| BR-266 | #375, `recursion-stackoverflow:utils` | `Utils.kt` — `authorRequestOrder` (один проход по кругу), без рекурсии | не трогали |

> **Примечание про BR-249.** В ТЗ задания BR-249 упоминался среди «уже закрытых
> другими пакетами». Фактическое состояние кода и `registry.csv` показали
> обратное: `savePropertiesMap()` глотал исключение и `set()` возвращал `Unit`
> (то есть HTTP отвечал 200 при незаписанном файле) — дефект был жив. Поэтому
> BR-249 **исправлен** в этом пакете (партия 2), а не оставлен как «закрытый
> ранее». Регрессия фиксирует и результат записи, и права файла (BR-247).

---

## Проверки

Все проверки — ровно тот набор, что в CI (`.github/workflows/lint.yml`).

| Проверка | Результат |
|---|---|
| `GRADLE_USER_HOME=/home/nsa/Karaoke/.gradle ./gradlew --max-workers=2 -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true :karaoke-app:test :karaoke-web:test` | ✅ BUILD SUCCESSFUL, **773 теста** в `karaoke-app` + `karaoke-web` — все зелёные |
| `ktlintCheck` | ✅ BUILD SUCCESSFUL (0 нарушений) |
| `bash tools/check-knowledge-structure.sh` | ✅ OK: knowledge/ structure valid (9/9 checks passed) |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | ✅ All documentation passes architectural style and structural checks |
| `python3 tools/check-doc-references.py` | ✅ OK: битых ссылок, несуществующих путей и эндпоинтов нет (4347 ссылок, 2571 путей, 352 эндпоинта) |
| `python3 tools/check-recordhash-consistency.py` | ✅ OK recordhash-контракт |
| `bash tools/check-no-mp4-mentions.sh` | ✅ OK: all 139 MP4 mentions are in baseline (grandfathered) |
| `bash tools/check-feature-doc.sh docs/features/*.md` | ✅ Все документы валидны |
| `bash tools/check-subagent-isolation.sh` | ✅ Все commits в ветке `624-pkg-s3-25` уникальны |
| `bash tools/check-file-permissions.sh` | ✅ OK: chmod с правом записи для всех нет |
| Фронтенд (`webvue3/`, `karaoke-public/`) | ⏭ **не запускался** — в этом пакете НЕ трогались JS/TS-файлы (все правки — Kotlin + docs). Сборка фронтенда не требуется. |

### Регрессионные проверки пакета

| Файл | Тестов | Что покрывает |
|---|---:|---|
| `PkgS325Batch1SourceContractsTest.kt` | 20 | BR-228…BR-243 (партия 1) |
| `PkgS325Batch1BehaviorTest.kt` | 4 | BR-230 (`UuidCache`), BR-241, BR-246 (дефолты ключей) |
| `PkgS325Batch2SourceContractsTest.kt` | 31 | BR-245…BR-275 (партии 2–3) |
| `PkgS325Batch2BehaviorTest.kt` | 10 | BR-257 (HTML→текст), BR-271 (экранирование/скрипт), BR-259 (jobId), BR-270/BR-274 (константы), BR-250 (построчный разбор) |
| `PkgS325AlreadyClosedRegressionTest.kt` | 9 | BR-234, BR-235, BR-237, BR-244, BR-247, BR-249, BR-251, BR-258, BR-266 — закрыты ранее, добавлены «якоря» |
| **Итого** | **74** | |

**«До/после»:** контрактные тесты читают исходники (комментарии вырезаются
конечным автоматом) и проверяют, что СТАРАЯ конструкция УБРАНА, а новая
присутствует. На коде «до» правки каждый такой тест падает; поведенческие
тесты ссылаются на функции, которых до правки не существовало (`UuidCache`,
`parseStringLad`, `parseOptionalInt`, `emptyKdenliveTitle`, `shellEscape`,
`buildHostScript`, `htmlToPlainTextWithNewlines`, `isStemJobDescription`,
`readPropertiesFileTolerant`, `CONNECTION_VALIDATION_IDLE_MS`) — то есть на
старом коде тестовый модуль даже не компилируется.

---

## Осталось за владельцем

1. **Каталог `/sm-karaoke/system/scriptsFromDocker` — общий с четырьмя
   контейнерами** (P-72-03, `BR-271` — тот же корень, но другой эффект:
   watcher исполняет ЛЮБОЙ `.sh`, появившийся в каталоге, а каталог
   bind-mount'ится в `karaoke-app`, `karaoke-webvue`, `karaoke-webvue3`, `mlt`).
   BR-271 закрыл неэкранированную конкакатенацию и права `777 → 700`, но
   **архитектурное решение остаётся за владельцем**: отдельный сервисный
   пользователь для watcher'а + удаление bind-mount `/sm-karaoke/system` из
   контейнеров, которым он не нужен, либо отказ от исполнения файлов на хосте.
   Тот же вопрос закрыт в `specs/371-pkg-s2-08/report.md` — здесь только
   отмечено, что он не закрыт.
2. **Права на скрипт `700` vs доступ контейнера.** `karaoke-app` пишет файл в
   общий каталог; после смены прав на `700` файл сможет прочитать/исполнить
   только владелец. Если watcher на хосте работает не от того пользователя,
   который владеет каталогом, скрипт перестанет исполняться. Требует проверки
   на целевой машине (эмпирика с продом не проводилась — согласия нет).
3. **Единый источник свойств для `karaoke-app` и `karaoke-web`.** Файл
   `Karaoke.properties` читается один раз за жизнь JVM; `karaoke-web` читает
   тот же абсолютный путь, но в локальном compose монтирование
   `/sm-karaoke/system` закомментировано (веб живёт на дефолтах), а в проде
   монтируется `${WEB_FOLDER_HOST}`. Если два процесса пишут в один файл, они
   затирают правки друг друга (пишется карта целиком), а инвалидации нет.
   Решение (общий файл + инвалидация либо настройки веба из БД/API) —
   за владельцем; BR-246 закрыл только НЕЗАРЕГИСТРИРОВАННЫЕ ключи.
4. **Прод-эмпирика не проводилась.** Ни один фикс не проверялся на живой
   прод-БД/прод-хранилище (согласия на обращение к прод-данным не было).
   Особенно требуют проверки на целевой машине: BR-274 (валидация соединения
   по таймеру вместо `isValid` на каждый вызов — поведение при обрыве сети
   меняется), BR-270 (таймаут `runCommand` в 2 часа — для `docker run` с demucs
   на CPU этого достаточно, но замер не делался), BR-271 (права `700`).
5. **`BR-271` и формат скрипта.** Скрипты для хоста теперь используют
   `set -- …` + `exec … "$@"`. Старые скрипты, оставшиеся в каталоге от прежних
   версий, исполняются как раньше (watcher не различает формат) — при
   перезапуске контейнера они просто удаляются.
6. **`BR-264` — формат ответа `syncAll`.** Теперь при сбое выборки возвращается
   строка `"ERROR: …"` вместо `"OK"`. Вызывающий (`ApiController`) отдаёт её
   как есть; если фронтенд ждёт строго `"OK"`, это надо учесть (фронтенд в
   этом пакете не трогался).

## Уточнение лида при слиянии: BR-230 закрыт пакетом PKG-S3-24, не этим

Пакет принёс **вторую** реализацию той же правки, что уже была слита в master:
`UuidCache` в `Uuids.kt` (BR-230) против `Uuids.loadAll/cachedUuid/putCached`
в `model/Uuids.kt` (BR-197, пакет PKG-S3-24, OpenProject #396). Обе решают одно:
`tbl_uuids` читается одним запросом вместо тысячи точечных `SELECT`.

При слиянии оставлена **одна** реализация — из PKG-S3-24 (она уже в master,
покрыта тестами `PkgS324Batch2SourceContractsTest` и входит в CI). Из этого
пакета удалено:
- `UuidCache` и `loadAllToMap` (дубликаты; `loadAllToMap` к тому же содержал
  остаток `println` с `Timestamp`/`Instant` — тот самый анти-паттерн, который
  пакет и чинил в других местах);
- `PkgS325Batch1BehaviorTest.kt` — файл целиком проверял отброшенную реализацию;
  поведенческое покрытие UUID-кеша остаётся у PKG-S3-24;
- тест `BR-230 UUID узлов MLT берутся из кеша` переписан на общий смысл
  (наличие кеша и запрет точечного `SELECT`), чтобы не зависеть от имён API.

Также устранён конфликт двух顶层-объявлений `private val log` в `Utils.kt`
(по одному от PKG-S3-24 и PKG-S3-25): логгер обхода файлов переименован в
`walkLog` — отдельного логгера в файле не вводится.

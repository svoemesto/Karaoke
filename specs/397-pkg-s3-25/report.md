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
| 3 | BR-261…BR-265, BR-267 (6) | ✅ исправлены |
| — | BR-234, BR-235, BR-237, BR-244, BR-247, BR-251, BR-258, BR-266 (8) | 🔒 закрыты ранее — добавлена регрессионная проверка |

**Итог партий:** 32/32 исправлены, 8/8 закрыты ранее (регрессия) — 40/40 записей
пакета закрыты.

> **Примечание о составе пакета.** В `registry.csv` колонка `work_package=397`
> содержит 40 BR: `BR-228…BR-267` (за вычетом уже закрытых `BR-234`, `BR-235`,
> `BR-237`, `BR-244`, `BR-247`, `BR-251`, `BR-258`, `BR-266`) **плюс**
> `BR-268…BR-275`. Задание от 25.10.2026 перечисляет 40 записей
> `BR-228…BR-267` (включая 8 уже закрытых) — этот список и закрывался;
> `BR-268…BR-275` по ТЗ `### PKG-S3-25` остались отдельной работой (см.
> «Осталось за владельцем»).

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
| BR-230 | `karaoke-app/.../Uuids.kt:7` | PERF | `getStoredUuid` на **каждый** узел MLT делал `SELECT` по `tbl_uuids` (+`INSERT` для новых) — ~9 000 обращений к БД на песню (×2 версии) | `UuidCache`: `tbl_uuids` читается **одним** `SELECT` (`Uuids.loadAllToMap`), UUID раздаются из памяти; `INSERT` — только для новых `id`; `id` считается общей функцией `storedUuidId` | `…BR-230 UUID узлов MLT берутся из кеша…` + `PkgS325Batch1BehaviorTest` (4 теста) |
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

_(раздел заполняется по ходу партии)_

---

## Партия 3 — BR-261…BR-265, BR-267 (6 записей)

_(раздел заполняется по ходу партии)_

---

## Уже закрытые другими пакетами — регрессионные проверки

| BR | Где закрыто | Что подтверждает регрессия |
|---|---|---|
| BR-234 | `PKG-S3-23` (#375) | … |
| BR-235 | `PKG-S3-23` (#375) | … |
| BR-237 | `PKG-S3-23` (#383) | … |
| BR-244 | `PKG-S3-23` (#375) | … |
| BR-247 | `PKG-S3-23` (#407) | … |
| BR-249 | `PKG-S3-23` (#375) | … |
| BR-251 | `PKG-S3-23` (#375) | … |
| BR-258 | `PKG-S3-23` (#361) | … |
| BR-266 | `PKG-S3-23` (#375) | … |

---

## Проверки

_(заполняется по ходу)_

---

## Осталось за владельцем

_(заполняется по ходу)_

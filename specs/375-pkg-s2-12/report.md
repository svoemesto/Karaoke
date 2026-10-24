# Пакет PKG-S2-12 — karaoke-app: COMPAT, DATA, ERR, PERF, RACE, SEC

- **Issue**: OpenProject #375 (бэклог аудита #336), пакет `PKG-S2-12`.
- **Ветка**: `597-pkg-s2-12` от `origin/master` @ `822bb3fa`.
- **Записей**: 15 — BR-188, BR-195, BR-200, BR-201, BR-205, BR-206, BR-213,
  BR-214, BR-219, BR-227, BR-234, BR-235, BR-244, BR-251, BR-266.
- **Источник правды**: карточки `specs/336-code-audit/findings/`
  (P-05, P-07a/P-07b, P-10, P-11, P-12, P-17, P-20, P-26, P-27, P-28).
- **Контекст**: сырой JDBC (ADR-0001), соединение в `ThreadLocal`, транзакции
  ручные; контракт `withDb` из #368 («одноразовый поток закрывает соединение,
  переиспользуемый — нет», `knowledge/adr/local-0009-thread-local-connection-lifecycle.md`)
  не менялся.
- **Дата**: 2026-10-24.

## Сводка по записям

| BR | Было (дефект) | Стало (правка) | Где | Тест |
|---|---|---|---|---|
| BR-188 | `while (it > 0)` по захваченному `maxOfOrNull { it.size }` — вечный цикл и OOM на `/unpublications` | `popPublicationStacks(stacks)` продолжает, пока есть непустая стопка | `model/Publication.kt:1542,1598`, helper в конце файла | `PkgS212BehaviorTest.BR-188…` (2), source-contract |
| BR-195 | `StatsCacheKey(endpoint, emptyMap())` — 60 с отдаются цифры другого `target`/`days`/`mode` | `StatsCacheKey.forEndpoint(endpoint, target, params)`; `timeseries` передаёт `days`/`mode` | `model/StatsCacheKey.kt:32`, `controllers/StatsController.kt:81,202` | behavior + source-contract |
| BR-200 | `/publications` рендерил весь каталог: 127 МиБ / 9,5 с | `pageSlice` по `PUBLICATION_PAGE_SIZE = 31` день + `#areaPager` | `MainController.kt:1898–1902`, `Pagination.kt`, `publications.html` | behavior (`pageSlice`) + source-contract |
| BR-201 | шаблоны подключались к SockJS/STOMP, которого в `karaoke-app` нет с 2024-08-15 (404) | STOMP/SockJS удалены; `/processes` опрашивает `GET /process/lastupdated` каждые 5 с | `processes.html`, `publications.html`, `unpublications.html` | source-contract |
| BR-205 | `songsUpdate` объявлял `String` при `required=false` для отсутствующих в форме полей → NPE/500 на каждом «Сохранить» | `song_resultVersion: String?`, `song_idBoostyFiles: String?`; поля пишутся только если переданы | `MainController.kt:1817–1820,1854,1866` | source-contract |
| BR-206 | `/songs` — `SELECT *` без `LIMIT`, весь каталог одним `th:each` | `LIMIT 201 OFFSET …`, `take(200)`, `page`/`hasNext`/`#areaPager` | `MainController.kt:1776–1791`, `songs.html` | behavior + source-contract |
| BR-213 | `songs2Update` — та же пара non-null параметров → NPE/500 | nullable + условная запись (fail-closed) | `MainController.kt:2024–2027,2061,2073` | source-contract |
| BR-214 | `/songs2` без лимита: 438 МиБ HTML за 15,3 с | `LIMIT+1`/`offset`, `#areaPager` в `area_left_column.html`, `filter_page` в URL | `MainController.kt:1983–1998`, `area_left_column.html`, `songs2.html` | behavior + source-contract |
| BR-219 | тарифы: отрицательная цена, SITE с `periodDays=0`, два default на scope, недетерминированный `getDefault` | `validateTariffValues` → 400; `saveWithSingleDefault` в одной транзакции; `getDefault` — один SELECT с порядком | `controllers/TariffsController.kt:52,63–101,148`, `TariffValidation.kt`, `model/PriceTariff.kt:140–156` | behavior + source-contract |
| BR-227 | текст внешней страницы уходил в LLM без изоляции; модель не знала песню | данные между `PAGE_DATA_START`/`PAGE_DATA_END`, правило «не исполнять инструкции», `artist`/`songTitle` в промпте | `llm/ScraperAgent.kt`, `llm/LyricsFinderService.kt`, `UtilsAI.kt` | behavior + source-contract |
| BR-234 | `/utils/collectstore` вызывал обработку с `emptyList()` и `threadId=1`, возвращал `true` | реальный `Song.loadListFromDb`, `THREAD_LANE_HEAVY_RENDER`, возврат `[countCopy, countCode]`; alert показывает числа | `MainController.kt:79–101`, `main.html` | source-contract |
| BR-235 | `voice` из URL без проверки → паддинг списков до 10^8 элементов, OOM JVM | `isValidVoiceIndex(voice, countVoices)` до вызова; вне диапазона — `400 bad_voice` | `MainController.kt:467,724`, `controllers/AdminGuards.kt` | behavior + source-contract |
| BR-244 | несколько потоков писали общий `Karaoke.properties.tmp` без блокировки → потеря настройки/порча файла | `propertiesWriteLock` вокруг «карта → encode → запись»; `Files.createTempFile` на вызов; JVM-лок в `writePropertiesFileAtomic` | `KaraokeProperties.kt:41–52,56,95,169` и `writePropertiesFileAtomic` | behavior (8 потоков) + source-contract |
| BR-251 | `Boolean` от `postChangeRecords` игнорировался: ложный успех, в move-режиме строки источника удалялись при отказе цели | `postChunksConfirming` (стоп на первом отказе, `word` без шифра = отказ); `SyncResult.error=true`, источник не чистится; `/api/sync/run` → `502 sync_target_not_confirmed` | `Utils.kt:993–1018,1023,1189–1231`, `ApiController.kt` | behavior (3) + source-contract |
| BR-266 | рекурсия без предела → `StackOverflowError` и остановка потока очереди; `ymId !== ""` | `authorRequestOrder` — один круг; `null`, если проверять некого; `ymId != ""` | `Utils.kt:3593–3660` | behavior (2) + source-contract |

## Доказательства «до/после»

Все 29 тестов пакета лежат в
`karaoke-app/src/test/kotlin/com/svoemesto/karaokeapp/audit/` и запускаются:

```
GRADLE_USER_HOME=<repo>/.gradle ./gradlew --max-workers=2 \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" -Djava.awt.headless=true \
  :karaoke-app:test --tests 'com.svoemesto.karaokeapp.audit.*'
```

После правок: `BUILD SUCCESSFUL`, 29 тестов. Ниже — три показательных прогона
на коде «до» (файл правки временно возвращался `git stash`, тест запускался
точечно, затем `git stash pop`).

### BR-201 — мёртвый STOMP в шаблонах

`processes.html` возвращён к версии до правки:

```
> Task :karaoke-app:test FAILED
PkgS212SourceContractsTest > BR-201 в шаблонах karaoke-app нет мёртвого STOMP, статус процессов идёт опросом() FAILED
    org.opentest4j.AssertionFailedError at PkgS212SourceContractsTest.kt:60
BUILD FAILED in 10s
```

После возврата правки тест проходит (`BUILD SUCCESSFUL`).

### BR-195 — ключ кеша статистики без параметров

`StatsController.kt` возвращён к версии до правки:

```
> Task :karaoke-app:test FAILED
PkgS212SourceContractsTest > BR-195 ключ кеша статистики строится с параметрами запроса() FAILED
    org.opentest4j.AssertionFailedError at PkgS212SourceContractsTest.kt:41
BUILD FAILED in 1m 11s
```

### BR-244 — гонка записи файла настроек

`KaraokeProperties.kt` возвращён к версии до правки (общий `<имя>.tmp`):

```
> Task :karaoke-app:test FAILED
PkgS212BehaviorTest > BR-244 параллельная атомарная запись не падает и не оставляет tmp() FAILED
    java.util.concurrent.ExecutionException at PkgS212BehaviorTest.kt:194
        Caused by: kotlin.io.FileAlreadyExistsException: .../Karaoke.properties.bak
BUILD FAILED in 1m 17s
```

Тест поднимает 8 потоков × 8 записей в один файл и проверяет, что в файле
оказался один из целиком записанных вариантов и не осталось `*.tmp`.
После правки — `BUILD SUCCESSFUL`.

### Почему не у всех записей поведенческий тест

- **BR-200/201/205/206/213/214/234** — контроллеры и шаблоны Thymeleaf:
  вызов требует Spring MVC/БД/рендера. Для них написаны source-contract-тесты
  (`PkgS212SourceContractsTest`), которые компилируются и на коде «до» и падают
  на нём (доказано выше для BR-201/BR-195). Для BR-200/206/214 дополнительно
  покрыта чистая пагинация (`pageOffset`/`pageSlice`/`hasNextPage`).
- **BR-188** — сам цикл на старом коде не завершается, запускать его как тест
  нельзя (OOM/зависание тест-JVM). Покрыт чистый шов `popPublicationStacks`
  (граничные размеры стопок) + source-contract на условие выхода.
- **BR-195/219/227/235/251/266** — поведенческие тесты на вынесенных чистых
  швах плюс source-contract на интеграцию в горячий путь.
- **BR-251**: сквозной сценарий с реальным отказом HTTP-цели требует двух БД;
  покрыт шов `postChunksConfirming` (стоп на первом отказе, нешифруемый `word`)
  и контракт `SyncResult.error` + source-contract на порядок «подтверждение →
  удаление источника».

## Проверки (CI-набор)

Выполнены на итоговом коде перед последним коммитом:

| Команда | Результат |
|---|---|
| `:karaoke-app:test` (DISPLAY не задан, как в CI) | `BUILD SUCCESSFUL`, 521 тест, 0 падений |
| `:karaoke-web:test` | `BUILD SUCCESSFUL` |
| `ktlintCheck` (оба модуля) | `BUILD SUCCESSFUL` |
| `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | `All documentation passes architectural style and structural checks` |
| `python3 tools/check-doc-references.py` | 0 проблем |
| `bash tools/check-no-mp4-mentions.sh` | `OK: all 139 MP4 mentions are in baseline` |
| `bash tools/check-feature-doc.sh docs/features/*.md` | все документы валидны |
| `bash tools/check-subagent-isolation.sh` | OK (ветка `597-pkg-s2-12`, отдельный worktree) |

Примечание по окружению: `VkPreviewWarmupClientTest` падает только если в
окружении задан `DISPLAY=:0` без X-сервера (`AWTError: Can't connect to X11`).
С `DISPLAY` не заданным (как на CI) класс проходит; на master поведение то же —
это окружение машины, не регрессия пакета. Поэтому финальный прогон выполнен
с `DISPLAY=`.

## За владельцем (решения, которые пакет не принимал)

1. **BR-200/206/214**: явный список колонок вместо `SELECT *` в
   `Song.loadListFromDb` и SQL-пагинация расписания публикаций (сейчас
   `getPublicationList` всё ещё читает все песни, режется только HTML-выдача).
2. **BR-205/213**: добавлять ли в legacy-формы hidden-поля
   `song_resultVersion`/`song_idBoostyFiles` (сейчас fail-closed: с этих страниц
   поля не редактируются), или признать страницы legacy и не развивать.
3. **BR-219**: partial unique index на `(scope, is_default)` и CHECK-ограничения
   `price_rub >= 0`, `period_days` в БД (пакет не трогал БД). Приложение уже
   снимает прежний default в одной транзакции.
4. **BR-227**: автоматическая запись найденного текста в `Song.sourceText`
   сохранена — нужна ли обязательная человеческая приёмка соответствия песне.
5. **BR-234**: нужна ли legacy-кнопка «Обновить хранилище» при наличии
   `POST /api/utils/collectstore`; приоритеты `priorLyrics`/`priorKaraoke`
   на legacy-странице не выведены (используются дефолты 10/10).
6. **BR-235**: оставить ли верхнюю границу `voice == countVoices` (как в
   `ApiController`, паддинг на один пустой голос) или запретить строго
   `voice < countVoices`.
7. **BR-201**: выбран polling, а не возврат STOMP-брокера в `karaoke-app`;
   интервал 5 с — настраиваемый параметр, если нужен другой.
8. **BR-251**: нужен ли per-chunk отчёт о частично применённых изменениях
   (сейчас при `error=true` списки пусты — точный состав неизвестен) и политика
   повторов.
9. **BR-244**: `chmod 666` и секреты в `Karaoke.properties` — семейство BR-115
   (P-05-04), в этот пакет не входит; запись под блокировкой это не лечит.
10. **BR-266**: условие отбора оставлено «яндекс-специфичным»
    (`ymId != "" && lastAlbumYm == lastAlbumProcessed`), а не
    `Author.haveNewAlbum` (тот допускает VK-авторов без `ym_id`, а
    `searchLastAlbumYm3` ищет по `ymId`).
11. **BR-188**: нужны ли legacy-методы `getUnPublicationList`/
    `getSkipedPublicationList` вообще (webvue3 их не вызывает).

## Хэши коммитов

| Коммит | Содержимое |
|---|---|
| `e61ce58c` | BR-188/195/219/227/251/266 — цикл публикаций, ключ кеша, тарифы, промпт, sync, авторы |
| `faf5f615` | BR-200/201/205/206/213/214/234/235 — пагинация, STOMP, сохранение песен, голос, хранилище |
| `e062e979` | BR-244 — блокировка и уникальный tmp файла настроек + регрессионные тесты пакета |
| `2da97a80` | SSoT `knowledge/` и per-feature `docs/features/pkg-s2-12-audit-fixes.md` |

Изменённые файлы: 15 Kotlin, 7 Thymeleaf-шаблонов, 2 тестовых файла,
7 документов `knowledge/`, per-feature документ и этот отчёт.
`specs/336-code-audit/registry.csv` не изменялся.

# Отчёт по пакету PKG-ERR-STATS-ZERO (OpenProject #385, аудит #336)

- **Ветка**: `604-pkg-err-stats` от `origin/master` `b3d4455a`.
- **Worktree**: `/home/nsa/Karaoke/.worktrees/waveE-err-stats` (главная копия и
  соседние worktree не изменялись).
- **Объём**: 2 записи S3 — BR-016, BR-185 (пакет `PKG-ERR-STATS-ZERO`, волна E).
- **Режим**: короткий цикл, ТЗ из карточек аудита; публичный формат менялся
  только там, где без этого нельзя различить состояния (см. § 2).
- **Коммиты**:
  - `6cbae426` — код + регрессионные тесты (BR-016, BR-185);
  - `ce17418e` — этот отчёт, `knowledge/` и per-feature документ
    (поверх — служебные `docs`-коммиты; точные хэши — в
    `git log 604-pkg-err-stats`).

## 1. Что было / что стало

### BR-016 — `karaoke-web/.../StatBySong.kt:234–262` (кеш счётчиков главной)

- **Где именно (до)**: `runCountQuery` (`:234–262`) при
  `database.getConnection() == null` печатал строку в stdout и делал
  `return 0`; в `catch (e: SQLException)` — `e.printStackTrace()` и тоже `0`.
  `refreshCache` (`:141–200`) безусловно публиковал результат в пять
  `AtomicInteger` (`:182–186`).
- **Почему ошибка превращалась в 0**: у `runCountQuery` не было способа
  сообщить «значения нет» — тип возврата `Int`, а единственное «безопасное»
  значение — `0`. `refreshCache` не проверял, были ли запросы успешны. После
  сбоя `cachedTotal == 0` (не `< 0`), поэтому cold-start-условие
  `ensureCacheInitialized` (`:217`) больше не запускало пересчёт: неверные
  нули жили до часового cron или `markDirty`.
- **Чем опасно**: главная и `/api/public/stats` показывали «0 песен в
  коллекции», «0 в работе» и т.д. — неотличимо от настоящего пустого каталога.
  Владелец принимает по этим цифрам решения о наполнении/публикации; посетитель
  видит пустой проект.
- **Что сделано**:
  - `runCountQuery` возвращает `Int?`: `null` — нет соединения, `SQLException`
    или агрегат не вернул строку. Ошибка логируется через SLF4J
    (`infra.cache.statbysong`, throwable передаётся) — `printStackTrace` больше
    нет.
  - Кеш — один неизменяемый снимок `StatBySongCounters` в `AtomicReference`
    (`StatBySong.kt:83`): `total`, `collection`, `freeNow`,
    `subscriptionOnly`, `inWork` публикуются одной операцией; частичного
    состояния не существует.
  - `refreshCacheInto` (`:269`) публикует снимок только если ВСЕ три запроса
    вернули значение; иначе `state = ERROR`, прежние значения остаются,
    `cache:refreshFailed total=… collection=… freeNow=…` уходит в лог.
  - Состояние `StatsCacheState` (`:52`): `warming_up` (нет данных), `ready`
    (ноль — настоящий), `error` (последний пересчёт упал). `hasData == false`
    — условие повторного пересчёта: после неудачной первой попытки следующий
    запрос снова запускает refresh (не залипает).
  - `/api/public/stats` отдаёт `state` (см. § 2); `main.html` при `warming_up`
    показывает `—`, при `error` — последние значения и предупреждение
    «БД временно недоступна».
- **Тесты**: `StatBySongCacheRegressionTest` (падает на старом коде),
  `StatBySongCacheStateTest` (семантика трёх состояний),
  `ThymeleafTemplatesTest` (3 теста отображения).

### BR-185 — `karaoke-app/.../model/MonetizationStats.kt:53–99` (сводка монетизации)

- **Где именно (до)**: `scalarInt` (`:53–75`) и `scalarDouble` (`:77–99`):
  `database.getConnection() ?: return 0/0.0`, а в `catch (e: SQLException)` —
  `e.printStackTrace(); return 0`. `getSummary` (`:101–140`) собирал DTO из 11
  независимых запросов. `getTopSubscribedSongs` (`:142–185`) при `null`-
  соединении отдавал `emptyList()`, а `SQLException` гасил в `catch`.
- **Почему ошибка превращалась в 0**: модель данных не пробрасывала сбой.
  Причём сценарий «too many clients already» вообще не доходил до
  `catch (SQLException)` в `StatsController.respondCached`
  (`StatsController.kt:71–94`): `KaraokeConnection.getConnection()` не бросает
  исключение, а логирует и возвращает `null`, который `scalar*` превращал в
  `0`. В результате `/api/stats/monetization` отдавал `200` с нулями и клал их
  в `StatsCache` на 60 секунд — вместо `503 stats.unavailable` по FR-003.
- **Чем опасно**: дашборд «Статистика» показывал нулевую выручку, ноль
  оплаченных подписок и ноль премиумов при живых платежах. Владелец принимает
  по этим цифрам денежные решения; отличить «нет продаж» от «нет БД» было
  нельзя.
- **Что сделано**:
  - `connectionOrFail` (`MonetizationStats.kt:88`) бросает `SQLException` с
    текстом `stats.unavailable`, если соединения нет; `catch` в `scalar*`
    удалён — `SQLException` запроса пробрасывается.
  - `getSummary` (`:117`) читает все 11 скаляров ОДНИМ запросом
    (`SUMMARY_SQL`, `:68`): один round-trip и согласованный снимок; при сбое
    любого подзапроса — исключение, а не «частично нулевая» сводка.
  - `getTopSubscribedSongs` (`:154`) больше не гасит ошибку и не возвращает
    пустой список при недоступной БД.
  - `StatsController`: `/api/stats/monetization` уже шёл через
    `respondCached` — после правки модели его `catch (SQLException)` начал
    срабатывать. Для `/api/stats/monetization/top-songs` добавлен
    `respondUncached` (`StatsController.kt:105`) — тот же `503
    stats.unavailable`, без кеша.
- **Тесты**: `PkgErrStatsZeroMonetizationTest` — 4 теста (нет соединения,
  `SQLException` запроса, top-songs, успешное чтение одним запросом).

## 2. Изменения публичного формата (осознанные)

| Контракт | Было | Стало | Совместимость |
|---|---|---|---|
| `GET /api/public/stats` | `Map<String, Int>`, 5 чисел | `Map<String, Any>`, те же 5 чисел + `state` (`ready`/`warming_up`/`error`) | пять ключей не менялись; новый ключ старые потребители игнорируют (SPA кладёт в store только известные поля). HTTP-статус остался `200` |
| `GET /api/stats/monetization` | сбой БД → `200` + нули (и кеш на 60 с) | сбой БД → `503 stats.unavailable` + `Retry-After: 10` (FR-003); успех — прежний формат `{"summary": {...}}` | ошибка больше не маскируется; успешный контракт не менялся |
| `GET /api/stats/monetization/top-songs` | сбой БД → `200` + `{"items": []}` | сбой БД → `503 stats.unavailable`; успех — прежний `{"items": [...]}` | успешный контракт не менялся |

## 3. «До/после» — прогоны

Метод: production-файлы временно возвращались к `HEAD` (`git checkout HEAD --
<7 файлов>`), новые тесты оставались; файл `StatBySongCacheStateTest`
исключался из прогона «до», потому что использует новые типы (`StatsCacheState`,
`refreshCacheInto`) и на старом коде не компилируется by design. Поведенческий
регрессионный тест BR-016 (`StatBySongCacheRegressionTest`) старого API не
касается и компилируется на обоих вариантах.

### 3.1 BR-185 — на старом коде

```
$ GRADLE_USER_HOME=… ./gradlew … :karaoke-app:test \
    --tests "com.svoemesto.karaokeapp.audit.PkgErrStatsZeroMonetizationTest"
PkgErrStatsZeroMonetizationTest > BR-185 нет соединения — сводка бросает SQLException, а не отдаёт нули() FAILED
    org.opentest4j.AssertionFailedError at PkgErrStatsZeroMonetizationTest.kt:122
PkgErrStatsZeroMonetizationTest > BR-185 топ-песен при недоступной БД — исключение, а не пустой список() FAILED
    org.opentest4j.AssertionFailedError at PkgErrStatsZeroMonetizationTest.kt:146
PkgErrStatsZeroMonetizationTest > BR-185 SQLException запроса не превращается в нулевую сводку() FAILED
    org.opentest4j.AssertionFailedError at PkgErrStatsZeroMonetizationTest.kt:134
PkgErrStatsZeroMonetizationTest > BR-185 сводка читается одним запросом и не подменяет значения нулями() FAILED
    org.opentest4j.AssertionFailedError at PkgErrStatsZeroMonetizationTest.kt:93
4 tests completed, 4 failed
BUILD FAILED in 28s
```

После правки:

```
$ … :karaoke-app:test --tests "…PkgErrStatsZeroMonetizationTest"
BUILD SUCCESSFUL in 11s   # 4 tests, 0 failed (XML: tests="4" failures="0" errors="0")
```

### 3.2 BR-016 — на старом коде

```
$ … :karaoke-web:test --tests "…StatBySongCacheRegressionTest" \
    --tests "…templates.ThymeleafTemplatesTest"
StatBySongCacheRegressionTest > BR-016 сбой БД не обнуляет уже посчитанные счётчики главной() FAILED
    org.opentest4j.AssertionFailedError at StatBySongCacheRegressionTest.kt:61
ThymeleafTemplatesTest > BR-016 при ready настоящий ноль показывается нулём() FAILED
    org.opentest4j.AssertionFailedError at ThymeleafTemplatesTest.kt:185
ThymeleafTemplatesTest > BR-016 при error главная показывает последние цифры и предупреждение() FAILED
    org.opentest4j.AssertionFailedError at ThymeleafTemplatesTest.kt:194
ThymeleafTemplatesTest > BR-016 при warming_up главная показывает прочерк вместо нуля() FAILED
    org.opentest4j.AssertionFailedError at ThymeleafTemplatesTest.kt:176
7 tests completed, 4 failed
BUILD FAILED in 26s
```

`StatBySongCacheRegressionTest.kt:61` — это первый assert после сбоя БД
(`total`), то есть на старом коде там был `0` вместо `42`.

После правки (все три класса, включая state-тесты):

```
$ … :karaoke-web:test --tests "…StatBySongCacheRegressionTest" \
    --tests "…StatBySongCacheStateTest" --tests "…templates.ThymeleafTemplatesTest"
BUILD SUCCESSFUL in 12s
  StatBySongCacheRegressionTest: tests="1" failures="0"
  StatBySongCacheStateTest:      tests="5" failures="0"
  ThymeleafTemplatesTest:        tests="5" failures="0"
```

## 4. Проверки (до коммита)

| Проверка | Команда | Результат |
|---|---|---|
| karaoke-app tests | `… ./gradlew --max-workers=2 … :karaoke-app:test` | **BUILD SUCCESSFUL**, 569 тестов, 0 падений (с `DISPLAY=`; см. примечание) |
| karaoke-web tests | `… ./gradlew --max-workers=2 … :karaoke-web:test` | **BUILD SUCCESSFUL**, 182 теста, 0 падений |
| ktlint | `… ./gradlew … ktlintCheck` | **BUILD SUCCESSFUL** (первый прогон нашёл `class-signature` в новом enum — поправлено) |
| knowledge structure | `bash tools/check-knowledge-structure.sh` | `OK: knowledge/ structure valid (9/9 checks passed)` |
| knowledge lint | `python3 tools/lint-knowledge.py --baseline config/knowledge/baseline-knowledge-lint.txt` | `All documentation passes architectural style and structural checks.` |
| doc references | `python3 tools/check-doc-references.py` | без новых находок (после создания этого отчёта) |
| recordhash | `python3 tools/check-recordhash-consistency.py` | `OK recordhash-контракт…` |
| MP4 | `bash tools/check-no-mp4-mentions.sh` | `OK: all 139 MP4 mentions are in baseline` |
| feature doc | `bash tools/check-feature-doc.sh docs/features/*.md` | `OK [docs/features/pkg-err-stats-zero.md]`, `Все документы валидны` |
| subagent isolation | `bash tools/check-subagent-isolation.sh` | OK (отдельный worktree, ветка `604-pkg-err-stats`) |

**Окружение.** На этой машине `DISPLAY=:0` указывает на несуществующий X11:
без сброса `DISPLAY` четыре теста `VkPreviewWarmupClientTest` падают с
`java.awt.AWTError: Can't connect to X11 window server using ':0'` (это не
связано с правкой; `-Djava.awt.headless=true` в test-JVM не пробрасывается).
Прецеденты и тот же обход — отчёты `specs/366-pkg-race-publish/report.md` и
`specs/374-pkg-s2-11/report.md` (`DISPLAY= … ./gradlew …`).

## 5. Что осталось за владельцем

1. **SPA `karaoke-public` не читает `state`.** `store/modules/stats.js` кладёт
   только пять чисел; при ошибке запроса остаются начальные нули, и посетитель
   снова увидит «0 песен». Бэкенд уже отдаёт `state` — нужно показать `—`/плашку
   на фронте (пакет #385 не включает frontend-сборку; отдельная задача).
2. **`webvue3` не показывает баннер деградации.** 503 с
   `/api/stats/monetization` обрабатывается generic-`catch` в
   `Stats/store.js` (панель остаётся пустой, но без ложного нуля). Компонент
   `DbOverloadBanner` в `webvue3/src` отсутствует — это отдельная находка
   P-11, BR не заводился.
3. **Остальные некэшируемые stats-endpoint'ы** (`/api/stats/by-song`,
   `top-listened`, `webevents`, `by-type`, `by-detail`, `top-users`,
   `user-events`, `song-events`) по-прежнему гасят ошибки внутри
   `StatsByEvents` и отдают `200` с пустым списком (P-20). Пакет #385 ограничен
   BR-016/BR-185; расширять на `StatsByEvents` — решение владельца (отдельный
   BR или расширение пакета).
4. **Три запроса `StatBySong` — не одна транзакция.** «Атомарная замена набора»
   здесь = «не публиковать частичный/нулевой набор», а не `REPEATABLE READ`.
   Для публичных счётчиков принято как допустимое; менять — только со спекой.
5. **Валидация `limit`** в `/api/stats/monetization/top-songs` не добавлялась:
   отрицательный `limit` теперь даст `503` (ошибка PostgreSQL), а не молча
   пустой список. Если нужен `400` — отдельная задача.

## 6. Файлы

Код: `karaoke-web/.../StatBySong.kt`, `.../controllers/PublicApiController.kt`,
`.../controllers/MainController.kt`, `.../services/StatsCacheScheduler.kt`,
`karaoke-web/src/main/resources/templates/main.html`,
`karaoke-app/.../model/MonetizationStats.kt`,
`karaoke-app/.../controllers/StatsController.kt`.

Тесты: `karaoke-web/src/test/.../StatBySongCacheRegressionTest.kt`,
`.../StatBySongCacheStateTest.kt`, `.../templates/ThymeleafTemplatesTest.kt`,
`karaoke-app/src/test/.../audit/PkgErrStatsZeroMonetizationTest.kt`.

Документы: [per-feature](../../docs/features/pkg-err-stats-zero.md),
[SSoT stats-cache](../../knowledge/domains/publishing/components/stats-cache.md),
[SSoT stats domain](../../knowledge/domains/stats/domain.md),
[SSoT public-controllers-3](../../knowledge/domains/karaoke-web/components/public-controllers-3.md),
[SSoT thymeleaf-templates](../../knowledge/domains/karaoke-web/components/thymeleaf-templates.md).

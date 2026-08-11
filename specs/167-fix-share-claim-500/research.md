# Phase 0 Research: Починить 500 на `POST /api/public/share/claim`

**Дата**: 2026-08-11
**Spec**: [./spec.md](./spec.md)
**Branch**: `167-fix-share-claim-500`

## Резюме

Hotfix состоит из двух независимых действий: (A) применение уже-существующих миграций 38/39 на прод-БД, (Б) правка catch-all'ов в коде, чтобы будущие системные ошибки не маскировались под `share.notFound`. Оба действия — bounded, без архитектурных изменений. Ниже — детальный разбор каждой области неопределённости, выявленной в Technical Context.

## R1: Где живёт catch-all маскировка и какие ветки catch нужны

**Вопрос**: какие именно catch-блоки в `PublicShareController.kt` и `SongShareLinkService.kt` маскируют системные ошибки под `share.notFound` / `share.leaseExpired`?

**Метод**: чтение исходников + grep по паттерну `catch (_: Exception)`.

**Решение** (Decision):

| Файл | Строка | Catch-блок | Текущее поведение | После фикса |
|---|---|---|---|---|
| `PublicShareController.kt` | 174-175 (claim) | `catch (_: Exception)` → 500 `share.notFound` | маскирует **все** исключения (SQLException, NPE и т.д.) | `catch (_: SongShareLinkService.InternalError)` → 500 `share.internal` |
| `PublicShareController.kt` | 87-89 (create) | `catch (_: Exception)` → 500 `share.notFound` | то же | то же |
| `PublicShareController.kt` | 189-191 (heartbeat) | `catch (_: Exception)` → 410 `share.leaseExpired` | маскирует под leaseExpired | `catch (_: SongShareLinkService.InternalError)` → 500 `share.internal` |
| `SongShareLinkService.kt` | 597-602 (tryClaim catch-all) | `catch (e: Exception)` → `throw NotFound()` | маскирует SQLException | `catch (e: Exception)` → `throw InternalError(e)` |

**Rationale**:
- Прямой симптом (500 share.notFound на /claim) — строки 174-175 + строки 597-602 (вызов `NotFound()`).
- Сопутствующие catch-all'ы в `create` (87-89) и `heartbeat` (189-191) имеют **ту же антипаттерн-структуру** — маскируют системные ошибки. FR-014 спеки требует «audit + replace if pattern found» — фикс покрывает все три.
- `release` (197-210) и `debug` (218-225) НЕ имеют `catch (_: Exception)` — System.out-of-band ошибки уйдут в дефолтный Spring-обработчик (HTTP 500 без `errorCode`). Это приемлемо для hotfix: фронт `ShareView.vue` показывает generic «Не удалось…» (уже есть в `songShareLink.js`).
- `getMine` (92-119) и `revoke` (121-130) тоже без `catch (_: Exception)` — то же.

**Alternatives considered**:
- (a) **Удалить catch-all полностью**, позволить исключениям пробрасываться к Spring default error handler. ❌ Отклонено: теряем структурированный JSON-ответ с `errorCode`, фронт ломается на разборе 500 без тела.
- (b) **Завести единый `@ControllerAdvice`** для share-эндпоинтов с `@ExceptionHandler(InternalError::class)`. ❌ Overengineering для hotfix: 4 файла → 1, плюс нужен class-level enable через `@RestControllerAdvice(basePackageClasses = …)`.
- (c) **Оставить catch-all только в /claim**, остальные — backlog. ❌ Частично закрывает проблему: при миграции на проде `create` тоже может упасть, и пользователь получит вводящий в заблуждение `share.notFound` от `ShareLinkModal.vue` (модалка показывает «Не удалось создать ссылку» — generic, но backend всё равно отдаёт вводящий в заблуждение код).

## R2: Структура `ShareException` и место для `InternalError`

**Вопрос**: куда встроить новый `InternalError` в иерархии sealed class?

**Метод**: чтение `SongShareLinkService.kt:166-193`.

**Решение** (Decision):

```kotlin
sealed class ShareException(
    val code: ShareErrorCode,
    val httpStatus: Int
) : RuntimeException(code.dbValue)

// ... существующие NotFound, Expired, Revoked, SongUnavailable,
// ConcurrentLimit, LeaseExpired, RateLimited, NotOwner,
// LinkAlreadyActive, TokenMissing ...

/**
 * Неожиданное системное исключение (SQLException, NPE в loadSongInfo и т.п.).
 * Пробрасывается через catch-all в [tryClaim] / [createLink] / [heartbeat] / [release]
 * и маппится контроллером в HTTP 500 + [ShareErrorCode.INTERNAL]. НЕ маскируется
 * под share.notFound/share.leaseExpired — иначе диагностика PROD-инцидентов
 * невозможна (Pass 50, см. specs/167-fix-share-claim-500/spec.md).
 *
 * @param cause оригинальное исключение (SQLException / RuntimeException / etc.)
 * @see docs/features/guest-share-link.md
 */
class InternalError(
    cause: Throwable,
) : ShareException(ShareErrorCode.INTERNAL, 500) {
    init {
        addSuppressed(cause)
    }
}
```

**Rationale**:
- Sealed class уже есть (`SongShareLinkService.kt:166`). Новый подтип — одна строчка.
- `httpStatus = 500` — наследуется полем `httpStatus` родителя, контроллер может использовать его, если в будущем появится общий `@ExceptionHandler`. Сейчас контроллер делает маппинг явно (`ResponseEntity.status(500).body(...)`), но структура готова.
- `cause` сохраняется через `addSuppressed`, чтобы при логировании `e.cause`/`getSuppressed()` работали стандартно. Полный стек-трейс уже пишется в `tryClaim` через `log.error("ShareLink tryClaim UNEXPECTED class=... msg=...", e)` — для `InternalError` оставляем тот же формат, добавляя `class=InternalError(cause=PSQLException)` если возможно (но это уже избыточно — стек-трейса `e` достаточно).

**Alternatives considered**:
- (a) **`InternalError` как `data class` без `cause`**. ❌ Теряем оригинальное исключение — нельзя будет восстановить цепочку. Логи пишут `log.error("ShareLink tryClaim UNEXPECTED ...", e)` где `e` — оригинал. Если `InternalError` без cause, нужно менять логирование.
- (b) **`InternalError(message: String, cause: Throwable)` с явным message**. ❌ Дублирование: `RuntimeException(message, cause)` уже есть, плюс message добавляет шум в логах (мы и так логируем `e.message`).
- (c) **Не вводить sealed-подтип, использовать `RuntimeException` напрямую**. ❌ Нарушает конвенцию: все доменные ошибки share идут через `ShareException` + `httpStatus` (используется потенциально в future `@ExceptionHandler`). Если оставить как обычный `RuntimeException`, теряем структуру и не сможем централизованно мапить.

## R3: Состав `ShareErrorCode` и конвенция

**Вопрос**: добавлять ли `INTERNAL` в существующий enum и где расположить.

**Метод**: чтение `ShareErrorCode.kt` (10 существующих кодов).

**Решение** (Decision):

```kotlin
// После TOKEN_MISSING, в конец enum:

// Неожиданная системная ошибка (SQLException, NPE и т.п.) — раньше маскировалась
// под share.notFound / share.leaseExpired, что делало невозможной диагностику PROD-инцидентов.
// Теперь это отдельный код 500, чтобы в логах было видно «у нас упало внутри», а не
// «пользователю показали что-то странное».
// См. specs/167-fix-share-claim-500/spec.md, FR-013.
INTERNAL("share.internal"),
```

**Rationale**:
- Имена констант — UPPER_SNAKE_CASE (конвенция enum в Kotlin), значения — `share.<camelCase>` (конвенция JSON-ключей в Karaoke). Оба уже соблюдены.
- Расположение — после `TOKEN_MISSING` (последний в файле). Логически это «последний рубеж» — все доменные коды выше, `INTERNAL` — для непредусмотренных случаев.
- На фронте (`karaoke-public`) `ShareView.vue` обрабатывает `errorCode` через `songShareLink.js` — если код неизвестен, показывается generic «Не удалось…» (см. `songShareLink.js` и `ShareLinkModal.vue:275`). После фикса `share.internal` будет попадать в эту generic ветку — пользователь увидит «Не удалось открыть плеер» вместо «Ссылка недоступна». Это **уже лучше** (более точно), но для полного UX нужен специфический текст в ShareView. **Backlog**: добавить текст «Внутренняя ошибка сервера, попробуйте позже» для `share.internal`.

**Alternatives considered**:
- (a) **`SHARE_INTERNAL("share.internal")`**. ❌ Не соответствует конвенции — другие коды без префикса `SHARE_`.
- (b) **`SERVER_ERROR("share.internal")`**. ❌ Семантически уже (500), но менее конкретно. `INTERNAL` совпадает с HTTP-стандартом (500 Internal Server Error).

## R4: Идемпотентность миграций 38/39 и edge cases «частичного применения»

**Вопрос**: что если на проде миграция применилась частично (например, только таблицы без триггеров)?

**Метод**: чтение `38_song_share_links.sql:23-27` («Идемпотентен»), `39_song_share_recordhash.sql:5-7» («Идемпотентен»), проверка SQL на наличие `CREATE TABLE IF NOT EXISTS`, `CREATE OR REPLACE FUNCTION`, `DO`-блоков для триггеров.

**Решение** (Decision):

Повторное применение миграций 38 и 39 БЕЗОПАСНО:
- `38_song_share_links.sql`:
  - `CREATE TABLE IF NOT EXISTS public.tbl_song_share_links` (line 32) — не упадёт если уже создана.
  - `CREATE TABLE IF NOT EXISTS public.tbl_song_share_sessions` (line 116) — то же.
  - `DO`-блоки для IDENTITY (lines 57-75, 139-157) — `IF NOT EXISTS` через `pg_attribute` + `pg_class`.
  - `DO`-блоки для PRIMARY KEY (lines 77-86, 159-167) — `IF NOT EXISTS` через `pg_constraint`.
  - `CREATE UNIQUE INDEX IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` (lines 89-111, 170-178) — стандартный идемпотентный паттерн.
- `39_song_share_recordhash.sql`:
  - `CREATE OR REPLACE FUNCTION` (lines 12-36, 69-90) — пересоздаёт функцию при повторном применении.
  - `CREATE INDEX IF NOT EXISTS` (lines 38-42, 92-96) — идемпотентно.
  - `DO`-блоки для триггеров (lines 45-64, 98-115) — `IF NOT EXISTS` через `pg_trigger`.

**Edge case «частичного применения»**: Если на проде случайно была создана только `tbl_song_share_links` без `tbl_song_share_sessions` (ручной запуск, обрыв трубы), повторное применение 38 создаст недостающую таблицу. Повторное применение 39 создаст недостающие триггеры. **Безопасно в любом порядке**.

**Edge case «DDL восстановлен обрезанным»**: Pass 47 восстановил DDL из `git fsck --lost-found` (см. шапку `38_song_share_links.sql:24-27»). Если восстановление было обрезанным (потеря колонок) — FR-003 ловит это: `psql \d tbl_song_share_links` покажет полный набор колонок или неполный. После фикса US2 клиент увидит 500 `share.internal` с указанием `column "..." does not exist` — оператор примет решение (дропнуть и применить заново, или ALTER).

**Alternatives considered**:
- (a) **Сделать миграцию «безопасной дропнуть-и-пересоздать»** (`DROP TABLE IF EXISTS` перед `CREATE`). ❌ Удаляет существующие данные — на проде может уже быть 0 строк (нет claim'ов, потому что таблиц не было), но если случайно apply прошёл раньше — потеря данных.
- (b) **Скрипт-проверка перед миграцией** (отдельный `.sql` который проверяет состояние и предлагает действия). ❌ Overengineering для hotfix — 7 строк SQL + лог-вывод достаточно показали через `psql \dt`.

## R5: Scope FR-014 audit — какие именно catch-all'ы маскируют

**Вопрос**: насколько широким делать аудит catch-all'ов в других share-эндпоинтах?

**Метод**: полное чтение `PublicShareController.kt` + grep по `catch (_: Exception`.

**Решение** (Decision):

Аудит покрывает **3 точки**:

1. `PublicShareController.kt:174-175` (`/claim`) — основная маскировка, симптом 500 share.notFound.
2. `PublicShareController.kt:87-89` (`/{songId}/create`) — та же маскировка. После применения миграции 38/39 `create` будет работать, но если что-то пойдёт не так — будет маскировать под 500 share.notFound.
3. `PublicShareController.kt:189-191` (`/heartbeat`) — маскировка под **410 share.leaseExpired** (вместо 500). Это **другой код ошибки**, но та же антипаттерн-структура (любой Exception → leaseExpired). После применения миграции 38/39 heartbeat должен работать, но если БД ляжет — heartbeat покажет «lease истёк» вместо «внутренняя ошибка» (вводит в заблуждение, владелец подумает «надо перезапустить плеер»).

`/release`, `/debug`, `/mine/{songId}`, `/mine/{songId}/revoke` — **без catch-all**, дефолтный Spring-обработчик вернёт 500 без структурированного JSON. Это **приемлемо** для hotfix: в спеку 164 это перенесено как «общий observability backlog», не блокер.

**Rationale**:
- 3 точки — bounded, ~6 строк кода суммарно (по 2 на точку: 1 для catch, 1 для return).
- Тесты (см. quickstart) проверяют все три точки явно.
- Не делаем 4-ю точку — refactor на `@ControllerAdvice` — это уже отдельная задача.

**Alternatives considered**:
- (a) **Полный рефактор всех share-эндпоинтов через `@ControllerAdvice`**. ❌ Overengineering: затрагивает 5+ эндпоинтов, требует нового файла, требует тестов для каждого эндпоинта. Backlog spec 164 или отдельная фича.
- (b) **Только /claim (минимальный hotfix)**. ❌ Частично: FR-014 спеки прямо говорит «audit + replace if pattern found», то есть три точки — это минимум. Пренебрежение = нарушение FR-014.
- (c) **Только /claim и /create (без heartbeat)**. ❌ Непоследовательно: heartbeat тоже маскирует, оставлять «более редкий» случай без фикса — это просто «дешевле, но неправильно».

## R6: Поведение `loadSongInfo` при удалённой из `tbl_songs` песне

**Вопрос**: что произойдёт, если владелец создал ссылку, а песню удалили из `tbl_songs` до claim?

**Метод**: чтение `SongShareLinkService.kt:954-990` (`loadSongInfo`).

**Решение** (Decision):

Текущее поведение: `loadSongInfo` (line 962) делает `if (!rs.next()) throw NotFound()` — если песни нет, бросает `NotFound`. Это **уже корректно**: claim вернёт 404 `share.notFound`. Это **ожидаемое** поведение (нет песни = нет шары), не путать с системной ошибкой.

Спека US2#2 (FR-013 спеки) описывает NPE как пример системной ошибки, но в текущем коде этот сценарий не возникает — `?: Elvis` на `rs.getString(...)` обрабатывает null-значения. **Сценарий NPE гипотетический**, реальная защита — `if (!rs.next()) throw NotFound()`. Эта защита работает правильно.

**Влияние на спеку**: US2#2 иллюстрирует принцип «любая неожиданная ошибка → 500 share.internal», пример с NPE — учебный, не функциональный. Реализация покрывает все варианты через `catch (e: Exception) → throw InternalError(e)` в `tryClaim`.

**Edge case для acceptance-сценария US2#2**: проверить **отсутствие таблицы** `tbl_songs` (не удаление одной строки, а DROP TABLE всей таблицы) — это приведёт к SQLException в `loadSongInfo` → проброс через контроллер → 500 `share.internal` с реальным классом исключения в логах. Этот сценарий покрывается US2#1 (relation does not exist для `tbl_song_share_links`); тот же путь для `tbl_songs` если таблица случайно дропнута — реализуется автоматически.

**Decision по спеке**: оставить US2#2 как есть, дополнить комментарием в `data-model.md` о реальном поведении `loadSongInfo`. Это **не меняет** спеку, только делает её более точной.

**Alternatives considered**:
- (a) **Заменить `throw NotFound()` на `throw SongUnavailable()`** в `loadSongInfo:962`. ❌ Семантически лучше (`songUnavailable` точнее для удалённой песни), но: (1) текущая логика «нет song = нет ссылки» уже работает как `notFound`; (2) изменение response code ломает существующие интеграционные тесты (если будут); (3) `notFound` обрабатывается фронтом одинаково с `songUnavailable` — оба показывают generic «Не удалось…». **Backlog spec 164**.

## R7: Когда применять миграцию относительно деплоя кода

**Вопрос**: каков правильный порядок — сначала миграция, потом деплой, или наоборот?

**Метод**: чтение шапки `38_song_share_links.sql:19-22`, AGENTS.md Q&A про миграции.

**Решение** (Decision):

**Миграция → Деплой** (миграция применяется первой, код деплоится вторым):

1. После применения миграции **существующая версия** `karaoke-web` (та, что в проде сейчас) **по-прежнему не сможет делать claim** — таблицы есть, но код не пишет в них (точнее, код уже пишет, но catch-all маскирует). Нет регрессии: всё работает как раньше (claim падает, но теперь БД готова).
2. После деплоя нового кода (с `InternalError` вместо `NotFound` + проброс наверх) **claim начинает работать**.

Обратный порядок (деплой кода → миграция) **более рискованный**:
- Новый код пишет в несуществующую таблицу → SQLException → `InternalError` → 500 `share.internal`.
- До фикса: 500 `share.notFound` (маскировка), после фикса без миграции: 500 `share.internal` (точнее, но всё равно 500).
- Владелец увидит «Не удалось создать ссылку» в модалке — **регрессия** по сравнению с «Ссылка формируется» (UI показывает URL из client-side cache даже на 500).

**Спека фиксирует**: FR-004 явно говорит «применить на PROD-БД вручную ДО деплоя karaoke-web». README шапки миграции — то же самое.

**Rationale**:
- Forward-compatible: применение миграции без нового кода не ломает ничего.
- Backward-incompatible (теоретически): деплой кода без миграции создаёт 500 `share.internal` вместо `share.notFound` — клиент видит то же «Не удалось…», но код ошибки в логах другой. **Приемлемо** — это явный признак «нужно применить миграцию».
- **Никаких data migrations** (только schema) — нет риска повреждения данных.

**Alternatives considered**:
- (a) **Деплой атомарно (в одном коммите + одном push)**. ❌ Невозможно физически: миграция применяется через `psql`, деплой — через `docker push/pull` + restart. Эти два действия выполняются раздельно (миграция — ssh + psql, деплой — deploy_web.sh).
- (b) **Добавить фичу-флаг в `WebShareProperties.share.internalErrorMode`** = `legacy`/`strict`. ❌ Overengineering: legacy mode = текущая маскировка (та, что приводит к багу); strict mode = новое поведение. Дефолт strict, откат = revert коммита. Никакого смысла в фича-флаге.
- (c) **Сначала деплой, потом миграция**. ❌ Описано выше — создаёт 500 `share.internal` окно между деплоем и миграцией. Хуже, чем текущее поведение, по UX.

## R8: Какие env-переменные / конфигурация требуются

**Вопрос**: нужно ли добавлять новые env-переменные (например, `WEB_SHARE_DEBUG_KEY`)?

**Метод**: чтение спеки FR-014, Clarification Q3.

**Решение** (Decision):

**Никаких новых env-переменных** для этого hotfix.

`WEB_SHARE_DEBUG_KEY` (защита `/debug`) — **backlog spec 164** (Clarification Q3 спеки 167). На текущем этапе:
- `/debug` остаётся публичным, как и сейчас.
- FR-020 (активация диагностической ценности) применяется.
- Реальная защита — отдельная задача (FR в спеке 164, ещё не написан).

**Rationale**:
- Backlog уже явно зафиксирован, дублировать не нужно.
- Hotfix не должен вводить новых конфигов — это прерогатива полноценной фичи (не hotfix).

**Alternatives considered**:
- (a) **Добавить `WEB_SHARE_DEBUG_KEY` как опциональную env-переменную в hotfix**. ❌ Увеличивает scope, требует изменений в `WebMvcConfig.kt`/`SecurityConfig.kt` (если есть) + README. Лучше отдельным PR.
- (b) **Сразу закрыть `/debug` (вернуть 404)**. ❌ Теряем диагностический инструмент, нужный для проверки фикса (см. quickstart).

## Сводка решений

| Область | Решение | Scope |
|---|---|---|
| **R1: catch-all** | 3 точки маскировки (claim/create/heartbeat) → проброс `InternalError` | ~6 строк Kotlin |
| **R2: sealed-подтип** | `class InternalError(cause: Throwable) : ShareException(INTERNAL, 500)` | ~10 строк Kotlin |
| **R3: enum** | `INTERNAL("share.internal")` в конец `ShareErrorCode` | 1 строка Kotlin + 4 строки KDoc |
| **R4: DDL idempotency** | миграции 38/39 применимы повторно безопасно | без изменений |
| **R5: scope FR-014** | audit 3 точки в PublicShareController, не больше | см. R1 |
| **R6: loadSongInfo edge case** | оставить как есть, поведение корректное | без изменений |
| **R7: order migration→deploy** | миграция первая, код вторым | операционная инструкция в quickstart |
| **R8: env-vars** | никаких новых | без изменений |

Все 8 областей resolved. Никаких `NEEDS CLARIFICATION` не осталось — спека + Clarifications (Q1-Q4) + research.md покрывают план полностью.

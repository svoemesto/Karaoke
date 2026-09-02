# Research: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Status**: Phase 0 complete | **Date**: 2026-09-02

Цель этого документа — зафиксировать технические решения по реализации,
основанные на анализе существующего кода. Все `NEEDS CLARIFICATION` из
`spec.md` (или потенциальные) разрешены здесь.

---

## R1: Паттерн передачи флага в runtime — `SiteUserResolver` без нового bean

**Decision**: Использовать существующий `SiteUserResolver`
(`karaoke-web/src/main/kotlin/com/svoemesto/karaokeweb/services/SiteUserResolver.kt`)
для получения текущего пользователя и чтения `canWorkWithSkipped`. Никаких
новых beans, никакого нового SQL — поле уже подгружается как часть
`SiteUser` через существующую цепочку
`Authorization → SiteUserTokenService.resolveToken → SiteUser.loadById`.

**Rationale**:
- `SiteUserResolver.resolve(request)` уже вызывается во многих контроллерах
  (`PublicSongeditorController.kt:58`, `PublicChatController.kt:43`,
  `PublicStemJobController.kt:54`, `PublicNewsController.kt:73`,
  `PublicCartController.kt:48` и т.д.). Добавление нового поля в `SiteUser`
  автоматически делает `siteUserResolver.resolve(request)?.canWorkWithSkipped`
  доступным во всех этих местах **без дополнительного кода на стороне
  контроллеров, где SiteUser уже резолвится**.
- `SiteUserResolver` намеренно НЕ кэширует результат (см. KDoc в файле:
  «Не кэшируется — бан/снятие премиума должны действовать немедленно»).
  Это означает, что изменение `can_work_with_skipped` админом отражается
  у пользователя на **следующем HTTP-запросе** — без logout/login (если
  смотреть строго по NFR-002). Это лучше, чем у `canSelfAssignTasks`,
  который завязан на кэшированный JWT-claims.
- Для контроллеров, которые сейчас НЕ резолвят SiteUser (например,
  `MainController.zakroma` с Thymeleaf-шаблоном), нужно либо добавить
  resolve, либо передавать флаг через параметр/Model attribute.

**Alternatives considered**:
- **A. Новый request-scoped bean `SkippedContentAccessChecker`** — отвергнуто.
  Дублирует функционал `SiteUserResolver`, увеличивает boilerplate без
  выгоды. Текущий паттерн «один резолвер, много контроллеров» —
  established в проекте.
- **B. SecurityContextHolder-атрибут с кэшированием на HTTP request** —
  отвергнуто. Spring Security в проекте используется только для
  `permitAll()` (admin webvue3) и для JWT-auth на `/api/public/account/*`.
  `karaoke-web` использует свой `SiteAuthInterceptor` + request attribute
  `SITE_USER_ATTR` (см. `PublicChatController.kt:43`,
  `PublicHistoryController.kt:27`). Дублировать через
  `SecurityContextHolder` = лишний слой.
- **C. Cookie-флаг с подписью HMAC** — отвергнуто. SKIP — это
  правообладатель, нельзя делегировать принятие решения о доступе
  клиенту (cookie можно подделать). A-004 в спеке явно требует
  серверного решения.

---

## R2: SQL-фильтрация vs фильтрация в Kotlin — где ставить гард

**Decision**: Везде, где сейчас SKIP-фильтр реализован в SQL
(`StatBySong.SKIP_FILTER`), переключение делается через **выбор SQL на
основе флага пользователя**. Везде, где фильтр в Kotlin
(`ListeningHistoryController.songHasSkipTag`, `PublicOgSongController.isSkipped`,
`SongShareLinkService.songHasSkipTag`) — добавляется early-return
`if (siteUser?.canWorkWithSkipped == true) return false`.

**Rationale**:
- В `StatBySong` фильтр `SKIP_FILTER` уже встроен в кэшированные SQL —
  нельзя «раскэшировать» на каждый запрос (cache key должен оставаться
  стабильным). Решение: использовать `cacheSkip = siteUser?.canWorkWithSkipped
  ?: false` для определения **строки SQL** на момент холодного старта
  кэша. То есть:
  - Если **никто из пользователей** не имеет галочки → кэш использует
    строгий `SKIP_FILTER` (как сейчас).
  - Если есть хотя бы один пользователь с галочкой → кэш использует
    OR-вариант `($SKIP_FILTER OR true)` (т.е. **никакого SKIP-фильтра**).
  - Альтернатива: всегда использовать OR-вариант + один флаг
    `cacheUserCanSeeSkipped`, прокидываемый в SQL — тогда **все**
    пользователи получают данные с SKIP-песнями, а фильтрация делается
    постфактум в Kotlin. Это безопаснее для консистентности.
- В Kotlin-фильтрах (`isSkipped`, `songHasSkipTag`) — простой
  `if (siteUser?.canWorkWithSkipped == true) return false` гарантирует,
  что SKIP-песни **никогда не помечаются как skipped** для авторизованного
  пользователя с галочкой. Это инвертирует существующую логику, но
  сохраняет обратную совместимость для анонимов (которые получают
  `null` от resolver).

**Alternatives considered**:
- **A. Делать гард в `Song.loadListFromDb`** (фильтровать SKIP-песни
  уже после загрузки) — отвергнуто. Нарушает существующий SQL-first
  паттерн, плюс создаёт N+1 (загрузили все, потом отфильтровали).
- **B. Делать гард только в UI** (фронт сам решает, что показывать) —
  отвергнуто. SKIP-песня содержит чувствительный контент; нельзя
  отдавать его анонимному пользователю в JSON-ответе, даже если
  фронт его не рендерит. Серверный фильтр обязателен.

---

## R3: SQL-варианты `SKIP_FILTER` в `StatBySong`

**Decision**: Расширить SQL в `StatBySong.kt:101` так, чтобы фильтр
**всегда** возвращал данные с включёнными SKIP-песнями (т.е. без
SKIP-фильтра), а сама фильтрация делалась постфактум в Kotlin-слое
прикладных контроллеров (`PublicApiController`, `PublicOgSongController`,
`SongShareLinkService`).

**Rationale**:
- `StatBySong` используется как статистический счётчик — значения идут в
  публичные тайлы и `StatBySong.getCountSongsInCollection()` (главная
  страница). Если убрать SKIP-фильтр из счётчика **для всех** —
  счётчик на главной вырастет на количество SKIP-песен, что для
  анонимного пользователя недопустимо (UI «скрыто от публики»
  нарушится).
- Решение: **оставить** SKIP-фильтр в `StatBySong` SQL как есть
  (для публичных счётчиков), но добавить **отдельный SQL-фильтр
  `$USER_CAN_SKIP_OVERRIDE`**, который применяется в контроллерах,
  отдающих авторизованному пользователю с галочкой:
  ```sql
  ($SKIP_FILTER OR $USER_CAN_SKIP_OVERRIDE)
  ```
  где `$USER_CAN_SKIP_OVERRIDE` — буквальный SQL `true` (для пользователя
  с галочкой) или `false` (для анонима/без галочки).
- Это сохраняет кэш `StatBySong` нетронутым (его ключ не зависит от
  пользователя), но добавляет динамический фильтр в контроллеры
  (`PublicApiController.zakroma`, `PublicApiController.authorsTiles`,
  `PublicOgSongController.isSkipped`).

**Alternatives considered**:
- **A. Делать отдельный кэш `StatBySongForEditors`** — отвергнуто.
  Удваивает кэш-инфраструктуру, усложняет refresh. На этом этапе
  пользователей с галочкой < 10, общий кэш работает для всех.
- **B. Фильтровать в Kotlin после загрузки** — отвергнуто как основной
  путь (см. R2), но допустимо как fallback для небольших результатов
  (например, `Zakroma.getZakroma(author=…)` — там нагрузка низкая).

---

## R4: `PublicOgSongController.isSkipped(song)` — текущая сигнатура не имеет request

**Decision**: Расширить сигнатуру `isSkipped(song: Song): Boolean` →
`isSkipped(song: Song, canSeeSkipped: Boolean): Boolean` и обновить все
вызывающие места в `PublicOgSongController` (3 места в текущем файле,
см. строки 106, 267, 333) для прокидывания `siteUserResolver.resolve(request)?.canWorkWithSkipped ?: false`.

**Rationale**:
- Метод `isSkipped` сейчас private (см. `PublicOgSongController.kt:437`),
  все вызовы — внутри одного класса, что упрощает рефакторинг.
- Альтернатива — хранить SiteUser как request-атрибут и читать в
  `isSkipped` через `RequestContextHolder` — анти-паттерн (тестируемость
  падает, связность растёт). Лучше явная сигнатура.

**Alternatives considered**:
- **A. Добавить request-scoped переменную через ThreadLocal** — отвергнуто.
  Усложняет тестирование (нужно чистить ThreadLocal), плюс в Spring уже
  есть `RequestContextHolder`, но его использование в private helper —
  overkill.
- **B. Сделать `isSkipped` extension-функцией на `(Song, SiteUser?)`** —
  рассмотрено, но Kotlin-стиль проекта — class methods, не extensions
  (см. `SiteUser.kt`, `Author.kt`).

---

## R5: Share-link для SKIP — `409 Conflict` + UI hide

**Decision**:
- В `SongShareLinkService.createShareLink(...)` (или эквивалентном
  публичном методе) добавить проверку `if (songHasSkipTag(song.tags))
  throw ShareLinkForSkippedContentException()` → контроллер переводит
  это в HTTP `409 Conflict` с сообщением
  «Невозможно создать share-link для SKIP-контента».
- В UI (karaoke-public) кнопка «Поделиться» в карточке SKIP-песни
  скрыта или disabled (`v-if="!song.skipped || !user.canWorkWithSkipped"`
  или просто `v-if="!song.skipped"` — SKIP-песни ВСЕГДА запрещены для
  share-link независимо от прав, см. FR-012).
- В существующем методе-получателе (`getSongByShareToken` или аналог)
  сохраняется фильтрация SKIP для анонимов как defense in depth.

**Rationale**:
- Это сознательное исключение из общего правила «с флагом всё
  разрешено» (см. A-003 доп. в спеке). Compliance с требованиями
  правообладателя важнее UX-единообразия.
- 409 (Conflict) — корректный HTTP-код: запрос синтаксически
  правильный, но конфликтует с текущим состоянием ресурса
  (SKIP-флагом).

**Alternatives considered**:
- **A. Молча отдавать 404** (как будто песни не существует) — отвергнуто.
  Затрудняет отладку для редакторов («почему моя ссылка не работает?»).
  Явный 409 с сообщением лучше для UX.
- **B. Разрешить share-link, но получатель должен иметь галочку** —
  отвергнуто пользователем в clarify Q2 (compliance risk).

---

## R6: Бейдж SKIP в UI — без новых Vue-компонентов

**Decision**: Inline-разметка `<span class="badge text-bg-warning ms-2">SKIP</span>`
рядом с именем автора/песни, рендеринг через `v-if="userCanSeeSkipped &&
song.tags.includes('SKIP')"`. CSS-класс `badge text-bg-warning` уже
используется в `karaoke-public` (Bootstrap 5).

**Rationale**:
- Минимум кода, нет новых компонентов, легко откатить если UX не зайдёт.
- Bootstrap-классы — устоявшийся паттерн в karaoke-public (см. существующие
  бейджи в `AccountView.vue`, `SearchView.vue`).

**Alternatives considered**:
- **A. Создать `KmBadgeSkip.vue` компонент** — отложено. Если inline-
  вариант будет рефакториться в 5+ местах — выделить в компонент.
  Сейчас мест 2-3 (карточка автора в Закромах, карточка песни в альбоме).

---

## R7: Номер миграции Flyway — V45

**Decision**: Новая миграция `deploy/karaoke-db/45_site_user_can_work_with_skipped.sql`.

**Rationale**:
- Текущая последняя миграция — `44_author_song_counts.sql` (см. `ls deploy/karaoke-db/`).
- Следующий номер — 45, по паттерну в файле V40 (`can_self_assign_tasks`)
  — additive ALTER TABLE с пересозданием `update_tbl_site_users_recordhash`
  + UPDATE для пересчёта md5 на существующих строках.

**Alternatives considered**:
- **A. Сгруппировать несколько фич в одну миграцию** — отвергнуто
  (Constitution §VI неявно, общая практика — одна фича = одна миграция).
- **B. Использовать timestamp-префикс** — отвергнуто (проект всегда
  использует sequential numbering).

---

## R8: Race condition — админ меняет флаг во время активной сессии

**Decision**: Не блокировать и не делать push-уведомлений. Решение —
**следующий HTTP-запрос пользователя прочитает актуальное значение** через
`SiteUserResolver.resolve(request)` (см. R1). Если у пользователя
активна долгая сессия (например, открытая страница в браузере без
перезагрузки), изменение флага отразится после первого AJAX-запроса
или refresh страницы.

**Rationale**:
- `SiteUserResolver` намеренно не кэширует результат (см. R1).
- Push-уведомления через WebSocket не используются для подобных
  метаданных в karaoke-public (только для событий и стримов Закромов).
- Для типичного workflow админа (выставить галочку → попросить
  редактора обновить страницу) — этого достаточно.

**Alternatives considered**:
- **A. Cache-invalidation через Redis pub/sub** — отвергнуто.
  Constitution §V явно: «Redis — не используется». Плюс overkill
  для одной колонки.
- **B. Polling каждые 30 секунд** — отвергнуто. Лишний трафик без
  явной UX-потребности.

---

## R9: Регресс-тест на анонимных пользователях

**Decision**: SC-003 («diff ответов до/после фичи = 0 для анонимов»)
проверяется вручную через `curl` без `Authorization`-заголовка.

**Rationale**:
- CI-тесты в проекте `@Disabled` (см. Constitution). Проверка —
  ручная, но простая и быстрая (1 SQL-запрос с записью response в файл
  до фичи → после фичи → `diff`).
- Запрос: `curl -s 'http://localhost:8897/api/public/zakroma' -o
  /tmp/before.json` (аналогично после).

**Alternatives considered**:
- **A. Добавить интеграционный тест** — отложено. Существующие
  `@Disabled` тесты не запускаются; добавлять новые без CI-gate
  бесполезно.

---

## R10: `Zakroma.getZakroma` — прокидывание флага через сигнатуру

**Decision**: Расширить сигнатуру `Zakroma.getZakroma(author, ..., canSeeSkipped: Boolean = false)`
и `Zakroma.getZakromaBySpecialOrder(..., canSeeSkipped: Boolean = false)`.
Внутри — выбор `withSkiped` для `Song.loadListAuthors` + пост-фильтрация
тегов SKIP в `buildFromSongs` через `canSeeSkipped`.

**Rationale**:
- `Zakroma.getZakroma` уже имеет `onlyPublished: Boolean` параметр для
  статуса готовности — паттерн расширения сигнатуры устоявшийся.
- `buildFromSongs` сейчас не фильтрует SKIP-песни (фильтр на уровне
  `Song.loadListAuthors` с `withSkiped=false` уже отсекает SKIP-авторов;
  для SKIP-песен НЕ-skip-авторов фильтр не нужен на стороне БД — он
  в `Song.loadListFromDb`). Если нужен фильтр на уровне песен —
  добавить в `Song.loadListFromDb` параметр `skipTags: Boolean = true`
  по аналогии с `withSkiped`.

**Alternatives considered**:
- **A. Полная переработка `Zakroma` на per-user DTO** — отвергнуто.
  Текущая структура работает, изменения минимальны.

---

## Resolved `NEEDS CLARIFICATION`

Все потенциальные NEEDS CLARIFICATION из спеки и из технического анализа
разрешены здесь:

| # | Вопрос | Решение |
|---|--------|---------|
| N1 | Где хранить lookup флага? | В SiteUser, без нового bean (R1) |
| N2 | Кэшировать на request scope или нет? | Нет (R1, R8) |
| N3 | SQL vs Kotlin фильтрация? | Гибрид (R2, R3) |
| N4 | Сигнатура `isSkipped(song)`? | Расширить до `(song, canSeeSkipped)` (R4) |
| N5 | Share-link для SKIP? | 409 Conflict + UI hide (R5) |
| N6 | Бейдж — отдельный компонент или inline? | Inline Bootstrap (R6) |
| N7 | Номер миграции? | V45 (R7) |
| N8 | Race condition при смене флага? | Без push, на следующем запросе (R8) |
| N9 | Регресс-тест анонимов? | curl + diff (R9) |
| N10 | Сигнатура `Zakroma.getZakroma`? | + `canSeeSkipped: Boolean = false` (R10) |

Готов к Phase 1.
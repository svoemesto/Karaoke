# Feature Specification: 293 — Галочка «Работа со SKIP-авторами и песнями» в настройках пользователя

**Feature Branch**: `293-skip-author-toggle`
**Created**: 2026-09-02
**Status**: Draft
**Input**: User description: "В настройках пользователя должна быть \"галочка\", разрешающая пользователю работать со SKIP-авторами и песнями."

## Контекст (живой глоссарий проекта)

В проекте «Karaoke» существуют **два независимых механизма «SKIP»**, скрывающих
контент от обычной публичной поверхности (UI сайта, SEO-страниц, share-ссылок,
истории прослушиваний, статистики):

| Источник SKIP | Где живёт | Что делает |
|---------------|-----------|------------|
| Флаг автора | `tbl_authors.skip = TRUE` | Скрывает автора целиком (вместе со всеми его песнями) из UI и тайлов; см. `Song.loadListAuthors(withSkiped: Boolean = true, …)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt:7211` — по умолчанию `withSkiped = true`, но все публичные вызовы в `MainController`/`PublicApiController` передают `withSkiped = false`, фильтруя `WHERE skip = false`. |
| Тег SKIP в песне | `tbl_songs.tags` содержит токен `SKIP` (split по пробелам, uppercase-сравнение) | Скрывает конкретную песню. Используется в: `PublicOgSongController.isSkipped` (OG/SEO), `SongShareLinkService` (share-link), `ListeningHistoryController` (история прослушиваний), `StatBySong` (SQL `SKIP_FILTER`), `SongPublicDto` (поле `skipped` в DTO). |

Семантика SKIP — «контент удалён по требованию правообладателя» (см.
`PublicOgSongController.kt:435` и `Constitution §V` про тег SKIP).

Сейчас **оба фильтра применяются безусловно ко всем пользователям** — даже к
редакторам и администраторам. Редакторы, которым нужно исправить метаданные
SKIP-песни или снять SKIP-тег с автора, не могут увидеть такие записи в
списках («Закрома», история, share-link), пока не залезут в БД напрямую.

**Цель фичи**: добавить булев флаг на уровне пользователя, разрешающий ему
работать со SKIP-контентом. Флаг выставляет **только администратор** в форме
редактирования пользователя в `webvue3` (аналогия с уже существующим флагом
`canSelfAssignTasks`). В `karaoke-public/AccountView` флаг не отображается —
по выбору пользователя (уточнение в /speckit.specify, 2026-09-02).

## Clarifications

### Session 2026-09-02

- Q: Должен ли редактор с галочкой видеть визуальный индикатор «SKIP» на SKIP-авторах и SKIP-песнях в UI? → A: Да, бейдж «SKIP» / «скрыто» в UI для редакторов. Только для пользователей с `can_work_with_skipped = TRUE`; обычные пользователи бейдж не видят.
- Q: Как должно работать создание share-link для SKIP-песни редактором с галочкой? → A: Запретить создание share-link для SKIP-песен (compliance: правообладатель). Кнопка «Поделиться» либо скрыта в UI для SKIP-песен, либо показывает ошибку «Невозможно создать share-link для SKIP-контента». Существующее поведение `SongShareLinkService` (фильтрация) сохраняется как защита в глубину — даже если ссылка создана в обход UI, анонимный получатель увидит заглушку.
- Q: Должна ли галочка автоматически даваться админам (`is_admin = TRUE`)? → A: Нет, только явная выдача. Админ выставляет галочку себе сам через webvue3, как и любому другому пользователю. Унифицированная логика без OR-усложнений: `can_work_with_skipped` проверяется независимо от `is_admin`. Это сознательный выбор — даже админам лучше иметь явный признак «у меня есть право работать с SKIP» в БД (для аудита и самоконтроля), чем полагаться на неявную роль.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Администратор выдаёт редактору право работать с SKIP (Priority: P1)

Администратор открывает в `webvue3` список пользователей → карточку редактора
Петра → раздел «Права и роли» → ставит новую галочку «Может работать со
SKIP-авторами и песнями» → сохраняет. После повторного логина Петра на
`karaoke-public` его фильтры на SKIP-контенте снимаются: в «Закромах» он
видит песни автора, у которого `tbl_authors.skip = true`, и отдельные песни
с тегом `SKIP` в `tbl_songs.tags`. Без галочки — поведение прежнее, как у
всех.

**Why this priority**: это базовая возможность, без которой фича не имеет
смысла. Без выставления флага админом нечего тестировать в UI редактора.

**Independent Test**: можно выдать галочку одному тестовому редактору и
проверить только его сессию — другие пользователи не затронуты.

**Acceptance Scenarios**:

1. **Given** админ открыл карточку редактора Петра в `webvue3`,
   `tbl_site_users.can_work_with_skipped = FALSE`, **When** админ ставит
   новую галочку и нажимает «Сохранить», **Then** в БД записано
   `can_work_with_skipped = TRUE`, и при следующем логине Петра фильтры SKIP
   для него снимаются.
2. **Given** редактор ранее получил `can_work_with_skipped = TRUE`,
   **When** админ снимает галочку и сохраняет, **Then** в БД записано
   `can_work_with_skipped = FALSE`, и при следующем логине редактора фильтры
   SKIP возвращаются (прежнее поведение).
3. **Given** админ редактирует обычного не-редактора (не `is_editor`),
   **When** он открывает карточку, **Then** галочка «Может работать со
   SKIP-авторами и песнями» отображается как **доступная** (по выбору
   пользователя — она не привязана жёстко к `is_editor`; см. Assumptions),
   но tooltip подсказывает, что она предназначена для редакторов.

### User Story 2 — Редактор с галочкой видит SKIP-контент в публичных списках (Priority: P1)

Редактор Иван (с `can_work_with_skipped = TRUE`) открывает «Закрома» на
`karaoke-public`. В списке авторов у него отображается «Skip-автор Тест»
(`tbl_authors.skip = TRUE`, был скрыт ранее). При выборе этого автора в
подробке видны все песни, в том числе с тегом `SKIP` в `tags`. История
прослушиваний и share-link тоже учитывают SKIP-песни.

**Why this priority**: основной потребительский сценарий — редактор должен
«видеть», чтобы «работать» (исправить метаданные, снять тег, переоформить
альбом).

**Independent Test**: можно проверить только endpoint `GET /api/public/zakroma`
для одного тестового пользователя — фронт не меняется в этой фиче.

**Acceptance Scenarios**:

1. **Given** редактор Иван с `can_work_with_skipped = TRUE`,
   **When** он вызывает `GET /api/public/zakroma` (или заходит на
   `/zakroma`), **Then** в ответе присутствуют записи, у которых
   `tbl_authors.skip = TRUE` (раньше они фильтровались по
   `withSkiped = false`).
2. **Given** редактор Иван с `can_work_with_skipped = TRUE`,
   **When** он открывает страницу SKIP-песни (через share-link или прямой
   URL), **Then** песня отображается (раньше `PublicOgSongController`
   отдавал SEO-заглушку «удалено по требованию правообладателя» для бота, а
   плеер показывал только превью; теперь для пользователя с галочкой — полный
   плеер).
3. **Given** обычный пользователь Мария (без редакторской роли и без галочки),
   **When** она делает те же запросы, **Then** SKIP-контент для неё
   по-прежнему скрыт (поведение не изменилось).

### User Story 3 — Администратор видит галочку в таблице пользователей (Priority: P2)

Администратор открывает список всех пользователей в `webvue3`. У каждого
отображается новая колонка «SKIP-доступ» с галочкой/пусто, чтобы быстро
найти, кому уже выдано право.

**Why this priority**: улучшает админский UX, но не блокирует основной сценарий.
Можно отложить в отдельный PR, если форма редактирования (User Story 1) уже
работает.

**Independent Test**: можно выдать галочку 2-3 редакторам и убедиться, что
в таблице видно, кто из них имеет право.

**Acceptance Scenarios**:

1. **Given** в `tbl_site_users` у части пользователей
   `can_work_with_skipped = TRUE`, **When** админ открывает таблицу
   пользователей в `webvue3`, **Then** новая колонка «SKIP-доступ» отражает
   состояние флага (✓ / пусто).

### Edge Cases

- Что если галочка выдана пользователю, который потом был забанен
  (`is_banned = TRUE`)? → Галочка остаётся в БД как атрибут, но
  фильтры по сути не имеют значения — забаненный пользователь не сможет
  залогиниться. На UI webvue3 это видно по строке статуса («Забанен: …»);
  никаких специальных обработок не требуется.
- Что если редактор с галочкой попытается создать share-link на SKIP-песню?
  → Создание **запрещено** независимо от `can_work_with_skipped`
  (clarify 2026-09-02, Q2 — compliance: SKIP-контент скрыт по требованию
  правообладателя, share-link не должен быть каналом распространения).
  UI скрывает/блокирует кнопку «Поделиться»; API возвращает `409 Conflict`.
  Существующая фильтрация `SongShareLinkService` остаётся как defense in
  depth — анонимный получатель увидит заглушку, даже если ссылка
  каким-то образом появится в обход UI.
- Что если в БД есть старые записи (до миграции) без колонки
  `can_work_with_skipped`? → Миграция добавляет колонку с `DEFAULT FALSE NOT
  NULL`; reflection-loader `KaraokeDbTable` трактует отсутствующее поле как
  `false` (см. инвариант в `SiteUser.kt:69-73` про `Timestamp?`). Backfill
  не требуется.
- Что если админ выставил галочку, но пользователь сейчас залогинен — увидит
  ли он эффект без re-login? → Решение — да, JWT/сессия читает флаг
  заново при каждом защищённом запросе (как `isEditor` и `canSelfAssignTasks`
  уже работают). Если сессионный кэш это блокирует — пользователь пере-
  логинивается. Документируется как «может потребоваться повторный вход».

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: В БД `tbl_site_users` MUST быть добавлена колонка
  `can_work_with_skipped BOOLEAN NOT NULL DEFAULT FALSE`. Миграция — через
  `karaoke-app/src/main/resources/db/migration/` (Flyway), номер — следующий
  свободный после текущего (см. `flyway_schema_history`).
- **FR-002**: В Kotlin-модели `SiteUser` (`karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/SiteUser.kt`)
  MUST появиться поле `@KaraokeDbTableField(name = "can_work_with_skipped")
  var canWorkWithSkipped: Boolean = false` с KDoc, ссылающимся на эту спеку.
- **FR-003**: В `SiteUser.toDTO()` MUST добавляться поле
  `canWorkWithSkipped = canWorkWithSkipped`, а в `SiteUserDto` — соответствующее
  camelCase-поле `canWorkWithSkipped` с KDoc. **Поле НЕ должно сериализоваться
  в `password_hash`-контексте** — следуем существующему `@JsonIgnoreProperties`
  на классе `SiteUser`.
- **FR-004**: В форме редактирования пользователя в `webvue3`
  (`webvue3/src/components/SiteUsers/edit/SiteUserEdit.vue`) MUST появиться
  новый блок «label-and-input» после блока «Может сам назначать себе
  задания» (`canSelfAssignTasks`) с лейблом «Может работать со SKIP-авторами
  и песнями:», чекбоксом, привязанным к `v-model="siteUserCurrent.canWorkWithSkipped"`,
  и подсказкой: «Снимает фильтр SKIP в «Закромах», истории прослушиваний,
  share-link и OG-SEO: редактор видит песни/авторов с тегом SKIP или
  `tbl_authors.skip = true`».
- **FR-005**: В таблице пользователей в `webvue3`
  (`webvue3/src/components/SiteUsers/SiteUsersTable.vue`) MUST появиться
  новая колонка «SKIP-доступ» (boolean-рендер ✓/пусто), подключённая к
  `siteUsers.columns` с `key: 'canWorkWithSkipped'`.
- **FR-006**: В бэкенде (`karaoke-web` / `karaoke-app`) MUST появиться
  единая точка принятия решения «показывать ли SKIP-контенту этому
  пользователю»: `currentSiteUserCanSeeSkipped(siteUserId)` или эквивалент.
  Реализация — single SQL
  `SELECT can_work_with_skipped FROM tbl_site_users WHERE id = ?`,
  кешируется в `SecurityContextHolder` на сессию (как `isEditor`).
- **FR-007**: В `Song.loadListAuthors(...)` (`Song.kt:7211`) параметр
  `withSkiped` MUST по-прежнему иметь дефолт `true` (обратная совместимость),
  но **все** публичные вызовы в `karaoke-web` MUST прокидывать значение
  на основе `currentSiteUserCanSeeSkipped(...)`: если true →
  `withSkiped = true`; если false → `withSkiped = false` (как сейчас).
- **FR-008**: Фильтр по тегу `SKIP` в `tbl_songs.tags` MUST быть
  обусловлен флагом пользователя во всех точках:
  - `PublicOgSongController.isSkipped` (`karaoke-web/.../PublicOgSongController.kt:437`)
    — для авторизованного пользователя с правом возвращать `false`;
  - `SongShareLinkService` (`karaoke-web/.../SongShareLinkService.kt:981`)
    — аналогично;
  - `ListeningHistoryController` (`karaoke-app/.../ListeningHistoryController.kt:191`)
    — аналогично;
  - `StatBySong` (`karaoke-web/.../StatBySong.kt:101`) — SQL
    `SKIP_FILTER` заменяется на `($SKIP_FILTER OR $USER_CAN_SEE_SKIP)`;
  - `SongPublicDto.isSkipped` (`karaoke-web/.../SongPublicDto.kt:138`)
    — вычисляется с учётом флага (для авторизованного юзера с правом —
    `false`).
- **FR-009**: Неавторизованный пользователь (аноним) MUST по-прежнему
  видеть строгий фильтр SKIP — галочка действует только для залогиненных
  пользователей с `can_work_with_skipped = TRUE`. Это критично для SEO и
  share-link, которые чаще всего открываются анонимно.
- **FR-010**: Поле `can_work_with_skipped` НЕ должно быть видимым в
  `karaoke-public/AccountView` (по выбору пользователя, см. Context) —
  никаких изменений в `AccountView.vue` или `SiteUserDto` для публичного
  endpoint'а `/api/public/account/me` **с точки зрения UI-рендера**.
  Поле `canWorkWithSkipped` всё равно прокидывается через DTO (для будущих
  фич), но AccountView его не использует.
- **FR-011**: В UI «Закромов» и связанных страницах редактор с
  `can_work_with_skipped = TRUE` MUST видеть визуальный индикатор
  «SKIP» / «скрыто от публики» на SKIP-авторах и SKIP-песнях:
  - SKIP-автор: бейдж рядом с именем автора (например,
    `<span class="km-badge km-badge-skip">SKIP</span>`), показывается только
    залогиненным пользователям с флагом; обычные пользователи бейдж не
    видят даже если они как-то получат доступ к контенту (UI-фильтр
    upstream);
  - SKIP-песня (тег `SKIP` в `tbl_songs.tags`): бейдж в карточке песни и в
    списке песен альбома; tooltip «Удалено по требованию правообладателя»;
  - Бейдж использует существующий паттерн (`Bootstrap`-класс `badge
    text-bg-warning` или аналог), чтобы не вводить новый CSS.
- **FR-012**: Создание share-link (`SongShareLinkService`) MUST быть
  **запрещено** для SKIP-песен независимо от `can_work_with_skipped`
  инициатора — compliance: SKIP-контент скрыт по требованию
  правообладателя, share-link не должен быть каналом распространения.
  - На UI кнопка «Поделиться» (`share`-action) MUST быть скрыта или
    disabled для SKIP-песен; при попытке создания через API
    возвращается `409 Conflict` с текстом
    «Невозможно создать share-link для SKIP-контента».
  - Это правило НЕ зависит от `can_work_with_skipped` — оно действует на
    сам механизм share-link, независимо от прав доступа.
  - Существующая защита в `SongShareLinkService:981` (фильтрация
    SKIP-песен для анонимного получателя) сохраняется как defense in
    depth.

### Non-Functional Requirements

- **NFR-001**: SQL-проверка `can_work_with_skipped` MUST выполняться **не
  чаще одного раза на HTTP-запрос**. Реализация — lazy-init в
  request-scoped bean или `SecurityContext`-атрибуте (по аналогии с тем,
  как уже решается `siteUserId` в существующих контроллерах через
  `siteUserResolver.resolve(request)`).
- **NFR-002**: Изменение флага админом в `webvue3` MUST отражаться у
  пользователя **в течение одного запроса** после re-login (или refresh
  JWT). Если текущая сессия использует кэшированный siteUser — кэш
  инвалидируется при logout/login; никакого push-механизма не требуется.
- **NFR-003**: Никаких изменений в SQL-триггерах `tbl_site_users` —
  reflection-loader `KaraokeDbTable` подхватит новую колонку автоматически,
  `recordhash`-триггер (если есть) пересчитывается миграцией (см.
  Constitution §III).

### Key Entities

- **`tbl_site_users.can_work_with_skipped`** — новая колонка (Boolean,
  NOT NULL, default FALSE) на пользователе сайта `karaoke-public`. Семантика:
  «этому пользователю разрешено видеть и работать с контентом, скрытым
  механизмами SKIP (`tbl_authors.skip = TRUE` или тег `SKIP` в
  `tbl_songs.tags`)». Не является ролью (как `is_editor`); это право поверх
  роли, по аналогии с `can_self_assign_tasks`.
- **`SiteUser.canWorkWithSkipped`** — Kotlin-поле модели `SiteUser`,
  отражающее колонку `can_work_with_skipped` (см. `SiteUser.kt:131-140`
  — паттерн `canSelfAssignTasks`).
- **`SiteUserDto.canWorkWithSkipped`** — camelCase-поле в DTO,
  передаваемое в webvue3 и karaoke-public через JSON.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Админ может выставить/снять галочку «Может работать со
  SKIP-авторами и песнями» в карточке пользователя в `webvue3` за ≤2
  клика (открыть карточку → переключить чекбокс → сохранить).
- **SC-002**: После выставления галочки и повторного логина редактор в
  «Закромах» видит всех авторов (включая `tbl_authors.skip = TRUE`) и все
  песни (включая те, у которых `tags` содержит `SKIP`). Проверяется на
  одном тестовом редакторе — для него `GET /api/public/zakroma` возвращает
  ≥1 записи с `skip = TRUE`, которой не было в той же выдаче для
  контрольного не-редактора.
- **SC-003**: Для неавторизованного пользователя и для обычного
  залогиненного пользователя без галочки поведение строго идентично
  текущему: ни одного SKIP-автора/песни в публичных endpoint'ах
  (`/api/public/zakroma`, `/api/public/zakroma/<author>`, share-link,
  OG/SEO). Регрессионный тест — diff ответов до/после фичи на 0 для
  анонимов.
- **SC-005**: Редактор с галочкой в «Закромах» визуально отличает
  SKIP-контент от обычного: ≥1 SKIP-автор и ≥1 SKIP-песня отображаются с
  бейджем «SKIP» / «скрыто» в UI; обычные пользователи этот бейдж **не
  видят** (рендеринг условный). Проверяется через скриншот или snapshot-
  тест ключевых страниц.
- **SC-006**: Создание share-link для SKIP-песни возвращает `409 Conflict`
  с сообщением «Невозможно создать share-link для SKIP-контента» —
  независимо от того, есть ли у инициатора `can_work_with_skipped = TRUE`.
  Кнопка «Поделиться» в UI для SKIP-песен скрыта или disabled.
- **SC-004**: Поле `can_work_with_skipped` присутствует в `SiteUserDto`
  и доступно через `GET /api/webvue3/site-users/{id}` (admin endpoint) и
  через `GET /api/public/account/me` (для будущих фич, см. FR-010 — UI не
  рендерит). Стоимость проверки флага на один запрос — ≤1 SQL (см.
  NFR-001).

## Assumptions

- **A-001**: Галочка выставляется администратором в `webvue3`. По выбору
  пользователя в /speckit.specify (2026-09-02) — НЕ отображается в
  `karaoke-public/AccountView`. Это узкий scope; если в будущем
  потребуется self-toggle для редакторов — расширение отдельной фичей.
- **A-002**: Флаг `can_work_with_skipped` НЕ жёстко привязан к
  `is_editor = TRUE` (см. US1, scenario 3). Админ может выдать его
  не-редактору (например, чтобы протестировать). Однако по умолчанию
  такая выдача — необычный кейс; tooltip подсказывает основное
  назначение. **Дополнение (clarify 2026-09-02, Q3)**: галочка НЕ
  выдаётся автоматически админам (`is_admin = TRUE`) — только явная
  выдача через webvue3, унифицированная логика без OR с `is_admin`.
- **A-003**: SKIP-фильтр по тегу `SKIP` в `tbl_songs.tags` снимается
  целиком для пользователя с галочкой — нет «частичного» снятия (типа
  «только в «Закромах», но не в share-link»). Это MVP; частичные
  режимы — отдельная фича при необходимости. **Уточнение
  (clarify 2026-09-02, Q2)**: несмотря на «целиком снимается», share-link
  для SKIP-песен остаётся **запрещён** (FR-012) — это сознательное
  исключение из общей логики ради compliance.
- **A-004**: Анонимный пользователь (без сессии) — всегда видит
  строгий фильтр SKIP (см. FR-009). Никаких cookie-флагов, никаких
  «запомнить выбор».
- **A-005**: Текущая сессия пользователя с уже выданной галочкой НЕ
  обновляется автоматически до logout/login — стандартное поведение для
  всех остальных прав пользователя (см. аналогичное замечание в
  `SiteUser.kt:135-140`). Документируется как edge-case (см. Edge
  Cases, последний пункт).
- **A-006**: Объём миграционных изменений минимален: одна колонка +
  пересоздание `recordhash`-триггера `update_tbl_site_users_recordhash`
  (см. Constitution §III) — без правок существующих триггеров других
  таблиц.
- **A-007**: Endpoint `/api/webvue3/site-users` (admin) уже умеет
  читать/писать `SiteUser` целиком — никаких новых endpoint'ов
  создавать не нужно, кроме возможного патча DTO. Это следует из
  структуры `webvue3` (см. `webvue3/src/components/SiteUsers/SiteUsers.vue`).

## Out of Scope

- Изменения `karaoke-public/AccountView.vue` (по выбору пользователя).
- Снятие фильтра SKIP из публичных OG/SEO-страниц для ботов
  (Googlebot, VK crawler) — тег SKIP **остаётся** в `meta` и в
  SEO-заглушке даже для авторизованного редактора, чтобы не
  индексировать скрытый контент. Это сознательное ограничение (право
  касается только UI редактора, не индексации).
- Изменения в `tbl_authors.skip` или `tbl_songs.tags` — фича только
  про право пользователя, не про сам механизм SKIP.
- Bulk-выдача галочки нескольким пользователям через `webvue3`
  (например, по списку). Если потребуется — отдельная фича.
- Аудит-логирование «кто и когда открыл SKIP-контент» (через
  `tbl_site_events` или аналог) — отдельная фича.

## Связанные документы

- `livedocs/domain/identity.md` — `SiteUser` aggregate.
- `livedocs/domain/catalog.md` — `Song`/`Author` SKIP-механика.
- `archive/docs/features/editor-tasks.md` — паттерн `canSelfAssignTasks`
  (аналогия для нового флага).
- `specs/286-author-song-counts-cache/spec.md` — фикс фильтрации SKIP
  в `Author.loadAuthorTilesWithCounts` (нужно ли обновлять? — Да,
  следует проверить, что публичный тайл `/api/public/authors-tiles`
  фильтрует skip; для редактора с правом — отдаёт всех).
- `Constitution §III` — синхронизация `tbl_site_users` через
  `SyncTarget<SiteUser>` и `recordhash`-триггер: при миграции колонки
  пересоздать триггер для обеих БД (LOCAL и SERVER).
- `Constitution §VI FR-009` — обновить per-feature документ
  `docs/features/editor-skipped-content-access.md` (создать новый)
  в том же PR.
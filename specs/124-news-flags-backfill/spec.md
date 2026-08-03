# Feature Specification: Backfill флагов публикаций готовых песен без создания новостей

**Feature Branch**: `124-news-flags-backfill`

**Created**: 2026-08-03

**Status**: Draft

**Input**: User description: «Надо правильно настроить публикацию новостей на сайте, в телеграми и группе вк. Сейчас в базе около 15000 готовых песен — часть в эфире, часть по подписке. Для всех этих песен надо считать, что уже были новости и публикации в телеграме и вк. А сейчас получается, что если для песни, которая уже давно в коллекции, на локальной машине делают save(), то обновляются флаги, и после синхронизации с сервером на сервере выходит новость о том, что эта песня появилась в коллекции, хотя она там уже давно. Надо привести в порядок флаги на сервере и на админской машине, но так, чтобы это не привело к созданию новостей про все песни.»

## Clarifications

### Session 2026-08-03

- Q: Какая модель распространения backfill-изменений на PROD? → A: A — backfill только на LOCAL; sync LOCAL→PROD сам разносит флаги; kill-switch на PROD блокирует создание новостей во время sync-окна. Отдельный backfill на PROD не нужен.
- Q: Как backfill должен запускаться и репортить прогресс администратору? → A: A — HTTP-endpoint в `ApiController` (по образцу `doBackfillNewsAvailable`), кнопка в webvue3, прогресс SSE-тостами по чанкам. Dry-run через параметр.
- Q: Критерий guard'а «уже была в коллекции» в `markNewsAvailableIfReady` на LOCAL? → Пользователь предложил временную последовательность: 1) backfill на LOCAL, 2) kill-switch на PROD блокирует любые auto-новости, 3) sync LOCAL→PROD (kill-switch не даёт создать новости), 4) снятие kill-switch. Постоянный guard не нужен — `markNewsAvailableIfReady` идемпотентен (после backfill `newsAvailableAnnounced=true` не сбрасывается обычным save(), а `premiumAutoPublishState=COMPLETE` блокирует повторную установку `newsPremiumPublishPending=true`).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Разовый backfill флагов готовых песен (Priority: P1)

Администратор запускает одноразовую операцию **на локальной админ-машине**, которая проходит по всем готовым песням (статус 6 — «готово») и приводит их флаги публикаций (новость «появилась в коллекции», премиум-публикация в Telegram и VK) в состояние «уже опубликовано». Изменения флагов расходятся на сервер штатным sync-движком LOCAL→PROD; kill-switch на PROD (включаемый администратором перед sync) блокирует создание ложных новостей «появилась в коллекции» в точке применения sync-изменений (`doChangeRecords` → `detectAndAnnouncementService.detectAndAnnounceAvailability`). После backfill, sync и снятия kill-switch ни на сайте, ни в Telegram, ни в VK не появляется ни одной новой новости/поста про старые песни — backfill тихо исправляет внутренние флаги, не порождая публичных артефактов.

**Why this priority**: Это корень проблемы пользователя — сегодня любой save() готовой старой песни на админ-машине создаёт ложную новость «появилась в коллекции» на сервере после sync. Без backfill флагов проблема воспроизводится на каждой правке карточки песни.

**Independent Test**: Запустить backfill на LOCAL, дождаться штатной синхронизации LOCAL→PROD. После завершения — проверить, что в `tbl_news` на PROD не появилось новых записей с датой проведения backfill, в Telegram-группе и VK-группе не появилось новых постов. Проверить на тестовой песне: открыть карточку готовой старой песни, изменить любое поле, нажать Save, дождаться sync — новость не создаётся.

**Acceptance Scenarios**:

1. **Given** в БД ~15000 готовых песен с неконсистентными флагами (часть с `newsAvailableAnnounced=false`, часть с `newsPremiumPublishPending=true`), **When** администратор запускает backfill на LOCAL и дожидается sync LOCAL→PROD, **Then** у 100% готовых песен `newsAvailableAnnounced=true`, `newsPremiumPublishPending=false`, `premiumAutoPublishState=COMPLETE`, оба канала помечены как закрытые — на LOCAL и на PROD.
2. **Given** backfill на LOCAL завершён и sync разнёс флаги на PROD, **When** в `tbl_news` на PROD считается число строк с `created_at` в диапазоне проведения backfill+sync, **Then** это число равно 0 (никаких новостей не создано).
3. **Given** backfill завершён, **When** проверяются логи Telegram/VK автопубликации за период backfill+sync, **Then** ни одного нового post/message id не зафиксировано.
4. **Given** backfill завершён, **When** повторно запускают backfill на LOCAL, **Then** состояние флагов не меняется (идемпотентность), sync не находит расхождений, 0 новостей.

---

### User Story 2 - Защита от лавины новостей при sync (Priority: P2)

После backfill на LOCAL флаги готовых песен расходятся на PROD штатным sync-движком. В точке применения sync-изменений (`doChangeRecords` → `detectAndAnnouncementService.detectAndAnnounceAvailability`) для каждой песни вычисляется `wasAvailableBefore = readNewsAvailableFlag(...)` ДО UPDATE — на PROD флаг ещё старый (false), поэтому без защиты sync создал бы ~15000 ложных новостей «появилась в коллекции». Чтобы этого избежать, администратор перед sync включает **kill-switch** (временный флаг в `KaraokeProperties`), блокирующий создание любых auto-новостей на PROD. После завершения sync и подтверждения, что в `tbl_news` нет новых записей — kill-switch снимается.

Постоянный guard от рецидивов при последующих точечных save() готовых песен НЕ требуется: `markNewsAvailableIfReady` в `saveToDb` идемпотентен — `newsAvailableAnnounced` только переходит false→true и никогда не сбрасывается обычным save(); `premiumAutoPublishState=COMPLETE` блокирует повторную установку `newsPremiumPublishPending=true` через условие `isBlank() || "RUNNING"`. После backfill оба флага у готовых песен в «закрытом» состоянии, обычный save() не нарушает его → sync не находит расхождений по этим флагам → ложная новость не создаётся.

**Why this priority**: Без kill-switch при sync разнесение backfill-изменений на PROD породит лавину ~15000 новостей «появилась в коллекции». Это второй по критичности шаг после самого backfill (P1) — без него backfill на LOCAL бесполезен. Постоянный guard избыточен — природа существующего кода (`markNewsAvailableIfReady` идемпотентен, `premiumAutoPublishState=COMPLETE` блокирует повтор) уже является защитой от рецидивов после backfill.

**Independent Test**: Запустить backfill на LOCAL → включить kill-switch на PROD → запустить sync LOCAL→PROD → проверить, что в `tbl_news` на PROD 0 новых записей → снять kill-switch → открыть карточку готовой старой песни, изменить поле, сохранить, дождаться sync → снова 0 новых новостей.

**Acceptance Scenarios**:

1. **Given** backfill на LOCAL завершён, **When** администратор включает kill-switch на PROD и запускает sync LOCAL→PROD, **Then** sync применяет UPDATE флагов для ~15000 готовых песен на PROD, но в `tbl_news` на PROD появляется 0 новых записей (kill-switch блокирует `detectAndAnnouncementService.detectAndAnnounceAvailability`).
2. **Given** sync завершён, kill-switch снят, **When** администратор меняет поле «Описание» готовой песни и сохраняет, sync уходит на PROD, **Then** на PROD `newsAvailableAnnounced` остаётся true (не было расхождения по этому флагу) и новость «появилась в коллекции» не создаётся.
3. **Given** sync завершён, kill-switch снят, **When** администратор меняет любое поле готовой песни и сохраняет, **Then** `newsPremiumPublishPending` не становится `true` на LOCAL (`markNewsAvailableIfReady` блокируется `premiumAutoPublishState=COMPLETE`) и не расходится на PROD.
4. **Given** действительно новая песня, впервые переходящая в статус 6 (готова) после backfill, **When** срабатывает `markNewsAvailableIfReady` (флаги ещё false → true) и sync уходит на PROD, **Then** новость «появилась в коллекции» создаётся корректно, премиум-публикация запускается — kill-switch уже снят, нормальный flow новых песен сохранён.
5. **Given** kill-switch включён, **When** `SongReleaseAnnouncementService.checkOnAirWindow` (scheduler air-новостей) срабатывает в это окно, **Then** air-новости тоже блокируются (kill-switch должен покрывать обе точки создания auto-новостей — `detectAndAnnouncementService.detectAndAnnounceAvailability` и `News.createAutoAnnouncement`, либо сам scheduler должен проверять флаг).

---

### User Story 3 - Диагностика и отчёт о расхождениях (Priority: P3)

До и после backfill администратор получает отчёт: сколько готовых песен имели некорректные флаги (по каждому типу флага отдельно), сколько были в «подозрительных» состояниях (RUNNING, FAILED) и как эти числа изменились после backfill. Отчёт позволяет убедиться, что backfill сделал то, что должен, и не задел песни, которые не должен был трогать.

**Why this priority**: Без отчёта администратор не может отличить «backfill починил 12000 песен» от «backfill починил 3 песни, а остальные уже были OK» — и не может обнаружить, если backfill пропустил партию. Это валидация результата, не основной механизм.

**Independent Test**: Запустить «dry run» backfill (без записи), получить отчёт с числами «будет исправлено». Запустить реальный backfill, получить отчёт «исправлено». Числа должны совпадать. Повторить dry run — отчёт должен показать 0 расхождений.

**Acceptance Scenarios**:

1. **Given** БД с расхождениями флагов, **When** администратор запускает dry-run backfill, **Then** возвращается отчёт: сколько песен имеют `newsAvailableAnnounced=false` при `id_status=6`, сколько `newsPremiumPublishPending=true`, сколько `premiumAutoPublishState in (RUNNING, FAILED, "")` — с разбивкой по типам.
2. **Given** dry-run показал N расхождений, **When** реальный backfill выполнен, **Then** отчёт показывает «исправлено N», а повторный dry-run показывает 0 расхождений.
3. **Given** в БД есть песни с `id_status < 6`, **When** dry-run выполняется, **Then** эти песни НЕ входят в отчёт расхождений (backfill их не касается).

---

### Edge Cases

- Что если во время backfill есть песня в активной премиум-публикации (`premiumAutoPublishState=RUNNING` с `newsPremiumPublishPending=true` и `telegramAutoPublishState` или `vkAutoPublishState` в rendering/publishing)? Backfill НЕ должен сбрасывать её флаги — дождаться завершения и только потом приводить в complete.
- Что если у готовой песни `premiumAutoPublishState=FAILED` (была неудачная попытка автопубликации, админ не разобрался)? При backfill такая песня переводится в COMPLETE с пустыми `idTelegramDemo`/`idVk` — считаем, что «публикация была, но не записалась». Не оставлять в FAILED (иначе scheduler будет бесконечно пытаться повторить после сброса newsPremiumPublishPending).
- Что если у готовой песни есть `idTelegramDemo` (публикация в TG была), но `idVk` пусто (в VK не публиковалась)? Не трогать частичные состояния — считать канал закрытым по факту наличия id, второй канал — закрывать как «не нуждается» (COMPLETE). Не запускать публикацию в недостающий канал задним числом.
- Что если `player_readiness_flags` у песни пустой/невалидный JSON? Трактовать как дефолт `{}` и записать корректный complete-набор.
- Что если песня имеет `id_status >= 6`, но `source_markers` пусто (некорректная готовая песня)? Не приводить флаги — такие песни должны разбираться отдельно, backfill не маскирует их проблему.
- Что если sync между LOCAL и PROD идёт во время backfill на LOCAL? Backfill меняет флаги на LOCAL через `saveToDb` (порождая SSE-события); recordhash пересчитывается триггером. Если sync стартует до завершения backfill — он увидит «полу-исправленные» записи и разнесёт их на PROD частично. Рекомендуется запускать backfill в окне без активного sync (sync ручной, по кнопке), либо backfill должен быть быстрым (≤15 мин на 15000 песен — см. SC-007) и запускаться сразу после завершившегося sync. Guard на PROD блокирует новости в любом случае — даже если sync разносит частично.
- Что если у готовой песни `newsAvailableAnnounced=true`, но `newsPremiumPublishPending=true` (частично неконсистентно)? Backfill приводит только незакрытые части, не трогая уже-правильные.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Система MUST предоставлять операцию backfill, которая для всех песен с `id_status = 6` (готово) и непустыми маркерами выставляет флаг «новость уже была анонсирована» (`newsAvailableAnnounced = true`) в `player_readiness_flags` на LOCAL и на PROD.
- **FR-002**: Система MUST в том же backfill выставлять премиум-флаги в «complete»-состояние: `newsPremiumPublishPending = false`, оба канала помечены как отправленные, `premiumAutoPublishState = COMPLETE`, `premiumAutoPublishLastError` очищена, `premiumAttemptCount = 0`.
- **FR-003**: Backfill MUST быть идемпотентным — повторный запуск не изменяет состояние уже-корректных песен и не создаёт новости/публикации.
- **FR-004**: Backfill MUST НЕ создавать записи в таблице новостей (`tbl_news`) ни на LOCAL, ни на PROD. Механизм детекции перехода флага (создание новости «появилась в коллекции») MUST быть отключён/обойдён на время backfill.
- **FR-005**: Backfill MUST НЕ запускать автопубликацию в Telegram и VK — премиум-scheduler и air-scheduler должны игнорировать песни, чьи флаги меняются backfill-ом, либо backfill должен идти в окне, когда эти scheduler'ы временно отключены.
- **FR-006**: Backfill MUST НЕ затрагивать песни с `id_status < 6` (не готовые) — их флаги остаются как есть.
- **FR-007**: Backfill MUST НЕ затрагивать песни, находящиеся в активной публикации на момент запуска (одна из: `telegramAutoPublishState in (rendering, publishing)` или `vkAutoPublishState in (rendering, publishing)`). Эти песни помечаются в отчёте как «пропущены — активная публикация».
- **FR-008**: Backfill MUST пропускать песни с пустыми `source_markers` (некорректно-готовые) и отмечать их в отчёте как «пропущены — нет маркеров».
- **FR-009**: Backfill MUST запускаться один раз на LOCAL; расхождение флагов на PROD происходит штатным sync-движком LOCAL→PROD. Отдельный backfill на PROD НЕ требуется — это создало бы гонку с sync и дублирующее применение. Штатный sync обнаружит изменённый `player_readiness_flags` (через recordhash) и применит UPDATE на PROD через `doChangeRecords`.
- **FR-010**: Перед запуском sync LOCAL→PROD (разносящего backfill-изменения) администратор MUST включить kill-switch — временный флаг в `KaraokeProperties` (например `newsAutoPublishKillSwitch`), читаемый в `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability` на PROD. При `killSwitch=true` детекция перехода флага возвращает false без создания новости. После завершения sync и подтверждения 0 новых записей в `tbl_news` — kill-switch снимается.
- **FR-011**: Kill-switch MUST блокировать обе точки создания auto-новостей на PROD во время sync-окна: (a) `detectAndAnnouncementService.detectAndAnnounceAvailability` (sync-точка, «премиум»-новости) и (b) `SongReleaseAnnouncementService.checkOnAirWindow` (scheduler, «air»-новости). Либо kill-switch проверяется в каждой точке, либо выносится в `News.createAutoAnnouncement` (единая точка создания auto-новостей, блокирует оба пути).
- **FR-012**: Постоянный guard от рецидивов при save() готовых песен НЕ требуется — `markNewsAvailableIfReady` идемпотентен (`newsAvailableAnnounced` только false→true, никогда не сбрасывается обычным save()), а `premiumAutoPublishState=COMPLETE` блокирует повторную установку `newsPremiumPublishPending=true` (условие `isBlank() || "RUNNING"` ложно). После backfill у готовых песен оба флага в «закрытом» состоянии, обычный save() не порождает расхождения по этим флагам → sync не создаёт новость. FR-012 сводится к проверке идемпотентности `markNewsAvailableIfReady` (тестовое сохранение готовой песни с complete-флагами не меняет их).
- **FR-013**: Система MUST предоставлять dry-run режим backfill, вызываемый через тот же endpoint с параметром `dryRun=true`, возвращающий отчёт о расхождениях БЕЗ записи в БД.
- **FR-014**: Система MUST предоставлять отчёт после backfill с разбивкой: сколько песен исправлено по каждому флагу, сколько пропущено (активная публикация / нет маркеров / уже OK), сколько потребовалось времени. Отчёт возвращается как финальный SSE-тост по завершении.
- **FR-015**: Прогресс backfill (для тяжёлой операции на ~15000 песен) MUST отображаться администратору через SSE-тосты по чанкам (каждые ~500 обработанных песен), в дополнение к финальному отчёту. Запуск — кнопкой в webvue3, вызов HTTP-endpoint'а в `ApiController` (по образцу существующего `POST /utils/backfillnewsavailable`, `ApiController.doBackfillNewsAvailable`), операция выполняется в фоновом потоке.
- **FR-016**: `markNewsAvailableIfReady` MUST НЕ переустанавливать `newsPremiumPublishPending=true` для песни, у которой оно уже было `false` в результате COMPLETE/FAILED премии-публикации — идемпотентность «после завершения не перезапускаем».
- **FR-017**: Backfill MUST корректно работать при пустом или невалидном JSON в `player_readiness_flags` — пересоздавать валидный JSON с complete-набором флагов, не падать.
- **FR-018**: Изменения флагов, вносимые backfill, MUST быть видны в штатной SSE-ленте изменений записей (как обычное обновление `tbl_songs`), чтобы администратор в webvue3 видел, что песня обновилась — но БЕЗ побочных эффектов создания новостей/публикаций.

### Key Entities *(include if feature involves data)*

- **Song** (`tbl_songs`): основная сущность. Ключевые атрибуты для backfill — `id_status` (готовность, >= 6 = готово), `source_markers` (наличие маркеров), `id_telegram_demo`/`id_vk` (факт публикации на площадках), `publish_date`/`publish_time` (момент эфира), `player_readiness_flags` (JSON-блоб с news-флагами: `newsAvailableAnnounced`, `newsPremiumPublishPending`, `newsPremiumTelegramSent`, `newsPremiumVkSent`, `premiumAutoPublishState`, `premiumAttemptCount`, `premiumAutoPublishLastError`, `telegramAutoPublishState`, `vkAutoPublishState`).
- **News** (`tbl_news`): публичные новости сайта. Backfill НЕ должен добавлять сюда строки. Поля: `song_id`, `category` (`air`/`premium`), `source` (`auto`/`manual`), `publish_at`, `created_at`.
- **Sync LOCAL↔PROD**: штатный движок синхронизации через `doChangeRecords` на сервере. Точка создания новости — `SongReleaseAnnouncementService.detectAndAnnouncementService.detectAndAnnounceAvailability`, вызываемая из `doChangeRecords` для каждой затронутой песни. Kill-switch (FR-010) живёт здесь.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: После backfill на LOCAL и последующего sync на PROD у ≥ 99.9% готовых песен (`id_status = 6`, непустые `source_markers`) флаг `newsAvailableAnnounced = true` — на обеих БД.
- **SC-002**: После backfill+sync у ≥ 99.9% готовых песен на LOCAL и PROD флаги премиум-публикации в complete-состоянии (`newsPremiumPublishPending = false`, `premiumAutoPublishState = COMPLETE`).
- **SC-003**: Число новых записей в `tbl_news` на PROD с `created_at` в диапазоне проведения backfill+sync = 0 (на LOCAL backfill не создаёт новостей по построению — нет `doChangeRecords`-точки).
- **SC-004**: Число новых публикаций (post/message id) в Telegram-группе и VK-группе за период backfill+sync = 0 (проверяется логами scheduler'ов на PROD и отсутствию свежих `id_telegram_demo`/`id_vk` у песен, у которых их не было).
- **SC-005**: После backfill+sync (с kill-switch) и последующего save() готовой старой песни + sync — на PROD в `tbl_news` не появляется новая запись «Новая песня: …» (проверка на тестовой песне).
- **SC-006**: После backfill+sync и последующего save() готовой старой песни + sync — на PROD `newsPremiumPublishPending` не становится `true` (премиум-scheduler не активируется повторно).
- **SC-007**: Полное время backfill на ~15000 готовых песен на LOCAL — не более 15 минут. Sync разносит изменения на PROD в штатном темпе (не больше обычного цикла sync). Окно kill-switch на PROD = время sync + небольшой буфер.
- **SC-008**: Повторный backfill на LOCAL, запущенный сразу после первого+sync, исправляет 0 песен (идемпотентность), sync не находит расхождений, 0 новостей.
- **SC-009**: Новая песня, впервые переходящая в статус 6 после backfill+sync (kill-switch уже снят), корректно получает новость «появилась в коллекции» и премиум-публикацию (нормальный flow новых песен сохранён).
- **SC-010**: Dry-run backfill на LOCAL возвращает числа расхождений, совпадающие с числами «исправлено» в реальном backfill.
- **SC-011**: Во время действия kill-switch (sync-окно) на PROD в `tbl_news` не появляется ни одной новой записи `source='auto'` (как premium из `detectAndAnnouncementService.detectAndAnnounceAvailability`, так и air из `checkOnAirWindow`/`News.createAutoAnnouncement`).

## Assumptions

- Все готовые песни (`id_status = 6`) в БД на момент backfill УЖЕ были опубликованы как новости и в Telegram, и в VK — даже если `id_telegram_demo`/`id_vk` пусты (публикации были до ведения этих полей). Учитывая возраст проекта и ~15000 готовых песен, это безопасное допущение.
- Песни в активной публикации (`telegramAutoPublishState`/`vkAutoPublishState` в rendering/publishing) на момент backfill — единичные (0-5 шт.), администратор видит их в отчёте и дождётся завершения перед повторным backfill, если они остались.
- Backfill выполняется один раз вручную администратором на LOCAL. Распространение на PROD — через штатный sync LOCAL→PROD, не отдельным backfill-процессом.
- Guard от рецидивов НЕ строится — он избыточен. Вместо него: (a) временный kill-switch в `KaraokeProperties` блокирует лавину auto-новостей на PROD во время sync-окна, (b) идемпотентность `markNewsAvailableIfReady` (существующий код) предотвращает рецидивы при последующих точечных save() готовых песен. Без добавления новых сущностей/таблиц/постоянных guard'ов.
- Штатный sync LOCAL→PROD — единственный канал распространения backfill-изменений флагов. Backfill НЕ должен обходить sync (через прямой SQL на PROD и т.п.) — это создало бы гонку и расхождение recordhash. Sync применяет UPDATE на PROD через `doChangeRecords`, где `wasAvailableBefore` для backfill-синхронизируемых песен = false (флаг на PROD ещё старый), и без kill-switch sync создал бы новость для каждой. Kill-switch блокирует только создание новости, не применение UPDATE.
- Kill-switch живёт в `KaraokeProperties` (base64-настройки, читаемые через `/api/properties/setproperty` без рестарта контейнера) — включается и снимается администратором в окне sync. Значение по умолчанию `false` (kill-switch выкл) — нормальный flow новостей сохранён для truly новых песен.
- На время backfill на LOCAL премиум-scheduler (`PremiumAutoPublishScheduler`) и air-scheduler на PROD продолжают работать — но backfill выставляет `newsPremiumPublishPending=false` на LOCAL, sync разносит это на PROD, поэтому scheduler'ы на PROD не находят кандидатов и не активируются. Дополнительно отключать scheduler'ы не требуется. Kill-switch на PROD блокирует auto-новости на время sync-окна, но scheduler-тик в это окно тоже попадает под блокировку (FR-011).
- `player_readiness_flags` хранится как JSON в единой колонке `tbl_songs` — обновление нескольких news-флагов идёт одной записью (не требует миграции схемы, не требует пересоздания recordhash-триггера). Sync увидит изменение recordhash и разнесёт на PROD.
- У готовой песни с `premiumAutoPublishState=FAILED` и непустым `id_telegram_demo` ИЛИ `id_vk` — хотя бы один канал был опубликован; backfill закрывает оба канала как отправленные, не пытается повторить недостающий.
- Для truly новых песен (впервые становящихся готовыми после backfill+sync, kill-switch уже снят) существующий flow новостей и премиум-публикации сохраняется без изменений — `markNewsAvailableIfReady` корректно переводит false→true, `newsPremiumPublishPending` устанавливается, sync разносит на PROD, новость создаётся.
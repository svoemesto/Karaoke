# Tasks: Автопубликация новостей в группу ВКонтакте

**Input**: Design documents from `/specs/121-vk-news-auto-publish/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required for user stories), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/vk-api-contract.md), [quickstart.md](./quickstart.md)

**Tests**: Тесты в CI не предусмотрены (см. `constitution.md` «Тесты»: интеграционные, `@Disabled`, требуют сеть/credentials). Проверка — вручную через сценарии [quickstart.md](./quickstart.md). Тест-задачи НЕ генерируются.

**Organization**: Задачи сгруппированы по user story (из spec.md), каждая story — независимо реализуема и тестируема.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Можно параллелить (разные файлы, нет зависимости от незавершённых задач)
- **[Story]**: Принадлежность к user story (US1, US2, US3, US4)
- Все пути — абсолютные от корня репозитория

## Path Conventions

- Backend: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/`
- Frontend: `webvue3/src/`
- Properties: `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt`
- Per-feature документ: `docs/features/vk-news-auto-publish.md`

## Технический контекст (из plan.md)

- **Язык**: Kotlin (JDK 17, Spring Boot 2.x/3.x)
- **Зависимости**: JDK `java.net.http.HttpClient`, `kotlinx.serialization`, Spring `@Scheduled`/`@Component`/`@EventListener`
- **Storage**: PostgreSQL (LOCAL на admin-машине), MinIO (демо-MP4), `KaraokeProperties` (base64-файл, секреты НЕ в git)
- **Образец**: Telegram-Фаза 2 (`specs/113-telegram-demo-publish`) — `TelegramAutoPublishService`, `TelegramApiClient`, `TelegramAutoPublishScheduler`, `TelegramAutoPublishSchedulerStarter`, `TelegramAutoPublishState`, `TelegramAutoPublishResult`
- **Без новых таблиц/колонок БД**: переиспользуется `tbl_songs.id_vk` (существующее поле, `SongField.ID_VK`); для редкого случая — ключ в `News.playerReadinessFlags` JSON-блобе
- **Бот работает на admin-машине** (`karaoke-app`), на проде не разворачивается (Constitution: «karaoke-app на проде не разворачивается вовсе»)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Регистрация новых `KaraokeProperties`-ключей и подготовка per-feature документа. Базовая инфраструктура, используемая всеми user stories.

- [X] T001 Добавить 11 новых `KaraokeProperty`-записей в `listKaraokeProperties` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (после блока `telegramAutoPublishMaxFileSizeMb` ~стр.1795, по образцу telegram-ключей): `vkAutoPublishEnabled` (Boolean, false), `vkGroupId` (String, ""), `vkAccessToken` (String, ""), `vkApiVersion` (String, "5.199"), `vkProxyUrl` (String, ""), `vkProxyModeTtlMs` (Long, 60000), `vkAutoPublishWindowMinutes` (Long, 5), `vkAutoPublishMaxVideoSizeMb` (Long, 50), `vkAutoPublishRateLimitPerHour` (Long, 3), `vkTemplateAir` (String, "", дефолт в коде — см. research.md §6), `vkTemplatePremium` (String, "", дефолт в коде — см. research.md §6). Описания на русском, по образцу `telegramAutoPublish*`. Шаблоны — многострочные String (FR-024, FR-023). Комментарий-блок перед группой ключей со ссылкой на `specs/121-vk-news-auto-publish` и `@see docs/features/vk-news-auto-publish.md`.
- [X] T002 [P] Создать per-feature документ `docs/features/vk-news-auto-publish.md` по структуре `docs/features/telegram-auto-publish.md` (секции: «Что делает», «Зачем», «Настройка бота с нуля», «Как работает», «Инварианты / правила», «Известные ловушки», «Ссылки»). Описать Community token, `wall.post`+`video.save` flow, идемпотентность по `Song.idVk`, триггер по `tbl_news` категории `air`. Добавить запись в таблицу `docs/features/README.md`.
- [X] T003 [P] Проверить, что `@EnableScheduling` уже есть в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt` (используется Telegram-Фазой 2). Если нет — добавить. Sanity-check, не новая зависимость.

**Checkpoint**: Properties-ключи зарегистрированы (видны в `webvue3` → Properties UI после перезапуска), per-feature документ создан.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Новые классы-модели и VK API-клиент, которые используют ВСЕ user stories. ДО завершения этой фазы user story implementation не начнётся.

**⚠️ CRITICAL**: US1-US4 зависят от `VkApiClient` и `VkAutoPublishState`/`Result`.

- [X] T004 [P] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishState.kt` — enum (по образцу `TelegramAutoPublishState.kt`): значения `SCHEDULED`, `RENDERING`, `PUBLISHING`, `PUBLISHED`, `SEND_FAILED`, `CANCELLED` с кодами. KDoc со ссылкой на `@see docs/features/vk-news-auto-publish.md` и описание хранения (для типичного случая — выводится из `Song.idVk`; для редкого — ключ `vkAutoPublishState` в `News.playerReadinessFlags` JSON-блобе, FR-004a). `companion object { fun fromCode(code: String?): VkAutoPublishState? }`.
- [X] T005 [P] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishResult.kt` — data class (по образцу `TelegramAutoPublishResult.kt`): `data class VkAutoPublishResult(val state: VkAutoPublishState, val postId: String? = null, val error: String? = null)`. KDoc со ссылкой на `@see docs/features/vk-news-auto-publish.md`.
- [X] T006a [P] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/PublicationType.kt` — enum (FR-027 — расширяемо): значения `AIR("air")`, `PREMIUM("premium")`. KDoc со ссылкой на `@see docs/features/vk-news-auto-publish.md` и пояснением: тип определяет шаблон (`vkTemplateAir` / `vkTemplatePremium`, FR-023) и источник текста поста; не хранится в БД — передаётся как параметр в `VkAutoPublishService.publishToVk(song, type)`; идемпотентность общая по `Song.idVk`. `companion object { fun fromCode(code: String?): PublicationType? = entries.firstOrNull { it.code == code } }`.
- [X] T006b [P] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkTemplateService.kt` — рендеринг шаблонов с плейсхолдерами (FR-023):
  - `object VkTemplateService` с `fun render(template: String, song: Song, news: News? = null): String` — заменяет плейсхолдеры `{author}` → `song.author`, `{songName}` → `song.songName`, `{link}` → `"https://sm-karaoke.ru/song?id=${song.id}"`, `{id}` → `song.id.toString()`, `{body}` → `news?.body ?: ""`. Регулярка `\{(\w+)\}`: если ключ известен — заменить, если нет — оставить literal (FR-023). Усечение итога до 10 000 символов (FR-005) с разумной границей + `…` при усечении.
  - `fun templateFor(type: PublicationType): String` — возвращает `KaraokeProperties.getString("vkTemplate${type.name.capitalize()}")` (т.е. `vkTemplateAir` / `vkTemplatePremium`); если пусто — дефолт по типу (research.md §6).
  - `fun placeholders(): List<Pair<String, String>>` — список доступных плейсхолдеров с описаниями (для `/api/vk/templates` endpoint и редактора).
  - KDoc со ссылкой `@see docs/features/vk-news-auto-publish.md` и FR-023/FR-024/FR-025.
- [X] T006 Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkApiClient.kt` (по образцу `TelegramApiClient.kt`, ~360 строк):
  - `baseUrl()` → `"https://api.vk.ru/method"` (без токена в URL, токен в параметрах).
  - `proxyClient()` / `send(request)` — прямой→прокси-fallback, по образцу `TelegramApiClient.kt:126-172` (ключи `vkProxyUrl`, `vkProxyModeTtlMs`).
  - `wallPost(ownerId: String, message: String, attachments: String?, maxAttempts: Int = 3, backoffScheduleMs: List<Long> = listOf(30000, 120000, 300000))`: VkAutoPublishResult — POST `wall.post` с `owner_id`, `from_group=1`, `message`, `attachments`, `access_token`, `v`. Парсинг ответа `response.post_id` → формат `"-<groupId>_<postId>"`. Non-retryable коды VK API: `4`, `5`, `15`, `100` (см. research.md §5). Retryable — остальные (сеть, 6/too-many-requests, 9, 1, 2700).
  - `videoSave(groupId: String, name: String, description: String)`: JSON-ответ `{owner_id, video_id, upload_url}`.
  - `uploadVideoFile(uploadUrl: String, videoFile: File)`: multipart/form-data POST с полем `video_file`.
  - `sendPostWithVideo(groupId, message, videoFile, songId)`: комбинированный flow — `videoSave` → `uploadVideoFile` → `wallPost(attachments="video<owner_id>_<video_id>")`. Возвращает `VkAutoPublishResult`.
  - Проверка размера файла перед отправкой (FR-020, по образцу `TelegramApiClient.sendVideo:241`): если `videoFile.length() > vkAutoPublishMaxVideoSizeMb * 1024 * 1024` → вернуть `SEND_FAILED` без сетевой попытки.
  - Проверка `vkGroupId`/`vkAccessToken` непустота (по образцу `TelegramApiClient.sendVideo:247` channelId check).
  - Все публичные методы с KDoc и `@see docs/features/vk-news-auto-publish.md`. Версия API — `KaraokeProperties.getString("vkApiVersion")` (дефолт `5.199`).
- [X] T007 Добавить расширения в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (по образцу Telegram-расширений, рядом с `telegramAutoPublishState` getter'ами):
  - `var vkAutoPublishState: String` — getter/setter через `player_readiness_flags` JSON (по образцу `telegramAutoPublishState`, `Song.kt`).
  - `var vkAutoPublishLastError: String` — аналогично `telegramAutoPublishLastError`.
  - `var vkAutoPublishLastAttemptAt: String` — аналогично `telegramAutoPublishLastAttemptAt`.
  - `val effectiveVkAutoPublishState: VkAutoPublishState` — computed: `PUBLISHED` если `idVk.isNotBlank()`, иначе `VkAutoPublishState.fromCode(vkAutoPublishState) ?: SCHEDULED` (по образцу `effectiveTelegramAutoPublishState`).
  - Импорт `VkAutoPublishState`. KDoc со ссылкой `@see docs/features/vk-news-auto-publish.md`.
- [X] T008 [P] Добавить getter/extension в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/News.kt` для редкого случая (FR-004a): `var vkAutoPublishState: String` через `playerReadinessFlags`-подобный JSON-блоб News (если у News уже есть такой блоб — переиспользовать; иначе — аналогичный паттерн `player_readiness_flags` Song). KDoc со ссылкой `@see docs/features/vk-news-auto-publish.md` и пояснением, что используется только для ручных `air`-новостей без `song_id`.

**Checkpoint**: Foundation готов — `VkApiClient`, `VkAutoPublishState`, `VkAutoPublishResult`, расширения `Song`/`News` готовы. US1-US4 могут начинаться.

---

## Phase 3: User Story 1 - Опубликованная на сайте новость автоматически появляется в группе ВКонтакте (Priority: P1) 🎯 MVP

**Goal**: Бот сам публикует пост с текстом новости `air` и демо-MP4 в группе ВК по наступлению `publish_at <= now()` для песни с пустым `idVk`.

**Independent Test**: Создать/дождаться опубликованную новость `air` для готовой песни с пустым `idVk`, дождаться тика планировщика — в группе ВК появляется пост с текстом и видео, `Song.idVk` заполняется. (см. quickstart.md Сценарий 1)

### Implementation for User Story 1

- [X] T009 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` (по образцу `TelegramAutoPublishService.kt`, ~280 строк):
  - `object VkAutoPublishService` с `private val client = VkApiClient()`.
  - `fun publishToVk(song: Song, type: PublicationType = PublicationType.AIR): VkAutoPublishResult` — основной цикл (по образцу `publishToTelegram`, FR-027 — `type` параметр, дефолт `AIR`):
    1. FR-008: если `song.idVk.isNotEmpty()` → вернуть `PUBLISHED` с `postId=idVk` (идемпотентность общая для всех типов).
    2. FR-022: если `!song.isContentReady` → вернуть `SCHEDULED` с ошибкой `"not content-ready"`.
    3. FR-020: проверить `demoFile = File(song.pathToFileRenderMp4ForVersion(RenderVersion.DEMO))` и размер; если нет или превышает `vkAutoPublishMaxVideoSizeMb` → `startRenderAndReturn(song, type)` (FR-003 сц. 2/3).
    4. Иначе → `publishFile(song, demoFile, type)`.
  - `fun onRenderCompleted(songId: Long, type: PublicationType, success: Boolean, error: String?): VkAutoPublishResult?` — по образцу `TelegramAutoPublishService.onRenderCompleted` (вызывается scheduler'ом): идемпотентность по `idVk`, при `success` — `publishFile(song, demoFile, type)`, при `!success` — `writeFailure`.
  - `private fun startRenderAndReturn(song: Song, type: PublicationType): VkAutoPublishResult` — `KaraokeProcess.createProcess(action=RENDER_MP4_DEMO, prior=1, threadId=HEAVY_RENDER, context={width=1280, height=720, fps=30, version=DEMO, type=type.code})`, запись `vkAutoPublishState=RENDERING`, `saveToDb()`.
  - `private fun publishFile(song: Song, demoFile: File, type: PublicationType): VkAutoPublishResult`:
    1. FR-023: `message = buildMessage(song, type)` через `VkTemplateService.render(VkTemplateService.templateFor(type), song, newsFor(song, type))`.
    2. Запись `vkAutoPublishState=PUBLISHING`, `vkAutoPublishLastAttemptAt=nowIso8601()`, `saveToDb()`.
    3. FR-019: `result = client.sendPostWithVideo(groupId, message, demoFile, songId)`.
    4. При `PUBLISHED` → FR-004: `song.fields[SongField.ID_VK] = result.postId`, `vkAutoPublishState=PUBLISHED`, `saveToDb()`.
    5. При `SEND_FAILED` → `writeFailure(song, result.error)`.
  - `private fun buildMessage(song: Song, type: PublicationType): String` — делегирует в `VkTemplateService.render(VkTemplateService.templateFor(type), song, newsFor(song, type))`. Для `air` — связанная новость `air` (`News` с `song_id=song.id` или `link=/song?id=${song.id}`); для `premium` — `news=null` (`{body}` → пусто).
  - `private fun newsFor(song: Song, type: PublicationType): News?` — для `air` ищет в `tbl_news` опубликованную новость `air` для песни; для `premium` — `null`.
  - `private fun writeFailure(song, error)` — по образцу `TelegramAutoPublishService.writeFailure`: `vkAutoPublishState=SEND_FAILED`, `vkAutoPublishLastError=error`, `vkAutoPublishLastAttemptAt=nowIso8601()`, `saveToDb()`.
  - `private fun nowMoscow()` / `nowIso8601()` — по образцу Telegram.
  - KDoc класса со ссылкой `@see docs/features/vk-news-auto-publish.md` и на `specs/121-vk-news-auto-publish/spec.md` (FR-001..FR-027).
- [X] T010 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt` (по образцу `TelegramAutoPublishScheduler.kt`, ~230 строк):
  - `@Component class VkAutoPublishScheduler` (Spring автоматически стартует при `@EnableScheduling`).
  - `@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L) fun tick()` — FR-002a:
    1. FR-013: если `!KaraokeProperties.getBoolean("vkAutoPublishEnabled")` → return.
    2. Если `vkGroupId.isBlank()` → лог + return.
    3. `resumeRenderingSongs()` (Phase 1 тика — см. T011).
    4. `publishScheduledNews()` (Phase 2 тика — см. T012).
    5. catch (e: Exception) → лог.
  - `private fun resumeRenderingSongs()` — по образцу `TelegramAutoPublishScheduler.resumeRenderingSongs`: cheap SELECT id песен с `vkAutoPublishState=rendering` в `player_readiness_flags` и пустым `id_vk`; для каждой — `findRenderDemoProcess`, при DONE/ERROR → `VkAutoPublishService.onRenderCompleted(songId, type=PublicationType.AIR, success, error)` (по умолчанию AIR; в future можно сохранить type в контексте рендера и читать). Для редкого случая (через News) — аналогично.
  - `private fun publishScheduledNews()` — Phase 2 тика:
    1. `val candidates = loadNewsCandidates()` (T012).
    2. Для каждой новости-кандидата: определить `songId` (из `News.song_id` или парсингом `News.link` `/song?id=<id>`).
    3. FR-021: если `songId` есть → `Song.loadFromDbById`, FR-008 если `idVk` не пуст → skip, иначе `VkAutoPublishService.publishToVk(song, PublicationType.AIR)` (air-автотриггер, FR-001).
    4. FR-021 редкий: если `songId` нет (ручная `air`-новость без `song_id`) → `publishNewsWithoutVideo(news)` (только текст через `client.wallPost` без `attachments`), запись `vkAutoPublishState=PUBLISHED` в `News.playerReadinessFlags` (FR-004a).
    5. FR-006 rate limit: считать посты за последний час (in-memory ring buffer timestamp'ов), если превысили `vkAutoPublishRateLimitPerHour` — остаток перенести на следующий тик (лог).
  - `private fun loadNewsCandidates(): List<News>` — SQL:
    ```sql
    SELECT id, title, body, link, song_id, category, publish_at
    FROM tbl_news
    WHERE category = 'air'
      AND publish_at IS NOT NULL AND publish_at <= now()
    ORDER BY publish_at ASC, id ASC
    ```
    Raw SQL через `WORKING_DATABASE.getConnection()` (по образцу `TelegramAutoPublishScheduler.loadWindowCandidateIds:148`).
  - `private fun findRenderDemoProcess(songId: Long): RenderProcessInfo?` — по образцу `TelegramAutoPublishScheduler.findRenderDemoProcess:186` (SELECT status, id FROM tbl_processes WHERE settings_id=? AND process_type='RENDER_MP4_DEMO' ORDER BY id DESC LIMIT 1).
  - `private fun parseSongIdFromLink(link: String): Long?` — разбор `/song?id=<id>` (regex `song\?id=(\d+)`).
  - `private data class RenderProcessInfo(status: String, id: Long)`.
  - KDoc класса со ссылкой `@see docs/features/vk-news-auto-publish.md` и FR-002a.
- [X] T011 [US1] Создать `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishSchedulerStarter.kt` (по образцу `TelegramAutoPublishSchedulerStarter.kt`):
  - `@Component class VkAutoPublishSchedulerStarter { @EventListener(ApplicationReadyEvent::class) fun onApplicationReady() { if (KaraokeProperties.getBoolean("vkAutoPublishEnabled")) println("VkAutoPublishScheduler: старт (vkAutoPublishEnabled=true)") } }`.
  - KDoc со ссылкой на `@see docs/features/vk-news-auto-publish.md` и пояснением, что endpoint `/api/song/publishToVkNow` работает независимо от флага.

**Checkpoint**: User Story 1 полностью функциональна. Бот запускается при старте `karaoke-app` (если `vkAutoPublishEnabled=true`), тикает каждые 60 сек, публикует `air`-новости с демо-MP4, заполняет `Song.idVk`. Проверить по quickstart.md Сценарий 1.

---

## Phase 4: User Story 2 - Повторная синхронизация/тик не дублирует уже опубликованные во ВКонтакте песни (Priority: P1)

**Goal**: Идемпотентность — повторные тики и рестарты не создают дублей постов в группе ВК.

**Independent Test**: После публикации (Сценарий 1) перезапустить `karaoke-app`, дождаться 2-3 тиков — в группе ВК нет второго поста, `Song.idVk` не изменился. (см. quickstart.md Сценарий 2)

### Implementation for User Story 2

> Идемпотентность уже реализована в US1 (FR-008 — `if (song.idVk.isNotEmpty()) return PUBLISHED` в `VkAutoPublishService.publishToVk`, и в `VkAutoPublishScheduler.publishScheduledNews` — skip при заполненном `idVk`). Эта фаза — валидация и доводка edge cases.

- [X] T012 [US2] Проверить и при необходимости усилить идемпотентность в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt` (функция `publishToVk`): гарантировать, что проверка `song.idVk.isNotEmpty()` выполняется **до** любых сетевых операций и **до** старта рендера (FR-007). Добавить явный лог `println("VkAutoPublishService: song id=${song.id} idVk not empty, skip (FR-008)")` для отладки.
- [X] T013 [US2] Проверить идемпотентность `onRenderCompleted` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishService.kt`: если за время рендера `idVk` заполнился (вручную или другим тиком) — вернуть `PUBLISHED` без повторной отправки (по образцу `TelegramAutoPublishService.onRenderCompleted:132`). Явный комментарий.
- [X] T014 [US2] Проверить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt` (функция `resumeRenderingSongs`): для каждой песни-кандидата в RENDERING — повторно проверить `idVk` после загрузки (гонка: за тик песня могла быть опубликована через `/publishToVkNow`). Skip если `idVk` не пуст (по образцу `TelegramAutoPublishScheduler.resumeRenderingSongs:83`).

**Checkpoint**: User Story 2 проверена — идемпотентность гарантирована на всех путях (плановый тик, ручной endpoint, after-render callback).

---

## Phase 5: User Story 3 - Администратор видит, какие песни опубликованы во ВКонтакте, и может переопубликовать (Priority: P2)

**Goal**: В админке `webvue3` администратор видит заполненный `idVk` (существующий UI карточки песни) и может очистить его для переопубликования.

**Independent Test**: После публикации (Сценарий 1) открыть карточку песни в `webvue3`, очистить `idVk`, нажать «Опубликовать во ВКонтакте сейчас» — пост снова появляется, `idVk` заполняется новым id. (см. quickstart.md Сценарий 3, 4)

### Implementation for User Story 3

- [X] T015 [US3] Добавить endpoint `POST /api/song/publishToVkNow` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (по образцу `publishToTelegramNow:6574`, рядом или сразу после):
  - `@PostMapping("/song/publishToVkNow") @ResponseBody fun publishToVkNow(@RequestParam id: Long, @RequestParam(required=false, defaultValue="air") type: String): Map<String, Any>`.
  - `Song.loadFromDbById(id=id, database=WORKING_DATABASE, storageService, storageApiClient)`.
  - FR-008: если `song.idVk.isNotEmpty()` → вернуть `{success:false, state:"published", postId:idVk, error:"Song $id is already published (idVk=${idVk}); clear idVk first to re-publish"}` (общая идемпотентность для air и premium, FR-016/FR-026).
  - Иначе → `val pubType = PublicationType.fromCode(type) ?: PublicationType.AIR`; `val result = VkAutoPublishService.publishToVk(settings, pubType)` → вернуть `{success: result.state==PUBLISHED, state: result.state.code, postId: result.postId, error: result.error}`.
  - Комментарий-контракт: `specs/121-vk-news-auto-publish/contracts/vk-api-contract.md` §6, FR-016, FR-026, FR-027. Endpoint доступен всегда (даже при `vkAutoPublishEnabled=false`) — ручной триггер.
- [X] T015a [US5] Добавить кнопку «Опубликовать во ВК (premium)» в `webvue3/src/components/Songs/edit/SongEdit.vue` (рядом с air-кнопкой T016):
  - `v-if="!song.idVk"` — видна только если `idVk` пуст (FR-026, общая идемпотентность с air-кнопкой).
  - `@click="publishToVkNow('premium')"` — метод-обработчик, вызов `POST /api/song/publishToVkNow?id=${song.id}&type=premium` (см. контракты §6).
  - После успеха — обновить `song.idVk`, тост «Опубликовано во ВК (premium)».
  - Комментарий-блок со ссылкой на `specs/121-vk-news-auto-publish` FR-026, User Story 5.
- [X] T016 [US3] Добавить кнопку «Опубликовать во ВК (air)» в `webvue3/src/components/Songs/edit/SongEdit.vue` (по образцу кнопки Telegram `publishToTelegramNow:1561`, рядом с полем `idVk` ~стр.596-620):
  - `v-if="!song.idVk"` — видна только если `idVk` пуст (FR-016, FR-008).
  - `@click="publishToVkNow('air')"` — метод-обработник, вызов `POST /api/song/publishToVkNow?id=${song.id}&type=air` (по умолчанию `type=air`, см. контракты §6).
  - В `data()` / `methods`: добавить `publishToVkNow(type='air')`, вызывающий `publishToVkNowPromise` → `POST /api/song/publishToVkNow?id=${song.id}&type=${type}` (по образцу `publishToTelegramNowPromise:4029`).
  - После успеха — обновить `song.idVk` из ответа, тост «Опубликовано во ВК (air)».
  - После ошибки — тост с `error` из ответа.
  - Комментарий-блок со ссылкой на `specs/121-vk-news-auto-publish` FR-016.

**Checkpoint**: User Story 3 функциональна — администратор может принудительно публиковать и переопубликовывать (после очистки `idVk`).

---

## Phase 6: User Story 4 - Существующие ручные новости администратора в категории `air` также кросс-постятся (Priority: P3)

**Goal**: Ручные новости `air` (без `song_id` или с ним) публикуются тем же путём, что и авто-новости. Новости других категорий (`general`/`feature`/`premium`) не публикуются.

**Independent Test**: Создать вручную новость категории `air` с `link=/song?id=<id>` и `publishAt` в прошлом — дождаться тика, пост появляется в ВК. Создать новость `general` — пост НЕ появляется. (см. quickstart.md Сценарий 7 для редкого случая)

### Implementation for User Story 4

> US4 по сути уже реализован в US1: `VkAutoPublishScheduler.loadNewsCandidates` фильтрует `WHERE category = 'air'` (FR-017), не различая `source='auto'` и `source='manual'`. Эта фаза — валидация и edge cases.

- [X] T017 [US4] Проверить в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt` (функция `loadNewsCandidates`): убедиться, что SQL-фильтр `category = 'air'` не различает `source` (FR-017 — и авто, и ручные `air`-новости публикуются). Явный комментарий.
- [X] T018 [US4] Реализовать `publishNewsWithoutVideo(news: News)` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/VkAutoPublishScheduler.kt` (для редкого случая FR-021 — ручная `air`-новость без `song_id` и нераспознаваемой ссылкой):
  1. FR-004a: проверить `News.vkAutoPublishState` — если `PUBLISHED` → skip (идемпотентность редкого случая).
  2. FR-003: `message = "${news.title}\n\n${news.body}\n${news.link ?: ""}"` (усечение до 10 000, FR-005).
  3. `client.wallPost(ownerId="-${KaraokeProperties.getString("vkGroupId")}", message=message, attachments=null)`.
  4. При успехе → `News.vkAutoPublishState = PUBLISHED`, сохранить News (FR-004a — JSON-блоб, без новой колонки).
  5. При `SEND_FAILED` → `News.vkAutoPublishState = SEND_FAILED`, лог.
  - KDoc со ссылкой `@see docs/features/vk-news-auto-publish.md` и FR-004a, FR-021.
- [X] T019 [US4] В `VkAutoPublishScheduler.publishScheduledNews` (T010) — вызвать `publishNewsWithoutVideo(news)` для новостей без определённого `songId` (после T017/T018). Защитить rate limit (FR-006) — общий счётчик с типичным случаем.

**Checkpoint**: User Story 4 функциональна — ручные `air`-новости публикуются; новости других категорий — нет; редкий случай (без `song_id`) — пост без видео с идемпотентностью через `News.playerReadinessFlags`.

---

## Phase 6a: User Story 5 - Премиум-выпуск песни публикуется в ВК вручную (Priority: P2)

**Goal**: Кнопка «Опубликовать во ВК (premium)» формирует пост по `vkTemplatePremium` и заполняет `idVk`. Идемпотентность общая с `air` по `Song.idVk`.

**Independent Test**: Подготовить песню статуса 6 с пустым `idVk`, настроить `vkTemplatePremium`, нажать кнопку — в ВК пост по премиум-шаблону, `idVk` заполнен. (см. quickstart.md Сценарий 8)

### Implementation for User Story 5

> US5 частично реализован в US3: endpoint `/api/song/publishToVkNow?type=` (T015) принимает `type=premium`, `VkAutoPublishService.publishToVk(song, type)` (T009) использует `VkTemplateService` (T006b). Эта фаза — UI-кнопка premium (T015a) и валидация premium-flow.

- [X] T016a [US5] Реализовать кнопку «Опубликовать во ВК (premium)» в `webvue3/src/components/Songs/edit/SongEdit.vue` (см. T015a — задача перенесена сюда как US5, т.к. US3 — air-кнопка, US5 — premium-кнопка). Дублирующая ссылка: убедиться, что обе кнопки (`air` из T016 и `premium` из T015a) сосуществуют, обе `v-if="!song.idVk"`, обе disabled после заполнения `idVk` (FR-016, общая идемпотентность).
- [X] T016b [US5] Валидация premium-flow: проверить, что `VkAutoPublishService.publishToVk(song, PublicationType.PREMIUM)` (T009) корректно:
  1. FR-026: не зависит от `tbl_news` — `newsFor(song, PREMIUM)` возвращает `null` (нет premium-новости в `tbl_news` для ручного типа).
  2. FR-023: `VkTemplateService.templateFor(PREMIUM)` → `vkTemplatePremium` (или дефолт research.md §6).
  3. FR-022: если песня не готова — `SEND_FAILED` с причиной (та же проверка, что и для air).
  4. FR-019..FR-021: прикрепление демо-MP4 — тот же flow, что и для air (песня есть → видео прикрепляется; premium — это про текст/шаблон, не про наличие/отсутствие видео).
  - Запустить quickstart.md Сценарий 8 (требует admin-машину + тестовую группу ВК — выполняет пользователь).

**Checkpoint**: User Story 5 функциональна — premium-публикация вручную работает, шаблон `vkTemplatePremium` используется, идемпотентность общая с `air`.

---

## Phase 6b: User Story 6 - Редактор шаблонов ВК (Priority: P2)

**Goal**: В `webvue3` редактор шаблонов показывает/правит `vkTemplateAir` и `vkTemplatePremium` через `/api/vk/templates` (GET/POST), без перезапуска `karaoke-app`.

**Independent Test**: Открыть редактор, изменить `vkTemplateAir`, сохранить, опубликовать — пост по новому шаблону. (см. quickstart.md Сценарий 9)

### Implementation for User Story 6

- [X] T016c [US6] Добавить endpoints `GET /api/vk/templates` и `POST /api/vk/templates` в `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt` (см. контракты §6):
  - `GET /api/vk/templates`: возвращает `{ templates: [{type, key, value, description}], placeholders: [{name, description}] }` через `VkTemplateService.placeholders()` (T006b) и чтение `KaraokeProperties.getString("vkTemplateAir"/"vkTemplatePremium")`.
  - `POST /api/vk/templates` с параметрами `key`, `value`: `KaraokeProperties.set(key, value)` (без перезапуска, FR-015). Валидация: `key` должен быть `vkTemplateAir` или `vkTemplatePremium` (или другой зарегистрированный `vkTemplate*`); иначе `{success:false, error:"unknown key"}`. Возврат `{success:true, key}`.
  - Комментарий-контракт со ссылкой на `specs/121-vk-news-auto-publish/contracts/vk-api-contract.md` §6, FR-025.
- [X] T016d [US6] Создать `webvue3/src/components/VkTemplates/VkTemplatesEditor.vue` (новый компонент, FR-025):
  - Шаблон: список типов публикаций (`air`, `premium`) → для каждого многострочный `<textarea v-model="templates[type].value">`.
  - Подсказка по плейсхолдерам (static список из `placeholders` ответа `GET /api/vk/templates`): `{author}`, `{songName}`, `{link}`, `{id}`, `{body}` — с описаниями.
  - Кнопка «Сохранить» → `POST /api/vk/templates?key=vkTemplate<Type>&value=...` для каждого изменённого шаблона.
  - Валидация скобок (client-side, предупреждение не блокирующее): регулярка `\{(\w+)\}`, проверка парности; при несбалансированной — тост-предупреждение, но сохранение разрешено (User Story 6 сценарий 3, FR-023).
  - После сохранения — тост «Шаблоны сохранены».
  - В `data()`: `templates: {air:'', premium:''}`, `placeholders: []`. В `mounted()`: `GET /api/vk/templates` → заполнить.
  - Подключить компонент в роутинг webvue3 (по конвенции проекта — в существующее меню админки, раздел «Шаблоны ВК» или в настройки).
  - Комментарий-блок со ссылкой на `specs/121-vk-news-auto-publish` FR-025, User Story 6.

**Checkpoint**: User Story 6 функциональна — редактор шаблонов показывает/правит оба шаблона, сохранение через `/api/vk/templates`, без перезапуска `karaoke-app`.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Финальная проверка качества, CI-совместимость, обновление per-feature документа.

- [X] T020 [P] Проверить KDoc coverage для всех новых классов (`VkApiClient`, `VkAutoPublishService`, `VkAutoPublishScheduler`, `VkAutoPublishSchedulerStarter`, `VkAutoPublishState`, `VkAutoPublishResult`) и расширений `Song`/`News`: каждый публичный класс/метод — с KDoc и `@see docs/features/vk-news-auto-publish.md`. Запустить `bash tools/check-kdoc-coverage.sh` — должно быть 100% (Constitution Principle VI FR-006).
- [X] T021 [P] Запустить `./gradlew ktlintCheck` — убедиться, что новые Kotlin-файлы проходят ktlint без новых нарушений (Constitution Principle VI FR-007). При нарушениях — исправить, не добавлять в baseline.
- [X] T022 [P] Запустить `cd webvue3 && npm run lint:check` — убедиться, что правки `SongEdit.vue` проходят ESLint без новых нарушений.
- [X] T023 [P] Проверить, что секреты не попадают в git: `vkAccessToken` — только в `KaraokeProperties` (base64-файл `/sm-karaoke/system/Karaoke.properties`, в `.gitignore`). `git status` не должен показывать изменений `Karaoke.properties`. Запустить `git diff --stat` — убедиться, что в индексе нет файлов с секретами (Constitution Principle VII, AGENTS.md «Запрещено»).
- [ ] T024 [P] Запустить ручную проверку по `specs/121-vk-news-auto-publish/quickstart.md` Сценарии 1-9 (требует admin-машину с работающим `karaoke-app` и тестовую группу ВК — выполняет пользователь; агент только на `dev-pc`/`dev`).
- [ ] T025 [P] Обновить `docs/architecture-notes.md` — добавить запись о PR `121-vk-news-auto-publish` (по образцу существующих записей): краткое описание, дата, ссылка на spec. Отдельный коммит в master (после merge PR с кодом, см. AGENTS.md «CI-gate для master»).
- [ ] T026 [P] Обновить `AGENTS.md` (при необходимости) — если фича добавляет новый архитектурный паттерн или ловушку, достойную упоминания в Q&A. По правилам AGENTS.md «Как обновлять этот файл» — отдельная feature-ветка + PR + CI 7/7.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: без зависимостей — можно начинать сразу. T002, T003 параллельны с T001.
- **Foundational (Phase 2)**: зависит от Setup (T001 — ключи Properties). **БЛОКИРУЕТ все user stories**. T004, T005, T008 параллельны; T006 — после T004/T005 (VkApiClient использует State/Result); T007 — после T004 (Song-расширения используют State).
- **User Stories (Phase 3-6)**: все зависят от Foundational (Phase 2).
  - **US1 (Phase 3)** — без зависимостей от других stories. T009 → T010 (Scheduler использует Service) → T011 (Starter).
  - **US2 (Phase 4)** — зависит от US1 (проверяет идемпотентность уже реализованного). T012, T013, T014 параллельны (разные места в одном файле — координировать).
  - **US3 (Phase 5)** — зависит от US1 (endpoint использует Service из US1). T015 (backend) → T016 (frontend) — последовательно.
  - **US4 (Phase 6)** — зависит от US1 (расширяет Scheduler из US1). T017 → T018 → T019 — последовательно.
- **Polish (Phase 7)**: зависит от завершения всех нужных user stories. T020-T026 — все параллельны.

### User Story Dependencies

- **User Story 1 (P1)**: после Foundational — без зависимостей от других stories. **🎯 MVP**.
- **User Story 2 (P1)**: после US1 (валидация идемпотентности).
- **User Story 3 (P2)**: после US1 (endpoint использует Service).
- **User Story 4 (P3)**: после US1 (расширяет Scheduler).
- **User Story 5 (P2)**: после US1 (Service принимает `type`) + US3 (endpoint `type=` параметр). UI-кнопка premium (T015a) может параллелиться с US3 (T016).
- **User Story 6 (P2)**: после US1 (VkTemplateService из Foundational T006b). Независима от US3/US5 — редактор работает с `KaraokeProperties` напрямую.

### Within Each User Story

- Models / State-классы → Services (T009) → Scheduler (T010) → Starter (T011)
- Backend (endpoint) → Frontend (кнопка)
- Core реализация → edge cases / идемпотентность

### Parallel Opportunities

- Phase 1: T002, T003 параллельны с T001.
- Phase 2: T004, T005, T008 параллельны (разные файлы).
- Phase 4 (US2): T012, T013, T014 — разные места одного файла (координировать, но логически параллельны).
- Phase 7: T020-T026 — все параллельны (разные файлы/инструменты).

---

## Parallel Example: User Story 1

```bash
# Phase 2 (Foundation) — parallel:
Task: "T004 Create VkAutoPublishState enum"
Task: "T005 Create VkAutoPublishResult data class"
Task: "T008 Add News.vkAutoPublishState extension"

# Phase 3 (US1) — sequential:
Task: "T009 Create VkAutoPublishService (depends on T004, T005, T006, T007, T008)"
Task: "T010 Create VkAutoPublishScheduler (depends on T009)"
Task: "T011 Create VkAutoPublishSchedulerStarter (depends on T010)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003) — Properties-ключи, per-feature документ.
2. Complete Phase 2: Foundational (T004-T008) — `VkApiClient`, `VkAutoPublishState`/`Result`, расширения Song/News.
3. Complete Phase 3: User Story 1 (T009-T011) — Service, Scheduler, Starter.
4. **STOP and VALIDATE**: проверка по quickstart.md Сценарий 1 (требует admin-машину + тестовую группу ВК — выполняет пользователь). Бот включается `vkAutoPublishEnabled=true`, тикает, публикует `air`-новости с демо-MP4, заполняет `idVk`.
5. При успехе — demo пользователю.

### Incremental Delivery

1. Setup + Foundational → Foundation готов (Properties UI показывает новые ключи, включая `vkTemplateAir`/`vkTemplatePremium`).
2. Add User Story 1 → Test (Сценарий 1) → **MVP** (бот автопубликует `air`-новости по `vkTemplateAir`).
3. Add User Story 2 → Test (Сценарий 2) → идемпотентность валидирована.
4. Add User Story 3 → Test (Сценарий 3, 4) → air-кнопка «Опубликовать во ВК (air)» работает.
5. Add User Story 4 → Test (Сценарий 7) → ручные `air`-новости + редкий случай.
6. Add User Story 5 → Test (Сценарий 8) → premium-кнопка работает, `vkTemplatePremium` используется.
7. Add User Story 6 → Test (Сценарий 9) → редактор шаблонов работает.
8. Polish (T020-T026) → CI 7/7 → PR → merge в master.

### Parallel Team Strategy

При нескольких разработчиках:
1. Команда вместе делает Setup + Foundational.
2. После Foundational:
   - Developer A: User Story 1 (T009-T011) — критический путь.
   - Developer B: User Story 3 (T015-T016) — после US1 (T015).
3. US2, US4 — после US1 (можно параллельно с US3).

---

## Notes

- [P] = разные файлы, нет зависимостей от незавершённых задач.
- [Story] = принадлежность к user story для traceability.
- Каждая user story независимо завершаема и тестируема (см. quickstart.md).
- Без CI-тестов (`constitution.md` «Тесты») — проверка вручную через quickstart.md.
- Commit после каждой задачи или логической группы.
- Останавливаться на checkpoint'ах для валидации story независимо.
- Избегать: vague-задач, конфликтов одного файла, cross-story зависимостей, ломающих независимость.
- CI-gate для master (AGENTS.md): feature-ветка `121-vk-news-auto-publish` уже создана → PR → дождаться CI 7/7 → `gh pr merge --merge --delete-branch`.
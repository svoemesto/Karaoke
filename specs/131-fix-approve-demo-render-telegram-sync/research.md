# Исследование: фича 131 — починка пайплайна после одобрения задания

> **Статус**: Phase 0 (research). Все NEEDS CLARIFICATION из `spec.md` закрыты как
> `Assumptions A-001..A-009` ещё на фазе specify. Настоящий документ фиксирует
> **технические решения** под этими предположениями, обходясь без новых уточнений.

## Содержание

1. [Контекст и существующая инфраструктура](#1-контекст-и-существующая-инфраструктура)
2. [Открытые вопросы спецификации и принятые решения](#2-открытые-вопросы-спецификации-и-принятые-решения)
3. [Сводка решений (Decision / Rationale / Alternatives)](#3-сводка-решений-decision--rationale--alternatives)

---

## 1. Контекст и существующая инфраструктура

### 1.1 Точка входа — `SongEditorController.approve()`

Файл `karaoke-app/.../controllers/SongEditorController.kt:309`. По состоянию «до
фичи» метод делает:

1. Применяет разметку/текст присланного задания к `song` (`setSourceMarkers`,
   `setSourceText`, `truncateVoicesTo`).
2. Ставит `song.fields[SongField.ID_STATUS] = "6"` и `song.saveToDb()` —
   внутри `saveToDb` срабатывает `markNewsAvailableIfReady()` и проставляет
   `newsAvailableAnnounced=true` (если стемы готовы и раньше флаг был `false`).
3. Вызывает `updateRemoteSongFromLocalDatabase(song.id)` — синк
   **только `tbl_songs`** через `SyncRegistry` (ключ `"song"`).
4. Сохраняет задание редактора (`aRead.save()`) со статусом approved.

Эта последовательность закреплена фичами specs/094, 095, 096 — трогать её
запрещено (A-001).

### 1.2 Чего не хватает (по наблюдаемой симптоматике)

| Требование | Что есть сейчас | Что отсутствует |
|---|---|---|
| FR-001: DEMO-рендер при approve | Никто не запускает. Создаётся только вручную «Рендер MP4 DEMO» или через плановый `SongReleaseAnnouncementScheduler`/`TelegramAutoPublishScheduler` (но оба стартуют рендер только если уже есть `dateTimePublish`/`newsPremiumPublishPending`). | Автозапуск `RENDER_MP4_DEMO` сразу после approve. |
| FR-003: публикация в Telegram | Только плановый `TelegramAutoPublishScheduler` (tick 60 c, окно `now-59min..now`). Запускается только для песен с заполненной `dateTimePublish`. | Прямая публикация в Telegram сразу после завершения рендера (не дожидаясь окна). |
| FR-005: sync связанных таблиц | `updateRemoteSongFromLocalDatabase(id)` пушит только `tbl_songs`. | После approve пушить и `tbl_pictures`, `tbl_authors`, `tbl_albums` (если они менялись). |
| FR-008: новость «в коллекции» на сервере | `MainController.doChangeRecords` уже вызывает `SongReleaseAnnouncementService.detectAndAnnounceAvailability` для каждой затронутой строки `tbl_songs`. Поскольку `tbl_songs` пушится, детекция перехода `newsAvailableAnnounced: false→true` **должна** сработать. | Если не срабатывает — причина в (а) уже `true` ДО approve или (б) ошибке apply на сервере. И то, и другое вне scope этой фичи (A-007). Наш вклад — гарантия, что `tbl_songs` точно пушится (она уже), и что approve не падает раньше пуша. |

### 1.3 Существующая инфраструктура рендера и публикации

| Подсистема | Файл | Что делает | Где вызывается |
|---|---|---|---|
| `KaraokeProcess.createProcess(song, type, doWait, prior, threadId)` | `karaoke-app/.../KaraokeProcess.kt:49` | Создаёт строку в `tbl_processes` со статусом WAITING (или WORKING при doWait=true). | Вручную из `ApiController.createRenderMp4PreviewProcess` (HTTP `POST /song/renderMp4Preview`); авто из `TelegramAutoPublishService.startRenderAndReturn` (FR-003 сц.2/3 Фазы 2), `PremiumAutoPublishScheduler`/`VkAutoPublishService`. |
| `KaraokeProcessWorker` + `KaraokeProcessThread.run()` | `karaoke-app/.../KaraokeProcessWorker.kt:62,74` | Подбирает WAITING-задания, ставит WORKING, запускает subprocess, по завершении выставляет DONE/ERROR. После — **пост-хук HealthReport + SSE**. | Сам воркер (потокобезопасно, per-process-`ThreadLocal`-соединение закрывается — паттерн specs/091). |
| `TelegramAutoPublishService.publishToTelegram(song, allowPastDate, publicationType, persistMessageId)` | `karaoke-app/.../services/TelegramAutoPublishService.kt:66` | Полный цикл: проверка флага `idTelegramDemo`, проверка файла DEMO, рендер если нужно, sendVideo. Идемпотентен по `idTelegramDemo`. | Из `TelegramAutoPublishScheduler.publishScheduledSongs` (плановый tick, 60 c). Из `PremiumAutoPublishScheduler.processSong` (для PREMIUM). Из `ApiController.dopublishToTelegram` (ручной триггер). |
| `TelegramAutoPublishService.onRenderCompleted(songId, success, error, publicationType, persistMessageId)` | тот же файл, ниже `:66` | Callback завершения `RENDER_MP4_DEMO` (если рендер запускался внутри `publishToTelegram`): публикует готовый файл. | Из `TelegramAutoPublishScheduler.resumeRenderingSongs` (планово). Из `PremiumAutoPublishScheduler.resumeRenderingSong` (для PREMIUM). |
| `MainController.doChangeRecords` (server-side) | `karaoke-web/.../controllers/MainController.kt:265` | Применяет SQL-апдейты от клиента; после — `SongReleaseAnnouncementService.detectAndAnnounceAvailability` для каждой изменённой `tbl_songs` строки. | Только от `karaoke-app` через `POST /changerecords` (вызывается из `updateDatabases`). |

### 1.4 Синхронизация LOCAL↔SERVER

- `updateRemoteSongFromLocalDatabase(id)` — `Utils.kt:570` → `updateDatabases(keys=setOf("song"))`. Покрывает **только** `tbl_songs` через `SongSyncTarget` (класс зарегистрирован в `SyncRegistry`, методы rowKeys и т.д.).
- `updateRemotePictureFromLocalDatabase(id)` — `Utils.kt:555` → `keys=setOf("pictures")`. Пушит одну запись `tbl_pictures` по id.
- `updateRemoteDatabaseFromLocalDatabase(updateSongs, updatePictures, updateAuthors)` — `Utils.kt:584` → `keys=legacySyncKeys(...)`. Пушит все записи указанных таблиц (через diff SyncTarget — фактически только изменённые хэши идут на сервер, остальные skip'ятся на уровне записи).
- Прямых аналогов `updateRemoteAuthorFromLocalDatabase(id)` / `updateRemoteAlbumFromLocalDatabase(id)` я **не нашёл** — только bulk.

### 1.5 Конституционные ограничения

Из `.specify/memory/constitution.md`:

- **II** — «recordhash-триггер» на `tbl_songs`/`tbl_settings`/etc. Любое новое поле в БД потребует пересоздания триггера в обеих БД. Поэтому **никаких миграций схемы** (A-002, подтверждено в spec.md).
- **III** — `SyncRegistry` — единственный механизм LOCAL↔SERVER. Не вводить параллельные пути.
- **VIII.5** — секреты в коде запрещены (нас не касается).
- **VI/IX** — асинхронные операции не должны блокировать UI-эндпоинт (A-006).
- **VII** — комментарии и KDoc на русском.

---

## 2. Открытые вопросы спецификации и принятые решения

Все 14 FR в `spec.md` сводятся к одному фундаментальному вопросу: **где и как
организовать конвейер рендер→публикация→sync внутри существующего approve-flow**.
Ниже — только те места, где нужно было фиксировать выбор между вариантами.

### 2.1 Точка запуска рендера

| Аспект | Решение | Обоснование |
|---|---|---|
| Где запускать `RENDER_MP4_DEMO` | Из нового блока в `SongEditorController.approve()`, **после** `updateRemoteSongFromLocalDatabase(song.id)`. | Существующий approve уже устоялся (094/095/096). Новая логика — добавление в конце, никаких перестановок шагов. |
| Приоритет `RENDER_MP4_DEMO` | `prior=5` (средний). | Рендер не срочный для пользователя, но админ нажал кнопку — должно начаться быстро. `5` совместим с `KaraokeProperties.prioritet` (низкие числа — выше приоритет). |
| `threadId` | `0` (`HEAVY_RENDER` lane). | То же значение, что у ручного `/song/renderMp4Preview`; рендеры DEMO живут в отдельном lane и не блокируют лёгкие задачи. |
| `doWait` | `false` (без ожидания). | Approve не должен блокировать HTTP-ответ админа (см. A-006). |

### 2.2 Идемпотентность шага рендера (FR-007)

| Аспект | Решение | Обоснование |
|---|---|---|
| Дедупликация создания процесса | Перед `createProcess` сделать SELECT по `tbl_processes WHERE song_id=? AND process_type='RENDER_MP4_DEMO' AND process_status IN ('WAITING','WORKING')`. Если есть — skip. | Простой `SELECT`, ~5 мс. Не плодит дублирующих процессов. |
| Допустимость «файл уже есть, процесс не создаём» | **Не используем** как единственный гард: процесс мог устареть (завершён ERROR), а рендер нужно перезапустить. | Idempotency **надёжнее** через проверку активного процесса, чем через проверку наличия файла. |

### 2.3 Публикация в Telegram — где вызывать `publishToTelegram`

| Аспект | Решение | Обоснование |
|---|---|---|
| Точка вызова | Из post-hook в `KaraokeProcessThread.run()` сразу после успешного завершения `RENDER_MP4_DEMO`. | `TelegramAutoPublishScheduler` тикает раз в 60 c — это нарушает SC-002 «в течение 60 с после рендера». Пост-хук даёт **немедленный** вызов. |
| Параметры вызова | `allowPastDate=true, publicationType=AIR, persistMessageId=true`. | approve-песня имеет заполненную `dateTimePublish` (иначе бы её не апрувили — это часть гейта `isContentReady`). `allowPastDate=true` страхует от ложного срабатывания «опоздавшая». `publicationType=AIR` — это та публикация, ради которой approve и нужен. `persistMessageId=true` — сохранить `idTelegramDemo` после успешной отправки. |
| Поток | В **том же** post-hook (синхронно с потоком задания) — отдельный `Thread` (через `thread { ... }`), чтобы не задерживать освобождение `ThreadLocal`-соединения. | Прямая публикация занимает 5-30 с (HTTP-вызов к Telegram). Блокировать `KaraokeProcessThread` плохо — это главный worker-thread. |
| Передача `song` в поток | Загрузить заново `Song.loadFromDbById(id, ...)` внутри потока — после завершения `WORKING→DONE` в БД уже всё видно (стемы, флаги). | Не пробрасываем `Song`-объект через post-hook (там уже лежит только `karaokeProcess`). Стандартный паттерн (используется и в `TelegramAutoPublishScheduler.resumeRenderingSongs`). |

### 2.4 Sync связанных таблиц (FR-005)

| Аспект | Решение | Обоснование |
|---|---|---|
| Какие таблицы пушить | `tbl_pictures`, `tbl_authors`, `tbl_albums`. | В спецификации перечислены явно; фотография обложки/исполнителя/альбома на сервере нужны для публичной страницы песни (иначе будет «Обложка исполнителя не обновилась»). |
| Какую функцию вызвать | **`updateRemoteDatabaseFromLocalDatabase(updateSongs=false, updatePictures=true, updateAuthors=true)`**. | Это единственная функция, которая покрывает все 3 таблицы одной транзакцией diff'а и уже используется в кодовой базе. `updateSongs=false` — `tbl_songs` уже засинкана существующим `updateRemoteSongFromLocalDatabase(id)`. |
| Допустимость «full scan» | Принимаем. `updateDatabases` хэширует каждую запись (O(n) на таблицу) — для 18k+ записей это десятки секунд, но approve-операция админская и редкая. | Альтернатива (per-id для каждой связанной записи) безопаснее по времени, но требует вычисления `pictureId`/`authorId`/`albumId` для конкретной песни и вызовов отсутствующих ныне функций `updateRemoteAuthorFromLocalDatabase`. |
| Поток | В **отдельном `thread { ... }`**, fire-and-forget. | Длительность может быть большой — нельзя блокировать HTTP approve (см. SC-003 «не задерживать ответ»). |
| Логирование | Перед стартом — `println("[approve/sync-related] старт")`, по завершении — `println("[approve/sync-related] созданных/обновлённых: X/Y")` (как в существующем `approve` для `tbl_songs`). | Соответствует существующему стилю логов approve (`specs/096-approve-news-timing-diagnostics`). |
| Ошибки | `try { ... } catch (e: Exception) { println("[approve/sync-related] ошибка: ${e.message}") }` — не пробрасываем наверх. | Approve уже состоялся, откатывать его из-за сбоя sync нельзя (FR-005 «best-effort», A-007). Кнопка «Обновить на сервере» остаётся доступной для ручного повтора. |

### 2.5 Где добавить новый код

| Аспект | Решение | Обоснование |
|---|---|---|
| Структура | **Не** выносим логику approve в отдельный сервис (`ApprovePipelineService`), а добавляем 2 блока (рендер + related-sync) **внутрь** существующего метода `SongEditorController.approve()` сразу после `updateRemoteSongFromLocalDatabase(song.id)`. | Минимизация изменений. Полноценный `ApprovePipeline` имел бы смысл если бы мы переписывали approve — нет. |
| Введение нового сервиса не противоречит конституции III, но в данной фиче преждевременно — YAGNI. | | |

---

## 3. Сводка решений (Decision / Rationale / Alternatives)

### D-1: Уведомление о завершении рендера — post-hook в `KaraokeProcessThread`

- **Decision**: добавить вызов `TelegramAutoPublishService.publishToTelegram(...)` в
  пост-хук `KaraokeProcessThread.run()` после успешного завершения
  `RENDER_MP4_DEMO`. Вызов обёрнут в `thread { ... }` чтобы не блокировать worker.
- **Rationale**: существующие потребители `publishToTelegram` (`TelegramAutoPublishScheduler`,
  `PremiumAutoPublishScheduler`, `ApiController.dopublishToTelegram`) — все
  используют сервис одинаково. Пост-хук `KaraokeProcessThread` уже есть для
  HealthReport — добавляем параллельную ветку. Прямой вызов даёт
  немедленную публикацию (SC-002).
- **Alternatives considered**:
  - (A) Опираться только на `TelegramAutoPublishScheduler` (60 c tick). Отклонено:
    не удовлетворяет SC-002 «в течение 60 с после рендера» (фактически даёт только
    «в течение ≤60 с + 60 с окна» = до 2 минут, плюс окно 59 минут — не
    гарантировано).
  - (B) WebSocket между karaoke-app и Telegram-bot. Отклонено: переусложнение,
    нет бизнес-требования, существующий scheduler уже работает.

### D-2: Sync связанных таблиц — bulk `updateRemoteDatabaseFromLocalDatabase`

- **Decision**: после `updateRemoteSongFromLocalDatabase(song.id)` параллельно
  запустить `thread { updateRemoteDatabaseFromLocalDatabase(updateSongs=false, updatePictures=true, updateAuthors=true) }`.
- **Rationale**: единственная готовая функция, покрывающая все 3 таблицы
  одной транзакцией. Diff через хэши SyncTarget — на сервер уйдут **только**
  изменённые записи, остальные skip-нутся на уровне `tbl_settings_sync`. Изоляция
  в `thread` гарантирует A-007/HTTP-ответ approve-эндпоинта.
- **Alternatives considered**:
  - (A) Per-id вызовы: `updateRemotePictureFromLocalDatabase(picId)` плюс явные
    `updateRemoteAuthorFromLocalDatabase(authorId)`, `updateRemoteAlbumFromLocalDatabase(albumId)`.
    Отклонено: вторые две функции отсутствуют в кодовой базе, требуют
    добавления и unit-сопровождения.
  - (B) Не пушить связанные таблицы вовсе (только `tbl_songs`). Отклонено:
    нарушает FR-005 явно.

### D-3: Идемпотентность рендер-процесса — гард по активному процессу

- **Decision**: перед `KaraokeProcess.createProcess(... RENDER_MP4_DEMO ...)`
  делаем `SELECT process_status, id FROM tbl_processes WHERE song_id=? AND
  process_type='RENDER_MP4_DEMO' AND process_status IN ('WAITING','WORKING')`.
  Если результат непуст — skip.
- **Rationale**: самая простая защита от дублей. Покрывает 99% повторов (двойной
  approve, ручной триггер approve+рендер кнопкой, рестарт admin-сервиса). Не
  защищает от гонки двух параллельных HTTP-вызовов — но в UI approve только
  одна кнопка, и двойной клик даёт ту же логику: «активный процесс есть — skip».
- **Alternatives considered**:
  - (A) Гард по наличию валидного DEMO-файла. Отклонено: не ловит случай
    неудачного рендера (файл от прошлой версии может остаться, а рендер был
    сброшен).
  - (B) `SELECT … FOR UPDATE` транзакционно. Отклонено: лишний overhead; approve
    — низкочастотная операция, простой SELECT достаточен.

### D-4: Тайминг/порядок шагов внутри approve

- **Decision**: после существующего `updateRemoteSongFromLocalDatabase(song.id)`
  и **до** `aRead.save()` добавить:

  ```text
  // 1. (синхронно, мгновенно) Идемпотентный запуск рендера DEMO
  triggerDemoRenderIfNeeded(song)            // SELECT + createProcess

  // 2. (fire-and-forget thread) sync связанных таблиц
  thread { updateRemoteDatabaseFromLocalDatabase(false, true, true) }

  // 3. (существующее, не трогаем) aRead.save()
  ```

  Публикация в Telegram — отдельный callback из KaraokeProcessThread post-hook,
  не блокирует approve-эндпоинт.
- **Rationale**: каждый шаг — лучшее качество обслуживания, и каждый изолирован
  (сбой одного не откатывает другие). Длинный sync в `thread` не задерживает
  UI. Публикация в Telegram в post-hook — то же самое: рендер уже завершился,
  поток `karaoke-app` освободил `ThreadLocal`, новый `thread { }` отвечает за
  HTTP-вызов.
- **Alternatives considered**:
  - (A) Ждать завершения рендера в `approve()`. Отклонено: блокирует
    UI до 5 минут (SC-003 нарушен, A-006).
  - (B) Publish в Telegram **после** sync сервера, а не по факту завершения
    рендера. Отклонено: если sync не дошёл, в Telegram опубликуют всё равно —
    новость «в коллекции» появится позже, читатель увидит ссылку без описания.
    Текущее решение (publish сразу после рендера, sync параллельно) — лучше
    по UX: постинг в Telegram → пользователь переходит → страница уже видит
    песню (sync обычно быстрее 1 с).

### D-5: Когда НЕ делать рендер / публикацию

- **Decision**: рендер создаётся всегда при approve (если активного процесса нет).
  Публикация в Telegram — внутри `publishToTelegram` уже есть гарды:
  `idTelegramDemo` непуст → skip; `!isContentReady` → skip; `telegramAutoPublishEnabled=false` →
  skip (выходит с SCHEDULED). Этого достаточно.
- **Rationale**: `publishToTelegram` уже инкапсулирует всю политику. Не
  дублируем проверки на стороне approve — это потенциальный рассинхрон.
- **Alternatives considered**:
  - (A) Явный if (telegramAutoPublishEnabled) в approve. Отклонено: дублирование
    логики с scheduler.

### D-6: Совместимость с PREMIUM-каналом

- **Decision**: approve-публикация идёт в AIR-канал (publicationType=AIR, persistMessageId=true).
  PREMIUM-канал (`newsPremiumPublishPending`) **не затрагивается** этой фичей.
- **Rationale**: approve — это завершение базового цикла (задание одобрено → песня
  в коллекции → пользователь увидит в эфире). PREMIUM — отдельный
  end-of-cycle, регулируемый `PremiumAutoPublishScheduler` (spec 122). В рамках
  этой фичи мы только усиливаем AIR-путь.
- **Alternatives considered**:
  - (A) Запускать и AIR, и PREMIUM одновременно. Отклонено: расширение scope,
    несогласовано со спецификацией (FR-003 явно один канал — Telegram, тип AIR,
    см. SC-002/Success Criteria).

---

## 4. Известные риски

| # | Риск | Митигация |
|---|---|---|
| R-1 | `updateRemoteDatabaseFromLocalDatabase(updatePictures=true, updateAuthors=true)` выполняется десятки секунд на больших объёмах — админ не получит моментального подтверждения. | Fire-and-forget + лог `[approve/sync-related] созданных/обновлённых: X/Y` + кнопка «Обновить на сервере» на UI для ручного ретрая. |
| R-2 | На сервере может быть ветка `MainController.doChangeRecords` с уже выставленным `newsAvailableAnnounced=true` для этой песни (например, если раньше уже была одобрена). Тогда детекция перехода не сработает — новость не появится. | Это вне scope фичи (A-007). Если бы это понадобилось — задним числом снимать/ставить флаг через `Song.markNewsAvailableIfReady()`/`(song.fields[…]=…)` + повторный sync. Не делаем, чтобы не плодить обходные пути. |
| R-3 | `RENDER_MP4_DEMO` задача завершается ERROR → пост-хук не вызывает `publishToTelegram`. Но `TelegramAutoPublishScheduler` увидит, что `idTelegramDemo` пуст, и попытается опубликовать **на следующем тике** — что после фейла рендера снова попадёт в `startRenderAndReturn` → новый процесс. | Поведение, согласующееся со сценарием 5a в spec.md (FR-007). |
| R-4 | `TelegramAutoPublishService.publishToTelegram` внутри вызывает `Thread.sleep`/HTTP — если поток Java-приложения упал (OOM), отправка не состоится. | Запускаем в отдельном `thread { }` с собственным `try/catch` — ловим и пишем в лог. Scheduler ретраит на следующих тиках. |
| R-5 | В approve сейчас уже есть лог `[approve/timing] push на SERVER: …`. Новые блоки `[approve/render-demo]` и `[approve/sync-related]` нужно оформить **тем же стилем**, чтобы grep работал. | Оформляю по образцу существующего. |

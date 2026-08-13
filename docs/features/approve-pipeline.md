# Approve-пайплайн: рендер DEMO + Telegram + sync related → новость

> **Status**: active
> **Feature Key**: approve-pipeline
> **Last Updated**: 2026-08-13 (Pass 51-3 — фича 184: условный запуск render-demo и sync-related по выбору статуса 5/6; см. секцию «Условный запуск при выборе статуса 5» ниже)

## Что делает

Расширяет `POST /editor/song/approve` (`SongEditorController.approve()`,
[karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt))
тремя аддитивными шагами, превращая «approve-в-локал-sync» в полный
автоматический конвейер:

1. **Идемпотентно создать** процесс `RENDER_MP4_DEMO` в `tbl_processes`
   (1280×720@30fps, lane `THREAD_LANE_HEAVY_RENDER=0`, приоритет 5).
2. **Fire-and-forget запустить** синхронизацию связанных таблиц
   `tbl_pictures`/`tbl_authors`/`tbl_albums` через
   `updateRemoteDatabaseFromLocalDatabase(updateSongs=false, true, true)`.
3. В пост-хуке `KaraokeProcessThread.run()`
   ([karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt)),
   **сразу после `DONE`** для `RENDER_MP4_DEMO`, запустить
   `TelegramAutoPublishService.publishToTelegram` в отдельном `thread { ... }`
   с параметрами `allowPastDate=true, publicationType=AIR, persistMessageId=true`.

Существующая логика approve (markers / `idStatus=6` /
`updateRemoteSongFromLocalDatabase(song.id)` / `aRead.save()`) **не меняется**
— закреплена specs/094, 095, 096. На сервере `MainController.doChangeRecords`
при применении `tbl_songs` создаёт ровно одну новость «появилась в коллекции»
через `SongReleaseAnnouncementService.detectAndAnnounceAvailability`.

## Зачем

До фичи approve админа только отмечал задание одобренным и пушил `tbl_songs`
на сервер. Рендер DEMO, публикация в Telegram и синхронизация связанных
таблиц (`tbl_pictures`, `tbl_authors`, `tbl_albums`) оставались на волю
ручных кнопок и плановых scheduler'ов — что давало «дрейф» после approve:

- **Песня уже на сервере, а DEMO-MP4 для Telegram ещё не отрендерен.**
- **Песня уже в эфире, а обложка исполнителя/альбома в публичной карточке
  песни не обновилась (sync related не пушился).**
- **Пользователь видит «свежую» песню в каталоге, а поста в Telegram-
  канале ещё нет (publish ждёт следующего тика scheduler'а через 60 с в
  лучшем случае; в худшем — выпадает в окне «опоздавших» и не публикуется
  вовсе до ручного триггера).**

Фича 131 закрывает все три зазора аддитивно (без новых сервисов, DTO,
миграций), чтобы approve = «бизнесовый» конец цикла (песня доступна +
видео готово + анонс опубликован), а не просто «задание одобрено».

## Как работает

### Архитектура пайплайна

```text
POST /editor/song/approve?id=<ID>
   │
   ▼
SongEditorController.approve()
   │
   ├─ [existing, specs/094, 095] apply markers/text, idStatus=6, saveToDb()
   ├─ [existing, specs/094] updateRemoteSongFromLocalDatabase(song.id)
   │
   ├─ [NEW, US1]  triggerRenderMp4DemoIfNeeded(song)         ← SELECT-гард + createProcess
   │
   ├─ [NEW, US2]  thread {                                          ← fire-and-forget
   │   updateRemoteDatabaseFromLocalDatabase(
   │       updateSongs=false, updatePictures=true, updateAuthors=true
   │   )
   │  }
   │
   └─ [existing, specs/094] aRead.save()
                  │
                  ▼
            HTTP 200 {"ok":true,"status":"success","idStatus":6}

(параллельно, по мере завершения рендера:)
KaraokeProcessWorker.KaraokeProcessThread.run()            [karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt]
   │
   └─ [NEW, US1 post-hook]
      if (type == "RENDER_MP4_DEMO" && status == DONE) {
          thread {
              val song = Song.loadFromDbById(...)
              TelegramAutoPublishService.publishToTelegram(
                  song, allowPastDate=true, PublicationType.AIR, persistMessageId=true,
              )
          }
      }
```

### Шаги внутри `approve()` (после existing push, до `aRead.save()`)

1. **`triggerRenderMp4DemoIfNeeded(song)`** (helper, добавлен в конец
   `SongEditorController`):
   - `SELECT id FROM tbl_processes WHERE song_id = ? AND process_type =
     'RENDER_MP4_DEMO' AND process_status IN ('WAITING','WORKING')`
   - Если непусто — `println("[approve/render-demo] skip — уже есть
     активный процесс для песни ${id}")` и `return` (идемпотентность).
   - Иначе — `KaraokeProcess.createProcess(song, action = RENDER_MP4_DEMO,
     doWait=false, prior=5, threadId=0)`.
   - Любое исключение — `println("[approve/render-demo] ошибка: ...")`,
     НЕ пробрасывается (изоляция сбоя см. ниже).
   - **Feature 184**: вызов обёрнут в `if (song.idStatus >= 6L) { ... }` —
     при `idStatus=5` вместо вызова логируется `render-demo SKIPPED reason=idStatus=5`.
2. **`thread { updateRemoteDatabaseFromLocalDatabase(false, true, true) }`**:
   - Fire-and-forget, не блокирует HTTP-ответ approve (SC-003 ≤5 с).
   - Покрывает `tbl_pictures`, `tbl_authors`, `tbl_albums` одной
     транзакцией; `tbl_songs` уже пушнута existing-блоком.
   - Сбой — `println("[approve/sync-related] ошибка sync related: ...")`,
     не откатывает approve.
3. **`aRead.save()`** (existing): статус задания = `ADMIN_APPROVED`,
   HTTP-ответ.

### Условный запуск при выборе статуса 5 (feature 184)

Спека [184-approve-status-choice](../../specs/184-approve-status-choice/spec.md) добавляет
необязательный параметр `?idStatus=` в `POST /api/songeditor/approve`:

- `5` — «Маркеры проверены» (каноническое имя из `specs/022-song-status-lifecycle`, label `MARKERS_VERIFIED`): маркеры одобрены редактором, но рендер DEMO и sync related-таблиц **не запускаются**.
- `6` — «Готово» (или параметр не передан) — текущее поведение выше.

**Гейт по ФАКТИЧЕСКОМУ `song.idStatus` после применения, не по запрошенному значению**
([research D-2](../../specs/184-approve-status-choice/research.md)):

| `requestedIdStatus` | `current idStatus` | `song.idStatus` после | render-demo | sync related | song push |
|---|---|---|---|---|---|
| 6 / null | 4 | 6 | ✅ | ✅ | ✅ |
| 5 | 4 | 5 | ⛔ (SKIPPED) | ⛔ (SKIPPED) | ✅ |
| 5 | 6 | 6 (downgrade-ignore) | ✅ | ✅ | ✅ |

**Push самой песни (`updateRemoteSongFromLocalDatabase`) НЕ гейтится** (research D-3):
одобренная разметка (маркеры/текст/`.srt`) должна попасть на PROD при любом выборе —
иначе смысл апрува теряется. Безопасность: `id_status=5` на сервере не делает песню
доступной в публичном плеере (`Song.isContentReady` требует `>= 6`, см.
[`Song.kt:1132-1139`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt)),
а `markNewsAvailableIfReady` не выставляет `newsAvailableAnnounced`/`newsPremiumPublishPending`
(см. `Song.kt:5126-5136`).

**Логирование**: при пропуске обоих шагов — строки с префиксом `[approve/feature-184]`
(`render-demo SKIPPED ... reason=idStatus=5`, `sync-related SKIPPED ... reason=idStatus=5`,
`news SKIPPED ... reason=idStatus=5`) для grep по инцидентам (US3 спеки 184).

**Downgrade-ignore**: если админ выбрал `idStatus=5` для песни, которая УЖЕ в 6,
бэкенд НЕ понижает (data-model INV-1) и пишет `idStatus downgrade IGNORED ... current=6 requested=5`.
В UI этот кейс недостижим (US2 скрывает radio для `idStatus >= 5`), но защита нужна
для прямых curl-вызовов и гонок.

**Раньше** (до feature 184), чтобы отложить релиз, админу приходилось после апрува
вручную понижать статус в `SongEdit` (с 6 на 4 или 5) — с риском, что в промежутке
успеют сработать автотриггеры (рендер DEMO, sync, новости). Фича 184 убирает
этот костыль.

### Пост-хук в `KaraokeProcessThread.run()`

Срабатывает **после** existing-пост-хука `HealthReport.onRepairProcessFinished`
(если применимо), в той же ветке успеха/ERROR, перед `finally closeThreadConnection()`:

```kotlin
if (!forceStopped &&
    karaokeProcess.type == KaraokeProcessTypes.RENDER_MP4_DEMO.name &&
    karaokeProcess.status == KaraokeProcessStatuses.DONE.name
) {
    thread {
        try {
            val song = Song.loadFromDbById(
                id = karaokeProcess.songId.toLong(),
                database = WORKING_DATABASE,
                storageService = KSS_APP,
                storageApiClient = SAC_APP,
            ) ?: return@thread
            TelegramAutoPublishService.publishToTelegram(
                song = song,
                allowPastDate = true,
                publicationType = PublicationType.AIR,
                persistMessageId = true,
            )
        } catch (e: Exception) {
            println("[render-demo/post-hook] ошибка публикации: ${e.message}")
        }
    }
}
```

`!forceStopped` — пост-хук **не** срабатывает при форс-стопе (подпроцесс
убит → нечего публиковать; следующая попытка — через scheduler или ручной
триггер).

**Спека 144 (fix): гейт `trigger=approve`.** До этой правки условие срабатывания
проверяло только `type == RENDER_MP4_DEMO && status == DONE` — то есть публикация
в Telegram запускалась и после рендера DEMO, поставленного **вручную из
интерфейса** (кнопка «Рендер MP4» в SongEdit/`ApiController`), что было
нежелательным поведением. Исправлено добавлением маркера в `args` задания:
`triggerRenderMp4DemoIfNeeded` (approve-flow) передаёт в `KaraokeProcess.createProcess`
`context["trigger"] = "approve"`, который `KaraokeProcess.kt` (ветка
`RENDER_MP4_LYRICS`/…/`RENDER_MP4_DEMO`) дописывает в персистентный `args[0]`
как токен `"trigger=approve"` (переживает `WAITING→WORKING→DONE`, без новой
колонки/миграции). Ручные render-эндпоинты в `ApiController.kt` этот ключ в
`context` не передают. Пост-хук в `KaraokeProcessThread.run()` теперь дополнительно
проверяет `karaokeProcess.args.firstOrNull()?.contains("trigger=approve") == true`
и публикует в Telegram **только** если это true.

## Инварианты / правила

1. **Никаких новых миграций, DTO, recordhash-триггеров, эндпоинтов.**
   Фича — аддитивные вставки в двух существующих методах. Это закреплено
   A-002 в [`spec.md`](../../specs/131-fix-approve-demo-render-telegram-sync/spec.md)
   и Principles II, III, IV в [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md).

2. **Идемпотентность в 4 уровня:**
   - L1 — `triggerRenderMp4DemoIfNeeded` SELECT-гард: один процесс на
     песню в `WAITING`/`WORKING`. Допускается несколько `DONE`/`ERROR`
     (например, после падения рендера + повторного ручного триггера).
   - L2 — `TelegramAutoPublishService.publishToTelegram` early-return
     `PUBLISHED` если `song.idTelegramDemo.isNotEmpty()`.
   - L3 — `SongReleaseAnnouncementService.detectAndAnnounceAvailability`:
     `wasAvailableBefore=true` — дешёвый fast-path (не грузить `Song`), но
     НЕ единственный гейт (**фикс specs/152-fix-false-collection-news**,
     2026-08-05 — до этого целевая БД, впервые узнавшая давно истинный
     флаг `newsAvailableAnnounced` через backfill/отложенный push, ложно
     трактовала это как новое событие и создавала лишнюю новость «в
     коллекции», в т.ч. уже после того как для той же песни вышла новость
     «в эфире»). Содержательная идемпотентность теперь — два гейта против
     `tbl_news` через `News.existsAnnouncement`: уже есть `category="premium"`
     по этой песне ⇒ не дублировать; уже есть `category="air"` ⇒ песня
     точно не новая (on-air подразумевает давнюю доступность), новость «в
     коллекции» не создаётся. См.
     [contracts/collection-news-trigger.md](../../specs/152-fix-false-collection-news/contracts/collection-news-trigger.md).
   - L4 — `updateDatabases` diff по `recordhash`-триггерам; неизменённые
     записи не пушатся.

3. **Approve НИКОГДА не откатывается из-за сбоев новых шагов.** Каждый
   новый блок обёрнут в свой `try { ... } catch (_: Exception) { println(...) }`,
   и `aRead.save()` (последняя запись approve) выполняется независимо.

4. **HTTP-ответ approve укладывается в ≤5 с** (SC-003):
   - `createProcess` — лёгкая запись в `tbl_processes` (единицы мс).
   - Sync-related и публикация Telegram — `thread { ... }`.
   - Самый «тяжёлый» sync-related блок может идти десятки секунд на
     больших объёмах (R-1 в research.md), но он не блокирует HTTP-поток.

5. **Только AIR-канал.** `publicationType = PublicationType.AIR`,
   `persistMessageId = true`. PREMIUM-цикл (`newsPremiumPublishPending`,
   `PremiumAutoPublishScheduler`, specs/122) **не затрагивается** этой
   фичей.

6. **`telegramAutoPublishEnabled=false` НЕ блокирует рендер и sync.** Гард
   находится **внутри** `TelegramAutoPublishService.publishToTelegram`
   (ранний `return SCHEDULED`, если выключено). Approve безусловно
   создаёт процесс; ДЕМО-MP4 рендерится; sync идёт; в Telegram поста
   нет. См. S-008 в [quickstart.md](../../specs/131-fix-approve-demo-render-telegram-sync/quickstart.md)
   и FR-012 в [spec.md](../../specs/131-fix-approve-demo-render-telegram-sync/spec.md).

7. **`Karaoke.allowUpdateRemote=false` НЕ блокирует новые шаги.** Блок
   `updateRemoteSongFromLocalDatabase(song.id)` пропускается (existing
   поведение), но `triggerRenderMp4DemoIfNeeded` и sync-related идут
   безусловно — это `LOCAL→LOCAL` или `LOCAL→SERVER` операции,
   не требующие флага.

8. **Helper и пост-хук — добавление, не правка.** Все existing-блоки
   `approve()` (markers, `saveToDb`, `updateRemoteSong`, `aRead.save()`)
   остаются без изменений; ни одной строки не удалено и не переставлено.

## Известные ловушки

| # | Ловушка | Что делать |
|---|---|---|
| P-1 | `KaraokeProcess.createProcess(...)` имеет `threadId` **без дефолта** — передавать обязательно. Опечатка → Type inference error. | Всегда: `threadId = 0` (HEAVY_RENDER). |
| P-2 | SELECT-гард по `tbl_processes` использует `song_id = ${song.id}` (интерполяция). `song.id` приходит из уже загруженной через `Song.loadFromDbById` сущности — гарантированно `Long`, защиты от SQL-инъекции не нужно. | Не подставлять пользовательский ввод напрямую. |
| P-3 | `song.idTelegramDemo` — `String` (не Long). Проверка `.isNotEmpty()`, не `!= null`. | Использовать `.isNotEmpty()` / `.isNullOrEmpty()` соответственно. |
| P-4 | В пост-хуке `KaraokeProcessThread.run()` переподнимаем `Song.loadFromDbById` заново — `karaokeProcess.songId` ссылается на «свежую» строку `tbl_songs` после `WORKING→DONE` (стемы и флаги уже актуальны). | Не пробрасывать `Song`-объект через post-hook (он жил в worker-потоке с ThreadLocal-соединением, которое закрывается). |
| P-5 | `redirectErrorStream(true)` обязательно для `ProcessBuilder` в любых subprocess (см. CONTRIBUTING.md, README раздел «ProcessBuilder»). Наш код использует существующий `KaraokeProcessThread.run()` — там уже OK, мы в нём ничего не запускаем. | Не добавлять новых `ProcessBuilder` в этой фиче. |
| P-6 | `WORKING_DATABASE` — глобал; `karaoke-db` ADmin-машина. На проде его нет; эта фича выполняется ТОЛЬКО на admin-машине в `karaoke-app`. Публичный flow (`karaoke-web`/`karaoke-public`) фичу не запускает — это и не требуется. | Если когда-нибудь понадобится «approve с прод-входом» — это уже отдельная спека. |
| P-7 | Error-ветка `KaraokeProcessThread.run()` (после `catch (e: Exception)` в subprocess, статус=ERROR) **не** запускает публикацию. Это intentional: рендер упал — Telegram-пост не выйдет. Telegram-пост можно опубликовать позже через ручной триггер «Опубликовать в Telegram сейчас» после фикса стема и перерендера. | Не добавлять «аварийный» post-hook на ERROR. |
| P-8 | Sync-related в `thread { ... }` может выполняться десятки секунд. Если процесс `karaoke-app` рестартует в этом окне — синк не возобновится автоматически (нет механизма retry). Админу достаточно нажать «Обновить на сервере» (`POST /utils/updateremotedatabasefromlocaldatabase`). | Документировать в PR-описании. |
| P-9 | **`KaraokeProcess.createProcess(..., doWait=false, ...)` НЕ делает «неблокирующий вызов» — это «создать zombie-процесс с `process_status='CREATING'`, который НИКОГДА не будет подобран воркером**.** Параметр `doWait` управляет **начальным статусом** записи в `tbl_processes`: `WAITING` или `CREATING`. Воркер `KaraokeProcessWorker.getProcessesToStart` фильтрует SQL строго `WHERE process_status='WAITING'` (`KaraokeProcess.kt:806`); никакого scheduler'а/пост-хука, который флипал бы `CREATING → WAITING`, в кодовой базе нет (проверено). Поэтому `doWait=false` в любых render-задачах — это просто «вечная запись в БД, которую никто не подберёт». Безопасно **только** в сценариях, где кто-то по другому каналу (scheduler, пост-хук, ручной триггер) потом переводит процесс в `WAITING` — но таких мест сейчас нет. **Правило: все render-задачи должны создаваться с `doWait=true`.** В нашем хелпере `triggerRenderMp4DemoIfNeeded` это было исправлено в Pass 39 (ранее `doWait=false` — zombie). | Аудит всех 26 `KaraokeProcess.createProcess` в `karaoke-app`: 23 уже используют `doWait=true` (HealthReport.kt, Song.kt, ApiController.kt), 3 были `doWait=false` (наш хелпер, TelegramAutoPublishService.startRenderAndReturn, VkAutoPublishService.startRenderAndReturn) — все 3 поправлены в Pass 39. |
| P-10 | Шаблон Telegram-публикации после approve должен быть «В коллекции» (PREMIUM), не «В эфире» (AIR). Approve выставляет `song.newsPremiumPublishPending=true` через `Song.markNewsAvailableIfReady` (см. `Song.kt:5113-5131`). Жёстко зашитый `publishToTelegram(..., PublicationType.AIR, persistMessageId=true)` публикует с AIR-шаблоном **И** заполняет `idTelegramDemo`, что ломает последующий AIR-цикл по `dateTimePublish`. Использовать `TelegramAutoPublishService.onRenderCompleted(success=true, error=null)` — он сам разруливает `effectivePublicationType`/`effectivePersistMessageId` по `song.newsPremiumPublishPending` (см. `TelegramAutoPublishService.kt:169-172`). Это канонический entry-point, уже используется в `PremiumAutoPublishScheduler.resumeRenderingSong`. | В нашем пост-хуке `KaraokeProcessWorker.KaraokeProcessThread.run()` была исправлено в Pass 39. |

## Ссылки

### Контракты и спецификации

- [spec.md](../../specs/184-approve-status-choice/spec.md) — спецификация фичи 184 «Выбор статуса песни при апруве задания (5 или 6)» (12 FR, 3 US, 6 SC).
- [plan.md](../../specs/184-approve-status-choice/plan.md) — Implementation Plan фичи 184 (Constitution Check 8/8 ✅).
- [research.md](../../specs/184-approve-status-choice/research.md) — Phase 0 research фичи 184 (8 решений D-1..D-8).
- [contracts/](../../specs/184-approve-status-choice/contracts/) — дельты контрактов `/approve` и `/byId` для feature 184.
- [spec.md](../../specs/131-fix-approve-demo-render-telegram-sync/spec.md) — спецификация фичи 131 (14 FR, 3 US, 6 SC).
- [plan.md](../../specs/131-fix-approve-demo-render-telegram-sync/plan.md) — Implementation Plan (Constitution Check passed, 10/10).
- [research.md](../../specs/131-fix-approve-demo-render-telegram-sync/research.md) — Phase 0 research, решения D-1..D-6, риски R-1..R-5.
- [data-model.md](../../specs/131-fix-approve-demo-render-telegram-sync/data-model.md) — Phase 1 data model (no schema changes).
- [contracts/pipeline.md](../../specs/131-fix-approve-demo-render-telegram-sync/contracts/pipeline.md) — внутренний контракт оркестрации, идемпотентность, матрица изоляции сбоев.
- [quickstart.md](../../specs/131-fix-approve-demo-render-telegram-sync/quickstart.md) — ручные сценарии S-001..S-009.
- [tasks.md](../../specs/131-fix-approve-demo-render-telegram-sync/tasks.md) — Phase 2 tasks (17 задач).

### Связанные документы проекта

- [async-process-queue.md](./async-process-queue.md) — `KaraokeProcess*` (создание/очередь/пост-хук); здесь — наш post-hook живёт ровно в этом файле.
- [telegram-auto-publish.md](./telegram-auto-publish.md) — `TelegramAutoPublishService.publishToTelegram` (то, что вызываем из пост-хука).
- [dual-db-sync.md](./dual-db-sync.md) — `updateRemoteDatabaseFromLocalDatabase` (sync related).
- [mp4-render.md](./mp4-render.md) — DEMO-MP4 рендер (1280×720@30fps, JPEG-секвенция + mux).
- [sse-notifications.md](./sse-notifications.md) — клиенты `/processes`, `/news` получают обновления без правок.
- [premium-auto-publish section в telegram-auto-publish.md](./telegram-auto-publish.md) — PREMIUM-цикл (НЕ затрагивается этой фичей).

### Файлы кода (только аддитивные вставки)

- [karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/SongEditorController.kt)
  — `approve()` + new helper `triggerRenderMp4DemoIfNeeded(song)`.
- [karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProcessWorker.kt)
  — `KaraokeProcessThread.run()` + new post-hook after HealthReport.

### Governance

- [AGENTS.md](../../AGENTS.md) — общие правила opencode, CI-gate для master, KDoc 100%, per-feature docs (FR-009).
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md) — Core Principles (II — нет миграций, III — `SyncRegistry`, VI — код-стандарты, IX — async).
- [CONTRIBUTING.md](../../CONTRIBUTING.md) — стиль кода (Kotlin), KDoc, logging-стиль `[approve/timing]`, `[approve/render-demo]`, `[approve/sync-related]`, `[render-demo/post-hook]`.

### История

- Pass 34 (2026-08-04) — фича 131, этот документ создан.

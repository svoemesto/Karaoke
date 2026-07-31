# Implementation Plan: Автопубликация демо-версий песен в Telegram-канал по расписанию

**Branch**: `113-telegram-demo-publish` | **Date**: 2026-07-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/113-telegram-demo-publish/spec.md`

## Summary

Сегодня жизненный цикл «опубликовать песню в Telegram-канале» требует
ручного труда администратора: завести `Publication`, открыть UI
Telegram, написать пост с текстом + ссылкой, выставить дату эфира.
Сегодняшний бот (`TelegramUpdatesConsumer`, Фаза 1) только **ловит**
уже опубликованный пост и привязывает `message_id` к песне — сам
он ничего не публикует. Эта фича добавляет **Фазу 2**: бот сам
оформляет и публикует демо-версию песни в Telegram-канал
непосредственно по наступлению её собственной даты/времени
публикации (`Settings.date` + `Settings.time`, существующие поля
песни `Song.dateTimePublish`), плюс — кнопка «Опубликовать сейчас»
в `webvue3` для принудительного запуска того же пути (например,
для исправления «опоздавших» публикаций, FR-015).

Ключевые решения:
1. **Триггер — по-песенный** (а не через сущность `Publication`): уже
   существующие поля `tbl_settings.date`/`tbl_settings.time` —
   источник истины; бот рассматривает каждую песню индивидуально
   в плановом тике (каждые 5–10 минут, см. Q1 spec.md).
2. **Демо-MP4 переиспользуется** без проверки «свежести»
   относительно содержимого песни (Q2 spec.md, FR-003 сценарий 1) —
   ответственность за перерендер после правок лежит на
   администраторе. Если файла нет — ставится `KaraokeProcess`
   задача `RENDER_MP4_DEMO` и публикация происходит по её
   завершении. Если файл превышает 50 МБ (лимит Telegram Bot API) —
   перерендер с уменьшенными параметрами (FR-003 сценарий 3 +
   FR-004).
3. **Ретрай с экспоненциальным backoff** на сбой `sendVideo`
   (Q3 spec.md, FR-010): до 3 попыток с интервалами
   30 сек → 2 мин → 5 мин, затем явный статус «ошибка отправки»;
   прокси-fallback из существующего `TelegramApiClient`
   переиспользуется на каждой попытке.
4. **Состояние публикации на уровне песни** (Q4 spec.md, FR-012)
   расширено до 6 значений: «запланирована» / «рендерится» /
   «публикуется» / «опубликована» / «ошибка отправки» /
   «отменена». Хранится как ключи внутри уже существующего
   текстового JSON-поля `player_readiness_flags` (паттерн
   `specs/101-song-news-flag` — без новой колонки, без правки
   recordhash-формулы).
5. **Кнопка «Опубликовать сейчас»** в `webvue3` (Q5 spec.md,
   FR-015) доступна только для песен с пустым `idTelegramDemo`
   (FR-016) и триггерит тот же путь, что и наступление
   даты/времени.
6. **Идемпотентность** (FR-007/FR-008) — `idTelegramDemo != ""`
   означает «уже опубликовано», бот пропускает публикацию и не
   трогает существующее значение. Прошлая дата/время
   (Q1 spec.md, FR-001 уточнение) — «опоздавшая» публикация,
   бот не публикует, пока администратор явно не переставит
   дату/время на будущее или не нажмёт «Опубликовать сейчас».

Фаза 1 (`TelegramUpdatesConsumer` + парсинг ручных постов) полностью
сохраняется без изменений (FR-009) — это страховочный
сценарий для случаев, когда администратор всё-таки публикует
вручную или Фаза 2 ещё не сработала.

## Technical Context

**Language/Version**: Kotlin 2.x / JDK 17 (существующий стек
`karaoke-app`/`karaoke-web`, Spring Boot).

**Primary Dependencies**:
- Spring `@Scheduled` (уже используется —
  `SongReleaseAnnouncementScheduler`, `StatsCacheScheduler`).
- `KaraokeConnection` / `KaraokeDbTable` (сырой JDBC, без
  JPA/Hibernate — Constitution Principle II).
- `KaraokeProcess*` (async-очередь задач с парсингом stdout,
  Constitution Principle IV) — `KaraokeProcessTypes.RENDER_MP4_DEMO`
  переиспользуется без изменений.
- `TelegramApiClient` (уже реализован) — расширяется методом
  `sendVideo` (загрузка файла + отправка в канал) с
  прокси-fallback'ом и retry-обёрткой.
- `KaraokeProperties` (через `getString`/`getBoolean`/`getLong`)
  — добавляется 4 новых ключа (`telegramAutoPublishEnabled`,
  `telegramAutoPublishChannelId`,
  `telegramAutoPublishWindowMinutes`,
  `telegramAutoPublishMaxFileSizeMb`).

**Storage**: PostgreSQL, сырой JDBC.
- **Схема `tbl_songs`/`tbl_settings` не меняется** — новые
  ключи живут внутри уже существующего текстового JSON-поля
  `player_readiness_flags` (паттерн `specs/101-song-news-flag`):
  recordhash-формула не трогается, миграция схемы не нужна.
- **Схема `tbl_news` не используется** — фича не создаёт
  новостей, только Telegram-посты.
- **`tbl_publications` не используется** (Out of Scope) —
  расписание живёт на уровне самой песни.

**Testing**: В CI юнит/интеграционных тестов для этого модуля нет
(Constitution, «Рабочий процесс» → «Тесты»); проверка — вручную
на prod-like окружении по `quickstart.md`. Существующие
`@Disabled`-тесты в `karaoke-app/src/test` не покрывают ни Фазу 1,
ни Фазу 2.

**Target Platform**:
- `karaoke-app` (admin-машина, **не разворачивается на PROD**,
  Constitution Principle I) — здесь живёт
  `TelegramAutoPublishScheduler` (фон-поток, как и
  `TelegramUpdatesConsumer`/`SongReleaseAnnouncementScheduler`),
  `TelegramAutoPublishService` (бизнес-логика), endpoint
  `/api/song/publishToTelegramNow` для кнопки «Опубликовать
  сейчас» (вызывается из `webvue3`).
- `webvue3` (admin SPA) — кнопка «Опубликовать сейчас» в
  карточке песни, индикатор состояния публикации в списке
  песен (новые значения «рендерится» / «публикуется» /
  «ошибка отправки» — `form-select` стиль и `badge` уже
  используются в `NewsTable.vue` для статусов новостей,
  переиспользуем).

**Project Type**: точечное расширение существующего backend +
frontend. Без нового модуля, без новой таблицы.

**Performance Goals**:
- Плановая проверка бота — раз в 5–10 минут (настраивается,
  `telegramAutoPublishWindowMinutes`).
- На каждый тик: первая фаза — дешёвый `SELECT id, date, time,
  id_status, ... FROM tbl_songs WHERE date IS NOT NULL AND time
  IS NOT NULL AND id_telegram_demo = ''` (без `loadListFromDb`,
  без base64/маркеров) с фильтром по `dateTimePublish` в
  скользящем окне `[now - window, now]` (Q1 spec.md: «5–10
  минут»). Полная загрузка `Song` — только для кандидатов,
  попавших в окно (обычно 0–5 строк за тик).
- Запись `message_id` — через штатный `Song.saveToDb()`, не
  raw SQL → автоматически попадает в SSE-уведомление
  админки и recordhash-diff LOCAL↔SERVER.
- Telegram rate limit (per-chat ≤20 сообщений/мин, глобально
  ~30/сек) — покрывается ретраями FR-010 и скользящим окном
  тика (5–10 мин, обычно 0–5 публикаций за тик; в худшем
  случае пакетная публикация упирается в ретраи).

**Constraints**:
- Только сырой JDBC, без JPA/Hibernate (Principle II).
- `KaraokeProcess*` для рендера демо-MP4 (Principle IV) —
  `redirectErrorStream(true)` обязателен (CONTRIBUTING.md).
- Любое изменение набора синхронизируемых полей обязано
  сохранять корректность recordhash — именно поэтому новое
  состояние публикации размещается внутри уже участвующего
  в формуле поля `player_readiness_flags` (паттерн
  `specs/101-song-news-flag`, не новый прецедент).
- `karaoke-app` не разворачивается на PROD (Principle I) →
  логика автопубликации, которая должна работать на проде
  (плановый тик), ОБЯЗАНА жить в `karaoke-app` и быть
  активирована через deploy на admin-машине (т.е. бот
  работает на admin-машине, а не на проде). Это согласуется
  с Фазой 1 (`TelegramUpdatesConsumer` уже работает на
  `karaoke-app`).
- Деплой и прямые DDL/DML на PROD — только по прямому
  согласию пользователя, на каждое действие отдельно (см.
  «Ограничения и доступы агента» в Constitution). Бэкфилл
  не нужен (новое состояние изначально отсутствует →
  читается как «запланирована» / «рендерится» в зависимости
  от того, заполнена ли дата/время, и так было бы).
- Per-feature документ `docs/features/telegram-auto-publish.md`
  ОБЯЗАН быть обновлён в этом же PR (FR-009 spec.md, см.
  Constitution Principle VI) — фича добавляет Фазу 2 к
  существующему документу.

**Scale/Scope**: 18k+ песен на проде (Constitution). Изменяемых
файлов — около 6 (см. Project Structure) плюс обновление
`docs/features/telegram-auto-publish.md`. Никаких новых таблиц
или сущностей.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1
design.*

| Principle | Статус | Комментарий |
|---|---|---|
| I. Self-contained автопайплайн | ✅ PASS | Telegram Bot API уже используется в Фазе 1 (с одобрения пользователя, см. `docs/features/telegram-auto-publish.md`). Фаза 2 не вводит новых внешних SaaS в горячем пути обработки медиа — только переиспользует уже одобренный канал. |
| II. Сырой JDBC + diff по хэшам | ✅ PASS | Новое состояние публикации размещено внутри уже хэшируемого поля `player_readiness_flags` — recordhash-формула не меняется, миграция схемы не нужна. Запись `message_id` идёт через штатный `Song.saveToDb()` с диффом и SSE-уведомлением, не через raw SQL. Сравнение кандидатов на тике — `associateBy`/`Map` lookup (O(n) в пределах кандидатов, не по всему каталогу), не вложенные `.any`/`.none`. |
| III. Двух-БД синхронизация через SyncRegistry | ✅ PASS | Состояние публикации — обычное поле `Song`, участвующее в штатной синхронизации (никакого нового исключения). `Settings.idTelegramDemo` — уже синхронизируемое поле, не новый прецедент. |
| IV. Async-очередь с парсингом stdout | ✅ PASS | Рендер демо-MP4 идёт через `KaraokeProcess` с типом `RENDER_MP4_DEMO` (уже существующий), `redirectErrorStream(true)` обязателен. Плановая проверка — `@Scheduled` с коротким интервалом (как `SongReleaseAnnouncementScheduler`). |
| V. Два фронтенда — разные приложения | ⚠️ К выполнению | `webvue3` — кнопка «Опубликовать сейчас» в карточке песни + отображение нового состояния публикации. `karaoke-public` — **никаких изменений** (функциональность не касается публичной стороны; читатели видят только итоговый Telegram-пост). |
| VI. Code Standards (KDoc/lint/per-feature-doc) | ⚠️ К выполнению | Новый/изменённый код должен получить KDoc с `@see docs/features/telegram-auto-publish.md`; сам документ должен быть обновлён в этом же PR (FR-009 spec.md, Constitution Principle VI) — фича добавляет Фазу 2. Линтеры: `ktlintCheck` (Kotlin), `npm run lint:check` (`webvue3`). |
| VII. Cross-Machine Setup | N/A | Фича не касается локальных AI-конфигов/line-endings. |
| Ограничения и доступы агента | ⚠️ Требует согласия пользователя | Деплой `karaoke-app` на admin-машине и активация `telegramAutoPublishEnabled=true` — по прямому согласию пользователя. Прямые изменения на PROD — запрещены агенту. |

**Вывод**: Gate пройден. Никаких нарушений. Единственная
особенность (не нарушение) — выбор JSON-блоба `player_readiness_flags`
для хранения состояния публикации вместо новой колонки; это
явно санкционировано в Constitution Principle II/III и уже
использовано в `specs/101-song-news-flag` для аналогичной задачи
(новый per-song булев флаг без миграции).

## Project Structure

### Documentation (this feature)

```text
specs/113-telegram-demo-publish/
├── plan.md              # Этот файл
├── research.md          # Phase 0 — решения и их обоснование
├── data-model.md        # Phase 1 — сущности и переходы состояний
├── quickstart.md        # Phase 1 — сценарии ручной проверки
└── contracts/
    └── telegram-auto-publish.md  # Phase 1 — контракты 3 точек (scheduler/button/Telegram API)
```

### Source Code (repository root)

```text
docs/features/
└── telegram-auto-publish.md   # ОБНОВЛЯЕТСЯ в этом PR — добавление Фазы 2

karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/
├── model/
│   ├── Song.kt                          # + telegramAutoPublishState / LastAttemptAt /
│   │                                    #   LastError внутри player_readiness_flags;
│   │                                    #   + property idTelegramDemo без изменений
│   │                                    #   (уже есть), но добавляется обёртка для проверки
│   │                                    #   "publishDateTime < now()" → "опоздавшая"
│   └── SongField.kt                     # БЕЗ изменений (новые ключи в player_readiness_flags
│                                         #   — не SongField, а просто ключи JSON-блоба)
├── services/
│   ├── TelegramApiClient.kt             # + sendVideo(channelId, file, caption)
│   │                                    #   с retry-обёрткой (3 попытки, 30с/2м/5м backoff)
│   │                                    #   и прокси-fallback'ом (уже есть)
│   ├── TelegramAutoPublishScheduler.kt  # НОВЫЙ: @Scheduled тик каждые
│   │                                    #   telegramAutoPublishWindowMinutes минут;
│   │                                    #   фаза 1 — дешёвый SELECT кандидатов;
│   │                                    #   фаза 2 — render-or-use по FR-003
│   ├── TelegramAutoPublishService.kt    # НОВЫЙ: бизнес-логика (рендер/публикация/
│   │                                    #   запись message_id), переиспользует
│   │                                    #   KaraokeProcess (RENDER_MP4_DEMO) и
│   │                                    #   TelegramApiClient.sendVideo
│   └── TelegramUpdatesConsumer.kt       # БЕЗ изменений (Фаза 1 сохранена, FR-009)
├── controllers/
│   ├── ApiController.kt                 # + POST /api/song/publishToTelegramNow?songId=...
│   │                                    #   (FR-015/FR-016) — вызывает тот же путь, что и
│   │                                    #   scheduler; возвращает success/error и текущий state
│   └── ...                              # (другие контроллеры без изменений)
└── scheduler/
    └── TelegramAutoPublishSchedulerStarter.kt  # НОВЫЙ: @EventListener(ApplicationReadyEvent)
                                                 #   по образцу TelegramUpdatesConsumerStarter
                                                 #   (стартует scheduler только если
                                                 #   telegramAutoPublishEnabled=true)

webvue3/src/components/Songs/edit/
└── SongEdit.vue                        # + кнопка "Опубликовать сейчас" (FR-015) —
                                          #   видна только если idTelegramDemo == ''
                                          #   (FR-016); вызывает
                                          #   /api/song/publishToTelegramNow

webvue3/src/components/Songs/
├── store.js                             # + getter telegramAutoPublishState для текущей
│                                          #   песни; + state.currentPage и т.п. — без
│                                          #   изменений (state публикации живёт в Song, не
│                                          #   в Vuex)
└── (другие компоненты)                  # БЕЗ изменений — статус публикации показывается
                                          #   в существующих полях карточки песни
                                          #   (через SSE-обновление settings)
```

**Structure Decision**:
- Никакого нового модуля Gradle, никакой новой таблицы.
- Состояние публикации — JSON-ключи в `player_readiness_flags`
  (паттерн `specs/101-song-news-flag`).
- `TelegramAutoPublishService` концентрирует всю бизнес-логику
  (рендер → публикация → сохранение `message_id`); вызывающий
  код (`TelegramAutoPublishScheduler` + `/api/song/publishToTelegramNow`)
  только запускает её.
- `TelegramApiClient.sendVideo` — расширение существующего
  клиента, не новый класс (минимальное изменение поверх уже
  работающей прокси-fallback-логики).
- Frontend — минимально: одна кнопка + одно условие её
  видимости в `SongEdit.vue`; никакой новой вкладки или
  страницы.

## Complexity Tracking

> Заполняется только при нарушениях Constitution Check, требующих
> обоснования. На данный момент — **нарушений нет** (см. Constitution
> Check), таблица пуста.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|---------------------------------------|
| (нет)     | —          | —                                     |

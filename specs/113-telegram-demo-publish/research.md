# Research: Автопубликация демо-версий песен в Telegram-канал по расписанию

> Phase 0 — решения, обоснования и рассмотренные альтернативы.
> Никаких NEEDS CLARIFICATION на этом этапе: спецификация (spec.md)
> завершена, 5 уточнений сняты в `/speckit.clarify`.

## 1. Где хранить состояние публикации (`telegramAutoPublishState`, `LastAttemptAt`, `LastError`)

**Decision**: Все три новых per-song поля хранятся как **ключи внутри
уже существующего текстового JSON-поля `player_readiness_flags`**
(`Song.playerReadinessFlagsMap`, `readinessFlag()` / `setReadinessFlag()` —
те же приватные хелперы, что уже используют `stemAccompanimentReady`,
`stemVocalReady`, `pictureAlbumReady`, `pictureAuthorReady` и
`newsAvailableAnnounced` из `specs/101-song-news-flag`).

Структура ключей:

```json
{
  "telegramAutoPublishState": "scheduled",
  "telegramAutoPublishLastAttemptAt": "2026-07-31T17:30:00",
  "telegramAutoPublishLastError": ""
}
```

Возможные значения `telegramAutoPublishState`:
`"scheduled"` / `"rendering"` / `"publishing"` / `"published"` /
`"send_failed"` / `"cancelled"` (соответствуют FR-012, внутренние
строки — на усмотрение реализации; админка показывает
локализованные лейблы).

**Rationale**:
- `deploy/karaoke-db/26_player_readiness_flags.sql` явно
  документирует, что это поле стало единым JSON-блобом именно
  для того, чтобы новые per-song флаги добавлялись **без новой
  миграции, без новой колонки и без правки md5-формулы
  recordhash-триггера** — и явно предупреждает, что предыдущая
  версия (отдельные boolean-колонки) требовала синхронной правки
  **двух** md5-формул (`tbl_songs` / `tbl_songs_sync`) и
  массового `UPDATE recordhash` по всем существующим строкам при
  каждом новом флаге, что было источником рассинхрона LOCAL/PROD.
- `specs/101-song-news-flag` уже успешно использовал этот же
  паттерн для `newsAvailableAnnounced` — прецедент не новый,
  риск воспроизведения миграционного бага сводится к нулю.
- Состояние `telegramAutoPublishState` само по себе не влияет
  на «публичную видимость» песни (в отличие от
  `newsAvailableAnnounced`) — это служебное состояние
  автопубликации. Поэтому нельзя оправдать введение новой
  колонки даже «для производительности» или «для семантической
  чистоты»: JSON-блоб уже синхронизируется и уже парсится в
  памяти один раз при `Song.loadListFromDb`; добавление ещё
  одного ключа — O(1) по памяти и времени.

**Alternatives considered**:
- **Новая колонка `telegram_auto_publish_state` на `tbl_settings`**
  — отклонено: требует правки обеих md5-формул
  recordhash-триггера (LOCAL + PROD) + миграция
  `ALTER TABLE` на обеих БД, что явно устраняет основное
  преимущество JSON-блоба. Семантически состояние не
  отделимо от уже существующих per-song readiness-флагов.
- **Отдельная таблица `tbl_telegram_publish_attempts` (журнал
  попыток)** — отклонено: для текущей версии спеки достаточно
  хранить только «последнюю попытку» (`LastAttemptAt`,
  `LastError`); полный журнал попыток — это не задача
  текущей фичи. Если в будущем понадобится — это отдельная
  фича.
- **Хранить состояние только в памяти `karaoke-app`** — отклонено:
  противоречит FR-013 (переживает перезапуск) и SC-006
  (администратор должен видеть состояние в админке после
  рестарта `karaoke-app`).

## 2. Какой API Telegram использовать для отправки демо-MP4

**Decision**: `sendVideo` через `TelegramApiClient`. Для
больших файлов (>50 МБ, локальный Bot API сервер) — сначала
`getFile` для получения `file_id` уже загруженного файла (если
применимо) или загрузка через `InputFile` (multipart).

```kotlin
// TelegramApiClient.kt — добавление
fun sendVideo(
    channelId: String,
    videoFile: File,
    caption: String,
    maxFileSizeBytes: Long,
    maxAttempts: Int = 3,
    backoffScheduleMs: List<Long> = listOf(30_000, 120_000, 300_000)
): SendVideoResult
```

**Rationale**:
- `sendVideo` — нативный метод Telegram Bot API для отправки
  видео с подписью (caption). Поддерживает файлы до 50 МБ
  (стандартный Bot API) или больше (локальный Bot API сервер,
  лимит задаётся настройкой `telegramAutoPublishMaxFileSizeMb`).
- Подпись (`caption`) — отдельный параметр метода, что
  естественно ложится на FR-005 («подпись до 1024 символов
  вместе с видео»).
- Альтернативы:
  - `sendAnimation` — поддерживает GIF/анимации, не подходит
    для MP4 с водяным знаком.
  - `sendMediaGroup` — для группы файлов, у нас один файл.
  - `sendDocument` — без preview, менее привлекательно в
    канале.

**Alternatives considered**:
- `sendAnimation` / `sendDocument` / `sendMediaGroup` —
  отклонены: менее подходят для случая «одно видео с
  подписью» (`sendVideo` ровно для этого и предназначен).

## 3. Retry-политика для `sendVideo` (FR-010)

**Decision**: Обёртка в `TelegramApiClient.sendVideo`:
до 3 попыток с экспоненциальным backoff (30 сек → 2 мин → 5 мин).
Каждая попытка использует уже существующий прокси-fallback
(метод сам уже пробрасывает `telegramProxyUrl`). После
исчерпания — возврат `SendVideoResult.Failed(reason)`,
вызывающий код переводит состояние в `"send_failed"`.

```kotlin
// Псевдокод retry-цикла внутри sendVideo:
for (attempt in 1..maxAttempts) {
    val result = trySendVideo(...)
    if (result.success) return result
    if (attempt < maxAttempts) delay(backoffScheduleMs[attempt - 1])
}
return SendVideoResult.Failed("retries exhausted: ${lastError}")
```

**Rationale**:
- 3 попытки — баланс между покрытием типичных сбоев (сеть,
  proxy hiccup, per-chat rate limit) и недопущением «вечных»
  ретраев, забивающих `KaraokeProcess*`-очередь.
- Интервалы 30с / 2м / 5м — экспоненциальный рост ×4, что
  хорошо работает для rate-limit'ов Telegram (per-chat ≤20
  сообщений/мин: 5-минутная пауза почти гарантированно
  сбрасывает счётчик).
- Прокси-fallback уже работает в существующем `TelegramApiClient`
  для `getUpdates` (Фаза 1) — переиспользуем, а не
  дублируем.

**Alternatives considered**:
- **Без ретраев** (сразу `"send_failed"`) — отклонено: поймает
  администратора на каждом кратковременном сбое.
- **Ретрай бесконечно** — отклонено: «вечный» ретрай может
  застрять надолго (если Telegram недоступен сутки), забить
  очередь и привести к «застрявшим» состояниям `"publishing"`,
  по которым администратор не понимает, что делать.
- **Ретрай + отложенный повтор на следующий тик** — отклонено:
  усложняет логику scheduler'а (нужно хранить «отложенные»
  попытки отдельно) и плохо сочетается с «окном 5–10 минут»
  из Q1 spec.md (если все ретраи исчерпаны к концу окна,
  откладывать уже некуда).

## 4. Плановая проверка (scheduler): периодичность и окно

**Decision**: `@Scheduled(fixedDelayString = "PT${window}PT${window}M")`
(где `window` = `telegramAutoPublishWindowMinutes`, по умолчанию
5 минут) + ручной запуск из endpoint'а «Опубликовать сейчас»
(для конкретной песни). На каждый тик:

1. **Фаза 1 (дешёвая)**: `SELECT id, date, time, id_status,
   player_readiness_flags, id_telegram_demo, demo_fragment_* FROM
   tbl_settings WHERE date IS NOT NULL AND time IS NOT NULL
   AND id_telegram_demo = ''` — фильтр на стороне БД
   исключает уже опубликованные песни.
2. **В Kotlin**: парсим каждую строку → вычисляем
   `dateTimePublish = parse("dd.MM.yy HH:mm", date + " " + time)`;
   фильтруем `dateTimePublish >= now() - window` И
   `dateTimePublish <= now()` (Q1 spec.md: «5–10 минут»);
   фильтруем `dateTimePublish < now() - some_buffer` →
   «опоздавшая» (Q1 spec.md, FR-001 уточнение).
3. **Фаза 2 (для каждого кандидата)**: вызываем
   `TelegramAutoPublishService.publishToTelegram(song)`, который
   проходит через FR-003 (render-or-use-existing) → FR-010
   (send с retry) → FR-006 (save `idTelegramDemo`).

**Rationale**:
- `fixedDelay` (не `fixedRate`) — гарантирует, что тик не
  «наезжает» на предыдущий, если тот затянулся (например,
  из-за долгого рендера). Это согласуется с `StatsCacheScheduler`
  (тоже `fixedDelay`).
- Дешёвая первая фаза — на 18k+ строках каталога это всё
  равно O(n) `SELECT`, но без `loadListFromDb` (без base64
  картинок, без маркеров), что занимает миллисекунды.
- Скользящее окно «±window вокруг now» — паттерн из
  `specs/101-song-news-flag` (механизм «в эфире»), та же
  логика, тот же смысл: «песня, чья дата эфира наступила
  совсем недавно».

**Alternatives considered**:
- **Cron** (`0/5 * * * *`) — отклонён: `fixedDelay` проще,
  не зависит от системного cron'а, и его легче override'нуть
  на период тестирования (через `KaraokeProperties`).
- **Параллельная обработка кандидатов** (coroutines/thread pool) —
  отклонена: кандидатов обычно 0–5 за тик, параллелизм не
  нужен; плюс параллельная отправка в один канал упрётся
  в тот же per-chat rate limit Telegram и потребует
  дополнительной координации.
- **Подписка на SSE-событие «песня сохранена»** вместо
  периодического сканирования — отклонена: расписание
  «на 12:00» не привязано к моменту сохранения, оно
  привязано к дате/времени. Сохранение может быть за
  неделю до эфира, и подписка ничего полезного не даст.

## 5. Кнопка «Опубликовать сейчас» (FR-015/FR-016)

**Decision**: Новый endpoint в `karaoke-app/ApiController.kt`:

```
POST /api/song/publishToTelegramNow
    @RequestParam songId: Long
    @RequestParam adminKey: String?  // защита от CSRF, как у других admin-endpoint'ов
    → JSON: { success: Boolean, state: String, messageId: String?, error: String? }
```

Логика:
1. Загрузить `Song` по `songId`.
2. Если `idTelegramDemo != ""` → `400` (FR-016).
3. Иначе — вызвать `TelegramAutoPublishService.publishToTelegram(song)`
   (тот же путь, что и scheduler).
4. Вернуть текущее `telegramAutoPublishState` (которое бот
   уже обновил в `player_readiness_flags` через
   `Song.saveToDb()`).

В `webvue3/Songs/edit/SongEdit.vue`: новая кнопка
«Опубликовать сейчас» (точная формулировка — на усмотрение
UI), видна только если `idTelegramDemo == ''`. По клику —
`POST /api/song/publishToTelegramNow?songId=...`; на
`success=true` — перезагрузить карточку (SSE само обновит
поля, но для UX — явный refresh); на `success=false` —
показать тост с `error`.

**Rationale**:
- Endpoint, а не прямой вызов сервиса из UI, — единая точка
  входа: тот же путь используется и scheduler'ом, и кнопкой
  (нет дублирования логики).
- `adminKey` (как у других admin-endpoint'ов) — лёгкая защита
  от случайного клика по чужой ссылке (см. `SecurityConfig.kt`,
  `/api/private/**`).
- Кнопка скрыта при `idTelegramDemo != ''` — защита от
  случайного дубля (FR-008). Двухшаговая перепубликация
  («очистить `idTelegramDemo` → нажать кнопку») делает
  дубль осознанным.

**Alternatives considered**:
- **Прямой вызов `TelegramAutoPublishService` из Vuex action**
  — отклонён: нарушает «всё через `ApiController`» (см. `webvue3`
  использует REST, не прямое подключение к `karaoke-app` JVM).
- **Кнопка без `adminKey` (полагаться на `permitAll()`)** —
  отклонён: `adminKey` — это уже устоявшаяся практика
  (`MainController` использует то же поле для других admin-
  эндпоинтов), не вводим новый паттерн.
- **Кнопка доступна всегда, но с подтверждением** (Q5 option
  C) — отклонён пользователем (выбран option B — кнопка
  скрыта для уже опубликованных).

## 6. Обработка превышения 50 МБ (FR-003 сценарий 3 + FR-004)

**Decision**: Перед `sendVideo` бот проверяет
`File.length() > telegramAutoPublishMaxFileSizeMb * 1024 * 1024`.
Если превышает — ставит задачу перерендера с уменьшенными
параметрами. Конкретные «уменьшенные параметры» (например,
`height=720` → `height=480`, или `demoFragmentEndSeconds -= 30`)
— на усмотрение реализации, важно лишь, чтобы итоговый файл
вписался в лимит.

**Rationale**:
- Стандартный Telegram Bot API лимит `sendVideo` — 50 МБ.
  Локальный Bot API сервер (если используется) позволяет
  больше — поэтому лимит задаётся настройкой
  `telegramAutoPublishMaxFileSizeMb`, а не хардкодится.
- Проверка `File.length()` перед отправкой — O(1), дешёвая.
- Перерендер через тот же `KaraokeProcess` с типом
  `RENDER_MP4_DEMO`, но с другими параметрами
  (`demoFragmentStartSeconds`, `demoFragmentEndSeconds`,
  или новые параметры рендера, если планирование решит
  ввести `demoResolution`/`demoBitrate`) — переиспользуем
  существующий пайплайн без новых типов задач.

**Alternatives considered**:
- **Попытаться отправить как есть, обработать ошибку
  `Request Entity Too Large` как «превышение лимита»** —
  отклонён: неэффективно (один запрос впустую), и обработка
  ошибки на стороне Telegram ненадёжна (текст ошибки может
  варьироваться).
- **Сжатие существующего файла ffmpeg'ом без перерендера
  плеера** — отклонён: можно потерять качество, и это
  принципиально другая операция (демо-MP4 — это рендер
  плеера, а не просто видеофайл; пересжимать — терять
  синхронизацию с маркерами).

## 7. Где живёт логика: `karaoke-app` или `karaoke-web`

**Decision**: Вся логика Фазы 2 живёт в **`karaoke-app`**:

- `TelegramAutoPublishScheduler` (фон-поток) — здесь.
- `TelegramAutoPublishService` (бизнес-логика) — здесь.
- Endpoint `POST /api/song/publishToTelegramNow` — здесь
  (вызывается из `webvue3`; `webvue3` общается с
  `karaoke-app` через `ApiController.kt`).
- `TelegramApiClient.sendVideo` — здесь (переиспользует
  уже существующий `TelegramApiClient` в `karaoke-app`).

**Rationale**:
- Constitution Principle I: `karaoke-app` не разворачивается
  на PROD — он работает на admin-машине. Это согласуется с
  Фазой 1 (`TelegramUpdatesConsumer` тоже в `karaoke-app`):
  вся Telegram-логика сосредоточена в одном месте, на
  одной машине, с одним набором credentials.
- `karaoke-web` на PROD (Германия) не имеет `karaoke-app` в
  зависимостях для production-использования (только как
  Gradle-модуль для переиспользования моделей; см. DEVELOPMENT.md,
  раздел «Modules / layout»). Добавлять туда
  Telegram-публикацию = дублировать логику в двух местах
  с разными deploy-циклами.

**Alternatives considered**:
- **`karaoke-web` для scheduler'а, `karaoke-app` для endpoint'а** —
  отклонён: рассогласование deploy-циклов и набора
  credentials. Плюс логика публикации (рендер →
  `sendVideo` → save) атомарна — её неестественно
  разрывать.
- **`karaoke-web` для всего** — отклонён: требует тянуть
  `TelegramApiClient` (с прокси-VLESS) в прод-контейнер,
  что нежелательно (принцип «прод-минимализма»: на проде
  только то, что обслуживает пользователей).

## 8. Per-feature документ

**Decision**: обновляется `docs/features/telegram-auto-publish.md`
в этом же PR — добавляется секция «Фаза 2 — автопубликация»
(с тем же форматом, что уже есть для Фазы 1: «Как работает»,
«Инварианты / правила», «Известные ловушки», «Ссылки на ключевые
классы/файлы»). В KDoc новых/изменённых классов — `@see
docs/features/telegram-auto-publish.md`.

**Rationale**: Constitution Principle VI (FR-009) + проектное
правило `AGENTS.md` (см. таблицу 9 ключевых подсистем):
изменение кода фичи = обновление per-feature документа в том
же PR.

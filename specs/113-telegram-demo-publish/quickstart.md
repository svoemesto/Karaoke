# Quickstart: проверка автопубликации демо-версий песен в Telegram-канал

Ручная проверка (в CI тестов для этого модуля нет — см.
Constitution, «Рабочий процесс» → «Тесты»; существующие
`@Disabled`-тесты в `karaoke-app/src/test` не покрывают Фазу 1
или Фазу 2 автопубликации). Выполняется на prod-like окружении
(LOCAL docker-стек или админская машина, по согласию пользователя
на каждое действие).

## Предпосылки

- Собран и перезапущен `karaoke-app` с изменениями этой фичи
  на **admin-машине** (не на проде — Constitution Principle I).
- В `Karaoke.properties` (`/sm-karaoke/system/Karaoke.properties`)
  установлены:
  - `telegramBotToken` (тот же, что для Фазы 1, уже был);
  - `telegramAutoPublishEnabled=true`;
  - `telegramAutoPublishChannelId=@svoemesto` (или
    числовой ID канала, в который должны приходить демо);
  - `telegramAutoPublishWindowMinutes=5` (для отладки
    удобно уменьшить до 1; для прод-режима — 5–10);
  - `telegramAutoPublishMaxFileSizeMb=50`.
- Telegram-канал, указанный в `telegramAutoPublishChannelId`,
  существует и бот `@<bot_username>` (чей токен в
  `telegramBotToken`) добавлен в канал как администратор
  с правом публикации сообщений.
- `deploy/karaoke-db/26_player_readiness_flags.sql` уже применена
  на LOCAL (это требовалось для `specs/101-song-news-flag`,
  миграция давно в проде).
- Per-feature документ `docs/features/telegram-auto-publish.md`
  обновлён (Фаза 2 добавлена).

## Шаг 0 — порядок действий

Все шаги ниже — **ручные**, выполняются на admin-машине (или
в LOCAL docker-стеке) с `karaoke-app`, имеющим доступ к той же
Postgres-БД, что и `webvue3`.

1. Включить `telegramAutoPublishEnabled=true` (через Properties
   UI или прямой правкой файла).
2. Дождаться `ApplicationReadyEvent` → проверить в логах
   `TelegramAutoPublishScheduler: старт` (по аналогии с
   `TelegramUpdatesConsumer: старт`).
3. Прогнать шаги 1–6 ниже.

## Шаг 1 — публикация по расписанию (US1 spec.md, FR-001)

1. В `webvue3` открыть карточку песни, которая:
   - полностью готова (`idStatus == 6`, все флаги готовности
     плеера включены);
   - имеет **непустые** `date` (формат `dd.MM.yy`) и `time`
     (формат `HH:mm`);
   - имеет **пустое** `idTelegramDemo`.
2. Установить `date`/`time` на **5 минут вперёд** от текущего
   момента (например, если сейчас 17:25 — `date` = сегодня,
   `time` = `17:30`).
3. Сохранить песню.
4. Дождаться наступления 17:30 (плюс окно до 17:34 при
   `window=5` минут).
5. Проверить:
   - В Telegram-канале появился пост с демо-MP4 и подписью
     («автор — название (демо)» + ссылка
     `https://sm-karaoke.ru/song?id=<id>`).
   - В `Settings` песни `idTelegramDemo` заполнен
     `message_id` (например, `67890`).
   - В `webvue3` карточка песни обновилась через SSE
     (state="published").
6. Проверить в логах `karaoke-app` строки
   `TelegramAutoPublishService: песня id=<id> → state=published,
   message_id=<id>` (по аналогии с `TelegramUpdatesConsumer`).

**Ожидаемый результат**: пост в канале появился в пределах
окна 5–10 минут после наступления `dateTimePublish`; `idTelegramDemo`
заполнен; state = `published`.

## Шаг 2 — идемпотентность (US1 acceptance scenario 3, FR-007/FR-008)

Повторный триггер на ту же песню:

1. Взять ту же песню, у которой `idTelegramDemo` уже заполнен
   (после Шага 1).
2. Изменить `date`/`time` на новое время через 5 минут
   (или оставить те же — не важно).
3. Сохранить. Дождаться следующего тика плановой проверки
   (5 минут по умолчанию).
4. Проверить: повторного сообщения в Telegram-канале **не**
   появилось; `idTelegramDemo` остался прежним (не изменился);
   в логах — `TelegramAutoPublishService: песня id=<id> →
   skipped (idTelegramDemo != '')`.

**Ожидаемый результат**: ровно одно сообщение в канале,
`idTelegramDemo` неизменён.

## Шаг 3 — прошлая дата/время (Q1 clarify, FR-001 уточнение)

1. Взять полностью готовую песню с пустым `idTelegramDemo`.
2. Установить `date`/`time` на **вчера** (например, сегодня
   2026-07-31, поставить `30.07.26` `12:00`).
3. Сохранить. Дождаться 1+ тика.
4. Проверить: в Telegram-канале **не** появилось сообщения;
   в логах — `TelegramAutoPublishService: песня id=<id> →
   skipped (dateTimePublish < now() — "опоздавшая")`.

**Ожидаемый результат**: 0 публикаций; state остался
`scheduled` (UI может показать «опоздавшая» как
отдельный визуальный маркер, но state не меняется);
администратор может либо переставить дату/время на будущее,
либо нажать «Опубликовать сейчас» (Шаг 6).

## Шаг 4 — рендер по расписанию, когда демо-MP4 ещё нет (US1, FR-003 сц. 2)

1. Взять полностью готовую песню с пустым `idTelegramDemo`,
   для которой ещё **никогда** не рендерился демо-MP4
   (например, новая песня, добавленная в этой сессии).
2. Установить `date`/`time` на **3 минуты вперёд**.
3. Сохранить.
4. Дождаться тика — бот должен увидеть отсутствующий
   `demo.mp4` и поставить задачу `RENDER_MP4_DEMO`
   в `KaraokeProcess*`-очередь.
5. В логах: `TelegramAutoPublishService: песня id=<id> →
   state=rendering, KaraokeProcess queued (type=RENDER_MP4_DEMO,
   processId=...)`.
6. Дождаться завершения рендера (для фрагмента 30–60 секунд
   это занимает ~20–60 секунд, в зависимости от длины).
7. В логах: `KaraokeProcessWorker: задача processId=...
   завершена успешно → onRenderCompleted(songId=<id>, success=true)`.
8. Бот продолжает публикацию: `state=publishing →
   TelegramApiClient.sendVideo(...) → state=published,
   message_id=<id>`.
9. В канале появился пост; в `Settings` `idTelegramDemo`
   заполнен.

**Ожидаемый результат**: пост в канале; `idTelegramDemo` заполнен;
state = `published`. Суммарное время от наступления
`dateTimePublish` до появления поста ≈ `длительность_рендера` +
`длительность_sendVideo`. Допустимо: до 2–3 минут для
типичного фрагмента.

## Шаг 5 — превышение лимита 50 МБ (US1, FR-003 сц. 3 + FR-004)

1. Взять песню, для которой уже отрендеренный `demo.mp4`
   превышает `telegramAutoPublishMaxFileSizeMb` (50 МБ).
   Это можно сделать вручную: `dd if=/dev/zero
   of=/path/to/demo.mp4 bs=1M count=60` (60 МБ файл —
   лимит больше 50 МБ).
2. Установить `date`/`time` на 5 минут вперёд.
3. Сохранить. Дождаться тика.
4. В логах: `TelegramAutoPublishService: песня id=<id> →
   demo.mp4 size=62914560 bytes > max=52428800 → state=rendering
   (re-render with reduced params), KaraokeProcess queued`.
5. Дождаться завершения перерендера.
6. Бот публикует файл (теперь укладывающийся в лимит);
   state = `published`.

**Ожидаемый результат**: пост в канале с **новым**
(уменьшенным) демо-MP4; `idTelegramDemo` заполнен; state =
`published`.

## Шаг 6 — кнопка «Опубликовать сейчас» (US1, FR-015)

1. Взять ту же песню, что в Шаге 3 (с `date`/`time` в
   прошлом, `idTelegramDemo` пусто).
2. В `webvue3` открыть карточку этой песни.
3. Найти кнопку «Опубликовать сейчас» (видима, потому что
   `idTelegramDemo == ''`).
4. Нажать её.
5. В логах: `TelegramAutoPublishService: песня id=<id> →
   manual trigger (FR-015) → state=publishing → ... → state=published`.
6. Проверить: пост в канале появился; `idTelegramDemo` заполнен.

**Ожидаемый результат**: бот проигнорировал условие
«дата/время в прошлом» (потому что триггер — ручной, не
scheduled), успешно опубликовал. State = `published`.

## Шаг 7 — кнопка скрыта для уже опубликованной (FR-016)

1. Взять песню с заполненным `idTelegramDemo` (после любого
   из Шагов 1–6).
2. Открыть карточку в `webvue3`.
3. Найти кнопку «Опубликовать сейчас» — **не должна быть видна**
   (или `disabled`).
4. Если кнопка всё же нажата (например, через прямой `fetch`
   к endpoint'у) — сервер возвращает `400 Bad Request`
   с `error = "Song <id> is already published (...)"`,
   `state = "published"`.

**Ожидаемый результат**: кнопка скрыта/disabled; прямой
запрос к endpoint'у возвращает 400 без побочных эффектов
(нет `sendVideo`, нет `saveToDb()`, нет изменений `idTelegramDemo`).

## Шаг 8 — ручная публикация в Telegram (Фаза 1) не сломалась (US2, FR-009)

1. Вручную (не через бота Фазы 2) опубликовать в Telegram-канале
   пост с корректной ссылкой
   `https://sm-karaoke.ru/song?id=<id>` для какой-нибудь
   **другой** песни (с пустым `idTelegramDemo`, `date`/`time`
   в прошлом или вообще пустыми).
2. Подождать, пока `TelegramUpdatesConsumer` (Фаза 1) поймает
   пост через long-polling.
3. Проверить: `message_id` поста записан в `idTelegram<Version>`
   той песни (как и до введения Фазы 2).
4. После этого проверить: повторный тик `TelegramAutoPublishScheduler`
   **не** пытается отправить свой демо для этой песни (потому что
   `idTelegram<Version>` != '' — или, точнее, бот проверяет
   `idTelegramDemo` != ''; если админ опубликовал, например,
   `idTelegramKaraoke` вручную, бот всё равно попробует отправить
   демо, если `idTelegramDemo` пусто).

**Ожидаемый результат**: Фаза 1 работает без регрессии.
Бот Фазы 2 для этой песни либо пропускает (если
`idTelegramDemo` уже заполнен Фазой 1), либо отправляет
своё демо (если `idTelegramDemo` пусто — админ опубликовал
другую версию). Поведение прозрачно для администратора.

## Шаг 9 — сбой Telegram (FR-010)

1. Временно выключить Telegram (например, `iptables -A OUTPUT
   -p tcp --dport 443 -j DROP` на admin-машине, или выключить
   прокси).
2. Взять полностью готовую песню с пустым `idTelegramDemo`,
   `date`/`time` через 2 минуты.
3. Сохранить. Дождаться тика.
4. В логах: `TelegramAutoPublishService: песня id=<id> →
   state=publishing → sendVideo attempt 1 failed: ...; backoff
   30s → attempt 2 failed: ...; backoff 2m → attempt 3 failed:
   ... → state=send_failed, lastError="retries exhausted: ..."`.
5. В `Settings` `playerReadinessFlagsMap`:
   `telegramAutoPublishState = "send_failed"`,
   `telegramAutoPublishLastError = "retries exhausted: ..."`.
6. Включить Telegram обратно.
7. Нажать «Опубликовать сейчас» (Шаг 6 — повторный триггер).
8. В логах: `TelegramAutoPublishService: песня id=<id> →
   state=publishing → sendVideo attempt 1 success → state=published`.

**Ожидаемый результат**: после восстановления сети повторная
попытка (через кнопку) успешно публикует. `idTelegramDemo`
заполнен.

## Шаг 10 — не-готовая песня (FR-011)

1. Взять песню с `idStatus = 5` (или любую не-готовую).
2. Установить `date`/`time` через 5 минут.
3. Сохранить. Дождаться тика.
4. Проверить: в канале **нет** сообщения; в логах —
   `TelegramAutoPublishService: песня id=<id> → skipped
   (not content-ready: idStatus=5, missing=stemAccompanimentReady)`.
5. В UI state = `scheduled`, но с «причиной пропуска» (опционально).

**Ожидаемый результат**: 0 публикаций для не-готовой песни;
state не двигается дальше `scheduled`.

## Шаг 11 — состояние «Отменена» (опционально, на усмотрение UI)

1. Взять песню с `date`/`time` через 5 минут.
2. Через UI (или прямой правкой `Settings.playerReadinessFlagsMap`)
   установить `telegramAutoPublishState = "cancelled"`.
3. Дождаться тика.
4. Проверить: в канале **нет** сообщения; бот пропускает
   (`cancelled` уважается до тех пор, пока админ не изменит
   `date`/`time` или явно не очистит `cancelled`).

**Ожидаемый результат**: 0 публикаций; state остаётся
`cancelled`.

## Контрольные проверки после прохождения

- [ ] `git status` — все изменения в feature-ветке
      `113-telegram-demo-publish`; master не сдвинут.
- [ ] `./gradlew ktlintCheck` — зелёный (или 0 нарушений сверх baseline).
- [ ] `cd webvue3 && npm run lint:check` — зелёный.
- [ ] `bash tools/check-kdoc-coverage.sh` — 100% (новые классы
      `TelegramAutoPublishScheduler`, `TelegramAutoPublishService`,
      `TelegramAutoPublishResult` — с KDoc + `@see
      docs/features/telegram-auto-publish.md`).
- [ ] `bash tools/check-jsdoc-coverage.sh webvue3` — 100%.
- [ ] `docs/features/telegram-auto-publish.md` — обновлён
      (Фаза 2 добавлена).
- [ ] Нет новых `NEEDS CLARIFICATION` в spec.md.
- [ ] PR прошёл CI 7/7 SUCCESS.

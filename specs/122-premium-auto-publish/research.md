# Research: Премиум-автопубликация в Telegram и ВК

## R1 — Root cause: почему Telegram-премиум не завершается, а ВК-премиум работает

**Вопрос**: baг-репорт гласит «новость + ВК публикуются, Telegram — нет».
Флаги (`premiumAutoPublishEnabled=true`, `telegramAutoPublishEnabled=true`)
исключают простое «настройка не включена». В чём структурная причина?

**Решение**: две асимметрии между каналами делают Telegram единственным
каналом с реальной зависимостью от асинхронного рендера:

1. **ВК-премиум — синхронный, текстовый.** Дефолтный `vkTemplatePremium`
   не содержит `{demoVideo}` (community-токен не имеет прав `video.save` —
   см. `KaraokeProperties.kt` описание ключа). `VkAutoPublishService.publishToVk`
   в этом случае идёт веткой `publishTextOnly` — публикует `wall.post`
   без видео **в тот же вызов**, без постановки `RENDER_MP4_DEMO` и без
   промежуточного состояния `rendering`. Отсюда — «ВК публикуется сразу».
2. **Telegram-премиум — асинхронный, обязательно с видео.** У Bot API
   `sendVideo` нет текстового fallback (`TelegramAutoPublishService.publishToTelegram`
   всегда идёт либо в `publishFile`, либо, если файла нет, в
   `startRenderAndReturn` → состояние `rendering`). Когда файла ещё нет —
   ЕДИНСТВЕННЫЙ код, который сегодня вызывает
   `TelegramAutoPublishService.onRenderCompleted()` для завершения такой
   отложенной публикации, находится в
   `TelegramAutoPublishScheduler.resumeRenderingSongs()` — планировщике
   Phase 2 «в эфире» (`specs/113-telegram-demo-publish`), НЕ в
   `PremiumAutoPublishScheduler`. Эта функция вызывается только когда
   `telegramAutoPublishEnabled=true` (проверка в начале `tick()`) — то есть
   завершение премиум-рендера Telegram технически работает **только пока
   выключатель чужой фичи включён**, и совершенно не задокументировано как
   намеренная зависимость.

**Почему это могло выглядеть как «Telegram не публикуется» в конкретном
случае пользователя**: помимо структурной хрупкости (п. 2), у
администратора **нет вообще никакого способа увидеть в UI**, что
Telegram-премиум ждёт рендера (`telegramAutoPublishState="rendering"`) —
единственный способ узнать реальное состояние песни — прямой запрос к
БД. Наблюдаемый симптом «не публикуется» мог фактически означать «ещё не
завершилось (рендер идёт/ждёт своей очереди), и это никак не видно» —
что для администратора неотличимо от «сломано».

**Решение (что делает эта фича)**:
- `PremiumAutoPublishScheduler.tick()` получает собственную фазу
  `resumeRenderingSongs()` (по образцу `TelegramAutoPublishScheduler`), которая
  ищет песни с `newsPremiumPublishPending=true` И
  `telegramAutoPublishState="rendering"` И `newsPremiumTelegramSent=false`,
  проверяет терминальный статус связанной `RENDER_MP4_DEMO`-задачи и вызывает
  `TelegramAutoPublishService.onRenderCompleted(songId, publicationType=PREMIUM,
  persistMessageId=false, success=.., error=..)` — **независимо** от
  `telegramAutoPublishEnabled`.
- `TelegramAutoPublishScheduler.resumeRenderingSongs()` (AIR) остаётся как
  есть — она по-прежнему корректно подхватит премиум-рендер, если
  `telegramAutoPublishEnabled=true` (двойное покрытие безопасно:
  `onRenderCompleted` идемпотентен по `newsPremiumTelegramSent`/`idTelegramDemo`,
  повторный вызов для уже обработанной песни — no-op, `PUBLISHED` без
  побочных эффектов).
- Аналогичная проверка нужна и для ВК (`VkAutoPublishScheduler.resumeRenderingSongs()`)
  на случай, если в будущем шаблон `vkTemplatePremium` получит
  `{demoVideo}` (см. Out of Scope spec.md) — сейчас не критично (ВК-премиум
  синхронный), но `PremiumAutoPublishScheduler` получает такую же
  собственную фазу для ВК ради симметрии и на случай будущего изменения
  шаблона.

**Alternatives considered**:
- *Убрать зависимость от рендера для Telegram (публиковать текстом, если
  видео нет)* — отклонено: Telegram Bot API `sendVideo` требует файл,
  альтернативный `sendMessage` (текст) изменил бы формат поста и вступил
  бы в противоречие с уже принятым решением спеки (User Story 1 явно
  требует «видео появляется после рендера», не «текст сразу + видео
  потом отдельным постом»).
- *Сделать `telegramAutoPublishEnabled` предпосылкой для premium (явно
  документировать, а не чинить)* — отклонено пользователем неявно: FR-003
  spec.md прямо требует независимости; баг-репорт был про «должно
  работать», а не «должно быть объяснено, почему не работает».

## R2 — Раздельные счётчики попыток на канал (FR-010)

**Вопрос**: сегодня `premiumAttemptCount` — один общий счётчик на обе
попытки (Telegram и ВК) внутри `PremiumAutoPublishScheduler.handleFailure()`.
Один быстро проваливающийся канал (например, временно недоступный ВК)
может исчерпать общий лимит попыток раньше, чем у другого канала (Telegram)
закончится собственный разумный запас попыток — тот канал будет
преждевременно помечен `premiumAutoPublishState="FAILED"`, хотя сам по
себе ещё не исчерпал лимит.

**Решение**: завести два независимых счётчика в том же JSON-блобе
`player_readiness_flags` — `premiumAttemptCountTelegram` /
`premiumAttemptCountVk` (тот же паттерн `readinessStringFlag`, что уже
используют `premiumAttemptCount`/`telegramAutoPublishState`). Итоговое
`premiumAutoPublishState="FAILED"` для ВСЕЙ задачи выставляется, только
когда **оба** канала либо завершены успешно (закрыто по
`newsPremiumTelegramSent`/`newsPremiumVkSent`), либо у обоих исчерпан
собственный лимит попыток. Если только один канал исчерпал лимит —
`newsPremiumPublishPending` остаётся `true` для второго канала (он
продолжает ретраиться), а первый прекращает попытки, но не блокирует
второй. UI (FR-006/FR-007 spec.md) отображает канал-специфичное
состояние `FAILED`, даже если общая задача ещё `RUNNING`.

Существующее поле `premiumAttemptCount` (общий счётчик) оставляется как
deprecated/read-only для обратной совместимости логов/мониторинга, но
логика перестаёт его инкрементировать — заменяется на два новых поля.

**Rationale**: не требует миграции БД (то же JSON-поле), сохраняет
принцип из `specs/101-song-news-flag` (флаги готовности — JSON-ключи, не
колонки), устраняет описанный в Edge Cases spec.md риск преждевременного
`FAILED`.

**Alternatives considered**:
- *Оставить общий счётчик, но увеличить лимит попыток* — отклонено:
  не устраняет причину (независимые сбои по-прежнему суммируются), только
  отодвигает симптом.
- *Отдельная таблица учёта попыток* — отклонено: избыточно для двух
  int-полей, нарушает принцип «JSON-блоб для флагов готовности», как и
  остальные попытки в этой части системы.

## R3 — UI-статус премиум-публикации в `SongEdit.vue`

**Вопрос**: где и как показать раздельный статус Telegram/ВК-премиум,
не путая его с уже существующим отображением AIR-статуса (`idTelegramDemo`/
`idVk`, вычисляемым client-side из raw-полей в `SongsTable.vue`/`SongEdit.vue`)?

**Решение**: новый вычисляемый (client-side, как и существующий
`telegramPublishState` computed в `SongEdit.vue`) статус на основе полей,
уже присутствующих в JSON песни (все НЕ `@JsonIgnore`):
`newsPremiumPublishPending`, `newsPremiumTelegramSent`, `newsPremiumVkSent`,
`telegramAutoPublishState`, `vkAutoPublishState`, `premiumAutoPublishLastError`,
новые `premiumAttemptCountTelegram`/`premiumAttemptCountVk`. Правило вывода
статуса канала (аналог серверного `effectiveTelegramAutoPublishState`, но
для премиум-контекста — тот геттер сегодня `@JsonIgnore` и заточен под AIR,
переиспользовать напрямую нельзя, см. Edge Cases spec.md FR-009):

- Если `newsPremiumTelegramSent=true` → «Опубликовано».
- Иначе если `newsPremiumPublishPending=false` (и не sent) → «Не требуется»
  (не показывается вовсе — песня не участвует в премиум-цикле).
- Иначе если `telegramAutoPublishState` в {rendering, publishing} → тот же
  лейбл («Рендерится»/«Публикуется»).
- Иначе если `premiumAttemptCountTelegram >= premiumAutoPublishMaxAttempts`
  (лимит приходит вместе с ответом одного из существующих `/api/song/*`
  или через уже загруженные `KaraokeProperties` в другом месте UI) →
  «Ошибка отправки» + `premiumAutoPublishLastError`.
- Иначе → «Ожидает».

Симметрично для ВК (`newsPremiumVkSent`/`vkAutoPublishState`/
`premiumAttemptCountVk`).

Кнопка «Повторить» — вызывает существующий
`/api/song/publishPremiumTelegram` или `/api/song/publishPremiumVk`
(POST, без изменений контракта) и видна только в состоянии «Ошибка
отправки» для соответствующего канала.

**Rationale**: переиспользует существующий UI-паттерн (client-side
вычисление статуса из уже загруженных полей, как `telegramPublishState`
в `SongEdit.vue` для AIR) — не требует новых endpoint'ов для отображения,
только (опционально) прокидки лимита попыток, если он ещё не доступен
фронтенду.

**Alternatives considered**:
- *Серверный derived-эндпоинт `/api/song/premiumStatus`* — отклонено как
  избыточное: все нужные поля уже присутствуют в стандартном ответе
  песни, добавлять отдельный round-trip не требуется.

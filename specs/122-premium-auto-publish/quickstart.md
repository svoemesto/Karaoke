# Quickstart: Проверка премиум-автопубликации Telegram/ВК

Ручная проверка на admin-машине (`karaoke-app`), CI-тестов нет
(`constitution.md` «Тесты»).

## Предпосылки

- `karaoke-app` собран и запущен локально с изменениями этой фичи.
- В `webvue3` → Свойства: `premiumAutoPublishEnabled=true`,
  `telegramAutoPublishChannelId`/`vkGroupId` заполнены тестовыми
  значениями (тестовый канал/группа, не боевые).
- Тестовая песня со статусом `idStatus=6`, всеми readiness-флагами
  (`stemAccompanimentReady`, `stemVocalReady`, `pictureAlbumReady`,
  `pictureAuthorReady`), маркерами — но **без** отрендеренного
  демо-MP4 (`RenderVersion.DEMO`) на диске.

## Сценарий 1 — Telegram-премиум завершается независимо от `telegramAutoPublishEnabled`

1. Выставить `telegramAutoPublishEnabled=false` в Свойствах (специально
   выключить AIR-фичу, чтобы исключить скрытую зависимость).
2. Убедиться, что `premiumAutoPublishEnabled=true`.
3. Перевести тестовую песню в `newsAvailableAnnounced=false→true`
   (например, через апрув задания редактора, или напрямую выставив флаг
   через существующий UI/endpoint).
4. Дождаться (или наблюдать логи) постановки `RENDER_MP4_DEMO` в очередь.
5. Дождаться завершения рендера (`tbl_processes.process_status=DONE`).
6. **Ожидаемый результат**: в течение одного тика `PremiumAutoPublishScheduler`
   (≤30 сек после DONE) в тестовом Telegram-канале появляется пост с
   демо-видео и подписью по `telegramTemplatePremium`;
   `Song.newsPremiumTelegramSent=true`; `Song.idTelegramDemo` остаётся
   пустым. Это должно произойти **несмотря на** `telegramAutoPublishEnabled=false`.

## Сценарий 2 — Статус виден в карточке песни

1. В процессе сценария 1, после шага 4 (рендер ещё идёт), открыть
   карточку тестовой песни в `webvue3`.
2. **Ожидаемый результат**: виден статус «Telegram (премиум): Рендерится»
   отдельно от статуса ВК-премиум.
3. После завершения (шаг 6 сценария 1) — обновить карточку, статус —
   «Опубликовано».

## Сценарий 3 — Ручной повтор после ошибки

1. Временно очистить `telegramAutoPublishChannelId` (спровоцировать
   `SEND_FAILED`).
2. Довести `premiumAttemptCountTelegram` до
   `premiumAutoPublishMaxAttempts` (несколько тиков планировщика, либо
   временно уменьшить лимит в Свойствах для ускорения проверки).
3. **Ожидаемый результат**: карточка песни показывает «Telegram (премиум):
   Ошибка отправки» с текстом `premiumAutoPublishLastError`, кнопка
   «Повторить» видна.
4. Восстановить `telegramAutoPublishChannelId`. Нажать «Повторить».
5. **Ожидаемый результат**: `POST /api/song/publishPremiumTelegram`
   отрабатывает успешно, статус меняется на «Опубликовано» (или
   «Рендерится», если демо ещё не готово).

## Сценарий 4 — Независимые счётчики попыток не блокируют друг друга

1. Спровоцировать несколько сбоев ТОЛЬКО в ВК-канале (например, временно
   очистить `vkGroupId`), оставив Telegram работоспособным.
2. **Ожидаемый результат**: `premiumAttemptCountVk` растёт,
   `premiumAttemptCountTelegram` остаётся `0`; как только ВК исчерпывает
   лимит — канал ВК показывает «Ошибка отправки», при этом Telegram
   продолжает нормально пытаться публиковаться (не заблокирован
   исчерпанием ВК-попыток) и в итоге переходит в «Опубликовано».

## Регрессия — AIR-путь не затронут

1. Убедиться (по образцу `specs/113`/`specs/121` quickstart), что
   плановая AIR-публикация (по `publish_date`/`publish_time` песни) в
   Telegram и ВК по-прежнему работает без изменений: сохраняет
   `idTelegramDemo`/`idVk`, использует `telegramTemplateAir`/`vkTemplateAir`.

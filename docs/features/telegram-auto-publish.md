# Автопубликация в Telegram-канал

> **Status**: active (Фаза 1 — отлов ссылки)
> **Feature Key**: telegram-auto-publish
> **Last Updated**: 2026-07-20

## Что делает

Фоновый демон-поток ловит вышедший `channel_post` Telegram-канала
через long-polling `getUpdates` и автоматически записывает `message_id`
в `Settings` (песню). Фаза 2 (постинг видео в момент эфира) спроектирована,
не реализована.

## Зачем

Раньше пользователь вручную создавал отложенный пост в Telegram (метка
`-` в `id_telegram_*`) и вручную вставлял ссылку после выхода. Теперь
это автоматизировано: бот сам ловит вышедший пост и привязывает
`message_id` к правильной песне/версии.

## Как работает (кратко)

1. **Запуск** — авто-старт на `ApplicationReadyEvent`, флаг
   `KaraokeProperties.telegramPollingEnabled`. Паттерн — как
   `KaraokeProcessWorker`, но не блокирует HTTP/event-поток.
2. **Long-polling** — `TelegramApiClient.getUpdates()` с таймаутом
   ~30 секунд, фон-цикл.
3. **Парсинг поста** — `Settings.parseTelegramPostSongId` /
   `parseTelegramPostSongVersion` (companion `Settings.kt`):
   - Пост уже содержит `linkSM` (`https://sm-karaoke.ru/song?id=<id>`).
   - Явный разделитель версии.
4. **Сохранение** — штатный `Settings.saveToDb()` пишет `message_id`
   в `id_telegram_<version>`.
5. **Прокси-фолбэк** — `TelegramApiClient` авто-fallback:
   - Сначала напрямую.
   - При ошибке — через HTTP-прокси (`telegramProxyUrl`).
   - Периодическая попытка вернуться на прямой путь.
   - Прокси — отдельный docker-сервис `karaoke-telegram-proxy`
     (`deploy/docker-compose-telegram-proxy.yml`).
6. **Конфиг прокси** — `/sm-karaoke/system/telegram-proxy/config.json`
   (реальный VLESS вне git).

## Инварианты / правила

- **MUST**: `telegramPollingEnabled` проверяется в `telegramPollingEnabled`-флага —
  без него цикл не стартует.
- **MUST**: при ошибке прокси — НЕ отключаем polling, только
  переключаемся на direct (через заданный таймаут).
- **MUST**: `message_id` сохраняется через `Settings.saveToDb()`, не
  через прямой SQL (SSE-уведомления).
- **SHOULD**: rate-limit на `getUpdates` — Telegram рекомендует не чаще
  раз в несколько секунд. Сейчас — long-poll, что OK.

## Известные ловушки

- **Telegram 403 из Docker**: на прод-сервере (Германия) Telegram
  доступен. На admin-машине через VPN — может быть заблокирован. Прокси
  решает проблему.
- **Множественные инстансы**: если запустить `karaoke-app` дважды —
  оба будут long-polling'ить, конфликт на стороне Telegram. Используйте
  `--no-telegram-polling` для второго инстанса.
- **Фаза 2 (постинг видео)**: ещё не реализована. Сейчас бот только
  ловит уже опубликованные посты. Не пытайтесь использовать для
  автоматической публикации.
- **Auth token rotation**: при смене `telegramBotToken` нужно
  перезапустить `karaoke-app`. Polling не подхватывает новый токен
  на лету.

## Ссылки на ключевые классы/файлы

- [`TelegramApiClient.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramApiClient.kt) — клиент Telegram Bot API
- [`TelegramUpdatesConsumer.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramUpdatesConsumer.kt) — long-polling
- [`Settings.parseTelegramPostSongId`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Settings.kt) — парсинг поста
- [`KaraokeAppApplication.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt) — `@EventListener(ApplicationReadyEvent)`
- [`deploy/docker-compose-telegram-proxy.yml`](../../deploy/docker-compose-telegram-proxy.yml) — прокси-сервис

# Память по задаче `vk-news-auto-publish` (feat 121, spec 121-vk-news-auto-publish)

Эта запись — контекст для продолжения после сжатия. Если контекст сжался и ты потерял
нить — начни с этой памяти.

## Что сделано (на 2026-08-02)

**Backend (`karaoke-app/`):**
1. `KaraokeAppApplication.kt` — добавлен **явный `TaskScheduler`-бин** (4 потока `karaoke-scheduler`,
   `ConcurrentTaskScheduler`). Без этого `@Scheduled` молча не работал в Spring 6.2.
2. `KaraokeAutoPublishScheduler.kt`:
   - `parseDateTimePublish` теперь с `TimeZone.getTimeZone("Europe/Moscow")`
     (был JVM-локаль = UTC, что давало ошибку в 3 часа при сравнении с `nowMoscow`).
   - `publishScheduledSongs` вызывает `publishToTelegram(song, allowPastDate = true)`.
     Без этого `dt.before(nowMoscow)` отвергал песни, у которых `publishTime` прошёл
     на несколько секунд назад.
   - Дефолт `telegramAutoPublishWindowMinutes = 59` (временно для отладки).
   - **Диагностические println'ы** (НЕ УБИРАТЬ до успешной автопубликации без ручного
     вмешательства):
       `TelegramAutoPublishScheduler: windowMinutes=…, candidates.size=…, ids=…`
       `TelegramAutoPublishScheduler: processing song id=… (автор - название)`
       `TelegramAutoPublishScheduler: song id=… result: state=…, messageId=…, error=…`
3. `TelegramAutoPublishService.kt`:
   - `Song.dateTimePublish` (в `Song.kt:613`) — теперь с `TimeZone.Moscow` (фикс для сравнения
     `dt.before(nowMoscow)`).
   - `caption` строится через `TelegramTemplateService.render(...)` вместо хардкод `buildCaption`.
   - **Диагностический println** в `if (!song.isContentReady)`:
     `println("TelegramAutoPublishService: song id=… isContentReady=false: idStatus=… stemAcc=… stemVoc=… picAlb=… picAuth=… markersLen=…")`
4. `KaraokeProperties.kt` — `telegramAutoPublishWindowMinutes` default = 59,
   описание с объяснением временной меры.
5. Новый `TelegramTemplateService.kt` — рендеринг caption (≤1024 символа) с плейсхолдерами.
6. Новые endpoint'ы `/api/telegram/templates` (GET/POST/preview/defaults).

**Frontend (`webvue3/`):**
1. `VkTemplatesView.vue` → `PublishTemplatesView.vue` (переименован и расширен).
2. Две главные вкладки: «ВКонтакте» / «Telegram», внутри каждой — air/premium.
3. Роут: `/vktemplates` → `/publish-templates`, имя `publish-templates`.
4. Меню: «Шаблоны ВК» → «Шаблоны публикаций».

## Ключевые баги (для справки)

1. **@Scheduled не тикал** — починено через явный `TaskScheduler`-бин.
2. **SimpleDateFormat без таймзоны** в 3 местах (`TelegramAutoPublishScheduler.parseDateTimePublish`,
   `Song.dateTimePublish`, `SongReleaseAnnouncementService.parseDateTimePublish`)
   — починено через `TimeZone.getTimeZone("Europe/Moscow")`.
3. **dt.before(nowMoscow)** отвергал опоздавшие песни — починено через `allowPastDate=true` в scheduler.
4. **isContentReady=false для готовой песни** — причина неизвестна (возможно, `sourceMarkersList` был пуст
   до того, как пользователь зашёл в карточку; saveToDb() в карточке исправил). Массовая SQL-проверка
   для 5 песен на 03.08.26 показала `isContentReady=true` для всех.

## Что осталось сделать

- Дождаться **успешной автопубликации без ручного вмешательства** (например, 03.08.26 11:00 для
  песни id=8154 «Х.. забей — Как наши прадеды»), чтобы убедиться, что isContentReady
  корректно работает для свежих песен.
- Если публикация проходит без ручного вмешательства — **убрать диагностические println'ы** в:
  - `TelegramAutoPublishScheduler.publishScheduledSongs` (4 println'а)
  - `TelegramAutoPublishService.publishToTelegram` (1 println в блоке isContentReady=false)
- После стабилизации — уменьшить `telegramAutoPublishWindowMinutes` обратно до 5-15 мин
  (сейчас 59).
- Снять диагностические println'ы в `ApiController` для VK-templates preview (если они есть
  в VK-пути — были ранее, проверь).

## Релевантные файлы (пути)

- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeAppApplication.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishScheduler.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramAutoPublishService.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/TelegramTemplateService.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/SongReleaseAnnouncementService.kt`
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/Song.kt` (строки 613, 1013-1020)
- `karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt` (telegramAutoPublishWindowMinutes)
- `webvue3/src/views/PublishTemplatesView.vue` (НЕ `VkTemplatesView.vue` — удалён)
- `webvue3/src/router/index.js` (роут `/publish-templates`)
- `webvue3/src/App.vue` (пункт меню «Шаблоны публикаций»)

## Ключевые команды

```bash
cd ~/Karaoke && ./gradlew :karaoke-app:bootJar
cd ~/Karaoke/deploy && bash do.sh build_app && bash do.sh start
cd ~/Karaoke/webvue3 && npm run build && cd ~/Karaoke/deploy && bash do.sh build_start_webvue3
```

```bash
# Свойство окна (через API работает сразу, без рестарта)
curl -s -X POST "http://localhost:8898/api/properties/setproperty" -d "key=telegramAutoPublishWindowMinutes&stringValue=59"
curl -s -X POST "http://localhost:8898/api/properties/getproperty" -d "key=telegramAutoPublishWindowMinutes"

# Сбросить 18492 в неопубликованное (для теста scheduler)
docker exec karaoke-db psql -U postgres -d karaoke -c "UPDATE tbl_songs SET id_telegram_demo = '' WHERE id = 18492;"

# Смотреть тики
docker logs -f karaoke-app 2>&1 | grep -E "TelegramAutoPublish|18492"

# Список песен на конкретную дату
docker exec karaoke-db psql -U postgres -d karaoke -c "SELECT id, publish_time, song_author, song_name FROM tbl_songs WHERE publish_date = '03.08.26' AND (id_telegram_demo IS NULL OR id_telegram_demo = '') ORDER BY publish_time;"
```
## Что сделано (на 2026-08-03) — попытка починить превью для бота

### Гипотеза 1: использовать `<meta property="og:image">` (стандартный OG)
- Добавлен endpoint `/api/public/og/song?id=NNN` в `karaoke-web` (`PublicOgSongController.kt`)
  с полным OG-HTML: `<meta property="og:image">`, `og:title`, `og:description`, `og:type=music.song`,
  `og:image:width=1200`, `og:image:height=630`, `og:image:type=image/png`.
- Картинка генерируется `PublicApiController.songVkImage` — тот же готовый endpoint
  `/api/public/song-vk-image/{id}` (537×240 PNG).
- nginx на проде обновлён: `if ($http_user_agent ~* "vkShare|...") rewrite ^/song(\?.*)?$ /api/public/og/song$1 last;`
- Прямой `curl -A vkShare` возвращает правильный HTML с meta-тегами + видимый `<img>` в body.
- **Результат: превью НЕ появляется** в постах автоматической публикации (для новых ID тоже).

### Гипотеза 2: вернуть «голый» HTML (старый формат до 02.08.2026)
- `PublicOgSongController.kt` теперь возвращает HTML без og-мета, только `<title>` и видимый `<img>`.
- Тестировали на новых песнях (id=24124, id=24125) — ранее не было публикаций.
- **Результат: превью НЕ появляется** даже в этом «голом» формате.
- При ручной публикации (когда пользователь сам вставлял ссылку на стену группы)
  сниппет с превью появлялся — это значит VK-парсер срабатывал.

### Гипотеза 3: размер картинки (213 КБ → JPEG 30-50 КБ)
- 537×240 PNG получался ~213 КБ — VK мог таймаутить.
- Переключил на JPEG с quality=0.85 → 50-54 КБ.
- **Результат: всё равно НЕ появляется.**

### Гипотеза 4: User-Agent VK-парсера не подходит под nginx правило
- Текущее правило: `vkShare|vkim|VKBot|vkshare|Mail\.RU_Bot|VKShareBot`
- При ручной публикации VK использует свой парсер (возможно, с другим User-Agent).
- При бот-публикации (`/method/wall.post`) VK может вообще НЕ ходить по ссылке для парсинга —
  сниппет создаётся «до публикации» на основе закэшированных данных.

### Выводы на 03.08.2026 (важно для будущего)
- VK-парсер при автоматической публикации через бота **НЕ формирует превью** — вне
  зависимости от формата HTML (OG-теги или «голый» HTML).
- При ручной публикации (через UI VK) сниппет с превью **появляется**.
- Вероятная причина: VK кэширует URL и не делает ре-парсинг при бот-публикации.
  Для обхода кэша может потребоваться: 1) wall.edit с другим сообщением, 2) короткий URL
  (через bit.ly), 3) `wall.repost` через другие группы.

## План на будущее (specs/123-vk-og-preview-fix/)
- Изучить, какие User-Agent'ы VK использует при бот-публикации (`/method/wall.post`).
- Добавить endpoint на проде (если есть способ), который VK сможет обходить.
- Возможно, потребуется VK API endpoint `wall.getPreview` или подобный.
- Альтернативно: прикреплять фото через `attachments=photo<owner>_<id>` (через user-token
  с правом photos) — тогда VK создаст превью из прикреплённого фото автоматически.


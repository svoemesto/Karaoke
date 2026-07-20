# Рендер MP4 из онлайн-плеера

> **Status**: active
> **Feature Key**: mp4-render
> **Last Updated**: 2026-07-20

## Что делает

Рендерит караоке-видео в MP4 через Playwright (headless Chromium) + ffmpeg
без MLT. Используется как fallback / preview / публикация в соцсети.
Три версии рендера: `LYRICS`, `KARAOKE`, `DEMO`.

## Зачем

MLT-рендер (`melt`) — основной путь, но иногда нужно быстро сделать
превью или версию для конкретной площадки (Telegram, VK) с другим
соотношением сторон или ватермарком. MP4-рендер через Playwright даёт
гибкость: можно подменить HTML/CSS на лету и сразу получить MP4.

## Как работает (кратко)

1. **`PlayerMp4RenderService.renderFrames()`**:
   - Headless Chromium открывает страницу онлайн-плеера Karaoke.
   - Для каждого кадра `canvas.toDataURL('image/jpeg', 0.95)` → JPEG.
   - 1920×1080@60fps для LYRICS/KARAOKE, 1280×720@30fps для DEMO.
2. **`PlayerMp4MuxService.mixAndMux()`**:
   - ffmpeg собирает JPEG-секвенцию + FLAC-стемы в MP4.
   - Прогресс через `-progress pipe:1` (парсинг `out_time_ms`).
3. **Три версии**:
   - **LYRICS** — acc(1.0)+voc(1.0), 1920×1080@60fps.
   - **KARAOKE** — acc(1.0)+voc(0.0), 1920×1080@60fps.
   - **DEMO** — acc(1.0)+voc(0.0), **1280×720@30fps**, фрагмент с
     fade-in/out, watermark «ДЕМО», end screen 10 сек.
4. **Результат** копируется в `done_files/$fileName [render].mp4`,
   temp-папка удаляется.
5. **Очередь** `RENDER_MP4_*` использует `THREAD_LANE_HEAVY_RENDER=0`.

## Инварианты / правила

- **MUST**: `ProcessBuilder.redirectErrorStream(true)` для ffmpeg (см.
  [CONTRIBUTING.md#kotlin-processbuilder-redirect-error-stream](../../CONTRIBUTING.md)).
- **MUST**: JPEG quality=95 (не PNG) — x3 скорость рендера.
- **MUST**: версия DEMO использует дефолты 1280/720/30fps, прописанные
  в `ApiController.kt` И в `KaraokeProcess.kt` (двойное fallback — иначе
  не сработает).
- **MUST**: cleanup temp-папки после успешного mux (или при ошибке).
- **SHOULD**: при `sendCountWaitingMessage` с пустой очередью бейдж
  сбрасывается в 0.

## Известные ловушки

- **ffmpeg stderr-buffer**: `ProcessBuilder.redirectErrorStream(false)`
  блокирует процесс — буфер stderr переполняется. **ВСЕГДА** `true`.
- **Дефолты DEMO**: контроллер подставляет дефолты раньше, чем они
  попадают в context — если задать только в `KaraokeProcess.kt`, не
  сработает. Нужна двойная установка.
- **Длинные песни**: рендер 4-минутной песни в LYRICS (60fps) = 14400
  кадров. На слабой машине занимает 10+ минут. Используйте DEMO для превью.
- **Playwright требует Chromium**: если `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright`
  не настроен, рендер падает. Проверяйте при первом запуске контейнера.

## Ссылки на ключевые классы/файлы

- [`PlayerMp4RenderService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4RenderService.kt) — рендер кадров
- [`PlayerMp4MuxService.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/services/PlayerMp4MuxService.kt) — ffmpeg mux
- [`Utils.executeRenderMp4`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — диспетчер
- [`ApiController.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/controllers/ApiController.kt) — REST `/song/renderMp4Preview`
- [`KaraokeProcess.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/model/KaraokeProcess.kt) — дефолты DEMO

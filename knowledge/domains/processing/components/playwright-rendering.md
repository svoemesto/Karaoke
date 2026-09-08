# Component: playwright-rendering

> **Домен**: [processing](../domain.md)
> **Компонент**: интеграция с Playwright (headless Chromium) для рендера
> кадров караоке-плеера в PNG/JPEG, которые затем скармливаются в melt.

## Ответственность | Responsibility

Playwright-интеграция — нетривиальный кусок processing-домена. Karaoke
рендерит каждый кадр караоке-плеера через headless Chromium:

1. Загружается страница плеера с текстом текущего сегмента.
2. JavaScript-плеер переходит к нужной секунде.
3. Делается `screenshot()` текущего состояния.
4. Кадр кодируется в JPEG (не PNG!) и передаётся в melt.

Эта компонента фиксирует неочевидные трюки и подводные камни интеграции.

**[WARN] Любая правка в `PlayerMp4RenderService.kt` или
`PlayerMp4MuxService.kt` требует ревью этой страницы.**

## Интерфейсы и Контракты | Interfaces and Contracts

### `PlayerMp4RenderService`

- **Поведение**: для каждого момента `t` песни открывает страницу
  плеера, выставляет `currentTime = t`, делает скриншот.
- **Вход**: `songId`, `startMs`, `endMs`, `fps`.
- **Выход**: список кадров в MinIO `frames/<songId>/<ms>.jpg`.

### `PlayerMp4MuxService`

- **Поведение**: склеивает JPEG-кадры + стемы в финальный MP4 через
  ffmpeg.
- **Вход**: `frames/`, `stems/`, `KaraokeProperties`.
- **Выход**: MP4 в MinIO `done_files/<songId>/<version>.mp4`.

### ffmpeg `-progress pipe:1`

ffmpeg выводит прогресс в формате key=value, обрабатывается парсером:

```
out_time_ms=12345
progress=continue
...
progress=end
```

Это **единственный** надёжный способ трекать прогресс ffmpeg (не
через `stderr`, не через callback).

## Логика и Алгоритмы | Logic and Algorithms

### Полный pipeline одного MP4

```
Song (idStatus=4: MARKED) + KaraokeProperties
  ↓
[1] PlayerMp4RenderService.start():
    - Открыть Chromium через Playwright
    - Для каждого момента t:
      - goto плеер с параметром ?t=<ms>
      - waitForSelector('.lyrics-segment.active')
      - canvas.toDataURL('image/jpeg', 0.95) ← JPEG QUALITY 95 TRICK
      - upload в MinIO frames/<songId>/<ms>.jpg
  ↓
[2] PlayerMp4MuxService.start():
    - ffmpeg -framerate <fps> -i frames/<songId>/%d.jpg
            -i stems/<songId>/vocals.flac
            -i stems/<songId>/accompaniment.flac
            -c:v libx264 -c:a aac -shortest out.mp4
    - ffmpeg выводит progress в pipe:1 → парсер out_time_ms
  ↓
[3] MP4 → MinIO done_files/<songId>/<version>.mp4
  ↓
[4] Event VideoRendered → Song.idStatus = 5
```

### JPEG Quality 95 Trick

**Зачем**: рендер PNG через canvas → melt медленный (×3).
**Трюк**: `canvas.toDataURL('image/jpeg', 0.95)` вместо PNG.
JPEG-сжатие в 95% визуально неотличимо, но рендер **×3 быстрее**.

**[WARN]** Качество ниже 0.90 даёт артефакты вокруг текста при
приближении. Не уменьшать.

**[WARN]** Если у вас возникает желание перейти обратно на PNG для
«лучшего качества» — сначала проверьте, что пользователь действительно
жалуется. По умолчанию — JPEG 0.95.

### Парсинг ffmpeg progress

```kotlin
// ffmpeg -progress pipe:1 ...
// Парсер: читаем stdin построчно, ищем "out_time_ms=<число>"
val progressRegex = Regex("""out_time_ms=(\d+)""")
process.inputStream.bufferedReader().forEachLine { line ->
    progressRegex.find(line)?.groupValues?.get(1)?.toLong()?.let { ms ->
        // обновить progress в БД или in-memory
    }
}
```

**[WARN]** Парсить через **stdin**, не **stderr** — ffmpeg выводит
`-progress` строго в указанный stream.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `KaraokeVideo`, `MLTProject`.
- → [rendering dictionaries](../rendering/components/dictionaries.md) —
  `RenderVersion`.
- → [karaoke-properties](karaoke-properties.md) — параметры рендера.
- → ADR-0006 — `redirectErrorStream(true)` обязателен для всех
  `ProcessBuilder`-вызовов в этом компоненте.

## Ловушки и предупреждения

**[WARN] JPEG quality < 0.90** даёт артефакты вокруг текста при
приближении. Не уменьшать.

**[WARN] Парсить progress через stdin** (`-progress pipe:1`), не через
stderr. Иначе прогресс не виден.

**[WARN] `ProcessBuilder` всегда с `redirectErrorStream(true)`** (см.
ADR-0006). Без этого ffmpeg заполнит stderr и процесс заблокируется.

**[WARN] Не закрывать Chromium** до конца рендера. Каждый кадр — новая
страница, но **тот же** browser-context. Закрытие = перезапуск = потеря
скорости в ×5.

**[WARN] Headless Chromium на проде** требует `--no-sandbox` в
`chrome-launch-args`. Иначе вылетает с permission denied.

## Связанные ADR | Related ADRs

- [0006-processbuilder-redirecterrorstream](../../adr/0006-processbuilder-redirecterrorstream.md)

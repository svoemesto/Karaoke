# Component: mlt-pipeline

> **Домен**: [rendering](../domain.md)
> **Компонент**: детальный MLT/melt пайплайн рендеринга караоке-видео.
> Включает mko-классы, JPEG quality трюк, CPU-limit и lane-разделение.

## Ответственность | Responsibility

MLT-пайплайн — сердце rendering-домена. Эта компонента:

- Описывает порядок слоёв в финальном MP4.
- Фиксирует неочевидные оптимизации (JPEG quality 95).
- Определяет CPU-лимиты и lane-разделение, без которых melt забивает
  ресурсы и UI деградирует.

**[WARN] Любая правка в mko-классах требует ревью этой страницы.**
Производительность melt неочевидна, оптимизации хрупкие.

## Интерфейсы и Контракты | Interfaces and Contracts

### mko (Melt Object) — Kotlin-класс, генерирующий MLT-property

Каждый mko — обёртка над одним визуальным слоем MLT:

- `BackgroundMko` — фоновый слой (картинка альбома).
- `TitleMko` — заголовок песни.
- `LyricsMko` — построчный вывод текста с подсветкой текущего слога.
- `MarkerMko` — таймлайн-маркер.
- и т.д. (~30 mko в проекте).

**Контракт**: каждый mko реализует `Mko.asMltProp()` → возвращает
строку MLT-property для вставки в mlt-файл.

### `MLT_CPU_LIMIT` — Docker CPU-limit на рендер

- **Прод**: `--cpus=2` (2 ядра на один melt-процесс).
- **Локально**: не ограничено (ноутбук может забить CPU).
- **Эффект**: параллельно может идти 1-2 рендера без деградации UI.

### `HEAVY_RENDER` lane

- `threadId = 0` — основной lane для тяжёлого рендера.
- Лёгкие задачи (CRUD, поиск) идут через другие lanes (1+).
- Идея: тяжёлые задачи не должны блокировать отзывчивость UI.

## Логика и Алгоритмы | Logic and Algorithms

### Полный pipeline одной песни

```
Song (idStatus=4: MARKED)
  ↓
[1] Build MLT project (mko composition)
  ↓
[2] Submit KaraokeProcess RENDER_MP4_<version>
  ↓
[3] Worker pickup (HEAVY_RENDER lane)
  ↓
[4] melt с --cpus=2, читает MLT + стемы из MinIO
  ↓
[5] JPEG quality 95 trick для text-слоёв
  ↓
[6] MP4 → MinIO done_files/<songId>/<version>.mp4
  ↓
[7] Event VideoRendered → Song.idStatus = 5
```

### Шаг [1]: mko composition

1. Инициализация: список mko-классов с параметрами из `Song.sourceMarkers`.
2. Каждый mko вычисляет свои `startMs`, `endMs`, `position`.
3. Результат — `MLTProject` (дерево mko).
4. `MLTProject.saveToDb()` → `Event MLTProjectCreated`.

### Шаг [5]: JPEG quality 95 trick

**Зачем**: рендер PNG-слоёв через canvas → melt медленный (×3).
**Трюк**: перед сериализацией `canvas.toDataURL('image/jpeg', 0.95)`
вместо `toDataURL('image/png')`. JPEG-сжатие в 95% даёт визуально
неотличимый результат, но рендер **×3 быстрее**.

**[WARN]** Качество ниже 0.90 даёт артефакты вокруг текста. Не уменьшать.

### Шаг [6]: выход MP4 в MinIO

- Ключ: `done_files/<songId>/<version>.mp4`.
- Версии: `lyrics`, `karaoke`, `demo` (lowercase для URL).
- `SongPublicDTO.videoUrl` строится из этого ключа + MinIO endpoint.

## Зависимости | Dependencies

- → [domain](../domain.md) — AR `Song` с `idStatus=4`.
- → [dictionaries](dictionaries.md) — `RenderVersion` enum.
- → [catalog domain](../../catalog/domain.md) — `sourceMarkers` из catalog.
- → [identity domain](../../identity/domain.md) — `Identity` editor
  инициирует Approve.
- → ADR-0002 — выбор MLT, обоснование архитектуры.
- → External: `melt` (MLT framework), `ffmpeg` (для финального mux).

## Ловушки и предупреждения

**[WARN] Без `--cpus=2`** melt может занять все ядра → UI отвечает
с задержкой 5-10 сек. Прод-серверы должны быть настроены через
`docker run --cpus=2` или эквивалент в `deploy/`.

**[WARN] HEAVY_RENDER lane** должен иметь concurrency 1-2. Если
выставить `concurrency=4`, одновременные melt'ы съедят RAM (каждый
~2-4 ГБ на пике).

**[WARN] JPEG quality < 0.90** даёт артефакты вокруг текста при
приближении. Не опускать.

**[WARN] Не путать mko с MktObject** (есть такой в marketing-пакете
Kotlin). У нас — `mko` без `t`.

## Связанные ADR | Related ADRs

- ADR-0002 — MLT вместо ffmpeg (обоснование выбора MLT framework).

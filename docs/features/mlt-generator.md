# MLT-генератор караоке-видео

> **Status**: active
> **Feature Key**: mlt-generator
> **Last Updated**: 2026-07-20

## Что делает

Генерирует MLT XML-проект для `melt` CLI — визуальную композицию караоке-видео
из слоёв (текст песни, аккорды, гриф, ноты/табы, счётчики, горизонт, заголовок,
сплэш, водяной знак, фон). Принимает `Settings` (песня + параметры) и
выдаёт `.mlt` файл, который `melt` рендерит в `.mp4`/`.mpg`.

## Зачем

Karaoke-видео — это не просто видео со звуком и текстом, а многослойная
композиция: бегущий текст подсвечивается синхронно с вокалом, аккорды
появляются над нужными словами, на грифе показывается аппликатура, на
фоне — горизонт, в правом верхнем углу — счётчик. Вручную собирать это в
видеоредакторе — часы на песню. MLT-генератор делает это за секунды.

## Как работает (кратко)

1. На вход: `Settings` (одна песня) + `KaraokeProperties` (глобальные
   параметры рендера, ~150 штук).
2. `MltGenerator` собирает XML-дерево: `<mlt>` → `<tractor>` → `<multitrack>`
   с producer'ами (видео/аудио) + filter'ами + transition'ами.
3. Для каждого визуального слоя Karaoke есть свой builder в `mlt/mko/`
   («Mlt Karaoke Object»):
   - `MkoSongText` — бегущий текст с подсветкой текущего слога.
   - `MkoScroller` — обёртка для длинных текстов (скроллинг).
   - `MkoChords` / `MkoFingerboard` / `MkoChordBoard` — аккорды и аппликатура.
   - Счётчики, горизонт, заголовок, сплэш, водяной знак, фон.
4. `KaraokeProperties` хранит ~150 настраиваемых параметров
   (шрифты, цвета, отступы, тайминги) в base64-properties-файле
   `/sm-karaoke/system/Karaoke.properties`, редактируются через
   Properties UI/API без перекомпиляции.
5. `Utils.executeMltRender()` запускает `melt` как subprocess, парсит
   stdout (`time=HH:MM:SS.ms`), обновляет прогресс через SSE.

## Инварианты / правила

- **MUST**: MLT-генератор НЕ лезет в сеть — все данные берёт из `Settings`
  + `KaraokeProperties` + локальной БД. Self-contained.
- **MUST**: `Settings` всегда имеет `songType` ∈ {song, instrumental, poetry} —
  это влияет на набор визуальных слоёв (например, для `poetry` нет
  `MkoChords`).
- **MUST**: `redirectErrorStream(true)` для `ProcessBuilder` при запуске
  `melt` (см. [CONTRIBUTING.md#kotlin-processbuilder-redirect-error-stream](../../CONTRIBUTING.md)).
- **SHOULD**: новые визуальные слои добавляются как новый `Mko*` класс в
  `mlt/mko/`, не правкой `MltGenerator` напрямую.
- **SHOULD**: `KaraokeProperties` — единственное место для глобальных
  параметров рендера. Локальные переопределения — через `Settings` (если
  нужно) или hardcode с комментарием «// intentional:».

## Известные ловушки

- **Шрифты с Unicode-символами** (CJK, иврит) — MLT не рендерит их
  одинаково на разных машинах. Используйте только те шрифты, что в
  `/sm-karaoke/system/fonts/`.
- **Длинные строки текста** — `MkoSongText` разбивает на слоги по `Settings.lyricsText`,
  но не умеет правильно переносить `CJK`-текст. Для иероглифов —
  ручная разбивка.
- **Цвета** — в `mlt` цвета в формате `red,green,blue,alpha` (через
  запятую), а НЕ `#RRGGBB`. `Color.mlt()` — helper для конверсии.
- **Аудио-синхронизация** — `melt` стартует с первого аудио-продьюсера.
  Если в `Settings` несколько аудио (минус + оригинал), первый — тот, у
  кого меньше `offset`.

## Ссылки на ключевые классы/файлы

- [`mlt/MltGenerator.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/MltGenerator.kt) — главный генератор
- [`mlt/MltProp.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/MltProp.kt) — property-билдер для MLT
- [`mlt/mko/MkoSongText.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoSongText.kt) — бегущий текст
- [`mlt/mko/MkoChords.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoChords.kt) — аккорды
- [`mlt/mko/MkoFingerboard.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/mlt/mko/MkoFingerboard.kt) — гриф
- [`KaraokeProperties.kt`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/KaraokeProperties.kt) — ~150 параметров рендера
- [`Utils.executeMltRender`](../../karaoke-app/src/main/kotlin/com/svoemesto/karaokeapp/Utils.kt) — запуск melt subprocess

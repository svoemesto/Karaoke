# ADR-0002: MLT/melt как основной стек для генерации караоке-видео

* **Status**: Accepted
* **Date**: 2026-07-20 (Phase 001, реализация эволюционирует)
* **Deciders**: команда Karaoke

## Context

Караоке-видео в проекте собирается **сервером**, не на стороне браузера.
Изначально использовался только ffmpeg, но обнаружились ограничения:

1. **Сложность композитинга**. Караоке-видео состоит из **слоёв**:
   - Фоновая картинка (обложка альбома).
   - Видеофон (опционально).
   - Текст с маркерами (timing, шрифты, размер, цвет).
   - Аудио (acc + vocals + bass + drums).
   - **Спецэффекты** (fade in/out, KARAOKE-mode, blur).
2. **Изменение параметров на лету**. Параметры (~150 штук) часто меняются
   без перезапуска пайплайна: размер шрифта, выравнивание, fade.
3. **Несколько версий** для одной песни: LYRICS / KARAOKE / DEMO (1280×720@30fps,
   1920×1080@60fps) — у каждой свои параметры.
4. **Рердер по таймкодам** (например, фрагмент 30 сек для DEMO).
5. **Playwright** для рендера кадров в PNG/JPEG (для текста с прозрачностью).

## Decision

Мы используем **MLT (Media Lovin' Toolkit, https://mltframework.org)** —
фреймворк композитинга melt/MLT, как основной стек:

- **На каждый слой видео** — отдельный объект `mlt/mko/*` (mko = melt object).
- **Параметры слоя** — атрибуты mlt-property, читаемые через `melt query`.
- **Render** — `melt -consumer avformat:output.mp4` + `-profile ...` (HDV, etc).
- **Playwright headless** — рендер фрейма текста (HTML+CSS) в PNG/JPEG (alpha
  channel через `canvas.toDataURL('image/jpeg', 0.95)`).
- **ffmpeg** — для мультиплексирования (mux) видео + аудио, прогрессивный
  прогресс через `-progress pipe:1` (парсинг regex `time=HH:MM:SS.ms`).

### Три версии финального видео

| Версия | Размер | FPS | Audio mix | Назначение |
|--------|--------|-----|-----------|------------|
| **LYRICS** | 1920×1080 | 60 | acc(1.0)+voc(1.0) | Полный показ с вокалом |
| **KARAOKE** | 1920×1080 | 60 | acc(1.0)+voc(0.0) | Без вокала |
| **DEMO** | 1280×720 | 30 | acc(1.0)+voc(0.0) | Превью (фрагмент 30 сек) |

Каждая версия — отдельный вызов `melt` с разным `-profile`.

### Конфигурация — `KaraokeProperties.kt`

~150 настраиваемых параметров живут в `Karaoke.properties` (на admin-машине)
и редактируются через Properties UI/API без перекомпиляции.

## Consequences

**Положительные**:
- **Композитинг на уровне mlt** даёт точный контроль над слоями (порядок,
  z-index, эффекты, blend modes).
- **Параметры без перекомпиляции** — UI редактирует, `melt reload` подхватывает.
- **Playwright + MLT** — браузерный рендер шрифта + композитинг = качественный
  текст без необходимости в Cairo/Pango (которые хлопотны в настройке).
- **JPEG quality 95** = в 3 раза быстрее PNG без видимой потери (заметил пользователь).
- **Three versions** — одна песня = три MP4 для разных сценариев (полный показ,
  караоке-вечеринка, превью).

**Отрицательные**:
- MLT — нишевая технология, мало кто её знает. Онбординг разработчиков занимает
  больше времени.
- Зависимость от системы (melt + ffmpeg + Playwright нужно ставить в admin-контейнер).
- Сложность отладки (melt не имеет удобного дебаггера).

**Нейтральные**:
- Разработчик должен знать MLT-prop syntax (`property=value`).
- Параметры могут устаревать — поэтому они в `Karaoke.properties`, а не в коде.

## Alternatives Considered

- **Только ffmpeg** (сложный graph через `-filter_complex`): рассматривался —
  проще стек, но создание сложных композиций с переходами и эффектами —
  очень запутанный ffmpeg-script, плохо отлаживается.
- **HTML5 Canvas в браузере + MediaRecorder**: отвергнут — перенести рендер
  на клиент = медленнее + платформо-зависимо (Safari/Firefox разное
  поведение Canvas).
- **Adobe After Effects / серверный рендер видео**: отвергнут — внешний
  SaaS-сервис, не self-contained (нарушает Constitution § I).
- **MoviePy / OpenCV (Python)**: отвергнут — Python-окружение + ограниченная
  поддержка эффектов.

## Ссылки

- [Constitution § I](.specify/memory/constitution.md) — self-contained
  автопайплайн (нет зависимостей от SaaS).
- [Constitution § IV](.specify/memory/constitution.md) — async-очередь задач
  + парсинг stdout (ffmpeg `time=`, melt `NN%`, Sheetsage `NN%|`).
- [livedocs/domain/processing.md](../../domain/processing.md) — bounded
  context `processing` (Ubiquitous Language MLT/Demucs/Sheetsage).
- [livedocs/architecture/queue-lanes.md](../queue-lanes.md) — `HEAVY_RENDER`
  lane для тяжёлого рендера.
- [livedocs/architecture/L3-components.md](../L3-components.md) — MLT
  Generator компонент.
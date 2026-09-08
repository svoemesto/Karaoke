# Component: karaoke-properties

> **Домен**: [processing](../domain.md)
> **Компонент**: единая точка конфигурации рендера
> (`KaraokeProperties.kt` + `/sm-karaoke/system/Karaoke.properties`).

## Ответственность | Responsibility

В Karaoke **~150 параметров** управляют рендерингом караоке-видео:
размер шрифта, цвета, позиции, размер видео, fps, codec, fade-in/out,
background gradient, и т.д. Все они живут в одном месте — этот
компонент — единая точка правды.

**[WARN] Правка параметров рендера** через UI обязательна, нельзя
править напрямую в `Karaoke.properties` — UI-state рассинхронизируется.

## Интерфейсы и Контракты | Interfaces and Contracts

### `KaraokeProperties.kt` — Kotlin-класс

- **Поведение**: Spring `@ConfigurationProperties` биндинг к
  файлу `Karaoke.properties`.
- **Использование**: во всех mko-классах и рендер-сервисах.
- **Размер**: ~150 полей (см. live-документацию по конкретным группам).

### `/sm-karaoke/system/Karaoke.properties` — внешний файл

- **Где**: на прод-сервере, в `/sm-karaoke/system/Karaoke.properties`.
- **Backup**: `/sm-karaoke/system/Karaoke.properties.bak` (создаётся
  автоматически перед каждой записью).
- **Формат**: Java properties (key=value).

### Admin UI — KaraokePropertiesView.vue

- **Поведение**: редактирование параметров через webvue3.
- **Сохранение**: PATCH `/api/karaoke-properties/save` → обновляет
  файл и in-memory биндинг.

## Логика и Алгоритмы | Logic and Algorithms

### Группы параметров

Параметры сгруппированы по домену рендера:

| Группа | Что описывает | Кол-во |
| --- | --- | --- |
| `video.*` | Размер (1280×720 / 1920×1080), fps, codec | ~10 |
| `lyrics.*` | Шрифт, размер, цвет, подсветка, тени | ~30 |
| `background.*` | Градиент, blur, opacity, image-fit | ~15 |
| `audio.*` | Громкость вокала/аккомпанемента по версии | ~5 |
| `marker.*` | Цвета маркеров на таймлайне, толщина | ~8 |
| `fade.*` | In/Out длительность, кривая | ~5 |
| `karaoke-title.*` | Заголовок песни: шрифт, позиция, тень | ~10 |
| `demo-fragment.*` | Стартовая/конечная секунда DEMO | ~3 |
| ... | (прочие группы) | ~60 |

### Цикл обновления параметра

```
User in Admin UI → KaraokePropertiesView.vue
  ↓ PATCH /api/karaoke-properties/save
KaraokePropertiesService.save(newProperties)
  ↓
[1] Backup: cp Karaoke.properties Karaoke.properties.bak
[2] Write new values to file
[3] Reload Spring @ConfigurationProperties bean
  ↓
[4] Все активные рендер-сервисы видят новые значения при следующем кадре
```

**[WARN]** Шаг [3] не прерывает уже идущие рендеры — они используют
in-memory snapshot, захваченный в начале. Чтобы изменение вступило в
силу, нужно дождаться завершения текущего рендера.

## Зависимости | Dependencies

- → [domain](../domain.md) — используется во всех mko-классах.
- → [playwright-rendering](playwright-rendering.md) — читает параметры
  для каждого кадра.
- → [rendering domain](../rendering/domain.md) — `MLTProject` строится
  на основе параметров.

## Ловушки и предупреждения

**[WARN] Прямая правка `Karaoke.properties` без UI** → UI-state
рассинхронизируется, при следующем сохранении через UI ваши изменения
будут перезаписаны. Правьте только через `KaraokePropertiesView.vue`.

**[WARN] Изменения не применяются к активным рендерам** — нужно
дождаться завершения.

**[WARN] Параметр `video.codec`** — если переключить с `libx264` на
`libx265`, время рендера вырастет в 5-10 раз. Не делать без миграционного
эпика.

**[WARN] `audio.vocals.volume` и `audio.accompaniment.volume`** —
изменение вступает в силу только для **новых** рендеров; существующие
MP4 в MinIO не перекодируются автоматически. Для перекодировки —
отдельный эпик «re-render all».

## Связанные ADR | Related ADRs

- ADR по admin UI для KaraokeProperties (TODO).

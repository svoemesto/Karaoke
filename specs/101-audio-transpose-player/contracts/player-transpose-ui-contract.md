# Контракт: UI транспонирования в плеере админки

**Feature**: 101-audio-transpose-player
**Date**: 2026-07-31
**Related**: [spec.md](../spec.md) (FR-001..FR-018), [data-model.md](../data-model.md)

Контракт описывает пользовательский интерфейс фичи в плеере админки (`webvue3/src/player/KaraokePlayer.js`): структуру меню, бейдж, публичный JS-API инстанса плеера, и контракты с существующими компонентами плеера. Фича не вводит новых серверных API и не меняет `playerdata`-ответ (см. [data-model.md](../data-model.md)).

## 1. Меню плеера — пункт «Тональность» (FR-001, FR-002, FR-003, FR-007)

### Расположение

Новый пункт «Тональность» в существующем меню плеера (`#kp-menu`), рядом с уже существующим пунктом «Скорость» (`#kp-menu-speed`). Меню открывается по клику на `#kp-menu-btn` (без изменений в wiring открытия/закрытия — переиспользуется существующий `_closeMenu`).

### HTML-структура (параллель `#kp-submenu-speed`)

```html
<div class="kp-menu-parent" id="kp-menu-transpose">
  <span class="kp-menu-label">Тональность</span>
  <span class="kp-menu-value" id="kp-transpose-label">0</span>
  <div class="kp-submenu" id="kp-submenu-transpose">
    <!-- 25 пунктов: от -12 до +12, генерируются в _buildUI из TRANSPOSE_OPTIONS -->
    <div data-transpose="-12">(-12) &lt;результирующая&gt;</div>
    ...
    <div data-transpose="0">(0) &lt;базовая&gt;</div>
    ...
    <div data-transpose="12">(+12) &lt;результирующая&gt;</div>
  </div>
</div>
```

- `data-transpose` — целое число −12..+12.
- Текст пункта — вычисляется `_transposeLabel(n)` (см. [data-model.md](../data-model.md), §Результирующая тональность). Примеры: `(0) Am`, `(+3) Cm`, `(-2) Gm`; при пустом `data.key` — `(0)`, `(+3)`, `(-2)` (FR-013).
- CSS-классы и поведение подменю (hover/click) — повторяют `#kp-submenu-speed` (см. `_buildMenu` speed-блок).

### Состояния

| Состояние | Условие | Визуально |
|-----------|---------|----------|
| Активно, базовое | `_transposeSupported === true` | Подменю раскрывается, пункты кликабельны. |
| Активно, выбран ненулевой сдвиг | `_transpose ∈ [-12, +12] \ {0}` | Текущий пункт подсвечен (`background: #08f; color: #fff` — параллель `_updateSpeedMenu`). `#kp-transpose-label` показывает краткую подпись (напр. `+3 Cm` или `+3` при пустом key). |
| Базовая тональность (сдвиг 0) | `_transpose === 0` | Подсвечен пункт `(0)`; `#kp-transpose-label` = `0` (или пусто). |
| Не поддерживается браузером | `_transposeSupported === false` (FR-018) | Подменю видно, но все 25 пунктов заблокированы (`pointer-events: none; opacity: 0.5`), над/под подменю — подсказка «Браузер не поддерживает». Родительский пункт «Тональность» не кликабелен (или клик показывает подсказку). |

### Wiring (точки расширения в `KaraokePlayer.js`)

| Метод | Действие |
|-------|---------|
| `_buildUI` (HTML-шаблон) | Добавить блок `#kp-menu-transpose` + сгенерировать 25 `<div data-transpose>` из статического массива `KaraokePlayer.TRANSPOSE_OPTIONS = [-12..+12]` (параллель `SPEED_OPTIONS`). |
| `_buildMenu` | Добавить wiring (параллельно `speedItem`): toggle `kp-submenu-open` по клику на `#kp-menu-transpose`; по клику на `[data-transpose]` → `this._closeMenu(); this.setTranspose(Number(el.dataset.transpose))`. |
| `_updateTransposeMenu` (новый) | Параллель `_updateSpeedMenu`: обновить `#kp-transpose-label` и подсветить активный `[data-transpose]`. Вызывается из `setTranspose` и после `init`/`playSong` (когда `data.key` известен и подписи пунктов пересчитываются). |
| `init` (после готовности `data`) | Пересчитать подписи пунктов подменю от `data.key` (если `key` изменился); восстановить `_transpose` из localStorage; `_updateTransposeMenu()`. |

## 2. Бейдж тональности на экране (FR-008, FR-009, FR-010)

### Расположение и стиль

Рисуется на canvas (в существующем render-loop) в правом верхнем углу, **под** бейджем скорости. Параллель существующему `_renderSpeedBadge`:

| Аспект | Бейдж скорости (существующий) | Бейдж тональности (новый) |
|-------|-------------------------------|---------------------------|
| Цвет | `#f80` (оранжевый) | `#08f` (синий) — FR-008 |
| Фон | `rgba(0,0,0,0.55)` | `rgba(0,0,0,0.55)` |
| Позиция Y | `margin` (сверху) | `margin + speedBadgeHeight + gap` (если бейдж скорости активен) или `margin` (если speed = 1x, бейджа скорости нет) |
| Позиция X | `W - boxW - margin` (правый край) | То же (правый край, выравнивание по правой стороне) |
| Шрифт | `700 ${fs}px sans-serif` | То же |
| Форма | `roundRect` (скруглённая, «таблетка») | То же |
| Подпись | `${rate}x` (напр. `0.75x`) | `_transposeLabel(n)` без скобок: `+3 Cm` / `-2 Gm` / `+3` (пустой key) |
| Условие показа | `_playbackRate !== 1` | `_transpose !== 0` (FR-010) |
| Условие скрытия | rate = 1 | n = 0 |

### Метод

`_renderTransposeBadge(ctx, W, H)` — параллель `_renderSpeedBadge`. Вызывается из `_renderFrame` (или где вызывается `_renderSpeedBadge`) **после** `_renderSpeedBadge`, чтобы знать высоту/наличие бейджа скорости для позиционирования под ним (FR-009 — не перекрывать).

## 3. Публичный JS-API инстанса `KaraokePlayer`

Новые public-методы/свойства (с JSDoc + `@see docs/features/player-transpose.md` — Constitution FR-006):

| API | Сигнатура | Описание |
|-----|-----------|---------|
| `setTranspose(n)` | `setTranspose(n: number): void` | Установить сдвиг тональности для текущей песни. Валидирует `n ∈ [-12, +12]` целое; иначе no-op. Сохраняет в localStorage per-song; применяет `pitch = n` ко **всем** pitch-shift узлам в `_pitchShifts` (Map по стемам — acc/voc сейчас, bass/drums/прочие при появлении); обновляет подменю и бейдж. Аналог `setPlaybackRate` по бесшовности. |
| `transpose` (getter) | `get transpose(): number` | Текущий сдвиг (0 если базовая). |

Не-public (внутренние): `_transpose`, `_transposeSupported`, `_pitchShifts` (Map), `_transposeLabel(n)`, `_updateTransposeMenu()`, `_renderTransposeBadge(ctx, W, H)`, `_saveTranspose()` (или расширенный `_savePersistedSettings`), `_ensurePitchShift(stemKey)` (lazy-создание узла для стема).

## 4. Контракт с существующими компонентами плеера

| Компонент | Взаимодействие | Контракт |
|-----------|---------------|----------|
| `setPlaybackRate` / `_playbackRate` | Независимое сосуществование (FR-014) | `setTranspose` НЕ трогает `_playbackRate`; `setPlaybackRate` НЕ трогает `_transpose`. Темп управляется `playbackRate`, высота — `pitchShift.pitch` (на всех стемах синхронно). `_getCurrentTime()` формула неизменна (темп = `_playbackRate`, не затрагивается pitch-shift'ом). |
| `_startAudio(offset)` | Вставка pitch-shift узлов в граф (по стемам) | Для каждого проигрываемого стема (acc/voc сейчас; bass/drums при появлении): `source.connect(this._pitchShifts.get(stemKey))` вместо прямой `source.connect(gain)`; `this._pitchShifts.get(stemKey).connect(gain)`. `source.playbackRate.value = _playbackRate` — без изменений. Pitch-shift узлы созданы заранее (lazy через `_ensurePitchShift(stemKey)` в `init`/при первом появлении стема), не пересоздаются в `_startAudio`. |
| `playSong(songId, ...)` | Сброс + восстановление per-song сдвига | Перед `init()`: `this._transpose = 0; this._updateTransposeMenu()`. После `init()` (когда `data.id` загружен): `this._transpose = localStorage.getItem('kp_transpose_<data.id>') \|\| 0`. |
| `_savePersistedSettings` / `LS_SETTINGS_KEY` | НЕ расширяется | `_transpose` хранится в **отдельном** per-song ключе (`kp_transpose_<songId>`), не в глобальном `LS_SETTINGS_KEY`. `_saveTranspose()` — отдельный метод, вызывается из `setTranspose`. |
| `_renderSpeedBadge` | Позиционирование под ним | `_renderTransposeBadge` запрашивает у `_renderSpeedBadge`/общего состояния высоту/наличие бейджа скорости для Y-смещения. Без перекрытия (FR-009). |
| `data.key` (от `playerdata`) | Источник базовой тональности | Без изменений бэкенда. `null` при пустом `key` (см. FR-013, [data-model.md](../data-model.md)). |

## 5. Серверный контракт (БЕЗ изменений)

Явно: фича **не вводит** и **не меняет** ни одного серверного эндпоинта, поля БД, схемы или контракта синхронизации.

- `GET /api/song/{id}/playerdata` (admin, `ApiController.kt:6823`) — отдаёт `data.key` уже сейчас (строка `6864`): `"key" to settings.key.takeIf { it.isNotBlank() }`. Без изменений.
- `GET /api/public/player/{id}/playerdata` (public, `PublicPlayerController.kt:341`) — аналогично (`397`). Без изменений и НЕ используется в этой фазе (FR-017 — public не затрагивается).
- `tbl_settings.key` — без изменений. `SyncRegistry` — без изменений (новых сущностей нет). `recordhash`-триггеры — без изменений (колонки не меняются).

Транспонирование — полностью клиентское; `localStorage` браузера не синхронизируется между LOCAL/SERVER и не попадает в diff/recordhash.
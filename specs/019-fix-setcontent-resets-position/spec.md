# 019 — Fix: setContent/setOptions в updateMarkersBySyllables сбрасывают позиции маркеров

> **Дата**: 2026-07-28
> **Автор**: opencode-агент
> **Branch**: `019-fix-setcontent-resets-position`
> **PR (целевой)**: #91 (после #90)
> **Зависит от**: #90 (PR #90, commit `e83bdff`), specs/018-fix-spec-tag-markers-at-zero
> **Pass**: 31 (Pass 30 — #018)

## Что случилось

PR #88 (Pass 29, спека #017) исправил «залипание маркеров в нуле» на ПЕРВОМ открытии.
PR #90 (Pass 30, спека #018) добавил safeguard в `syncMarkersFromSpecTags` для тонких gap'ов.

После обоих фиксов на ПЕРВОМ открытии наблюдается **остаточная регрессия**:
- Сначала все маркеры (syllables, eol, beat, spec tags) отрисовываются **правильно** на вейвформе.
- Затем **ещё раз** те же маркеры отрисовываются, но **в позиции 0** — «красная линия в нуле» возвращается.

При этом **в массиве `sourceMarkers` лишних маркеров нет** — данные правильные, регресс чисто в рендеринге.

## Первопричина (локализация)

В `updateMarkersBySyllables()` (`SubsEdit.vue:3401-3424`) для syllables-маркеров вызываются:

```js
marker.region.setOptions({ color: color })        // <-- виновник #1
marker.region.setContent(this.getRegionContentFromMarker(marker))  // <-- виновник #2
```

В wavesurfer regions-plugin (`v7.x`) эти вызовы **перерисовывают регион на вейвформе**.
В зависимости от версии/состояния плагина перерисовка использует **внутренний кешированный
`start`** или рендерит регион заново из defaults — в обоих случаях визуально все маркеры
оказываются в `start: 0` (потому что **до audio decode в `addRegion` регион создаётся с
`start: marker.time`, но `pixelsPerSecond=NaN`**, и при последующей перерисовке без
пересоздания `start` остаётся «застрявшим» в нуле).

Цепочка событий на свежем открытии:
```
1. mounted(): createRegionMarker → addRegion({ start: marker.time, ... })
   Регион создан с правильным start, но pixelsPerSecond=NaN (audio не загружен).
2. ws.on('decode'): clearRegions + пересоздание addRegion({ start: marker.time, ... })
   Регионы созданы заново с правильным start, pixelsPerSecond уже валиден.
   Юзер видит правильные маркеры на вейвформе — ПЕРВЫЙ РЕНДЕР ✓
3. mounted() → sourceText = ... → sourceText watcher
   → updateMarkersBySyllables()
   → marker.region.setOptions({ color: ... })  // <-- ВОТ ТУТ СБРОС
   → marker.region.setContent(...)
   → перерисовка регионов в позиции 0 — ВТОРОЙ РЕНДЕР ✗
4. sourceSyllables watcher → updateMarkersBySyllables() — повторный сброс
5. syncMarkersFromSpecTags() — добавляет spec tag-маркеры (с safeguard из #018, time ≥ 0.5)
```

После шага 3 на вейвформе видны:
- Правильно отрисованные маркеры из шага 2
- + «красная линия в нуле» из шага 3 (старые регионы перерисованы в start: 0)

## Скоуп

**Только `webvue3/src/components/Songs/edit/SubsEdit.vue`**, watchers `sourceSyllables` и `sourceText`. Добавлен явный вызов `redrawMarkers()` после `updateMarkersBySyllables()` — это clearRegions + пересоздание ВСЕХ регионов из `sourceMarkers` с актуальным `marker.time`, что исправляет сброс.

**НЕ в скоупе**:
- Изменения в `updateMarkersBySyllables` (не трогаем setContent/setOptions — они нужны для обновления label/color).
- Изменения в wavesurfer.
- `applyAutoMarkersToEditor`, `currentVoice` watcher, `decode` handler.
- Backend, `karaoke-public`, схема БД, Vuex-стор.

## Что меняется в коде (дифф-мысль)

**Было (после #90)**:
```js
sourceSyllables: { handler() { this.updateMarkersBySyllables() } },
sourceText: {
  handler() {
    this.sourceSyllables = this.getSyllables
    this.updateMarkersBySyllables()
    this.syncMarkersFromSpecTags()
    this.tail = this.getTail
    // ...
  },
},
```

**Стало (фикс #019)**:
```js
sourceSyllables: {
  handler() {
    this.updateMarkersBySyllables()
+   this.redrawMarkers()  // FIX #019
  },
},
sourceText: {
  handler() {
    this.sourceSyllables = this.getSyllables
    this.updateMarkersBySyllables()
    this.syncMarkersFromSpecTags()
+   this.redrawMarkers()  // FIX #019
    this.tail = this.getTail
    // ...
  },
},
```

`redrawMarkers()` = `clearRegions() + for { createRegionMarker(marker) }` — то же, что
`ws.on('decode')` handler делает вручную. Пересоздаёт ВСЕ регионы с актуальным
`marker.time`, что и нужно для исправления сброса.

## План проверки

### Линтеры / тесты / сборка
- [ ] `cd webvue3 && npm run lint:check` — 0 errors
- [ ] `./gradlew ktlintCheck` — BUILD SUCCESSFUL
- [ ] `bash tools/check-jsdoc-coverage.sh webvue3` — 100% (134/134)
- [ ] `./gradlew :karaoke-app:test --tests "SpecTagsTest" --tests "WhisperMarkerAlignerSpecTagsTest"` — PASS

### Ручные сценарии (после `deploy_web.sh`, на работающем `karaoke-app`)
- [ ] **Сценарий A** (баг-репро): открыть «Костёр» Машина Времени — все маркеры на правильных позициях, **красной полосы в нуле нет**
- [ ] **Сценарий B** (без спецтегов): любая песня без тегов — регрессии нет
- [ ] **Сценарий C** (со спецтегами, нормальный gap): все spec tag-маркеры на правильных местах
- [ ] **Сценарий D** (Apply «Точные маркеры»): все маркеры на местах
- [ ] **Сценарий E** (reopen): открыть → Save → close → reopen — все маркеры на местах

## Нумерация

`019-` — следующий свободный номер после `018-` (Pass 30, PR #90). Других параллельных веток
с этим номером не было.

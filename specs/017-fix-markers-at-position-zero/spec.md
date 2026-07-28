# 017 — Spec tags: маркеры в нулевой позиции после фикса #016

> **Дата**: 2026-07-28
> **Автор**: opencode-агент
> **Branch**: `017-fix-markers-at-position-zero`
> **PR (целевой)**: #86 (после #85)
> **Зависит от**: #85 (PR #85, commit `c33dc91`), specs/016-fix-spec-tags-marker-loss-on-reopen
> **Pass**: 29 (Pass 28 — #016)

## Что случилось

PR #85 (Pass 28) перенёс `loadedMarkers` + создание регионов из `ws.on('decode')` в `mounted()` **синхронно ДО** `this.sourceText = await ...`. Это устранило гонку `sourceText` watcher ↔ `ws.on('decode')` для спецтегов (см. research.md §2.2 спеки #016), но породило **новую регрессию**: на ПЕРВОМ открытии любой песни (не только со спецтегами) **все маркеры в вейвформе «залипают» в позиции 0** — выглядит как толстая красная линия на старте таймлайна, реальные маркеры (syllables, eol, beat, …) пропадают.

## Репро (минимум)

**Сценарий А** (баг-репро):
1. Открыть любую песню в `SubsEdit.vue` (например, «Костёр» Машина Времени, год 1991, альбом «Медленная хорошая музыка»).
2. Загрузить вейвформу.
3. **Ожидание**: маркеры видны на своих таймкодах (например, syllables-маркеры распределены по всей длительности).
4. **Факт**: все маркеры «слиплись» в одну красную линию на `time = 0`. Таймкод `Time: 39.624` показан, но маркеров в районе 39 сек нет.

Скриншот: [от пользователя, 2026-07-28] — красная линия на старте таймлайна, маркеры видны только в позиции 0.

## Первопричина

`addRegion({ start: marker.time, ... })` (внутри `createRegionMarker()`) **до загрузки аудио** создаёт регион в wavesurfer, но wavesurfer не может его корректно спозиционировать: `pixelsPerSecond` рассчитывается из `duration`, а `duration === 0` пока аудио не загружено. Регион «залипает» в позиции 0 (или в начале видимой области вейвформы).

Затем `ws.on('decode')` срабатывает ПОСЛЕ загрузки аудио и вызывает `wsRegions.clearRegions()` — **сносит все регионы, созданные в `mounted()`**. Восстановления нет: в новом `decode`-handler цикл `createRegionMarker` убран намеренно (см. JSDoc в `SubsEdit.vue:2669-2674`, `specs/016/research.md §2.2`).

В итоге:
- `marker.region` в `sourceMarkers` указывает на **удалённый** wavesurfer-объект.
- При следующем `redrawMarkers()` (например, переключение `markerTypesToShow`) регионы пересоздаются на правильных позициях — баг «самопроизвольно» исправляется.
- Но на свежем открытии, до первого `redrawMarkers()`, пользователь видит «красную линию в нуле» — это и есть удалённые, но ещё не убранные из DOM регионы.

**Хронология событий на свежем открытии (с PR #85)**:

```
1. mounted(): createRegionMarker()  → addRegion({ start: 39.624, ... })
   wavesurfer.addRegion: регион создан, но duration=0, pixelsPerSecond=NaN
   → регион «залипает» в позиции 0
2. mounted(): sourceText = await ...  → на следующем микротаске срабатывает sourceText watcher
3. ws.on('decode')  → clearRegions()  → удаляет регион, созданный в шаге 1
   маркер.region указывает на «мёртвый» объект, но в DOM он ещё висит
4. UI рендерит вейвформу — пользователь видит «красную линию в нуле»
```

В **PR до #85** (когда регионы создавались в `ws.on('decode')`) такой проблемы не было, потому что:
- `ws.on('decode')` срабатывал ТОЛЬКО после загрузки аудио,
- `createRegionMarker()` вызывался с известным `duration`,
- регионы сразу создавались на правильных позициях.

## Скоуп

**Только `webvue3/src/components/Songs/edit/SubsEdit.vue`.** Один файл. Минимальное вмешательство:
- `mounted()`: поднять настройку `ws.on('decode', ...)` ДО первого `await`; handler теперь не только чистит регионы, но и **пересоздаёт** их из `sourceMarkers` (если они уже заполнены).
- `mounted()`: после заполнения `sourceMarkers` добавить ручной trigger: если `this.duration > 0` (аудио уже загружено, `decode` сработал раньше), пересоздать регионы немедленно.
- `currentVoice` watcher: НЕ трогаем — там аудио уже загружено, `createRegionMarker` отрабатывает корректно.

**НЕ в скоупе** (явно):
- Изменения в `applyAutoMarkersToEditor`, контракте спецтегов, backend, `karaoke-public`, схеме БД.
- US2/US3 из спеки #016 (рассинхрон `sourceSyllables`, уведомление пользователю) — отдельный PR.
- Рефакторинг `redrawMarkers` / `updateMarkersBySyllables`.

## Что меняется в коде (дифф-мысль)

**Было (после #85, commit `776ffa9` → `c33dc91`)**:
```js
async mounted() {
  this.initWavesurfer()                        // (1) старт audio load
  this.isEditMode = true
  this.loadedMarkers = await getSourceMarkers  // (2) ждём маркеры
  for (...) {                                  // (3) создаём регионы (audio ещё не загружен!)
    marker.region = this.createRegionMarker(marker)
    this.sourceMarkers.push(marker)
  }
  this.createBeatMarkers()
  this.sourceText = await getSourceText        // (4) ждём текст
  this.indexTabsVariant = await ...
  this.ws.on('decode', () => {                 // (5) handler ставим ПОЗЖЕ старта загрузки
    this.duration = this.ws.getDuration()
    ...
    this.wsRegions.clearRegions()              // (6) сносит регионы из шага 3
    // нет re-create!
  })
  // ... остальные handlers
}
```

**Стало (фикс #017)**:
```js
async mounted() {
  this.initWavesurfer()                        // (1) старт audio load
  this.isEditMode = true

  // (NEW) Поднимаем настройку decode-handler ДО первого await — иначе если аудио
  // загрузится быстрее маркеров, handler никогда не сработает.
  this.ws.on('decode', () => {
    this.duration = this.ws.getDuration()
    if (this.visibleStartTime < 0) this.visibleStartTime = 0
    if (this.visibleEndTime < 0) this.visibleEndTime = this.duration
    this.wsRegions.clearRegions()
    // (NEW) Re-create регионов: они либо уже заполнены (если маркеры пришли раньше
    // аудио), либо ещё нет (если аудио пришло раньше — тогда ничего не делаем,
    // и mounted() досоздаст их вручную после await getSourceMarkers).
    for (let i = 0; i < this.sourceMarkers.length; i++) {
      this.sourceMarkers[i].region = this.createRegionMarker(this.sourceMarkers[i])
    }
  })

  this.loadedMarkers = await getSourceMarkers  // (2) ждём маркеры
  for (...) {                                  // (3) создаём регионы
    marker.region = this.createRegionMarker(marker)
    this.sourceMarkers.push(marker)
  }
  this.createBeatMarkers()

  // (NEW) Если аудио уже загружено (decode сработал раньше, чем мы дождались маркеров),
  // регионы в шаге 3 созданы с duration=0 и «залипли» в позиции 0. Пересоздаём.
  if (this.duration > 0) {
    this.wsRegions.clearRegions()
    for (let i = 0; i < this.sourceMarkers.length; i++) {
      this.sourceMarkers[i].region = this.createRegionMarker(this.sourceMarkers[i])
    }
  }

  this.sourceText = await getSourceText        // (4) ждём текст
  this.indexTabsVariant = await ...
  // (REMOVED) старый ws.on('decode') — перенесён выше
  // ... остальные handlers
}
```

**Ключевые инварианты, которые НЕ должны быть нарушены**:
- `sourceMarkers` заполняется ДО `sourceText = await ...` (требование спеки #016, иначе
  `syncMarkersFromSpecTags` вставит spec tag-маркеры в позицию 0 через `splice(0,0,...)`).
- `syncMarkersFromSpecTags` имеет защитный гард `if (this.sourceMarkers.length === 0) return`
  (добавлен в #016) — на случай, если watcher `sourceText` сработает до заполнения маркеров.
- `createBeatMarkers` создаёт регионы для beat-маркеров; если аудио не загружено, эти регионы
  тоже «залипнут» в нуле, но будут пересозданы в `ws.on('decode')` (новый re-create) или в
  ручном trigger после `await getSourceMarkers`.

## План проверки

### Линтеры / типы / сборка
- [ ] `cd webvue3 && npm run lint:check` — 0 errors
- [ ] `cd webvue3 && npm run build` — успешная сборка
- [ ] `bash tools/check-jsdoc-coverage.sh webvue3` — 100% (134/134)
- [ ] `./gradlew ktlintCheck` — BUILD SUCCESSFUL
- [ ] `./gradlew :karaoke-app:test --tests "SpecTagsTest" --tests "WhisperMarkerAlignerSpecTagsTest"` — PASS

### Ручные сценарии (после `deploy_web.sh`, на работающем `karaoke-app`)
- [ ] **Сценарий A** (баг-репро): открыть любую песню → все маркеры на своих таймкодах (НЕ в нуле)
- [ ] **Сценарий B** (со спецтегами): «Костёр» Машина Времени — маркеры + ~Припев~ маркеры на правильных позициях
- [ ] **Сценарий C** (без спецтегов): любая песня без тегов — только syllables/eol/beat, всё на местах
- [ ] **Сценарий D** (переключение голоса): с голоса 0 на голос 1 и обратно — маркеры обоих голосов на местах
- [ ] **Сценарий E** (reopen): открыть → Save → close → reopen — маркеры на местах (это и был сценарий A из #016)
- [ ] **Сценарий F** (Apply «Точные маркеры»): на песне со спецтегами после Apply все маркеры на местах
- [ ] **Сценарий G** (быстрый кэш аудио): если аудио уже в кэше браузера, `decode` сработает ДО `getSourceMarkers` — проверяем, что ручной trigger `if (this.duration > 0)` корректно пересоздаёт регионы

## Нумерация

`017-` — следующий свободный номер после `016-` (Pass 28, PR #85). Других параллельных веток
с этим номером не было (на момент 2026-07-28 в `ls specs/` нет `017-`).

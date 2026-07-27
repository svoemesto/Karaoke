# Research: Спецтеги — потеря маркеров после «Точные маркеры → Apply → Save → reopen»

**Input**: spec.md, FR-001..FR-011
**Phase**: 0 (исследование первопричины и дизайн фикса)

## 1. Проблема и наблюдаемое поведение

**Сценарий пользователя** (воспроизведён):

1. Открыть `SubsEdit.vue` для голоса, в `sourceText` которого есть спецтеги (`~Припев~` и т.п.) и в БД уже есть размеченные маркеры.
2. Нажать «Точные маркеры» → дождаться модалку → нажать «Применить маркеры к голосу».
3. Маркеры видны на экране.
4. Нажать Save.
5. Закрыть редактор.
6. Открыть редактор снова.
7. **Маркеров нет** (только спецтег-маркеры, автоподставленные `syncMarkersFromSpecTags`, без syllable/endofline/beat).

**На песнях БЕЗ спецтегов** тот же сценарий работает корректно — все маркеры на месте после reopen.

## 2. Корневые механизмы (анализ кода)

Прочитан `webvue3/src/components/Songs/edit/SubsEdit.vue` целиком (5711 строк), плюс `store.js:948-962` (`getSourceMarkers`), `store.js:2490-2500` (`saveSourceTextAndMarkers`), `controllers/ApiController.kt:3411-3448` (`saveSourceTextAndMarkers`), `model/Song.kt:3328-3355` (`setSourceMarkers`).

### 2.1 Save / Load — оба корректны, не источник бага

- `save()` (`SubsEdit.vue:2854-2862`) → `getMarkersToSave()` (`SubsEdit.vue:2863-2877`) шлёт ВЕСЬ `this.sourceMarkers` (с `time/label/note/chord/stringLad/locklad/color/position/markertype`) в `saveSourceTextAndMarkers`.
- Backend `ApiController.saveSourceTextAndMarkers` (`ApiController.kt:3411-3448`) парсит JSON и зовёт `settings.setSourceMarkers(voice, markers)` (`Song.kt:3328-3355`), который через `lst[voice] = markers` + `Json.encodeToString` + `saveToDb()` сохраняет полный список.
- `getSourceMarkers` (`store.js:948-962`) возвращает полный отсортированный список через `/api/song/voicesourcemarkers` (отдельный эндпойнт, не фильтрует спецтег-маркеры).

**Вывод**: данные в БД полные, на чтение приходят полные. Потеря происходит **до** записи или **на пути из БД в UI**, но не в БД.

### 2.2 ПЕРВОПРИЧИНА: `ws.on('decode')` имеет предохранитель, который отключает загрузку маркеров на ПЕРВОМ открытии при наличии спецтегов

`SubsEdit.vue:2619-2657` (начало `mounted()`):

```js
async mounted() {
  this.initWavesurfer()  // ← вызывает ws.load() асинхронно; «decode» прилетит потом
  this.isEditMode = true
  this.sourceText = await this.$store.getters.getSourceText(this.currentVoice)  // ← ШАГ A
  this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice)  // ← ШАГ B
  this.indexTabsVariant = await this.$store.getters.getIndexTabsVariant
  ...
  this.ws.on('decode', () => {  // ← регистрация обработчика
    ...
    if (this.loadedMarkers.length > 0 && this.sourceMarkers.length === 0) {  // ← ШАГ C
      for (let index = 0; index < this.loadedMarkers.length; index++) {
        let marker = Object.assign({}, this.loadedMarkers[index])
        if (
          (marker.markertype === 'setting' && marker.label && marker.label === 'COMMENT| ') ||
          (marker.markertype === 'syllables' && marker.label && marker.label.trim() === '')
        ) {
          console.log('ignored')
        } else {
          marker.region = this.createRegionMarker(marker)
          this.sourceMarkers.push(marker)
        }
      }
      this.createBeatMarkers()
    }
  })
  ...
}
```

Watcher на `sourceText` (`SubsEdit.vue:2168-2178`):

```js
sourceText: {
  handler() {
    this.sourceSyllables = this.getSyllables
    this.updateMarkersBySyllables()         // ← пустой sourceMarkers → noop
    this.syncMarkersFromSpecTags()           // ← ОПАСНО: sourceMarkers ещё []
    ...
  },
},
```

**Реальный порядок выполнения при ПЕРВОМ открытии (с расстановкой по микротаскам Vue 2):**

| # | Что происходит | Состояние `sourceMarkers` | Состояние `loadedMarkers` |
|---|---|---|---|
| 1 | `mounted()` стартует, `initWavesurfer()` асинхронно грузит аудио | `[]` (data) | `[]` (data) |
| 2 | `this.sourceText = await getSourceText(0)` — await, потом присваивание | `[]` | `[]` |
| 3 | `this.loadedMarkers = await getSourceMarkers(0)` — await, потом присваивание | `[]` | `[...N маркеров из БД...]` |
| 4 | Регистрация `ws.on('decode', ...)` | `[]` | заполнен |
| 5 | `mounted()` завершается, Vue 2 сбрасывает очередь watchers | `[]` | заполнен |
| 6 | **Watcher на `sourceText` срабатывает** (он был поставлен в очередь на ШАГ 2) | `[]` | заполнен |
| 7 | Watcher зовёт `syncMarkersFromSpecTags()` с пустым `sourceMarkers` | `[]` | заполнен |
| 8 | Внутри `syncMarkersFromSpecTags()`: `syllablePositions = []` (нет syllables-маркеров) | `[]` | заполнен |
| 9 | Для каждого anchor `insertPos = this.sourceMarkers.length = 0` | `[]` → `[]` | заполнен |
| 10 | Все spec tag-маркеры вставляются через `splice(0, 0, newMarker)` (один за другим, но все в позицию 0) | `[spec-tag-1, spec-tag-2, ...]` (N штук) | заполнен |
| 11 | Позже `ws.load()` завершает декодирование, `ws.on('decode')` срабатывает | `[spec-tag-1, spec-tag-2, ...]` | заполнен |
| 12 | Условие `this.loadedMarkers.length > 0 && this.sourceMarkers.length === 0` — **ЛОЖЬ** (длина > 0) | `[spec-tag-1, spec-tag-2, ...]` | заполнен, но НЕ ПРИМЕНЕНО |
| 13 | Цикл по `loadedMarkers` НЕ выполняется. `createBeatMarkers()` НЕ вызывается. | `[spec-tag-1, spec-tag-2, ...]` | — |

**Результат на UI**: только что вставленные в ШАГ 10 spec tag-маркеры. Ни syllable, ни endofline, ни newline, ни group, ни comment, ни beat — **всё, что было в БД, потеряно в UI**. На последующий Save уезжает то, что в UI — снова только spec tag-маркеры. Цикл «применил-сохранил-открыл» каждый раз усугубляет потерю.

**Это и есть наблюдаемый пользователем баг.** «Точные маркеры + Apply + Save» — обёртка, маскирующая первопричину. Сам баг живёт на «первое открытие голоса со спецтегами» (или на «открытие после save+reopen» — потому что watcher `sourceText` срабатывает и при `saveSourceTextAndMarkers`, см. ниже).

### 2.3 Второй механизм: watcher `sourceText` срабатывает ДО загрузки маркеров

Корень — порядок вызовов внутри `mounted()` (и в `currentVoice` watcher'е, см. 2.4). Watcher на `sourceText` срабатывает на следующий микротаск после присваивания `this.sourceText = ...`. К этому моменту `loadedMarkers` может быть уже прочитан, но `sourceMarkers` **ещё не заполнен** через `ws.on('decode')` (который ждёт декодирования аудио — десятки-сотни мс, иногда секунды на длинных треках).

**Дополнительный под-механизм**: даже если бы `ws.on('decode')` сработал раньше watcher'а (теоретически), сам `ws.on('decode')` имеет предохранитель `sourceMarkers.length === 0`, чтобы не «дублировать» маркеры при повторной загрузке трека. Этот предохранитель ломает сценарий 2.2.

### 2.4 Третий механизм (связанный): watcher `currentVoice` имеет ту же проблему при переключении голоса

`SubsEdit.vue:2105-2138`:

```js
currentVoice: {
  async handler() {
    if (this.currentVoice === this.dataVoices.length) { ... }
    this.sourceText = await this.$store.getters.getSourceText(this.currentVoice)  // ← ШАГ X
    this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice)
    this.sourceMarkers = []
    this.wsRegions.clearRegions()
    if (this.loadedMarkers.length > 0) {
      for (let index = 0; index < this.loadedMarkers.length; index++) {
        let marker = Object.assign({}, this.loadedMarkers[index])
        if (...) {} else { this.sourceMarkers.push(marker) }
      }
      this.createBeatMarkers()
    }
    this.syncMarkersFromSpecTags()  // ← вызывается явно ПОСЛЕ заполнения
  },
},
```

Здесь `this.sourceText = await ...` тоже ставит watcher в очередь. Watcher может сработать на следующем микротаске — и к этому моменту `sourceMarkers = []` уже выполнен, но `loadedMarkers` и цикл ещё не выполнены (ещё один `await` для `getSourceMarkers`). Поэтому:

| # | Что | `sourceMarkers` |
|---|---|---|
| X.1 | `this.sourceText = await ...` (await #1) | `[OLD voice markers]` |
| X.2 | watcher `sourceText` поставлен в очередь, выполняется после await #1 (ещё до await #2 для markers) | `[OLD voice markers]` |
| X.3 | watcher вызывает `syncMarkersFromSpecTags()` с **OLD markers** | `[OLD markers, + new spec tag markers из NEW text, вставленные в неправильные окна]` |
| X.4 | `this.loadedMarkers = await getSourceMarkers(...)` | `[OLD + new spec tag мусор]` |
| X.5 | `this.sourceMarkers = []` | `[]` |
| X.6 | Цикл заполняет `sourceMarkers` из `loadedMarkers` | `[NEW voice real markers]` |
| X.7 | `createBeatMarkers()` | `[NEW voice real markers + beat]` |
| X.8 | `this.syncMarkersFromSpecTags()` (явный вызов) | `[NEW voice real + spec tag markers]` |

**Между ШАГАМИ X.3 и X.5 sourceMarkers содержит мусор** (OLD markers + дублирующиеся spec tag markers из NEW text). Если пользователь в этот момент сделает Save (через горячую клавишу или быстрый клик) — в БД уедет мусор. Это отдельный сценарий, но тот же корневой механизм.

**Замечание**: ШАГ X.8 (явный вызов) **спасает** в типичном сценарии переключения голоса — но при «первом открытии» (ШАГИ 6-13 в 2.2) такого спасающего вызова нет вообще.

### 2.5 Четвёртый механизм (потенциальный): `updateMarkersBySyllables` молча обнуляет label

`SubsEdit.vue:3348-3406`:

```js
updateMarkersBySyllables() {
  this.sortSourceMarkers()
  ...
  for (let i = 0; i < this.sourceMarkers.length; i++) {
    let marker = this.sourceMarkers[i]
    if (marker.markertype === 'syllables') {
      if (index >= this.sourceSyllables.length) {
        marker.label = ''           // ← МОЛЧА ОБНУЛЯЕТ
        ...
      } else if (marker.label !== this.sourceSyllables[index] || marker.color !== color) {
        marker.label = this.sourceSyllables[index]
        ...
      }
      index++
    }
  }
}
```

Если число syllables-маркеров в `sourceMarkers` **больше** числа элементов в `sourceSyllables`, лишние syllables-маркеры получают `label = ''`. Это может произойти, если:
- Текст был отредактирован после размеки (спецтег удалили, и `getProcessedSourceText` снимает строку, которой соответствовал syllables-маркер),
- ИЛИ рассинхрон между старым и новым билдом (например, новый `getSyllables` считает иначе, чем старый),
- ИЛИ БД хранит маркеры с дублями (после бага 2.2: «дубликаты syllables» накапливаются, `updateMarkersBySyllables` не дропает, но молча обнуляет лишние).

**На первом открытии** этот механизм НЕ участвует напрямую (на ШАГ 6 `sourceMarkers.length === 0`, цикл не крутится). Но он становится актуальным после частичного фикса 2.2, если в `loadedMarkers` есть лишние syllables-маркеры.

### 2.6 Пятый механизм (потенциальный): инвариант `loadedMarkers.length > 0` не гарантирует наличие «правильных» маркеров

Если БД после бага 2.2 уже хранит только spec tag-маркеры (потому что пользователь нажал Save после Apply-мусора), то `loadedMarkers` вернёт только их. Это порочный круг.

## 3. Альтернативные гипотезы (рассмотрены и отвергнуты)

### 3.1 «Apply затирает маркеры в `applyAutoMarkersToEditor` через `this.sourceMarkers = []`»

**Рассмотрено**: да, `applyAutoMarkersToEditor` (`SubsEdit.vue:4525-4536`) делает `this.sourceMarkers = []` и заменяет на `autoMarkersDebug.markers`. Это **by design** функции «Точные маркеры + Apply» (текст подтверждения в `doForcedAlignMarkers` явно говорит «Текущие маркеры голоса будут полностью заменены»). Это **не баг** — это намеренная UX-семантика. Спека 015 в FR-008 явно фиксирует, что эта семантика остаётся.

**Почему отвергнуто как первопричина наблюдаемой потери при reopen**: Apply **заменяет** маркеры на новые, но **новые маркеры** корректны (включают syllables, endofline, spec-tag-based — `buildMarkersFromSyllableTimes` в `WhisperMarkerAligner.kt:94-144` всё строит). Если бы Apply был виновником — пользователь видел бы мусор сразу после Apply, а не только на reopen.

### 3.2 «Save не отправляет все маркеры»

**Рассмотрено**: `getMarkersToSave()` (`SubsEdit.vue:2863-2877`) маппит ВСЕ поля, включая `time/label/note/chord/stringLad/locklad/color/position/markertype`. `ApiController.saveSourceTextAndMarkers` (`ApiController.kt:3411-3448`) парсит JSON и сохраняет через `setSourceMarkers`, который делает `lst[voice] = markers` (полная замена списка, не merge).

**Почему отвергнуто**: save отправляет всё, что в `sourceMarkers` на момент Save. Если `sourceMarkers` к этому моменту уже содержит мусор (из-за 2.2) — да, в БД уедет мусор. Но это **следствие**, а не первопричина.

### 3.3 «`getSourceMarkers` фильтрует спецтег-маркеры»

**Рассмотрено**: `store.js:948-962` — обычный JSON.parse + sort, без фильтрации. Backend-эндпойнт `/api/song/voicesourcemarkers` (отдельный) не имеет специальной логики для спецтегов.

**Почему отвергнуто**: нет фильтра, читается полный список. Подтверждается анализом кода `voicesourcemarkers`-эндпойнта в `ApiController.kt`.

### 3.4 «Маркеры из `loadedMarkers` не подходят, потому что `color`/`position` из БД не совпадают с тем, что ожидает UI»

**Рассмотрено**: цвет/позиция в `loadedMarkers` — это то, что было сохранено. UI их не валидирует. Если были сохранены `newline`-маркеры с цветом `#FF0000` — они и загрузятся с этим цветом.

**Почему отвергнуто**: предположение не подтверждается чтением кода; в БД сохраняется ровно то, что в UI.

## 4. Дизайн фикса

### 4.1 Стратегия: «Загрузить маркеры СИНХРОННО (в `mounted()`), до watcher'а `sourceText`»

Минимальный, локальный фикс, не требующий рефакторинга `currentVoice` watcher'а:

1. **`mounted()`**: перенести загрузку маркеров из `ws.on('decode')` в САМОЕ начало `mounted()` — **синхронно сразу после `loadedMarkers = await ...`**, ДО регистрации `ws.on('decode')` И ДО присваивания `this.sourceText = ...`. То есть:

   ```js
   async mounted() {
     this.initWavesurfer()
     this.isEditMode = true
     this.loadedMarkers = await this.$store.getters.getSourceMarkers(this.currentVoice)  // ← НОВЫЙ ПОРЯДОК: сначала markers
     if (this.loadedMarkers.length > 0) {
       for (let index = 0; index < this.loadedMarkers.length; index++) {
         const m = Object.assign({}, this.loadedMarkers[index])
         if (
           (m.markertype === 'setting' && m.label && m.label === 'COMMENT| ') ||
           (m.markertype === 'syllables' && m.label && m.label.trim() === '')
         ) {
           // skip
         } else {
           m.region = this.createRegionMarker(m)
           this.sourceMarkers.push(m)
         }
       }
       this.createBeatMarkers()
     }
     this.sourceText = await this.$store.getters.getSourceText(this.currentVoice)  // ← ШАГ A теперь ПОСЛЕ markers
     this.indexTabsVariant = await this.$store.getters.getIndexTabsVariant
     this.ws.on('decode', () => {
       // Ничего не делаем — маркеры уже загружены
       // (Опционально: если в будущем понадобится re-decode — вызвать явно)
     })
     ...
   }
   ```

   Эффект: watcher на `sourceText` срабатывает с **уже заполненным** `sourceMarkers` → `syncMarkersFromSpecTags()` корректно проверяет наличие syllables-маркеров в окнах и аддитивно добавляет spec tag-маркеры. Дубликаты не возникают, `loadedMarkers` не теряются.

2. **`currentVoice` watcher** (ШAГИ 2.4): применить тот же приём — загрузить `loadedMarkers` и заполнить `sourceMarkers` ДО `this.sourceText = await ...`. Уже есть явный вызов `this.syncMarkersFromSpecTags()` в конце, оставляем его.

3. **`syncMarkersFromSpecTags()`** (`SubsEdit.vue:3412-3466`): оставить как есть (строго аддитивна, по FR-005/FR-006/FR-007 спеки 010). Можно опционально добавить **предохранитель** `if (this.sourceMarkers.length === 0) return` для защиты от повторного вызова в неожиданных местах в будущем.

4. **`updateMarkersBySyllables()`** (ШAГ 2.5): **дополнительный фикс (P2 из спека)**: заменить `marker.label = ''` на «пропустить лишние syllables-маркеры, оставив их label/color как есть». Это защищает от потери syllable-маркеров в случае рассинхрона `sourceSyllables` ↔ числу syllables-маркеров.

5. **`ws.on('decode')`** — оставить как есть, но тело функции свести к `if (this.duration === 0) { this.duration = this.ws.getDuration() }` + `this.wsRegions.clearRegions()` (на случай re-decode, чтобы визуально почистить регионы). Удалить цикл загрузки маркеров отсюда — он перенесён в `mounted()`.

### 4.2 Альтернатива: «флаг `isLoadingVoice` + гард в watcher'е»

Менее инвазивный, но оставляет больше шансов на регрессию в будущем:

- Добавить `data().isLoadingVoice = false`.
- В `mounted()` / `currentVoice` watcher'е: устанавливать `this.isLoadingVoice = true` ДО `this.sourceText = ...`, и `false` ПОСЛЕ загрузки маркеров.
- В `sourceText` watcher'е: `if (this.isLoadingVoice) return` перед `syncMarkersFromSpecTags()`.

**Почему НЕ выбираем**: флаг-гард — это «костыль», который легко забыть в новом коде, тогда как прямой порядок загрузки (4.1) делает watcher'ы на `sourceText` корректными по построению.

### 4.3 Альтернатива: «вынести `syncMarkersFromSpecTags` из watcher'а вообще»

- Удалить вызов из `sourceText` watcher'а.
- Заменить на единый `watch: { sourceText: { handler() { ... } } }` (deep), в котором после всех модификаций вызвать `syncMarkersFromSpecTags()`.

**Почему НЕ выбираем**: ничего принципиально не меняет по сравнению с 4.1; только переименование watcher'а.

## 5. Принятое решение

**Стратегия 4.1 (минимальный, прямой фикс)**:

- Шаг 1 (P1, обязателен): перенести загрузку `loadedMarkers` в `sourceMarkers` в `mounted()` **синхронно** до `this.sourceText = ...`; убрать цикл из `ws.on('decode')` (или оставить, но с условием «только если `sourceMarkers.length === 0` И предыдущая загрузка не удалась» — для отказоустойчивости).
- Шаг 2 (P1, обязателен): применить тот же приём в `currentVoice` watcher'е.
- Шаг 3 (P2, рекомендуется): добавить `if (this.sourceMarkers.length === 0) return` в `syncMarkersFromSpecTags()` как защитный гард.
- Шаг 4 (P2, рекомендуется): в `updateMarkersBySyllables()` не обнулять `label` syllables-маркера, если `index >= sourceSyllables.length`, а пропускать такие маркеры (т.е. оставлять их label/color как есть). Альтернатива — алертить пользователя (P3 из спека).

**Покрытие US1 спека**: фикс Шагов 1+2 полностью покрывает P1 (маркеры на месте после reopen). US2 (рассинхрон `sourceSyllables`) — Шаг 4. US3 (явное уведомление) — оставлено на будущее как «защитный сценарий», не критично для P1.

## 6. Что НЕ меняется в этом фиксе

- Контракт `specs/010-lyrics-spec-tags/contracts/tag-registry.md` — грамматика, реестр, алиасы, инварианты.
- Семантика «Точные маркеры + Apply = полная замена маркеров» (FR-008 спека 015).
- `applyAutoMarkersToEditor` (`SubsEdit.vue:4525-4536`) — остаётся «жёсткой заменой», как было.
- Backend `WhisperMarkerAligner.buildMarkersFromSyllableTimes` — не трогаем.
- Save/load (`saveSourceTextAndMarkers`, `voicesourcemarkers`) — не трогаем.
- Лёгкий `SongKaraokeEditorView` и краудсорсинг `EditorWorkView` — не в скоупе (там нет «Точные маркеры»-потока).
- Поток «Распознать текст (Whisper)» (`doApplyAutoMarkers`) — не в скоупе (маркеры там не расставляются, см. `WhisperDebugModal.vue:67`).

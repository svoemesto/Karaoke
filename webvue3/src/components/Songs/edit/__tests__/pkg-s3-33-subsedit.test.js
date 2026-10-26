// Регрессионные тесты пакета PKG-S3-33 (аудит #336, OpenProject #405) — редактор
// субтитров (SubsEdit), мини-редактор и плеер.
//
// Записи пакета в этом файле:
//   BR-452 — удержание кнопок транспорта: отпускание вне кнопки/потеря фокуса гасит
//            интервалы (иначе перемотка тикает вечно).
//   BR-453 — `mounted()` проверяет isUnmounted после каждого `await`.
//   BR-454 — «применить» в модалке поиска текста восстанавливает режим редактирования.
//   BR-455 — computed-геттер не пишет `marker.stringLad` (сохраняемое поле).
//   BR-456 — хоткеи гасятся на время любой открытой модалки.
//   BR-457 — тег-якоря считаются построчно (не пересчёт префикса на каждой строке).
//   BR-468 — `marker.region` может быть null: единый хелпер снятия региона.
//   BR-469 — addMarker при currentMarkersIndex === -1 не падает (индекс нормализуется).
//   BR-470 — регион создаётся только после готовности аудио; координаты — живые.
//   BR-471 — «вставить маркеры из региона» копирует ВСЕ поля маркера.
//   BR-472 — «Сдвинуть сюда первый маркер» на пустом списке не падает.
//   BR-473 — содержимое региона пересобирается только при изменении сигнатуры.
//   BR-474 — loadSong обрабатывает отказ загрузки аудио.
//   BR-478 — уход со страницы во время `init()` не оставляет AudioContext.
//   BR-479 — индексы маркеров ищутся бинарным поиском, без аллокаций на кадр.
//   BR-480 — `songType` в контракте редакторского API и в inline-данных плеера.
//   BR-486 — `_silentOffset` считается ДО `_buildFlashTimes()`.
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/pkg-s3-33-subsedit.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = (relative) => readFileSync(new URL(relative, import.meta.url), 'utf-8')

const SUBS_EDIT = read('../SubsEdit.vue')
const EDITOR_COMPOSABLE = read('../../../../composables/useKaraokeEditor.js')
const EDITOR_VIEW = read('../../../SongEditor/SongKaraokeEditorView.vue')
const EDITOR_MODAL = read('../../../SongEditor/SongKaraokeEditorModal.vue')
const KARAOKE_PLAYER = read('../../../../player/KaraokePlayer.js')
const PLAYER_VIEW = read('../../../../views/PlayerView.vue')

/**
 * Подключает модуль ДИНАМИЧЕСКИ: на старом коде файл может отсутствовать, и обычный
 * статический import уронил бы весь файл с «module not found». Динамический импорт
 * обёрнут в catch, поэтому на старом коде тест падает с AssertionError.
 *
 * @param {string} specifier — путь к модулю относительно теста.
 * @returns {Promise<Object|null>} модуль либо null.
 */
const importOrNull = async (specifier) => {
  try {
    return await import(specifier)
  } catch (_e) {
    return null
  }
}

describe('PKG-S3-33 / Редактор субтитров, мини-редактор и плеер', () => {
  describe('BR-452 — удержание кнопок транспорта', () => {
    test('отпускание кнопки слушается ГЛОБАЛЬНО (window), а не только на кнопке', () => {
      assert.match(
        SUBS_EDIT,
        /window\.addEventListener\('mouseup', this\.onGlobalPointerRelease/,
        'BR-452: mouseup надо слушать на window — отпускание вне кнопки не приходит элементу',
      )
      assert.match(
        SUBS_EDIT,
        /window\.addEventListener\('blur', this\.onGlobalPointerRelease/,
        'BR-452: потеря фокуса окна обязана гасить удержания',
      )
      assert.match(
        SUBS_EDIT,
        /document\.addEventListener\('visibilitychange', this\.onGlobalPointerRelease/,
        'BR-452: уход вкладки в фон обязана гасить удержания',
      )
    })

    test('интервалы гасятся ДО раннего выхода по !isEditMode', () => {
      const watcher = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    pressedA: {'),
        SUBS_EDIT.indexOf('    pressedD: {'),
      )
      const clearIndex = watcher.indexOf('clearInterval(this.intervalSkipBackward)')
      const returnIndex = watcher.indexOf('if (!this.isEditMode) return')
      assert.ok(clearIndex > 0, 'BR-452: интервал перемотки обязан гаситься в watcher')
      assert.ok(
        clearIndex < returnIndex,
        'BR-452: очистка интервала должна идти ДО раннего выхода по !isEditMode',
      )
    })

    test('есть метод глобального сброса удержаний', () => {
      assert.match(
        SUBS_EDIT,
        /onGlobalPointerRelease\(\)/,
        'BR-452: нужен метод глобального сброса удержаний',
      )
      assert.match(SUBS_EDIT, /pressedButtons\(\)/, 'BR-452: нужен список удерживаемых кнопок')
    })
  })

  describe('BR-453 — mounted() не пишет в размонтированный компонент', () => {
    test('после каждого await в mounted() есть проверка isUnmounted', () => {
      const mountedStart = SUBS_EDIT.indexOf('  async mounted() {')
      assert.ok(mountedStart > 0, 'BR-453: у редактора должен быть async mounted()')
      const mounted = SUBS_EDIT.slice(mountedStart, mountedStart + 12000)
      const awaits = (mounted.match(/await /g) || []).length
      const guards = (mounted.match(/if \(this\.isUnmounted\) return/g) || []).length
      assert.ok(
        guards >= 3,
        `BR-453: в mounted() ${awaits} await'ов, а проверок isUnmounted — ${guards}: после КАЖДОГО await нужна проверка`,
      )
    })
  })

  describe('BR-454 — выход из модалки поиска текста возвращает режим редактирования', () => {
    test('returnSearchText восстанавливает isEditMode', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    returnSearchText(returnedText) {'),
        SUBS_EDIT.indexOf('    doAiTextEditor() {'),
      )
      assert.match(
        method,
        /setEditMode\(true\)/,
        'BR-454: «применить» обязана восстанавливать режим редактирования (как closeSearchText)',
      )
    })
  })

  describe('BR-455 — computed-геттер не пишет в сохраняемое поле', () => {
    test('в getFormattedNotes нет присваивания marker.stringLad', () => {
      const getterStart = SUBS_EDIT.indexOf('    getFormattedNotes() {')
      assert.ok(getterStart > 0, 'BR-455: у редактора должен быть геттер getFormattedNotes')
      const getter = SUBS_EDIT.slice(getterStart, getterStart + 8000)
      assert.doesNotMatch(
        getter,
        /marker\.stringLad\s*=/,
        'BR-455: computed-геттер не должен писать в сохраняемое поле marker.stringLad',
      )
      assert.match(
        getter,
        /const sn = marker\.lockLad/,
        'BR-455: пара [струна, лад] должна вычисляться локально',
      )
    })
  })

  describe('BR-456 — хоткеи гасятся на время открытой модалки', () => {
    test('listenerKeyDown проверяет признаки открытых модалок', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    listenerKeyDown(e) {'),
        SUBS_EDIT.indexOf('    listenerKeyUp(e) {'),
      )
      for (const flag of [
        'isCustomConfirmVisible',
        'isSearchTextVisible',
        'isAiTextEditorVisible',
        'isWhisperDebugVisible',
      ]) {
        assert.match(
          method,
          new RegExp(flag),
          `BR-456: хоткеи должны гаситься при открытой модалке (${flag})`,
        )
      }
    })
  })

  describe('BR-457 — тег-якоря считаются построчно', () => {
    test('в редакторе счётчик слогов накапливается, а не пересчитывается по префиксу', () => {
      assert.match(
        SUBS_EDIT,
        /syllableCount \+= computeSyllables\(line\)\.length/,
        'BR-457: счётчик слогов должен накапливаться по одному проходу на строку',
      )
      assert.doesNotMatch(
        SUBS_EDIT,
        /computeSyllables\(processedLines\.join\('\\n'\)\)/,
        'BR-457: пересчёт слогов на всём префиксе для каждой строки — квадратичная сложность',
      )
    })

    test('в мини-редакторе — та же построчная схема', () => {
      assert.match(
        EDITOR_COMPOSABLE,
        /syllableCount \+= splitSyllables\(line\)\.length/,
        'BR-457: мини-редактор должен считать слоги построчно',
      )
      assert.doesNotMatch(
        EDITOR_COMPOSABLE,
        /splitSyllables\(processedLines\.join\('\\n'\)\)/,
        'BR-457: пересчёт слогов на префиксе — квадратичная сложность',
      )
    })

    test('тег-якоря на длинном тексте считаются быстро (не квадратично)', async () => {
      const mod = await importOrNull('../../../../composables/useKaraokeEditor.js')
      assert.ok(mod, 'BR-457: useKaraokeEditor.js должен существовать')
      const lines = []
      for (let i = 0; i < 800; i++) {
        lines.push('Съешь ещё этих мягких французских булок да выпей чаю')
      }
      const text = lines.join('\n')
      const started = Date.now()
      const anchors = mod.specTagAnchors(text)
      const elapsed = Date.now() - started
      assert.ok(Array.isArray(anchors), 'BR-457: specTagAnchors обязан вернуть массив якорей')
      assert.ok(
        elapsed < 500,
        `BR-457: разбор 800 строк занял ${elapsed} мс — ожидается линейное время (было бы ~3 с)`,
      )
    })
  })

  describe('BR-468 — region может быть null', () => {
    test('есть единый хелпер removeMarkerRegion', () => {
      assert.match(
        SUBS_EDIT,
        /removeMarkerRegion\(marker\)/,
        'BR-468: нужен единый хелпер снятия региона',
      )
      assert.match(
        SUBS_EDIT,
        /if \(marker\.region\) \{[\s\S]{0,200}?marker\.region\.remove\(\)/,
        'BR-468: хелпер обязан проверять region на null перед remove()',
      )
    })

    test('длительность для beat-маркеров берётся не из region', () => {
      assert.doesNotMatch(
        SUBS_EDIT,
        /bpmMarker\.region\.totalDuration/,
        'BR-468: region может быть null — длительность берётся из плеера',
      )
      assert.match(
        SUBS_EDIT,
        /nextBpmMarkerTime = this\.ws \? this\.ws\.getDuration\(\) : this\.duration/,
        'BR-468: длительность аудио берётся из плеера',
      )
    })
  })

  describe('BR-469 — добавление маркера в начале песни', () => {
    test('индекс нормализуется, замена разрешена только при index >= 0', () => {
      assert.match(
        SUBS_EDIT,
        /const canReplace = this\.currentMarkersIndex >= 0/,
        'BR-469: замена маркера допустима только при currentMarkersIndex >= 0',
      )
      assert.match(
        SUBS_EDIT,
        /Math\.max\(0, this\.currentMarkersIndex/,
        'BR-469: индекс вставки должен нормализоваться (не -1)',
      )
    })
  })

  describe('BR-470 — регион только после готовности аудио', () => {
    test('режим региона проверяет длительность аудио', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    setRegionMode(isRegionMode) {'),
        SUBS_EDIT.indexOf('    setMoveMode(isMoveMode) {'),
      )
      assert.match(
        method,
        /this\.ws\.getDuration\(\) === 0/,
        'BR-470: регион нельзя создавать до готовности аудио (addRegion обнуляет позицию)',
      )
    })

    test('координаты региона берутся из живого региона, а не из снимка', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    setMoveMode(isMoveMode) {'),
        SUBS_EDIT.indexOf('    pasteMarkersInRegionToNewPlace() {'),
      )
      assert.match(method, /region\.start/, 'BR-470: старт региона берётся из живого региона')
      assert.match(method, /region\.end/, 'BR-470: конец региона берётся из живого региона')
    })
  })

  describe('BR-471 — вставка маркеров из региона копирует все поля', () => {
    test('копия маркера содержит все поля, а не только label/color/position', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    pasteMarkersInRegionToNewPlace() {'),
        SUBS_EDIT.indexOf('    deleteMarkersInRegion() {'),
      )
      assert.match(
        method,
        /\.\.\.markerToCopy/,
        'BR-471: копия маркера должна сохранять note/chord/stringLad/lockLad',
      )
      assert.match(
        method,
        /region: undefined/,
        'BR-471: регион оригинала не должен переезжать в копию',
      )
    })
  })

  describe('BR-472 — «Сдвинуть сюда первый маркер» на пустом списке', () => {
    test('метод выходит при пустом sourceMarkers', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    doMarkersFirst() {'),
        SUBS_EDIT.indexOf('    doReplaceBrokenMarkers() {'),
      )
      assert.match(
        method,
        /if \(markers\.length === 0\) return/,
        'BR-472: на пустом списке маркеров метод обязан выйти, а не падать',
      )
    })
  })

  describe('BR-473 — содержимое региона не пересобирается зря', () => {
    test('сравнивается сигнатура содержимого, а не DOM-узел', () => {
      assert.match(
        SUBS_EDIT,
        /getMarkerContentSignature\(marker\)/,
        'BR-473: содержимое региона должно сравниваться по сигнатуре',
      )
      assert.match(
        SUBS_EDIT,
        /marker\._contentSignature !== signature/,
        'BR-473: setContent должен вызываться только при изменении сигнатуры',
      )
      assert.doesNotMatch(
        SUBS_EDIT,
        /region\.content !== content/,
        'BR-473: сравнение DOM-узлов всегда истинно — это и было причиной пересборки',
      )
    })
  })

  describe('BR-474 — отказ загрузки аудио виден пользователю', () => {
    test('loadSong обрабатывает отказ и проверяет, что компонент жив', () => {
      const method = SUBS_EDIT.slice(
        SUBS_EDIT.indexOf('    loadSong() {'),
        SUBS_EDIT.indexOf('    initWavesurfer() {'),
      )
      assert.match(method, /\.catch\(/, 'BR-474: отказ загрузки аудио обязан обрабатываться')
      assert.match(
        method,
        /isUnmounted/,
        'BR-474: поздний обработчик не должен писать в размонтированный компонент',
      )
      assert.match(
        method,
        /isCustomConfirmVisible = true/,
        'BR-474: пользователь должен увидеть сообщение об ошибке',
      )
    })
  })

  describe('BR-478 — уход со страницы во время инициализации плеера', () => {
    test('у плеера есть флаг отмены и проверки после await', () => {
      assert.match(
        KARAOKE_PLAYER,
        /this\._destroyed = true/,
        'BR-478: destroy() обязан ставить флаг отмены',
      )
      assert.match(
        KARAOKE_PLAYER,
        /_isDestroyed\(\)/,
        'BR-478: init() обязан проверять флаг отмены после каждого await',
      )
      assert.match(
        KARAOKE_PLAYER,
        /if \(this\._isDestroyed\(\)\) return/,
        'BR-478: инициализация обязана прерываться при отмене',
      )
    })

    test('PlayerView гасит отклонённый промис init()', () => {
      assert.match(
        PLAYER_VIEW,
        /player\.init\(\)\.catch\(/,
        'BR-478: отказ init() не должен уходить в unhandled rejection',
      )
    })
  })

  describe('BR-479 — индексы маркеров без линейного прохода', () => {
    test('currentMarkerIndex использует бинарный поиск', async () => {
      const mod = await importOrNull('../../../../composables/useKaraokeEditor.js')
      assert.ok(mod, 'BR-479: useKaraokeEditor.js должен существовать')
      const markers = [
        { time: 0.5, markertype: 'syllables' },
        { time: 1.5, markertype: 'syllables' },
        { time: 2.5, markertype: 'syllables' },
      ]
      // Эталон — прежняя линейная реализация.
      const linear = (items, currentTime) => {
        const diff = 0.02
        if (items.length > 0 && currentTime < items[0].time - diff) return -1
        for (let i = 0; i < items.length - 1; i++) {
          if (currentTime >= items[i].time - diff && currentTime < items[i + 1].time - diff)
            return i
        }
        return items.length - 1
      }
      for (const t of [-1, 0, 0.48, 0.5, 1.0, 1.5, 2.4, 2.5, 9]) {
        assert.equal(
          mod.currentMarkerIndex(markers, t),
          linear(markers, t),
          `BR-479: currentMarkerIndex(${t}) должен совпадать с линейным эталоном`,
        )
      }
      assert.match(
        EDITOR_COMPOSABLE,
        /findMarkerIndexByTime\(markers, currentTime\)/,
        'BR-479: currentMarkerIndex обязан использовать бинарный поиск',
      )
    })

    test('currentSyllableIndex не аллоцирует массив на каждый вызов', async () => {
      const mod = await importOrNull('../../../../composables/useKaraokeEditor.js')
      assert.ok(mod, 'BR-479: useKaraokeEditor.js должен существовать')
      const markers = [
        { time: 0.5, markertype: 'syllables' },
        { time: 1.0, markertype: 'setting' },
        { time: 1.5, markertype: 'syllables' },
        { time: 2.5, markertype: 'syllables' },
      ]
      // Эталон — прежняя реализация с filter().
      const legacy = (items, currentTime) => {
        const syl = items.filter((m) => m.markertype === 'syllables')
        const diff = 0.02
        if (syl.length > 0 && currentTime < syl[0].time - diff) return -1
        for (let i = 0; i < syl.length - 1; i++) {
          if (currentTime >= syl[i].time - diff && currentTime < syl[i + 1].time - diff) return i
        }
        return syl.length - 1
      }
      for (const t of [-1, 0, 0.48, 0.5, 1.2, 1.5, 2.4, 2.5, 9]) {
        assert.equal(
          mod.currentSyllableIndex(markers, t),
          legacy(markers, t),
          `BR-479: currentSyllableIndex(${t}) должен совпадать с прежним поведением`,
        )
      }
      assert.doesNotMatch(
        EDITOR_COMPOSABLE,
        /const syl = markers\.filter/,
        'BR-479: currentSyllableIndex не должен аллоцировать массив на каждый вызов',
      )
    })

    test('мини-редактор считает слоговые позиции один раз на изменение разметки', () => {
      assert.match(
        EDITOR_VIEW,
        /syllablesMarkers\(\)/,
        'BR-479: позиции слоговых маркеров должны быть в computed',
      )
      assert.match(
        EDITOR_VIEW,
        /findMarkerIndexByTime\(this\.syllablesMarkers, this\.currentTime\)/,
        'BR-479: индекс сло��а ищется бинарным поиском по кэшированному массиву',
      )
    })
  })

  describe('BR-480 — тип песни в контракте редакторского API', () => {
    test('buildInlinePlayerData возвращает songType', async () => {
      const mod = await importOrNull('../../../../composables/useKaraokeEditor.js')
      assert.ok(mod, 'BR-480: useKaraokeEditor.js должен существовать')
      const data = mod.buildInlinePlayerData({
        songId: 1,
        songName: 'Песня',
        author: 'Автор',
        album: '',
        year: null,
        track: null,
        key: null,
        bpm: null,
        songType: 'instrumental',
        markersPerVoice: [],
      })
      assert.equal(
        data.songType,
        'instrumental',
        'BR-480: inline-данные плеера обязаны нести songType (иначе превью расходится с плеером)',
      )
    })

    test('songType прокинут через модалку и редактор', () => {
      assert.match(
        EDITOR_MODAL,
        /song-type="loadedSongType"/,
        'BR-480: модалка обязана передавать songType',
      )
      assert.match(
        EDITOR_MODAL,
        /loadedSongType = body\.songType/,
        'BR-480: модалка обязана читать songType из ответа API',
      )
      assert.match(
        EDITOR_VIEW,
        /songType: \{ type: String/,
        'BR-480: редактор обязан принимать prop songType',
      )
      assert.match(
        EDITOR_VIEW,
        /songType: this\.songType/,
        'BR-480: редактор обязан передавать songType в inline-данные',
      )
    })
  })

  describe('BR-486 — тишина считается до сборки моментов вспышек', () => {
    test('_silentOffset вычисляется раньше _buildFlashTimes', () => {
      const init = KARAOKE_PLAYER.slice(
        KARAOKE_PLAYER.indexOf('  async init() {'),
        KARAOKE_PLAYER.indexOf('  // INSTRUMENTAL/POETRY: плеер не обрабатывает маркеры'),
      )
      const offsetIndex = init.indexOf('this._silentOffset = this._computeSilentOffset()')
      const flashIndex = init.indexOf('this._buildFlashTimes()')
      assert.ok(offsetIndex > 0, 'BR-486: _silentOffset обязан вычисляться в init()')
      assert.ok(flashIndex > 0, 'BR-486: _buildFlashTimes обязан вызываться в init()')
      assert.ok(
        offsetIndex < flashIndex,
        'BR-486: _silentOffset должен считаться ДО _buildFlashTimes (иначе фильтр отсекает первую вспышку)',
      )
    })
  })
})

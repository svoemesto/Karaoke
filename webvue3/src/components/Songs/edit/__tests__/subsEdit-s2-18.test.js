// Регрессии пакета PKG-S2-18 (аудит #336) по редактору SubsEdit.
//
// Проверяются четыре записи:
//   BR-449 — поле «Time:» не должно пропускать произвольный текст в JSON;
//   BR-450 — ошибка сохранения должна быть видна пользователю;
//   BR-451 — поздний ответ другого голоса не должен перетирать состояние;
//   BR-467 — chord-метка старой формы (`label` без `chord`) не должна ронять redraw.
//
// Часть проверок — по исходнику компонента: у Vue-компонента нет экспортируемых
// методов, а поднимать jsdom ради одного метода дороже, чем извлечь тело метода
// из SFC (тот же приём, что в subsEdit-invariants.test.js). Где поведение можно
// воспроизвести без DOM (watcher голоса), тело метода исполняется на фейковом
// `this` — это проверяет именно гонку, а не наличие строчки в файле.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { parseMarkerTime, resolveChordLabel } from '../../../../lib/marker-fields.js'

const SOURCE = readFileSync(new URL('../SubsEdit.vue', import.meta.url), 'utf-8')

/**
 * Достать тело метода по имени, сопоставив скобки.
 *
 * @param {string} source
 * @param {string} name
 * @returns {string|null}
 */
const methodBody = (source, name) => {
  const definition = new RegExp('\\n\\s*(?:async\\s+)?' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(
    source,
  )
  if (!definition) return null
  const open = source.indexOf('{', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(open + 1, i)
    }
  }
  return null
}

/**
 * Достать тело асинхронного обработчика watcher'а `name: { async handler() { ... } }`.
 *
 * @param {string} source
 * @param {string} name
 * @returns {string|null}
 */
const asyncWatcherBody = (source, name) => {
  const definition = new RegExp(
    '\\n\\s*' + name + '\\s*:\\s*\\{[^}]*?async handler\\s*\\([^)]*\\)\\s*\\{',
  ).exec(source)
  if (!definition) return null
  const open = source.indexOf('{', definition.index + definition[0].length - 1)
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '{') depth++
    else if (source[i] === '}') {
      depth--
      if (depth === 0) return source.slice(open + 1, i)
    }
  }
  return null
}

/**
 * Отложенный промис: тест сам решает, когда ответить «серверу».
 *
 * @returns {{promise: Promise, resolve: Function, reject: Function}}
 */
const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** Дождаться, пока отработают все микрозадачи. */
const flush = () => new Promise((resolve) => setImmediate(resolve))

describe('BR-449: время маркера не уходит строкой в JSON', () => {
  test('parseMarkerTime понимает запятую, точку и отбрасывает мусор', () => {
    assert.equal(parseMarkerTime('1,5'), 1.5)
    assert.equal(parseMarkerTime('1.5'), 1.5)
    assert.equal(parseMarkerTime(' 2 '), 2)
    assert.equal(parseMarkerTime(3), 3)
    assert.equal(parseMarkerTime(''), null)
    assert.equal(parseMarkerTime('1,5,6'), null)
    assert.equal(parseMarkerTime('abc'), null)
    assert.equal(parseMarkerTime(null), null)
    assert.equal(parseMarkerTime(Number.NaN), null)
  })

  test('getMarkersToSave приводит time к числу и выбрасывает нечисловые маркеры', () => {
    const body = methodBody(SOURCE, 'getMarkersToSave')
    assert.ok(body, 'getMarkersToSave должен существовать')
    const factory = new Function('parseMarkerTime', 'return function(){' + body + '}')
    const getMarkersToSave = factory(parseMarkerTime)
    const result = getMarkersToSave.call({
      sourceMarkers: [
        { time: '1,5', label: 'a', markertype: 'syllables' },
        { time: 'oops', label: 'b', markertype: 'syllables' },
        { time: 2, label: 'c', markertype: 'syllables' },
      ],
    })
    assert.deepEqual(
      result.map((m) => m.time),
      [1.5, 2],
      'время должно уходить числом, а маркер с нечисловым временем — не отправляться',
    )
  })

  test('поле «Time:» объявлено числовым и нормализуется на change', () => {
    const block = /<div class="se-item-left-label">Time:<\/div>([\s\S]{0,900}?)\/>/.exec(SOURCE)
    assert.ok(block, 'поле «Time:» должно быть в шаблоне')
    assert.match(block[1], /type="number"/, 'нужен type="number", иначе ввод остаётся строкой')
    assert.match(block[1], /@change="normalizeMarkerTime"/, 'нужна нормализация на change')
  })

  test('normalizeMarkerTime откатывает нечисловой ввод к прежнему значению', () => {
    const body = methodBody(SOURCE, 'normalizeMarkerTime')
    assert.ok(body, 'normalizeMarkerTime должен существовать')
    const normalize = new Function('parseMarkerTime', 'return function(){' + body + '}')(
      parseMarkerTime,
    )
    const component = {
      currentMarker: { time: '1,5' },
      markerTimeBeforeEdit: 7,
    }
    normalize.call(component)
    assert.equal(component.currentMarker.time, 1.5)
    component.currentMarker.time = 'oops'
    normalize.call(component)
    assert.equal(component.currentMarker.time, 1.5, 'нечисловое значение должно откатиться')
  })
})

describe('BR-450: ошибка сохранения видна пользователю', () => {
  test('catch сохранения показывает модалку и возвращает false', () => {
    const body = methodBody(SOURCE, 'save')
    assert.ok(body, 'save должен существовать')
    const catchPart = body.slice(body.indexOf('catch (e)'))
    assert.ok(catchPart.length > 0, 'в save должен быть catch')
    assert.match(
      catchPart,
      /this\.isCustomConfirmVisible\s*=\s*true/,
      'без isCustomConfirmVisible=true модалка не отрисуется (v-if) и ошибка невидима',
    )
    assert.match(catchPart, /isAlert:\s*true/, 'ошибка должна быть alert-модалкой')
    assert.match(body, /saved = false/, 'неуспех должен фиксироваться')
    assert.match(body, /return saved/, 'save должен возвращать признак успеха')
  })
})

describe('BR-451: ответ другого голоса не перетирает разметку', () => {
  /**
   * Собрать фейковый компонент с настоящим телом watcher'а `currentVoice`.
   *
   * @returns {{run: Function, component: Object, markers: Object, texts: Object}}
   */
  const buildWatcher = () => {
    const body = asyncWatcherBody(SOURCE, 'currentVoice')
    assert.ok(body, 'watcher currentVoice должен существовать')
    const run = new Function('return async function(){' + body + '}')()
    const markers = { 0: deferred(), 1: deferred() }
    const texts = { 0: deferred(), 1: deferred() }
    const component = {
      currentVoice: 0,
      dataVoices: [{ text: '', markers: [], syllables: [] }],
      isUnmounted: false,
      voiceLoadToken: 0,
      voiceLoadFailed: false,
      loadedMarkers: [],
      sourceMarkers: [],
      sourceText: '',
      customConfirmParams: null,
      isCustomConfirmVisible: false,
      createBeatMarkers() {},
      syncMarkersFromSpecTags() {},
      redrawMarkers() {},
      $store: {
        getters: {
          getSourceMarkers: (voice) => markers[voice].promise,
          getSourceText: (voice) => texts[voice].promise,
        },
      },
    }
    return { run, component, markers, texts }
  }

  test('поздний ответ прежнего голоса игнорируется', async () => {
    const { run, component, markers, texts } = buildWatcher()
    const first = run.call(component)
    // пользователь переключил голос, не дождавшись ответа
    component.currentVoice = 1
    const second = run.call(component)

    markers[1].resolve([{ time: 1, label: 'voice-1', markertype: 'syllables' }])
    await flush()
    texts[1].resolve('текст голоса 1')
    await flush()
    markers[0].resolve([{ time: 1, label: 'voice-0', markertype: 'syllables' }])
    await flush()
    texts[0].resolve('текст голоса 0')
    await Promise.all([first, second])

    assert.deepEqual(
      component.sourceMarkers.map((m) => m.label),
      ['voice-1'],
      'в состоянии должны остаться маркеры голоса, который выбран сейчас',
    )
    assert.equal(component.sourceText, 'текст голоса 1')
    assert.equal(component.voiceLoadFailed, false)
  })

  test('отказ загрузки голоса блокирует сохранение (fail-closed)', async () => {
    const { run, component, markers } = buildWatcher()
    component.sourceMarkers = [{ time: 0, label: 'старый-голос' }]
    component.sourceText = 'старый текст'
    const failed = run.call(component)
    markers[0].reject(new Error('HTTP 500'))
    await failed

    assert.equal(component.voiceLoadFailed, true)
    assert.deepEqual(component.sourceMarkers, [], 'чужие маркеры должны быть убраны')
    assert.equal(component.sourceText, '')
    assert.equal(component.isCustomConfirmVisible, true, 'ошибка должна быть показана')

    const saveBody = methodBody(SOURCE, 'save')
    assert.match(
      saveBody.slice(0, saveBody.indexOf('this.addEndMarker()')),
      /if \(this\.voiceLoadFailed\)/,
      'save должен отказываться работать, пока голос не загружен',
    )
  })
})

describe('BR-467: chord-метка без поля chord не роняет redraw', () => {
  test('resolveChordLabel берёт label, chord или пустую строку', () => {
    assert.equal(resolveChordLabel({ label: 'Gm', markertype: 'chord' }), 'Gm')
    assert.equal(resolveChordLabel({ chord: 'C|m', label: 'C' }), 'C|m')
    assert.equal(resolveChordLabel({ markertype: 'chord' }), '')
    assert.equal(resolveChordLabel({ chord: 42 }), '')
    assert.equal(resolveChordLabel(null), '')
  })

  test('ветка chord использует resolveChordLabel и не падает без значения', () => {
    const method = methodBody(SOURCE, 'getRegionContentFromMarker')
    assert.ok(method, 'getRegionContentFromMarker должен существовать')
    const start = method.indexOf("case 'chord': {")
    assert.ok(start > 0, 'ветка chord должна существовать внутри getRegionContentFromMarker')
    const branch = method.slice(start, start + 700)
    assert.match(branch, /resolveChordLabel\(marker\)/, 'значение должно браться из label/chord')
    assert.match(branch, /if \(!label\) return ''/, 'пустое значение не должно разыменовываться')
    assert.doesNotMatch(branch, /marker\.chord\.split/, 'прямой split по marker.chord недопустим')
  })
})

// BR-331 / BR-332 / BR-333 / BR-335 / BR-336 / BR-337 / BR-338
// (аудит #336, пакет PKG-S3-27, OpenProject #399): инварианты редактора заданий.
//
// ЧТО БЫЛО.
//   - BR-331: `saveNow()` ничего не возвращал, а `submit()` отправлял задание независимо от
//     того, сохранился ли черновик (уходила СТАРАЯ версия разметки).
//   - BR-332: фронт слепо применял `body.status` из ответа save (сервер всегда отдаёт
//     `in_progress`), из-за чего баннер «Возвращено на доработку» исчезал после автосейва.
//   - BR-333: `mounted()` навешивал keydown/keyup/resize ПОСЛЕ двух await — при уходе со
//     страницы листенеры оставались на window навсегда.
//   - BR-335: `timeupdate` (60 раз/с) перерисовывал компонент целиком.
//   - BR-336: `activeSound` выставлялся ДО загрузки стема; reject от `ws.load()` оставлял
//     UI в состоянии «выбран музыка», а повторный клик ничего не делал.
//   - BR-337: интервалы перемотки останавливались только по keyup — при потере фокуса окна
//     keyup не приходит и интервал крутился бесконечно.
//   - BR-338: у голоса без текста `relabelSyllables(markers, [])` обнулял label всех
//     слоговых маркеров, а автосейв закреплял пустые метки (текст голоса терялся после апрува).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import {
  SOUND_SWITCH,
  isSaveSuccessful,
  isUnmounted,
  mergeSaveStatus,
  releaseAllScrubTimers,
  shouldRefreshPreview,
  soundSwitchOutcome,
  trimTrailingEmptyVoices,
  voiceLabelPlan,
  voiceLengthMismatchWarning,
} from '../editorGuards.js'
import { readCode } from './sourceContract.js'

describe('BR-331: результат сохранения известен вызывающему', () => {
  test('saved — успех', () => {
    assert.equal(isSaveSuccessful('saved'), true)
  })

  test('error — не успех (submit обязан прерваться)', () => {
    assert.equal(isSaveSuccessful('error'), false)
  })

  test('saving/idle — не успех', () => {
    assert.equal(isSaveSuccessful('saving'), false)
    assert.equal(isSaveSuccessful('idle'), false)
  })
})

describe('BR-332: композитный статус не затирается ответом save', () => {
  test('отклонённое задание остаётся отклонённым (баннер с комментарием админа виден)', () => {
    assert.equal(
      mergeSaveStatus('rejected', 'in_progress'),
      'rejected',
      'сервер save всегда отдаёт in_progress — применять его нельзя',
    )
  })

  test('отправленное задание остаётся отправленным', () => {
    assert.equal(mergeSaveStatus('submitted', 'in_progress'), 'submitted')
  })

  test('обычный статус берётся из ответа', () => {
    assert.equal(mergeSaveStatus('in_progress', 'submitted'), 'submitted')
  })

  test('пустой ответ — статус не меняется', () => {
    assert.equal(mergeSaveStatus('in_progress', null), 'in_progress')
  })
})

describe('BR-333: флаг размонтирования', () => {
  test('не размонтирован — false', () => {
    assert.equal(isUnmounted({}), false)
  })

  test('размонтирован — true', () => {
    assert.equal(isUnmounted({ _unmounted: true }), true)
  })
})

describe('BR-335: превью пересобирается только при смене индекса маркера', () => {
  test('индекс не изменился — пересобирать не нужно', () => {
    assert.equal(shouldRefreshPreview(5, 5), false)
  })

  test('индекс изменился — пересобрать', () => {
    assert.equal(shouldRefreshPreview(5, 6), true)
    assert.equal(shouldRefreshPreview(-1, 0), true)
  })
})

describe('BR-336: стем фиксируется только после успешной загрузки', () => {
  test('URL есть, загрузка успешна — стем фиксируется', () => {
    const out = soundSwitchOutcome('music', 'https://x/stem.mp3', null)
    assert.equal(out.result, SOUND_SWITCH.OK)
    assert.equal(out.activeSound, 'music')
  })

  test('загрузка упала — активный стем НЕ меняется (иначе повторный клик ничего не сделает)', () => {
    const out = soundSwitchOutcome('music', 'https://x/stem.mp3', new Error('403'))
    assert.equal(out.result, SOUND_SWITCH.FAILED)
    assert.equal(out.activeSound, null)
    assert.ok(out.message.length > 0)
  })

  test('URL отсутствует — стем не фиксируется', () => {
    const out = soundSwitchOutcome('music', '', null)
    assert.equal(out.result, SOUND_SWITCH.NO_URL)
    assert.equal(out.activeSound, null)
  })
})

describe('BR-337: потеря фокуса останавливает перемотку', () => {
  test('все таймеры очищены, heldKeys сброшены', () => {
    const cleared = []
    const scrubTimers = { KeyA: 1, KeyD: 2 }
    const heldKeys = { KeyA: true, KeyD: true }
    const originalClearInterval = globalThis.clearInterval
    globalThis.clearInterval = (id) => cleared.push(id)
    let released
    try {
      released = releaseAllScrubTimers(scrubTimers, heldKeys)
    } finally {
      globalThis.clearInterval = originalClearInterval
    }
    assert.deepEqual(released.sort(), ['KeyA', 'KeyD'])
    assert.equal(cleared.length, 2, 'оба интервала должны быть очищены')
    assert.equal(Object.keys(scrubTimers).length, 0)
    assert.equal(heldKeys.KeyA, false)
    assert.equal(heldKeys.KeyD, false)
  })

  test('пустые карты — безопасный no-op', () => {
    assert.deepEqual(releaseAllScrubTimers({}, {}), [])
    assert.deepEqual(releaseAllScrubTimers(null, null), [])
  })
})

describe('BR-338: расхождение текста и разметки', () => {
  test('есть текст — маркеры переразмечаются', () => {
    const plan = voiceLabelPlan({ sourceText: 'привет', markers: [{}] })
    assert.equal(plan.relabel, true)
  })

  test('нет текста, но есть маркеры — НЕ переразмечать (иначе label обнулится)', () => {
    const plan = voiceLabelPlan({ sourceText: '', markers: [{ label: 'ма' }, { label: 'ма' }] })
    assert.equal(
      plan.relabel,
      false,
      'relabelSyllables(markers, []) обнуляет label всех слоговых маркеров — потеря текста',
    )
    assert.equal(plan.hasMarkers, true)
  })

  test('пустой голос — relabel не нужен', () => {
    const plan = voiceLabelPlan({ sourceText: '   ', markers: [] })
    assert.equal(plan.relabel, false)
    assert.equal(plan.hasMarkers, false)
  })

  test('предупреждение о расхождении длин', () => {
    assert.ok(voiceLengthMismatchWarning(2, 3).length > 0)
    assert.equal(voiceLengthMismatchWarning(2, 2), '')
  })

  test('пустые голоса в хвосте не сохраняются', () => {
    const voices = [
      { sourceText: 'раз', markers: [{}] },
      { sourceText: '', markers: [] },
    ]
    const trimmed = trimTrailingEmptyVoices(voices)
    assert.equal(trimmed.length, 1, 'синтезированный пустой голос в хвосте не сохраняется')
    assert.equal(voices.length, 2, 'исходный массив не мутируется')
  })

  test('пустой голос В СЕРЕДИНЕ не удаляется (индекс голоса на сервере)', () => {
    const voices = [
      { sourceText: 'раз', markers: [{}] },
      { sourceText: '', markers: [] },
      { sourceText: 'два', markers: [{}] },
    ]
    const trimmed = trimTrailingEmptyVoices(voices)
    assert.equal(trimmed.length, 3)
  })

  test('голос с маркерами, но без текста — остаётся (маркеры несут данные)', () => {
    const voices = [{ sourceText: '', markers: [{ label: 'x' }] }]
    assert.equal(trimTrailingEmptyVoices(voices).length, 1)
  })
})

describe('BR-331…BR-338: контракт исходников (падает на старом коде)', () => {
  test('saveNow возвращает признак успеха, submit прерывается', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    const save = src.slice(src.indexOf('    async saveNow() {'), src.indexOf('    // --- Превью в плеере ---'))
    assert.ok(/return true/.test(save), 'saveNow должен возвращать признак успеха')
    assert.ok(/return false/.test(save), 'saveNow должен возвращать false при ошибке')
    const submit = src.slice(src.indexOf('    async submit() {'), src.indexOf('    async recall() {'))
    assert.ok(
      /if \(!isSaveSuccessful\(this\.saveState\)\) \{/.test(submit),
      'submit обязан прерваться, если черновик не сохранён (BR-331)',
    )
  })

  test('статус из ответа save не применяется слепо', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    assert.ok(
      src.includes('mergeSaveStatus'),
      'нужен mergeSaveStatus (BR-332)',
    )
    assert.ok(
      !/if \(body && body\.status\) this\.status = body\.status/.test(src),
      'слепое присваивание статуса из ответа save — старый баг',
    )
  })

  test('листенеры вешаются ДО асинхронной загрузки и снимаются в beforeUnmount', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    const mounted = src.slice(src.indexOf('  async mounted() {'), src.indexOf('  beforeUnmount() {'))
    const addIdx = mounted.indexOf("window.addEventListener('keydown'")
    const awaitIdx = mounted.indexOf('await this.fetchMe()')
    assert.ok(addIdx >= 0, 'keydown должен навешиваться')
    assert.ok(
      addIdx < awaitIdx,
      'листенеры должны вешаться ДО await — иначе они остаются после размонтирования (BR-333)',
    )
    assert.ok(src.includes('this._unmounted = true'), 'нужен флаг размонтирования')
    const unmount = src.slice(src.indexOf('  beforeUnmount() {'), src.indexOf('  methods: {'))
    assert.ok(
      unmount.includes("window.removeEventListener('keydown'"),
      'листенеры должны сниматься в beforeUnmount',
    )
  })

  test('timeupdate троттлится через rAF', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    assert.ok(src.includes('_rafPending'), 'нужен rAF-троттлинг currentTime')
    assert.ok(
      /formattedTextHtml\(\) \{[\s\S]{0,200}this\.previewMarkerIndex/.test(src),
      'превью должно собираться по индексу маркера (BR-335)',
    )
  })

  test('setActiveSound: сначала загрузка, потом фиксация', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    const fn = src.slice(src.indexOf('    async setActiveSound(sound) {'), src.indexOf('    // --- Голоса ---'))
    const loadIdx = fn.indexOf('await this.ws.load(url)')
    const setIdx = fn.indexOf('this.activeSound = sound')
    assert.ok(loadIdx >= 0, 'загрузка стема обязательна')
    assert.ok(
      setIdx > loadIdx,
      'activeSound должен фиксироваться ПОСЛЕ успешной загрузки (BR-336)',
    )
    assert.ok(fn.includes('catch'), 'reject от загрузки должен обрабатываться')
  })

  test('blur/visibilitychange останавливают перемотку', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    assert.ok(src.includes("window.addEventListener('blur'"), 'нужен слушатель blur')
    assert.ok(
      src.includes("document.addEventListener('visibilitychange'"),
      'нужен слушатель visibilitychange',
    )
    assert.ok(src.includes('releaseAllScrubTimers'), 'нужен сброс таймеров перемотки')
  })

  test('голос без текста не переразмечается; пустые хвостовые голоса не сохраняются', () => {
    const src = readCode('src/views/EditorWorkView.vue')
    assert.ok(src.includes('voiceLabelPlan'), 'нужен план разметки голоса (BR-338)')
    assert.ok(src.includes('trimTrailingEmptyVoices'), 'нужен обрез пустых голосов')
    assert.ok(src.includes('lengthMismatchWarning'), 'нужно предупреждение о расхождении длин')
    const loop = src.slice(src.indexOf('      for (let i = 0; i < voiceCount; i++) {'), src.indexOf('      this.currentVoiceIdx = 0'))
    assert.ok(
      /if \(plan\.relabel\) \{/.test(loop),
      'без текста relabelSyllables вызываться не должен',
    )
  })
})

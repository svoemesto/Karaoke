// BR-416 + BR-417 (аудит #336, пакет PKG-S2-17, OpenProject #380): админский лёгкий
// редактор караоке-разметки (SongKaraokeEditorModal).
//
// BR-416. Сохранение собирало маркеры ВРУЧНУЮ и жёстко писало `note: ''`, `chord: ''`,
// `stringLad: ''`, `lockLad: ''`, хотя сам редактор эти поля не редактирует и обязан
// «пронести» их с сервера. Бэкенд ПОЛНОСТЬЮ заменяет набор маркеров голоса
// (`setAllVoices`), поэтому любое сохранение — включая автосохранение через 3 с после
// правки — стирало у всех голосов ноты, аккорды и привязку к ладу, а `Song.hasChords`
// становился false. Общий helper `markersToSave` эту проблему уже решал (тикет #200), но
// в модалке осталась локальная копия старого поведения.
//
// BR-417. `requestClose` показывал подтверждение только при `saveState === 'error'`;
// после правки состояние равно 'idle', поэтому закрытие крестиком/«Отменой» в течение
// 3 с молча теряло правки: `beforeUnmount` гасил таймер, `POST
// /api/songeditor/edit/save` не отправлялся.
//
// Проверка статическая (по исходнику компонента) + поведенческая — через реальный helper,
// который теперь использует компонент. Запуск:
//   cd webvue3 && node --test \
//     src/components/SongEditor/__tests__/songkaraoke-editor-modal.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import { markersToSave } from '../../../composables/useKaraokeEditor.js'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const MODAL = stripComments(
  readFileSync(new URL('../SongKaraokeEditorModal.vue', import.meta.url), 'utf-8'),
)

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

describe('редактор: payload маркеров не стирает ноты/аккорды (BR-416)', () => {
  test('saveNow собирает маркеры общим helper-ом, а не вручную', () => {
    const body = methodBody(MODAL, 'saveNow')
    assert.ok(body)
    assert.match(body, /markersToSave\(/, 'должен использоваться общий helper (как в View)')
    assert.ok(
      !/note:\s*''/.test(body),
      "жёсткий note: '' стирал ноты всех голосов при каждом сохранении",
    )
    assert.ok(!/chord:\s*''/.test(body), "жёсткий chord: '' стирал аккорды")
    assert.ok(!/stringLad:\s*''/.test(body), "жёсткий stringLad: '' стирал привязку к ладу")
  })

  test('helper, который теперь использует модалка, проносит поля сервера', () => {
    // Ровно та структура, что приходит с сервера в editor.voices[].markers.
    const fromEditor = [
      { time: 1.5, label: 'сло_во', note: 'E|659', chord: 'Em', stringLad: '2|4', lockLad: 'true' },
    ]
    const [saved] = markersToSave(fromEditor)
    assert.equal(saved.note, 'E|659')
    assert.equal(saved.chord, 'Em')
    assert.equal(saved.stringLad, '2|4')
    assert.equal(saved.lockLad, 'true')
    assert.equal(saved.label, 'сло_во')
  })
})

describe('редактор: закрытие не теряет правки последних секунд (BR-417)', () => {
  test('правка помечается несохранённой сразу', () => {
    const body = methodBody(MODAL, 'onEditorChange')
    assert.ok(body)
    assert.match(body, /this\.hasUnsavedChanges = true/, 'без флага закрытие не знало о правке')
  })

  test('успешное сохранение снимает флаг, отказ сервера — нет', () => {
    assert.match(
      MODAL,
      /this\.hasUnsavedChanges = this\.saveState !== 'saved'/,
      'editSave умеет вернуть {ok:false} с кодом 200 — флаг обязан сниматься только при ok',
    )
  })

  test('requestClose сохраняет непустой pending перед закрытием', () => {
    const body = methodBody(MODAL, 'requestClose')
    assert.ok(body)
    assert.match(body, /await this\.saveNow\(\)/, 'закрытие обязано дождаться сохранения')
    assert.match(body, /this\.hasUnsavedChanges \|\| this\.saveTimer/, 'условие flush')
    assert.match(
      body,
      /showCloseWithoutSaveConfirm\(\)/,
      'при отказе сервера — то же подтверждение, что и для error',
    )
  })

  test('внешнее размонтирование тоже отправляет несохранённые правки', () => {
    const body = methodBody(MODAL, 'beforeUnmount')
    assert.ok(body)
    assert.match(body, /if \(this\.hasUnsavedChanges && !this\.saving\)/)
    assert.match(body, /this\.saveNow\(\)/)
  })
})

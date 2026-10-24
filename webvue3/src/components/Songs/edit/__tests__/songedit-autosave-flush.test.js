// BR-088 (аудит #336, пакет PKG-S2-17, OpenProject #380): автосейв не сбрасывался при
// смене песни и размонтировании карточки.
//
// ДО ПРАВКИ `diff`-watcher ставил таймер на `autoSaveDelayMs` (1000 мс), а сброса не было
// ни при переходе по стрелкам, ни в `beforeUnmount` (там таймер просто гасился).
// `goToPreviousSong` синхронно вызывал `setCurrentSongId` → `setCurrentSongData`
// перезаписывал `snapshotSong` → `diff` становился пустым → таймер срабатывал уже на
// пустом diff. Правка, сделанная менее секунды назад, в БД не уходила; при этом локальная
// копия песни в `songPages` уже содержала новое значение, поэтому при возврате поле
// выглядело сохранённым, а после F5 правка исчезала.
//
// Поведение самой очереди сохранения проверяет `src/lib/__tests__/autosave-queue.test.js`.
// Здесь — СТАТИЧЕСКАЯ проверка проводки: flush действительно вызывается в тех точках, где
// правка терялась (навигация и размонтирование).
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-autosave-flush.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8'))

const methodBody = (source, name) => {
  // `async ` перед именем — часть объявления (goToPreviousSong/findAudioParent и т.п.).
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

describe('автосейв сбрасывается перед сменой песни и размонтированием (BR-088)', () => {
  test('переход по стрелкам сначала сохраняет непустой diff', () => {
    for (const method of ['goToPreviousSong', 'goToNextSong', 'goToLeftSong', 'goToRightSong']) {
      const body = methodBody(SONG_EDIT, method)
      assert.ok(body, `${method} не найден`)
      assert.match(body, /await this\.flushPendingSave\(\)/, `${method} обязан сохранить правку`)
    }
  })

  test('навигация не выполняется, если сохранение не удалось (fail-closed)', () => {
    const body = methodBody(SONG_EDIT, 'goToPreviousSong')
    assert.match(
      body,
      /if \(!\(await this\.flushPendingSave\(\)\)\) return/,
      'потерять правку хуже, чем остаться на карточке с видимой ошибкой',
    )
  })

  test('beforeUnmount отправляет непустой diff, а не просто гасит таймер', () => {
    const body = methodBody(SONG_EDIT, 'beforeUnmount')
    assert.ok(body)
    assert.match(body, /if \(this\.diff\.length !== 0\)/, 'непустой diff обязан уходить на сервер')
    assert.match(body, /this\.executeSave\(\)/)
  })

  test('executeSave больше не отбрасывает вызов во время запроса', () => {
    const body = methodBody(SONG_EDIT, 'executeSave')
    assert.ok(body)
    assert.ok(
      !/if \(this\.isSaving\) return/.test(body),
      'старый guard молча терял вызов сохранения (например, flush перед размонтированием)',
    )
    assert.match(body, /this\.autosaveQueue\.run\(/, 'вызов ставится в очередь, а не отбрасывается')
  })

  test('flushPendingSave дожидается и текущего, и поставленного в очередь сохранения', () => {
    const body = methodBody(SONG_EDIT, 'flushPendingSave')
    assert.ok(body)
    assert.match(body, /await this\.executeSave\(\)/)
    assert.match(body, /await this\.autosaveQueue\.whenIdle\(\)/)
    assert.match(body, /return this\.diff\.length === 0/)
  })
})

// BR-124 (аудит #336, пакет PKG-S2-17, OpenProject #380): ручная публикация в ВК из
// карточки песни.
//
// ДО ПРАВКИ `publishToVkNow` вызывал `this.$store.dispatch('promisedXMLHttpRequest',
// request)`, хотя такого action нет ни в одном модуле (`promisedXMLHttpRequest` —
// импортированная функция из lib/utils). Vuex на неизвестный тип печатает
// `[vuex] unknown action type` и возвращает `undefined`, поэтому `.then` бросал
// `TypeError: Cannot read properties of undefined (reading 'then')` ДО отправки запроса,
// а `isPublishingVk` оставался true — обе кнопки «Публикация…» были disabled до
// перемонтирования карточки. Вторая независимая поломка: id брался из
// `this.$store.state.currentSongId`, но состояние модуля лежит под ключом `song`, в
// params уходил `id=undefined` (бэкенд ответил бы 400).
//
// Проверить это кликом нельзя без стенда с Vuex-стором, поэтому инварианты зафиксированы
// СТАТИЧЕСКИ по исходнику компонента — как в subsEdit-invariants.test.js.
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-publish-guards.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

const SONG_EDIT_RAW = readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8')

// Комментарии объясняют дефект и цитируют старый код — искать по ним нельзя,
// иначе тест «находит» то, чего в коде уже нет.
const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(SONG_EDIT_RAW)

/** Тело метода по имени (сопоставление скобок). */
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

/** Все .vue/.js файлы webvue3/src (для проверки, что «action-фантом» не вернулся). */
const walk = (dir, acc = []) => {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__' || entry === 'node_modules') continue
      walk(full, acc)
    } else if (/\.(vue|js)$/.test(entry)) {
      acc.push(full)
    }
  }
  return acc
}

describe('publishToVkNow: прямой XHR и правильный id (BR-124)', () => {
  test('не диспатчит несуществующий action promisedXMLHttpRequest', () => {
    const body = methodBody(SONG_EDIT, 'publishToVkNow')
    assert.ok(body, 'метод publishToVkNow не найден')
    assert.ok(
      !/dispatch\(\s*['"]promisedXMLHttpRequest['"]/.test(body),
      'dispatch неизвестного action возвращает undefined — .then упадёт до отправки запроса',
    )
    assert.match(body, /promisedXMLHttpRequest\(request\)/, 'должен вызываться импорт')
  })

  test('фантомный action не используется во всём webvue3', () => {
    const srcRoot = new URL('../../../../', import.meta.url).pathname
    const offenders = walk(srcRoot).filter((file) => {
      const text = stripComments(readFileSync(file, 'utf-8'))
      return /dispatch\(\s*['"]promisedXMLHttpRequest['"]/.test(text)
    })
    assert.deepEqual(offenders, [], 'promisedXMLHttpRequest — функция, а не Vuex-action')
  })

  test('id берётся из объекта песни, а не из несуществующего state.currentSongId', () => {
    const body = methodBody(SONG_EDIT, 'publishToVkNow')
    assert.ok(
      !/state\.currentSongId/.test(body),
      'состояние модуля лежит под ключом `song`, state.currentSongId даёт undefined',
    )
    assert.match(body, /this\.song\.id/, 'id песни берётся из открытой карточки')
  })

  test('флаг isPublishingVk сбрасывается в finally', () => {
    const body = methodBody(SONG_EDIT, 'publishToVkNow')
    assert.match(body, /\.finally\(/, 'сброс флага должен быть в finally')
    const finallyBlock = body.slice(body.indexOf('.finally('))
    assert.match(finallyBlock, /isPublishingVk\s*=\s*false/)
  })
})

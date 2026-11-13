// KARAOKE-429 (спека 739): карточка SongEdit закрывается сразу после подтверждения
// удаления песни.
//
// ДО ПРАВКИ (жалоба владельца на живой приёмке 2026-11-13):
// - `doDeleteSong()` ждал ответа сервера, а `$emit('close')` делал в `.then()` —
//   форма SongEdit оставалась открытой до конца запроса;
// - store-действие `deleteCurrentSong` дополнительно `await`-ило `setCurrentSongId`
//   (загрузка соседней песни) — форма держалась ещё дольше.
//
// ПОСЛЕ ПРАВКИ:
// - `doDeleteSong()` закрывает форму (`$emit('close')`) сразу, до запроса; итог
//   приходит тостом (успех / частично / ошибка) — «ложного успеха» не бывает;
// - `deleteCurrentSong` не ждёт перехода к соседней песне.
//
// Проверяется СТАТИЧЕСКИ по исходникам (без Vue Test Utils и стенда это единственный
// способ зафиксировать инвариант «форма закрывается немедленно»).
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-delete-close-immediately.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8'))
const SONGS_STORE = stripComments(readFileSync(new URL('../../store.js', import.meta.url), 'utf-8'))

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

describe('карточка SongEdit закрывается сразу после подтверждения удаления', () => {
  test('doDeleteSong закрывает форму до запроса, а не после ответа', () => {
    const body = methodBody(SONG_EDIT, 'doDeleteSong')
    assert.ok(body, 'метод doDeleteSong не найден в SongEdit.vue')

    const closeIndex = body.indexOf("$emit('close')")
    const dispatchIndex = body.indexOf("dispatch('deleteCurrentSong')")
    assert.ok(closeIndex >= 0, 'doDeleteSong должен закрывать форму ($emit close)')
    assert.ok(dispatchIndex >= 0, 'doDeleteSong должен отправлять удаление')
    assert.ok(
      closeIndex < dispatchIndex,
      'форма должна закрываться СРАЗУ: $emit(close) идёт до запроса удаления, ' +
        'иначе карточка висит до ответа сервера',
    )
  })

  test('doDeleteSong показывает итог тостом (успех/частично/ошибка)', () => {
    const body = methodBody(SONG_EDIT, 'doDeleteSong')
    assert.ok(body, 'метод doDeleteSong не найден в SongEdit.vue')
    assert.ok(
      body.includes('Удаление: успешно'),
      'после закрытия формы админ должен получить подтверждение тостом',
    )
    assert.ok(
      body.includes('Удаление: частично'),
      'частичное удаление (серверная БД) должно показываться тостом',
    )
    assert.ok(
      body.includes('Удаление: ошибка'),
      'отказ удаления должен показываться тостом — форма уже закрыта',
    )
  })

  test('deleteCurrentSong не ждёт перехода к соседней песне', () => {
    const body = methodBody(SONGS_STORE, 'deleteCurrentSong')
    assert.ok(body, 'действие deleteCurrentSong не найдено в store.js')
    assert.ok(
      !body.includes("await ctx.dispatch('setCurrentSongId'"),
      'переход к соседней песне не должен задерживать закрытие карточки',
    )
    assert.ok(
      body.includes("ctx.dispatch('setCurrentSongId'"),
      'переход к соседней песне сохраняется, но выполняется в фоне',
    )
  })
})

// BR-125 (аудит #336, пакет PKG-S2-17, OpenProject #380): асинхронные ответы применялись
// к песне, открытой СЕЙЧАС, а не к той, для которой ушёл запрос.
//
// ДО ПРАВКИ:
// - `findAudioParent()` запрашивал `/api/song/findaudioparent` по `state.currentSongId` и
//   по ответу писал `this.song.audioParentId/audioSimilarityPercent/audioDeltaMs` — то
//   есть в карточку ДРУГОЙ песни. Окно ожидания — десятки секунд (ffmpeg-декод на
//   бэкенде), навигация не блокируется. Дальше автосейв отправлял эти поля в B, и песне
//   B приписывался аудио-родитель A.
// - `showSubsEdit()` открывал модалку с голосами A при карточке B, а «Сохранить»
//   адресовалось `ctx.state.currentSongId` (B) — разметка A затирала разметку B.
// - `loadCoAuthors()` показывал соавторов A в карточке B.
//
// Проверяется СТАТИЧЕСКИ по исходникам (компонент + store): без Vue Test Utils и стенда
// это единственный способ зафиксировать инвариант «ответ адресуется песне запроса».
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-stale-response-guards.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8'))
const SUBS_EDIT = stripComments(readFileSync(new URL('../SubsEdit.vue', import.meta.url), 'utf-8'))
const SONGS_STORE = stripComments(readFileSync(new URL('../../store.js', import.meta.url), 'utf-8'))

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

describe('ответ применяется только к песне, для которой ушёл запрос (BR-125)', () => {
  test('findAudioParent сверяет id песни перед записью audio*-полей', () => {
    const body = methodBody(SONG_EDIT, 'findAudioParent')
    assert.ok(body)
    assert.match(
      body,
      /const requestedId = this\.song(?: && this\.song)?\.id/,
      'id фиксируется на момент запроса',
    )
    assert.match(
      body,
      /this\.song\.id !== requestedId/,
      'без сверки поля audio* песни A попадали в карточку B',
    )
  })

  test('showSubsEdit не открывает модалку с голосами другой песни', () => {
    const body = methodBody(SONG_EDIT, 'showSubsEdit')
    assert.ok(body)
    assert.match(body, /const requestedId = this\.song(?: && this\.song)?\.id/)
    assert.match(body, /this\.song\.id !== requestedId/)
    assert.match(body, /subsEditSongId = requestedId/, 'модалка запоминает свою песню')
  })

  test('loadCoAuthors применяет ответ только к своей песне', () => {
    const body = methodBody(SONG_EDIT, 'loadCoAuthors')
    assert.ok(body)
    assert.match(body, /const requestedId = this\.song\.id/)
    assert.match(body, /this\.song\.id !== requestedId/)
  })

  test('сохранение субтитров адресуется песне модалки, а не currentSongId', () => {
    assert.match(SONG_EDIT, /:song-id="subsEditSongId"/, 'модалка должна получать id своей песни')
    assert.match(SUBS_EDIT, /songId: this\.songId/, 'save() передаёт id песни модалки')
    // Тело action проверяем отдельно: prettier переносит длинное тернарное выражение,
    // поэтому ищем смысловые части, а не одну строку.
    const storeBody = methodBody(SONGS_STORE, 'saveSourceTextAndMarkers')
    assert.ok(storeBody, 'action saveSourceTextAndMarkers не найден')
    assert.match(storeBody, /payload\.songId !== undefined && payload\.songId !== null/)
    assert.match(storeBody, /\? payload\.songId/)
    assert.match(storeBody, /: ctx\.state\.currentSongId/)
  })
})

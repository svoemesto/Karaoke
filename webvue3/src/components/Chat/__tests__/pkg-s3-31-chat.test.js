// Регрессионные тесты пакета PKG-S3-31 (аудит #336, OpenProject #403) — чат.
//
// Запись пакета в этом файле: BR-371 (гонка загрузок чата).
//
// ДО ПРАВКИ: ни одна из трёх загрузок чата не проверяла, что ответ относится к текущему
// треду. При быстром переключении тредов ответ старого приходил позже и подменял
// переписку другого пользователя, тогда как `chatCurrentUserId` уже указывал на нового:
// админ читал переписку A, а ответ уходил бы B. Параллельно `sendChatReply` и
// 7-секундный таймер могли поллить с одинаковым `afterId` — одни и те же сообщения
// добавлялись дважды (дубликаты с одинаковым `:key`).
//
// Запуск: cd webvue3 && node --test src/components/Chat/__tests__/pkg-s3-31-chat.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const CHAT_STORE = stripComments(readFileSync(new URL('../store.js', import.meta.url), 'utf-8'))

/**
 * Достаёт тело мутации/экшена по имени.
 *
 * @param {string} source — исходник.
 * @param {string} name — имя блока.
 * @returns {string|null} тело блока либо null.
 */
const memberBody = (source, name) => {
  const definition = new RegExp('\\n\\s{4}(?:async\\s+)?' + name + '\\s*\\(').exec(source)
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

describe('PKG-S3-31 / Чат', () => {
  describe('BR-371 — ответ применяется только к текущему треду', () => {
    test('loadChatMessages сверяет тред и target', () => {
      const body = memberBody(CHAT_STORE, 'loadChatMessages')
      assert.ok(body, 'BR-371: не найден action loadChatMessages')
      assert.match(
        body,
        /ctx\.state\.chatCurrentUserId !== siteUserId/,
        'BR-371: ответ другого треда не должен применяться',
      )
      assert.match(body, /ctx\.state\.chatTarget !== target/, 'BR-371: сверять надо и БД')
      assert.match(body, /chatMessagesGeneration/, 'BR-371: нужен счётчик поколения запроса')
    })

    test('loadMoreChatMessages фиксирует тред на момент запроса', () => {
      const body = memberBody(CHAT_STORE, 'loadMoreChatMessages')
      assert.ok(body, 'BR-371: не найден action loadMoreChatMessages')
      assert.match(body, /const siteUserId = ctx\.state\.chatCurrentUserId/)
      assert.match(body, /ctx\.state\.chatCurrentUserId !== siteUserId/)
    })

    test('pollChatMessages фиксирует тред, блокирует параллельный poll и дедуплицирует', () => {
      const body = memberBody(CHAT_STORE, 'pollChatMessages')
      assert.ok(body, 'BR-371: не найден action pollChatMessages')
      assert.match(body, /chatPollInFlight/, 'BR-371: параллельный poll должен блокироваться')
      assert.match(body, /ctx\.state\.chatCurrentUserId !== siteUserId/)
      const append = memberBody(CHAT_STORE, 'appendChatMessages')
      assert.ok(append, 'BR-371: не найдена мутация appendChatMessages')
      assert.match(
        append,
        /known\.has\(message\.id\)/,
        'BR-371: сообщения не должны добавляться дважды',
      )
    })
  })
})

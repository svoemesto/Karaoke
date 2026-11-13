// KARAOKE-429 (спека 739): окно подтверждения удаления открывается СРАЗУ.
//
// Жалоба владельца на живой приёмке 2026-11-13: «между кликом на кнопку удаления
// и появлением запроса на удаление с информацией сколько чего будет удалено
// проходит около 10 секунд — за это время ничего не происходит и складывается
// ощущение что клик на кнопку ничего не дал».
//
// Причина: предпросмотр (`/api/song/deletepreview`) ходит в оба хранилища
// полным листингом бакета и занимал около десяти секунд.
//
// ДО ПРАВКИ:
// - `deleteSong()` сначала ЖДАЛ предпросмотр и только потом показывал окно;
// - обратный отсчёт подтверждения (10 секунд) стартовал при появлении окна —
//   при асинхронных числах окно закрывалось само, пока числа считались.
//
// ПОСЛЕ ПРАВКИ:
// - окно открывается сразу («Считаю, что будет удалено…»), числа подставляются
//   позже (`applyDeletePreview`);
// - отсчёт подтверждения стартует вместе с числами: `CustomConfirm` запускает
//   таймер по `params.timeout`, а он выставляется в `applyDeletePreview`.
//
// Проверяется СТАТИЧЕСКИ по исходникам (без Vue Test Utils и стенда это единственный
// способ зафиксировать инвариант «окно открывается немедленно»).
//
// Запуск: cd webvue3 && node --test src/components/Songs/edit/__tests__/songedit-delete-preview-instant.test.js

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const stripComments = (source) =>
  source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|[^:])\/\/[^\n]*/g, '$1')

const SONG_EDIT = stripComments(readFileSync(new URL('../SongEdit.vue', import.meta.url), 'utf-8'))
const CUSTOM_CONFIRM = stripComments(
  readFileSync(new URL('../../../Common/CustomConfirm.vue', import.meta.url), 'utf-8'),
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

describe('окно подтверждения удаления открывается сразу (FR-023)', () => {
  test('deleteSong показывает окно до запроса предпросмотра', () => {
    const body = methodBody(SONG_EDIT, 'deleteSong')
    assert.ok(body, 'метод deleteSong не найден в SongEdit.vue')

    const visibleIndex = body.indexOf('this.isCustomConfirmVisible = true')
    const dispatchIndex = body.indexOf("dispatch('getSongDeletePreview')")
    assert.ok(visibleIndex >= 0, 'deleteSong должен открывать окно подтверждения')
    assert.ok(dispatchIndex >= 0, 'deleteSong должен запрашивать предпросмотр')
    assert.ok(
      visibleIndex < dispatchIndex,
      'окно подтверждения должно открываться ДО предпросмотра — иначе клик выглядит ' +
        '«ничего не происходит»',
    )
    assert.ok(
      body.includes('Считаю, что будет удалено'),
      'пока числа считаются, в окне должно быть видно, что идёт подсчёт',
    )
  })

  test('отсчёт подтверждения стартует вместе с числами', () => {
    const deleteBody = methodBody(SONG_EDIT, 'deleteSong')
    const applyBody = methodBody(SONG_EDIT, 'applyDeletePreview')
    assert.ok(deleteBody, 'метод deleteSong не найден в SongEdit.vue')
    assert.ok(applyBody, 'метод applyDeletePreview не найден в SongEdit.vue')

    assert.ok(
      !deleteBody.includes('timeout: 10'),
      'отсчёт не должен стартовать вместе с пустым окном — к моменту его истечения ' +
        'числа могут быть ещё не посчитаны',
    )
    assert.ok(
      applyBody.includes('timeout = 10'),
      'отсчёт запускается, когда числа подставлены в окно',
    )
    assert.ok(
      applyBody.includes('deletePreviewText(preview)'),
      'числа предпросмотра подставляются в окно',
    )
  })

  test('CustomConfirm запускает отсчёт по params.timeout', () => {
    assert.ok(
      CUSTOM_CONFIRM.includes("'params.timeout'"),
      'CustomConfirm должен следить за params.timeout — окно с асинхронным содержимым ' +
        'должно уметь запустить отсчёт позже',
    )
    assert.ok(
      CUSTOM_CONFIRM.includes('immediate: true'),
      'отсчёт должен стартовать и для окна, у которого timeout задан сразу',
    )
  })

  test('предпросмотр говорит про папку альбома по правилу уборки', () => {
    const body = methodBody(SONG_EDIT, 'deletePreviewText')
    assert.ok(body, 'метод deletePreviewText не найден в SongEdit.vue')
    assert.ok(
      body.includes('albumFolderWillBeDeleted'),
      'текст предпросмотра обязан учитывать, удаляется ли папка альбома',
    )
    assert.ok(
      body.includes('albumFolderLeftoverFiles'),
      'текст предпросмотра должен называть остатки, которые уйдут вместе с альбомом',
    )
    assert.ok(
      !body.includes('папка не удаляется'),
      'формулировка про папку должна соответствовать фактическому результату уборки',
    )
  })
})

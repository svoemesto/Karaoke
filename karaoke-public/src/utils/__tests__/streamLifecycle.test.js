// BR-320 (аудит #336, пакет PKG-S3-27, OpenProject #399): жизненный цикл NDJSON-стрима.
//
// ЧТО БЫЛО. Store-экшен `loadZakromaStream` создавал composable стрима и не отдавал наружу
// способа его прервать. Кнопка «Отмена» на странице (`ZakromaView.cancelZakromaStream`)
// лишь ПОВТОРНО диспатчила тот же action без `force`: на первом заходе (дедуп ещё не сработал)
// это запускало ВТОРОЙ NDJSON-стрим поверх живого первого (двойная нагрузка на публичный
// hot-path эндпоинт), а при повторном заходе (< 30 с) не делало вообще ничего.
//
// ЧТО СТАЛО. Store держит активный стрим, отдаёт наружу `cancelZakromaStream`, а новый
// поток всегда ЗАМЕНЯЕТ текущий (cancel-and-restart).
//
// Запуск: cd karaoke-public && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { releaseStream, shouldReplaceActiveStream } from '../streamLifecycle.js'
import { readCode } from './sourceContract.js'

describe('BR-320: освобождение активного стрима', () => {
  test('cancel() и cleanup() вызываются', () => {
    const calls = []
    const stream = {
      cancel: () => calls.push('cancel'),
      cleanup: () => calls.push('cleanup'),
    }
    assert.equal(releaseStream(stream), true)
    assert.deepEqual(calls, ['cancel', 'cleanup'])
  })

  test('активного стрима нет — no-op', () => {
    assert.equal(releaseStream(null), false)
  })

  test('исключение в cancel() не мешает cleanup()', () => {
    const calls = []
    const stream = {
      cancel: () => {
        throw new Error('already aborted')
      },
      cleanup: () => calls.push('cleanup'),
    }
    assert.doesNotThrow(() => releaseStream(stream))
    assert.deepEqual(calls, ['cleanup'])
  })

  test('stream без cancel/cleanup — безопасно', () => {
    assert.doesNotThrow(() => releaseStream({}))
  })

  test('активный стрим всегда заменяется новым (cancel-and-restart)', () => {
    assert.equal(shouldReplaceActiveStream({}), true)
    assert.equal(shouldReplaceActiveStream(null), false)
  })
})

describe('BR-320: контракт исходников (падает на старом коде)', () => {
  test('store держит активный стрим и отдаёт cancelZakromaStream', () => {
    const src = readCode('src/store/modules/zakroma.js')
    assert.ok(src.includes('releaseStream'), 'нужен единый освободитель стрима')
    assert.ok(
      /cancelZakromaStream\(\{ commit, state \}\)/.test(src),
      'store должен отдавать наружу cancelZakromaStream (BR-320)',
    )
    assert.ok(
      /if \(shouldReplaceActiveStream\(activeStream\)\) releaseStream\(activeStream\)/.test(src),
      'новый стрим должен заменять текущий',
    )
    assert.ok(
      /finally \{[\s\S]{0,400}releaseStream\(composable\)/.test(src),
      'в finally стрим обязан быть освобождён (AbortController + подписки)',
    )
  })

  test('ZakromaView вызывает store-экшен отмены, а не перезапускает стрим', () => {
    const src = readCode('src/views/ZakromaView.vue')
    assert.ok(
      src.includes("cancelZakromaStreamAction: 'cancelZakromaStream'"),
      'store-экшен отмены должен быть промаплен',
    )
    const fn = src.slice(src.indexOf('    cancelZakromaStream() {'), src.indexOf('    onSelectSpecialBucket()'))
    assert.ok(
      /this\.cancelZakromaStreamAction\(\)/.test(fn),
      '«Отмена» должна вызывать отмену потока (BR-320)',
    )
    assert.ok(
      !/this\.loadZakromaStream\(\{ author: this\.selectedAuthor/.test(fn),
      'прежний обходной путь (повторный dispatch) запускал ВТОРОЙ стрим — это старый баг',
    )
    assert.ok(
      /beforeUnmount\(\) \{[\s\S]{0,200}this\.cancelZakromaStreamAction\(\)/.test(src),
      'уход со страницы должен прерывать стрим',
    )
  })
})

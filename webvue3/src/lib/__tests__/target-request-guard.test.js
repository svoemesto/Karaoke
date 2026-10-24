// BR-397 / BR-407 / BR-409 (аудит #336, пакет PKG-RACE-TARGET, OpenProject #367).
//
// Поколение запроса — общий примитив трёх правок: ответ target-aware запроса применяется,
// только если (а) его поколение всё ещё текущее и (б) target, с которым он отправлен, всё ещё
// выбран. Тест фиксирует семантику примитива и воспроизводит перемежающийся порядок «два
// reload, ответ старого приходит последним».
//
// Запуск: cd webvue3 && npm run test:unit

import { describe, test } from 'node:test'
import assert from 'node:assert/strict'

import { createRequestGeneration, isStaleTargetResponse } from '../target-request-guard.js'

describe('target-request-guard: поколение target-aware запросов', () => {
  test('ответ применяется только для текущего поколения', () => {
    const generation = createRequestGeneration()
    const first = generation.next()
    assert.equal(generation.isCurrent(first), true)

    const second = generation.next()
    assert.equal(generation.isCurrent(first), false)
    assert.equal(generation.isCurrent(second), true)
  })

  test('устаревший ответ отбрасывается при перемежающемся порядке (A → B → ответ B → ответ A)', () => {
    const generation = createRequestGeneration()
    const ticketA = generation.next()
    const ticketB = generation.next()

    const isStale = (ticket, requestTarget, currentTarget) =>
      isStaleTargetResponse({ ticket, requestTarget, currentTarget, generation })

    // Ответ B пришёл первым — он актуален.
    assert.equal(isStale(ticketB, 'remote', 'remote'), false)
    // Ответ A пришёл последним, но его поколение уже не текущее и target другой.
    assert.equal(isStale(ticketA, 'local', 'remote'), true)
  })

  test('ответ той же эпохи, но другой БД, тоже отбрасывается', () => {
    const generation = createRequestGeneration()
    const ticket = generation.next()
    assert.equal(
      isStaleTargetResponse({
        ticket,
        requestTarget: 'local',
        currentTarget: 'remote',
        generation,
      }),
      true,
    )
  })
})

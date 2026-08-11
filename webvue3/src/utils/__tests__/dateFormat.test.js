// Тесты для src/utils/dateFormat.js — формат момента в поясе устройства.
// Запуск: `node --test webvue3/src/utils/__tests__/dateFormat.test.js`
// (Node 22 LTS, встроенный test-runner, без vitest/jest).
//
// ВАЖНО (FR-014): тесты инвариантны от TZ машины. `getDate()/getHours()`
// читают TZ процесса; `process.env.TZ` НЕ переключает `Date` API в Node.
// Здесь тесты проверяют ФОРМАТ и КОРРЕКТНОСТЬ относительно момента,
// а не конкретный час — для конкретного TZ смотрите TZ-specific тесты ниже.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'

import formatDate from '../dateFormat.js'

const sysTz = new Intl.DateTimeFormat().resolvedOptions().timeZone

describe('formatDate (webvue3/src/utils)', () => {
  test('returns empty string for null/undefined/0/NaN/invalid Date', () => {
    assert.equal(formatDate(0), '')
    assert.equal(formatDate(null), '')
    assert.equal(formatDate(undefined), '')
    assert.equal(formatDate(NaN), '')
    assert.equal(formatDate('not a number'), '')
  })

  test('format всегда dd.MM.yyyy HH:mm (5-значная дата + пробел + 5-значное время)', () => {
    const out = formatDate(1786431456000)
    assert.match(out, /^\d{2}\.\d{2}\.\d{4} \d{2}:\d{2}$/, `format: ${out}`)
  })

  test('golden: epoch 1786431456000 — реальный момент (минуты всегда 57)', () => {
    // 1786431456000 = 2026-08-11 06:57:36 UTC. Минуты = 57, секунды = 36.
    // Не зависят от TZ (минуты и секунды одинаковы во всех TZ).
    const out = formatDate(1786431456000)
    const mm = out.split(' ')[1].split(':')[1]
    assert.equal(mm, '57', `минуты должны быть 57 во всех TZ, got: ${out}`)
  })

  test('golden: epoch 1786431456000 в текущей TZ системы', () => {
    // Реальный момент 06:57:36 UTC. В зависимости от системной TZ результат разный.
    // Moscow (+3):     09:57
    // Vladivostok(+10): 16:57
    // New_York (-4/-5): 01:57 / 02:57
    const out = formatDate(1786431456000)
    if (sysTz === 'Europe/Moscow') {
      assert.equal(out, '11.08.2026 09:57')
    } else if (sysTz === 'Asia/Vladivostok') {
      assert.equal(out, '11.08.2026 16:57')
    } else if (sysTz.startsWith('America/')) {
      assert.match(out, /^11\.08\.2026 0[12]:57$/, `got: ${out}`)
    } else {
      // Универсальный fallback: проверяем только инвариантные части.
      assert.match(out, /^11\.08\.2026 \d{2}:57$/, `got: ${out}`)
    }
  })

  test('TTL=1ч: дельта минут между двумя epoch 1 час назад и сейчас = 60', () => {
    const earlier = formatDate(1786427856000) // минус 1 час
    const later = formatDate(1786431456000)
    const h1 = parseInt(earlier.split(' ')[1].split(':')[0], 10)
    const m1 = parseInt(earlier.split(' ')[1].split(':')[1], 10)
    const h2 = parseInt(later.split(' ')[1].split(':')[0], 10)
    const m2 = parseInt(later.split(' ')[1].split(':')[1], 10)
    const diffMin = (h2 - h1) * 60 + (m2 - m1)
    assert.equal(diffMin, 60, `earlier=${earlier} later=${later}`)
  })

  test('пустая дата (revokedAt=null) -> "" — для UI админки превратится в "—"', () => {
    assert.equal(formatDate(null), '')
    assert.equal(formatDate(0), '')
  })
})

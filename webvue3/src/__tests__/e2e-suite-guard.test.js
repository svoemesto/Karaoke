// Страж e2e-набора админки webvue3.
//
// ЗАЧЕМ ЭТОТ ФАЙЛ. Аудит #336 (порция P-68) нашёл в наборе e2e четыре
// проверки, которые НЕ МОГУТ упасть, и общий корень у них один: набор никто
// не проверяет на «содержательность». Этот тест — страж, который не даёт
// таким дефектам вернуться:
//
//   1. спецификация без единого утверждения (было: measure-input-latency.spec.js,
//      BR-615) — файл с `test(...)`, но без `expect(...)` выглядит в отчёте
//      зелёным и ничего не проверяет;
//   2. тавтологичное утверждение вида `expect(x).toBeGreaterThan(0)` (было:
//      measure-formatter-vhtml.spec.js, BR-616) — истинно при любом состоянии;
//   3. зашитый id песни (BR-625) — набор работал только на одной боевой песне;
//   4. абсолютный адрес вместо baseURL (BR-617) — прогон против другого стенда
//      бил бы в локальный контейнер;
//   5. фиксированные ожидания в несколько секунд (BR-624) — набор зависел от
//      скорости машины и не мог выполняться параллельно.
//
// Тест работает без браузера и без стенда, поэтому выполняется в CI
// (npm run test:unit) — в отличие от самого e2e-набора.

import { test, describe } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'

const E2E_DIR = new URL('../../e2e/', import.meta.url)
const FIXTURES = new URL('../../e2e/fixtures/song.js', import.meta.url)

const specFiles = readdirSync(E2E_DIR)
  .filter((name) => name.endsWith('.spec.js'))
  .sort()

/**
 * Убирает комментарии, чтобы правила ниже проверяли КОД, а не прозу.
 *
 * Строчный комментарий начинается с `//` в начале строки или после пробела —
 * так `http://localhost` внутри строки не принимается за комментарий.
 * Блочный комментарий распознаётся только по `/**` или по `/*` с пробелом —
 * иначе глоб маршрута Playwright (двойная звёздочка и слэш) был бы принят за
 * начало блочного комментария и «съел» бы кусок кода.
 *
 * @param {string} src
 * @returns {string}
 */
const stripComments = (src) =>
  src
    .replace(/\/\*(?:\*|\s)[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => {
      const m = line.match(/(^|\s)\/\/.*$/)
      return m ? line.slice(0, m.index) : line
    })
    .join('\n')

describe('e2e-набор админки: страж содержательности', () => {
  test('в наборе есть спецификации', () => {
    assert.ok(specFiles.length > 0, 'в webvue3/e2e должны быть спецификации')
  })

  for (const name of specFiles) {
    test(`${name}: есть тесты и есть утверждения`, () => {
      const code = stripComments(readFileSync(new URL(name, E2E_DIR), 'utf-8'))
      assert.ok(/\btest\s*\(/.test(code), `в ${name} нет ни одного test(...)`)
      assert.ok(
        /\bexpect\s*\(/.test(code),
        `в ${name} нет ни одного expect(...) — файл ничего не проверяет ` + '(аудит #336, BR-615)',
      )
    })

    test(`${name}: нет тавтологичных утверждений`, () => {
      const code = stripComments(readFileSync(new URL(name, E2E_DIR), 'utf-8'))
      // Тавтологией считается утверждение «положительное время»: performance.now()
      // в Chromium всегда положителен, поэтому `expect(res.median).toBeGreaterThan(0)`
      // истинно даже при полностью сломанной вставке. Утверждения о КОЛИЧЕСТВЕ
      // (`marks > 0`) и о длительности АУДИО (`duration > 0` — «аудио декодировано»)
      // тавтологией не являются: их проверка и есть смысл теста.
      const tautologies =
        code.match(
          /expect\([^)]*(median|elapsed|latency|delay|\d\s*ms\b)[^)]*\)\s*\.toBeGreaterThan\(\s*0\s*\)/gi,
        ) || []
      assert.deepEqual(
        tautologies,
        [],
        `в ${name} тавтологичное утверждение (аудит #336, BR-616): ` + JSON.stringify(tautologies),
      )
    })

    test(`${name}: id песни не зашит`, () => {
      const code = stripComments(readFileSync(new URL(name, E2E_DIR), 'utf-8'))
      const hardcoded = code.match(/filterId\s*:\s*['"]\d+['"]/g) || []
      assert.deepEqual(
        hardcoded,
        [],
        `в ${name} зашит id песни (аудит #336, BR-625): ` + JSON.stringify(hardcoded),
      )
    })

    test(`${name}: нет абсолютных адресов вместо baseURL`, () => {
      const code = stripComments(readFileSync(new URL(name, E2E_DIR), 'utf-8'))
      const absolute = code.match(/['"]https?:\/\/localhost[^'"]*['"]/g) || []
      assert.deepEqual(
        absolute,
        [],
        `в ${name} абсолютный адрес вместо baseURL (аудит #336, BR-617): ` +
          JSON.stringify(absolute),
      )
    })

    test(`${name}: нет фиксированных ожиданий в несколько секунд`, () => {
      const code = stripComments(readFileSync(new URL(name, E2E_DIR), 'utf-8'))
      // Паузы до 5 секунд — это ожидание перерисовки/дебаунса, они допустимы.
      // Паузы в десятки секунд (waitForTimeout(7000…12000)) означают, что
      // тест ждёт не состояние, а время: набор зависел от скорости машины.
      const longWaits = (code.match(/waitForTimeout\(\s*(\d+)/g) || []).filter((m) => {
        const ms = Number(/\d+/.exec(m)[0])
        return ms >= 5000
      })
      assert.deepEqual(
        longWaits,
        [],
        `в ${name} фиксированное ожидание вместо ожидания состояния ` +
          `(аудит #336, BR-624): ` +
          JSON.stringify(longWaits),
      )
    })
  }

  test('общая оснастка задаёт id песни через переменную окружения', () => {
    const fixtures = readFileSync(FIXTURES, 'utf-8')
    assert.ok(
      /E2E_SONG_ID/.test(fixtures),
      'id песни должен задаваться переменной окружения E2E_SONG_ID',
    )
    assert.ok(
      /E2E_BASE_URL|baseURL/.test(fixtures),
      'адрес приложения должен задаваться через baseURL / E2E_BASE_URL',
    )
  })
})

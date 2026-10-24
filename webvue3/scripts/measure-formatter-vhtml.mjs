// Измеритель стоимости вставки HTML форматтера через v-html (#214).
//
// ЗАЧЕМ ЭТО НЕ ТЕСТ (аудит #336, BR-616, BR-617). Прежде файл лежал в каталоге
// e2e под именем `measure-formatter-vhtml.spec.js` и содержал:
//
//   * единственное утверждение `expect(res.median).toBeGreaterThan(0)` —
//     тавтологию: performance.now() в Chromium всегда положителен, поэтому
//     утверждение было истинно даже при полностью сломанной вставке;
//   * АБСОЛЮТНЫЙ адрес `http://localhost:7906` в page.request.post — он
//     перекрывал baseURL из E2E_BASE_URL, то есть при прогоне против другого
//     стенда запрос всё равно ушёл бы на локальный контейнер;
//   * ЗАШИТЫЙ id песни '11718' — измерялка работала только на одной БД;
//   * константу PAYLOAD_BYTES = 197911, которую никто не проверял: уже сейчас
//     фактический payload другой, и измерялка молча меряла бы не то.
//
// Теперь это измерялка (npm-скрипт `measure:formatter-vhtml`): адрес берётся
// из E2E_BASE_URL, id песни — из E2E_SONG_ID, ожидаемый размер payload'а —
// из E2E_EXPECTED_PAYLOAD_BYTES (если задан), а «тест» с тавтологией не
// осталось вовсе.
//
// Метод: прямая вставка innerHTML, как это делает Vue на обновлении v-html.
//
// Запуск:
//   E2E_BASE_URL=http://localhost:7906 npm run measure:formatter-vhtml

import { chromium } from '@playwright/test'

import { SONG_ID } from '../e2e/fixtures/song.js'

const BASE_URL = process.env.E2E_BASE_URL || 'http://localhost:7906'
const EXPECTED_BYTES = process.env.E2E_EXPECTED_PAYLOAD_BYTES
  ? Number(process.env.E2E_EXPECTED_PAYLOAD_BYTES)
  : null

const browser = await chromium.launch({ args: ['--no-sandbox', '--disable-dev-shm-usage'] })
const context = await browser.newContext({ baseURL: BASE_URL })
const page = await context.newPage()

try {
  // Относительный путь — baseURL подставится сам (в т.ч. из E2E_BASE_URL).
  const response = await page.request.post('/api/song/textformatted', {
    form: { id: String(SONG_ID) },
  })
  if (!response.ok()) {
    throw new Error('запрос /api/song/textformatted вернул ' + response.status())
  }
  const payload = await response.text()
  if (!payload) {
    throw new Error('песня ' + SONG_ID + ' вернула пустой payload — измерять нечего')
  }
  console.log(
    'payload: ' +
      payload.length +
      ' символов' +
      (EXPECTED_BYTES === null ? '' : ' (ожидаем ' + EXPECTED_BYTES + ')') +
      ', ' +
      (payload.match(/<span/g) || []).length +
      ' span',
  )
  if (EXPECTED_BYTES !== null && payload.length !== EXPECTED_BYTES) {
    // Не «падаем» — но честно сообщаем: молчаливое расхождение означало бы, что
    // меряем не тот payload.
    console.warn(
      'ВНИМАНИЕ: размер payload не совпал с ожиданием (' +
        payload.length +
        ' ≠ ' +
        EXPECTED_BYTES +
        ')',
    )
  }

  await page.setContent('<div id="host"></div>')
  const res = await page.evaluate((html) => {
    const host = document.getElementById('host')
    const runs = []
    for (let i = 0; i < 7; i++) {
      const t0 = performance.now()
      host.innerHTML = html // ровно то, что делает v-html
      host.getBoundingClientRect() // форсируем layout
      runs.push(performance.now() - t0)
    }
    runs.sort((a, b) => a - b)
    return { median: +runs[3].toFixed(1), min: +runs[0].toFixed(1), max: +runs[6].toFixed(1) }
  }, payload)

  console.log('вставка v-html (median/min/max мс): ' + res.median + ' / ' + res.min + ' / ' + res.max)
  console.log(
    'Бюджет — 16 мс (один кадр), см. knowledge/system/frontend/subs-edit-admin-editor.md; ' +
      'решение по порогу принимает владелец',
  )
} catch (e) {
  console.error('ИЗМЕРЕНИЕ НЕ УДАЛОСЬ: ' + (e && e.message ? e.message : e))
  process.exitCode = 1
} finally {
  await browser.close()
}

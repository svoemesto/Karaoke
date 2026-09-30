// Замер (одноразовый): стоимость вставки payload'а форматтера через v-html.
// SongEdit.vue держит три v-html (строки 2436/2441/2446), которые перезаписываются
// целиком при каждом изменении song — то есть на каждый автосейв.
// Метод: прямая вставка innerHTML, как это делает Vue на обновлении v-html.
import { expect, test } from '@playwright/test'

const PAYLOAD_BYTES = 197911

test('#214: стоимость вставки HTML форматтера в DOM', async ({ page }) => {
  test.setTimeout(120000)
  const response = await page.request.post('http://localhost:7906/api/song/textformatted', {
    form: { id: '11718' },
  })
  const payload = await response.text()
  console.log(
    `payload: ${payload.length} символов (ожидаем ~${PAYLOAD_BYTES}), ` +
      `${(payload.match(/<span/g) || []).length} span`,
  )

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

  console.log(`вставка v-html (median/min/max мс): ${res.median} / ${res.min} / ${res.max}`)
  // Порог владельца — 16 мс (один кадр). Задача только измеряет,
  // порог здесь не проверяется: решение по находке принимает владелец.
  expect(res.median).toBeGreaterThan(0)
})

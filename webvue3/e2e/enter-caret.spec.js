// Регрессия: Enter в конце строки добавляет перевод, но каретка возвращается
// на прежнюю строку вместо новой.
//
// Симптом владельца: «если встать в конец строки и нажать Enter, происходит
// добавление перевода строки, но тут же курсор переходит опять в конец строки,
// где был раньше, а не остаётся, как должно было бы быть, на новой строке».
import { expect, test } from '@playwright/test'

test.setTimeout(180000)

const openEditor = async (page) => {
  await page.route(
    (u) => u.pathname === '/api/songs',
    (r) => r.abort(),
  )
  await page.goto('/songs', { waitUntil: 'domcontentloaded' })
  await page.evaluate(async () => {
    const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    s.commit(
      'updateSongsDigests',
      JSON.parse(await s.dispatch('loadSongsDigestsPromise', { filterId: '11718' })),
    )
  })
  await page.locator('.fld-song-name').first().click()
  await page.locator('button[title="Редактировать субтитры"]').first().click()
  await page.waitForTimeout(9000)
}

const readState = (page) =>
  page.evaluate(() => {
    const el = document.querySelector('#editor')
    const sel = window.getSelection()
    const model = el.innerText.endsWith('\n') ? el.innerText.slice(0, -1) : el.innerText
    // Смещение каретки в координатах модели. Проба обязана быть ОТРИСОВАНА:
    // с visibility:hidden innerText вырождается в textContent, а на скрытом
    // элементе и вовсе отдаёт пустую строку — из-за этого первая версия
    // измерения врала и показывала смещение 0.
    let modelOffset = 0
    if (sel && sel.rangeCount) {
      const r = sel.getRangeAt(0)
      const range = document.createRange()
      range.selectNodeContents(el)
      range.setEnd(r.startContainer, r.startOffset)
      const probe = document.createElement('div')
      probe.style.cssText = 'position:absolute;left:-9999px;top:0;white-space:pre-wrap'
      probe.appendChild(range.cloneContents())
      document.body.appendChild(probe)
      const t = probe.innerText
      modelOffset = t.endsWith('\n') ? Math.max(0, t.length - 1) : t.length
      probe.remove()
    }
    return { model, caretOffset: modelOffset }
  })

test('Enter в конце строки оставляет каретку на новую строку', async ({ page }) => {
  await openEditor(page)

  // Детерминированный вход: первая строка — «АБВ», каретка в её конце.
  await page.locator('#editor').click()
  await page.keyboard.press('Control+a')
  await page.keyboard.type('АБВ')
  await page.waitForTimeout(900) // ждём отложенную перерисовку подсветки

  const before = await readState(page)
  expect(before.model, 'подготовка: в блоке должна быть строка АБВ').toBe('АБВ')
  expect(before.caretOffset, 'подготовка: каретка в конце строки').toBe(3)

  await page.keyboard.press('Enter')
  await page.waitForTimeout(1200)

  const after = await readState(page)
  console.log('МОДЕЛЬ после Enter:', JSON.stringify(after.model), 'каретка', after.caretOffset)

  // Одно нажатие Enter — ровно один перевод строки.
  expect(after.model, 'одно нажатие Enter даёт ровно один перевод строки').toBe('АБВ\n')
  expect(after.caretOffset, 'каретка должна стоять сразу за вставленным переводом').toBe(3)

  // Главная проверка — поведенческая: печатаем символ после Enter. Если каретка
  // вернулась на прежнюю строку, символ окажется ВЫШЕ перевода.
  //
  // Номер строки и экранная координата не годятся: первое считает строку как
  // число \n слева и даёт сдвиг на единицу, второе у схлопнутого Range
  // возвращает нулевой прямоугольник. Печать символа проверяет ровно то, что
  // важно человеку.
  await page.keyboard.type('Г')
  await page.waitForTimeout(900)
  const typed = await readState(page)
  console.log('МОДЕЛЬ после печати «Г»:', JSON.stringify(typed.model))
  expect(typed.model, 'символ после Enter должен попасть на новую строку').toBe('АБВ\nГ')
})

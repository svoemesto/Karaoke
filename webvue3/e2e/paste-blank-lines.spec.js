// Регрессия: вставка многоабзацного текста удваивает каждую пустую строку.
//
// Владелец сообщил: «если вставить текст, и в нём несколько абзацев, после
// вставки он задваивает каждую пустую строку (прямо видно, как добавляется
// ещё одна строка)».
//
// Тест вставляет ровно то, что owner описал, и сравнивает:
//   pasted   — что ушло в буфер обмена;
//   model    — sourceText в модели после вставки;
//   domText  — innerText блока после вставки (что реально видит человек).
// Расхождение любой из строк с pasted — дефект.

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

// Вставляет текст в блок исходного текста через настоящий paste-событие.
const pasteIntoEditor = (page, text) =>
  page.evaluate((payload) => {
    const el = document.querySelector('#editor')
    if (!el) throw new Error('блок #editor не найден')
    el.focus()
    // Выделяем всё, чтобы вставка шла на замену, как в реальной работе.
    const sel = window.getSelection()
    const range = document.createRange()
    range.selectNodeContents(el)
    sel.removeAllRanges()
    sel.addRange(range)
    const dt = new DataTransfer()
    dt.setData('text/plain', payload)
    el.dispatchEvent(new ClipboardEvent('paste', { clipboardData: dt, bubbles: true, cancelable: true }))
    return true
  }, text)

const readState = (page) =>
  page.evaluate(() => {
    const el = document.querySelector('#editor')
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    let sub = null
    const seen = new Set()
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const t = v.component?.type
      if (v.component && t && (t.name || t.__name) === 'SubsEdit') sub = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    const t = el.innerText
    return {
      model: sub ? sub.proxy.sourceText : null,
      domText: t.endsWith('\n') ? t.slice(0, -1) : t,
      html: el.innerHTML,
    }
  })

const show = (s) => JSON.stringify(s)

test('вставка с пустыми строками не удваивает их', async ({ page }) => {
  await openEditor(page)

  // Три абзаца, разделённых пустой строкой, — как в описании владельца.
  const pasted = 'Первый абзац\n\nВторой абзац\n\nТретий абзац'

  await pasteIntoEditor(page, pasted)
  await page.waitForTimeout(600)
  const afterPaste = await readState(page)

  console.log('ВСТАВЛЕНО :', show(pasted))
  console.log('МОДЕЛЬ   :', show(afterPaste.model))
  console.log('В DOM     :', show(afterPaste.domText))
  console.log('HTML      :', show(afterPaste.html.slice(0, 220)))

  // Проверяем и модель, и то, что реально видит человек.
  expect(afterPaste.model, 'sourceText должен совпадать с вставленным').toBe(pasted)
  expect(afterPaste.domText, 'текст в блоке должен совпадать с вставленным').toBe(pasted)
})

test('пустая строка в конце вставки не удваивается', async ({ page }) => {
  await openEditor(page)

  const pasted = 'Строка\n\n'

  await pasteIntoEditor(page, pasted)
  await page.waitForTimeout(600)
  const after = await readState(page)

  console.log('ВСТАВЛЕНО :', show(pasted))
  console.log('МОДЕЛЬ   :', show(after.model))
  console.log('HTML      :', show(after.html.slice(0, 200)))

  expect(after.model, 'sourceText должен совпадать с вставленным').toBe(pasted)
})

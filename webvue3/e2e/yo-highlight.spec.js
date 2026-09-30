import { test, expect } from '@playwright/test'
test.setTimeout(180000)

async function openEditor(page) {
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
  await page.locator('#waveform').waitFor({ state: 'attached' })
  await page.waitForTimeout(7000)
}

const state = (page) =>
  page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let i = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const n = v.component?.type
      if (v.component && n && (n.name || n.__name) === 'SubsEdit') i = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    const el = document.querySelector('#editor')
    const marks = el ? el.querySelectorAll('span.se-hl-attention') : []
    const sel = window.getSelection()
    return {
      words: i ? (i.proxy.attentionWords || []).length : -1,
      isCE: el ? el.getAttribute('contenteditable') : null,
      domTextMatchesModel: el
        ? el.innerText.trim() === (i ? i.proxy.sourceText : '').trim()
        : false,
      marks: marks.length,
      markBold: marks[0] ? getComputedStyle(marks[0]).fontWeight : null,
      markBg: marks[0] ? getComputedStyle(marks[0]).backgroundColor : null,
      markDisplay: marks[0] ? getComputedStyle(marks[0]).display : null,
      textAlign: el ? getComputedStyle(el).textAlign : null,
      caretOffset:
        sel && sel.rangeCount > 0 && el && el.contains(sel.anchorNode)
          ? sel.getRangeAt(0).startOffset
          : -1,
    }
  })

test('contenteditable: текст по левому краю, каретка на месте, подсветка жирная', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)
  await page.waitForTimeout(1500)
  const s0 = await state(page)
  console.log('СОСТОЯНИЕ:', JSON.stringify(s0))

  expect(s0.isCE, 'блок исходного текста должен быть contenteditable').toBe('true')
  // Словарь наполняет владелец, поэтому проверка НЕ завязана на его размер:
  // при пустом словаре подсветки просто нет, и это нормальное поведение.
  expect(s0.words, 'словарь должен загружаться без ошибки').toBeGreaterThanOrEqual(0)
  expect(s0.textAlign, 'текст должен быть по левому краю').toBe('left')
  if (s0.words > 0) {
    expect(s0.marks, 'при непустом словаре слова должны быть подсвечены').toBeGreaterThan(0)
    expect(s0.markBold, 'подсветка жирная').toBe('700')
    expect(s0.markBg, 'фон жёлтый').toBe('rgb(255, 255, 0)')
    expect(s0.markDisplay, 'подсветка inline — слово не должно уезжать на свою строку').toBe(
      'inline',
    )
  } else {
    test.info().annotations.push({
      type: 'note',
      description: 'Словарь «Слова для внимания» пуст — проверки подсветки пропущены',
    })
  }
  expect(s0.domTextMatchesModel, 'текст в DOM должен совпадать с моделью').toBe(true)
  expect(errors).toEqual([])
})

test('каретка НЕ прыгает в начало при наборе (это была поломка «слоя сверху»)', async ({
  page,
}) => {
  await openEditor(page)
  await page.waitForTimeout(1500)

  // Ставим каретку внутрь текста, печатаем и смотрим, что она осталась там же.
  await page.evaluate(() => {
    const el = document.querySelector('#editor')
    el.focus()
    const node = el.querySelector('span.se-hl-attention')?.firstChild || el.firstChild
    const r = document.createRange()
    r.setStart(node, 2)
    r.collapse(true)
    const s = window.getSelection()
    s.removeAllRanges()
    s.addRange(r)
  })
  const before = await state(page)
  await page.keyboard.type('Ж')
  await page.waitForTimeout(700)
  const after = await state(page)
  console.log(
    'ДО:',
    JSON.stringify({ off: before.caretOffset }),
    'ПОСЛЕ:',
    JSON.stringify({ off: after.caretOffset }),
  )

  // Каретка должна сдвинуться ВПЕРЁД на 1, а не схлопнуться в 0.
  expect(after.caretOffset, 'каретка должна быть после введённой буквы, а не в начале').toBe(
    before.caretOffset + 1,
  )
})

test('печать в середине текста не ломает подсветку и выравнивание', async ({ page }) => {
  await openEditor(page)
  await page.waitForTimeout(1500)
  await page.locator('#editor').click()
  await page.keyboard.press('Control+Home')
  await page.keyboard.type('Всё поёт ')
  await page.waitForTimeout(1200)
  const s = await state(page)
  console.log('ПОСЛЕ ПЕЧАТИ:', JSON.stringify(s))
  expect(s.textAlign).toBe('left')
  const words = await page.evaluate(() =>
    [...document.querySelectorAll('#editor span.se-hl-attention')].map((m) => m.textContent),
  )
  console.log('ПОДСВЕЧЕНО:', JSON.stringify(words))
  if (s.words > 0) expect(words).toContain('Всё')
  expect(s.domTextMatchesModel, 'DOM и модель не разошлись').toBe(true)
})

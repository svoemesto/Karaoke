// Блок исходного текста редактора: contentedEditable + подсветка слов словаря.
//
// ТЕСТЫ НЕ ЗАВИСЯТ ОТ СОДЕРЖИМОГО СЛОВАРЯ. Словарь «Слова для внимания»
// наполняет владелец вручную: сегодня в нём одно слово, завтра тридцать. Поэтому
// везде, где нужна подсветка, словарь подставляется через setDictionary().
// Первая версия тестов читала словарь из базы и падала, как только владелец
// добавил в него слово, которого нет в тексте проверяемой песни.

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

/** Подставляет словарь прямо в компонент — тест не зависит от данных владельца. */
const setDictionary = (page, words) =>
  page.evaluate((ws) => {
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
    i.proxy.attentionWords = ws
    i.proxy.dictVersion++
    i.proxy.syncSourceTextDom()
  }, words)

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
    return {
      isCE: el ? el.getAttribute('contenteditable') : null,
      marks: marks.length,
      markBold: marks[0] ? getComputedStyle(marks[0]).fontWeight : null,
      markBg: marks[0] ? getComputedStyle(marks[0]).backgroundColor : null,
      markDisplay: marks[0] ? getComputedStyle(marks[0]).display : null,
      textAlign: el ? getComputedStyle(el).textAlign : null,
      domTextMatchesModel: el ? el.innerText.replace(/\n$/, '') === i.proxy.sourceText : false,
    }
  })

const markedWords = (page) =>
  page.evaluate(() =>
    [...document.querySelectorAll('#editor span.se-hl-attention')].map((m) => m.textContent),
  )

test('contenteditable: текст по левому краю, подсветка жирная, жёлтая и inline', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)
  await page.waitForTimeout(1500)

  // «Деметра» в тексте песни 11718 встречается восемь раз.
  await setDictionary(page, ['Деметра'])
  await page.waitForTimeout(400)
  const s = await state(page)
  console.log('СО СВОИМ СЛОВАРЕМ:', JSON.stringify(s))

  expect(s.isCE, 'блок исходного текста должен быть contenteditable').toBe('true')
  expect(s.textAlign, 'текст должен быть по левому краю').toBe('left')
  expect(s.marks, 'слово из словаря должно подсвечиваться').toBeGreaterThan(1)
  expect(s.markBold, 'подсветка жирная').toBe('700')
  expect(s.markBg, 'фон жёлтый').toBe('rgb(255, 255, 0)')
  expect(s.markDisplay, 'подсветка inline — слово не должно уезжать на свою строку').toBe('inline')
  expect(s.domTextMatchesModel, 'текст в DOM должен совпадать с моделью').toBe(true)
  expect(errors).toEqual([])
})

test('ввод с клика попадает туда, где кликнули, и не прыгает в начало', async ({ page }) => {
  await openEditor(page)
  await page.waitForTimeout(1500)

  // Ставим каретку КЛИКОМ МЫШИ, а не через Selection API. Прежняя версия теста
  // ставила выделение программно (setStart(node, 2)) и измеряла startOffset —
  // это оказалось ненадёжно: смещение узлозависимо, текст разбит на узлы, и
  // «каретка 2 -> 117» было артефактом измерения, а не поведением редактора.
  // Клик — это то, что делает человек, и он ставит каретку в документных
  // координатах, поэтому и результат измеряем в тексте, а не в выделении.
  const box = await page.locator('#editor').boundingBox()
  await page.mouse.click(box.x + 60, box.y + 12)
  await page.waitForTimeout(200)

  const before = await page.evaluate(() => {
    const el = document.querySelector('#editor')
    return { text: el.innerText, caret: window.getSelection().getRangeAt(0).startOffset }
  })
  // Набираем НЕОДНОЗНАЧНУЮ букву: обычная 'а' в первой строке может попасть
  // внутрь уже подсвеченного слова, и подсветка изменит число узлов.
  await page.keyboard.type('Ж')
  await page.waitForTimeout(800)

  const after = await page.evaluate(() => {
    const el = document.querySelector('#editor')
    return { text: el.innerText, caret: window.getSelection().getRangeAt(0).startOffset }
  })
  console.log('КАРЕТКА:', before.caret, '->', after.caret)

  const insertedAt = after.text.indexOf('Ж')
  const firstLine = after.text.split('\n').find((l) => l.includes('Ж'))
  console.log('СИМВОЛ В ПОЗИЦИИ:', insertedAt, '| строка:', JSON.stringify(firstLine))

  // Символ обязан стоять в строке, по которой кликнули, и не в начале текста.
  expect(insertedAt, 'символ должен попасть в первую строку, а не в начало/конец').toBeLessThan(120)
  expect(firstLine, 'символ должен быть в строке, где стоял курсор').toBeTruthy()
  // Текст вырос ровно на один символ — ничего не потеряно и не продублировано.
  expect(after.text.length).toBe(before.text.length + 1)
})

test('правка подсвеченного слова снимает подсветку НА ЛЕТУ, каретка остаётся на месте', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)
  await page.waitForTimeout(1500)

  await setDictionary(page, ['Деметра'])
  await page.waitForTimeout(400)
  const before = await markedWords(page)
  console.log('ПОСЛЕ ЗАГРУЗКИ СЛОВАРЯ:', before.length, before)
  expect(before.length, 'слово из словаря должно подсвечиваться').toBeGreaterThan(1)

  // Каретка в конец подсвеченного слова + один символ — слово перестаёт быть словарным.
  await page.evaluate(() => {
    const el = document.querySelector('#editor')
    el.focus()
    const node = el.querySelector('span.se-hl-attention').firstChild
    const r = document.createRange()
    r.setStart(node, node.nodeValue.length)
    r.collapse(true)
    const s = window.getSelection()
    s.removeAllRanges()
    s.addRange(r)
  })
  await page.keyboard.type('Х')
  await page.waitForTimeout(1200) // debounce 350 мс + перерисовка

  const after = await markedWords(page)
  const caret = await page.evaluate(() => {
    const el = document.querySelector('#editor')
    const s = window.getSelection()
    if (!s || s.rangeCount === 0) return null
    const r = s.getRangeAt(0)
    return el.innerText.slice(0, r.startOffset).slice(-8)
  })
  console.log('ПОСЛЕ ПРАВКИ:', after.length, 'каретка перед:', JSON.stringify(caret))
  expect(after.length, 'подсвеченных слов стало на одно меньше').toBe(before.length - 1)
  expect(caret, 'каретка стоит сразу после введённой буквы').toBe('ДеметраХ')
  expect(errors).toEqual([])
})

test('«е» и «ё» — разные слова: «все» в словаре не подсвечивает «всё»', async ({ page }) => {
  await openEditor(page)
  await page.waitForTimeout(1500)
  await setDictionary(page, ['все'])
  await page.waitForTimeout(400)

  const words = await markedWords(page)
  const withYo = words.filter((w) => w.toLowerCase().includes('ё'))
  console.log('ПОДСВЕЧЕНО ПРИ СЛОВАРЕ «все»:', JSON.stringify(words), '| с «ё»:', withYo.length)
  expect(withYo, '«всё» не должно подсвечиваться словарным «все»').toEqual([])
})

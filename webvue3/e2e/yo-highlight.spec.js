// Блок исходного текста редактора: contentedEditable + подсветка слов словаря.
//
// ТЕСТЫ НЕ ЗАВИСЯТ ОТ СОДЕРЖИМОГО СЛОВАРЯ. Словарь «Слова для внимания»
// наполняет владелец вручную: сегодня в нём одно слово, завтра тридцать. Поэтому
// везде, где нужна подсветка, словарь подставляется через setDictionary().
//
// И НЕ ЗАВИСЯТ ОТ СОДЕРЖИМОГО ПЕСНИ (аудит #336, BR-623). Прежняя версия
// брала слово «Деметра» из текста песни 11718 и ждала, что оно встретится
// восемь раз, а каретку после правки сравнивала с константой «ДеметраХ».
// Любая легальная правка текста песни (замена слова, перенос строки) ломала
// эти утверждения без связи с проверяемым поведением. Теперь:
//   * слово для словаря берётся ИЗ ТЕКСТА, который открыт в редакторе;
//   * ожидания выводятся из фактического текста и позиции каретки, а не из
//     констант.

import { test, expect } from '@playwright/test'

import {
  markedWords,
  openEditor,
  pickWordFromSongText,
  readCaretState,
  setDictionary,
  waitForEditorText,
  waitForMarkedWords,
  waitForMarkedWordsAtLeast,
} from './fixtures/song.js'

test.setTimeout(180000)

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

test('contenteditable: текст по левому краю, подсветка жирная, жёлтая и inline', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)
  await page.waitForTimeout(1500)

  // Слово для словаря берём из текста песни, которая открыта в редакторе:
  // тест не должен зависеть от того, какие слова в тексте (аудит #336, BR-623).
  const word = await pickWordFromSongText(page)
  expect(word, 'в тексте песни должно быть слово длиной от 4 символов').not.toBe('')

  await setDictionary(page, [word])
  // Подсветка перерисовывается с задержкой (debounce) — ждём состояние, а не время.
  await waitForMarkedWordsAtLeast(page, 1)
  const s = await state(page)
  const words = await markedWords(page)
  console.log('СО СВОИМ СЛОВАРЕМ:', JSON.stringify(s), 'слово:', word, 'подсвечено:', words.length)

  expect(s.isCE, 'блок исходного текста должен быть contenteditable').toBe('true')
  expect(s.textAlign, 'текст должен быть по левому краю').toBe('left')
  // Подсветка обязана быть НЕПУСТОЙ: прежнее ожидание «больше одной» зависело
  // от того, сколько раз «Деметра» встречается в тексте песни.
  expect(s.marks, 'слово из словаря должно подсвечиваться').toBeGreaterThan(0)
  expect(
    words.every((w) => w.toLowerCase() === word.toLowerCase()),
    'подсвечено должно быть ровно слово из словаря, а не что-то другое: ' + JSON.stringify(words),
  ).toBe(true)
  expect(s.markBold, 'подсветка жирная').toBe('700')
  expect(s.markBg, 'фон жёлтый').toBe('rgb(255, 255, 0)')
  expect(s.markDisplay, 'подсветка inline — слово не должно уезжать на свою строку').toBe('inline')
  expect(s.domTextMatchesModel, 'текст в DOM должен совпадать с моделью').toBe(true)
  expect(errors).toEqual([])
})

test('ввод с клика попадает туда, где кликнули, и не прыгает в начало', async ({ page }) => {
  await openEditor(page)
  await page.waitForTimeout(1500)

  // Опорная точка — подсвеченное слово: его прямоугольник известен, а само
  // слово гарантированно есть в тексте. Кликаем мышью в конце слова — так
  // делает человек. Программная установка выделения (Selection API) ненадёжна:
  // смещение узлозависимо, и «каретка 2 → 117» было артефактом измерения.
  const word = await pickWordFromSongText(page)
  expect(word, 'в тексте песни должно быть слово длиной от 4 символов').not.toBe('')
  await setDictionary(page, [word])
  await page.waitForTimeout(400)

  const rect = await page.evaluate(() => {
    const spans = [...document.querySelectorAll('#editor span.se-hl-attention')]
    if (spans.length < 2) return null
    // Второе вхождение — не самое первое, чтобы каретка не оказалась в начале.
    const box = spans[1].getBoundingClientRect()
    return { x: box.right - 1, y: box.top + box.height / 2 }
  })
  expect(rect, 'подсвеченных слов должно быть хотя бы два').not.toBeNull()

  await page.mouse.click(rect.x, rect.y)
  await page.waitForTimeout(300)

  const before = await readCaretState(page)
  console.log('КАРЕТКА:', before.caretOffset, '| текст:', before.model.length)

  // Каретка обязана стоять НЕ в начале текста: прежняя проверка «insertedAt < 120»
  // проходила и при вставке в позицию 0 (а именно так и происходило).
  expect(
    before.caretOffset,
    'клик должен поставить каретку в текст, а не в его начало',
  ).toBeGreaterThan(0)

  // Набираем НЕОДНОЗНАЧНУЮ букву: обычная 'а' в первой строке может попасть
  // внутрь уже подсвеченного слова, и подсветка изменит число узлов.
  await page.keyboard.type('Ж')
  // Ждём, пока модель редактора примет введённый символ.
  await page.waitForFunction(
    (expected) => {
      const root = document.querySelector('#app[data-v-app]')
      const app = root && root.__vue_app__
      if (!app) return false
      const seen = new Set()
      let inst = null
      const walk = (v) => {
        if (!v || typeof v !== 'object' || seen.has(v)) return
        seen.add(v)
        const t = v.component?.type
        if (v.component && t && (t.name || t.__name) === 'SubsEdit') inst = v.component
        if (Array.isArray(v.children)) v.children.forEach(walk)
        if (v.component?.subTree) walk(v.component.subTree)
      }
      walk(app._container._vnode)
      return Boolean(inst) && inst.proxy.sourceText.length === expected
    },
    before.model.length + 1,
    { timeout: 30000 },
  )

  const after = await readCaretState(page)
  console.log('ПОСЛЕ ВВОДА:', JSON.stringify({ before: before.caretOffset, after: after.caretOffset }))

  // Текст вырос ровно на один символ — ничего не потеряно и не продублировано.
  expect(after.model.length).toBe(before.model.length + 1)
  // Символ вставлен РОВНО в позицию каретки — это и есть «ввод попадает туда,
  // где кликнули».
  expect(
    after.model[before.caretOffset],
    'введённый символ должен стоять ровно там, где была каретка',
  ).toBe('Ж')
  // Каретка сдвинулась на один символ.
  expect(after.caretOffset, 'каретка должна сдвинуться на один символ').toBe(before.caretOffset + 1)
})

test('правка подсвеченного слова снимает подсветку НА ЛЕТУ, каретка остаётся на месте', async ({
  page,
}) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)
  await page.waitForTimeout(1500)

  const word = await pickWordFromSongText(page)
  expect(word, 'в тексте песни должно быть слово длиной от 4 символов').not.toBe('')
  await setDictionary(page, [word])
  await waitForMarkedWordsAtLeast(page, 1)
  const before = await markedWords(page)
  console.log('ПОСЛЕ ЗАГРУЗКИ СЛОВАРЯ:', before.length, before)
  expect(before.length, 'слово из словаря должно подсвечиваться').toBeGreaterThan(0)

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
  // Перерисовка подсветки отложена (debounce 350 мс) и на загруженной машине
  // отрабатывает через несколько секунд — ждём состояние, а не время.
  await waitForMarkedWords(page, before.length - 1)

  const after = await markedWords(page)
  const caret = await readCaretState(page)
  console.log('ПОСЛЕ ПРАВКИ:', after.length, 'каретка перед:', caret.caretOffset)
  expect(after.length, 'подсвеченных слов стало на одно меньше').toBe(before.length - 1)
  // Каретка стоит сразу после введённой буквы: текст перед ней заканчивается
  // словом из словаря и введённой буквой. Раньше здесь стояла константа
  // «ДеметраХ» — то есть ожидание зависело от текста конкретной песни.
  expect(
    caret.model.slice(0, caret.caretOffset).endsWith(word + 'Х'),
    'каретка должна стоять сразу после введённой буквы',
  ).toBe(true)
  expect(errors).toEqual([])
})

test('«е» и «ё» — разные слова: «все» в словаре не подсвечивает «всё»', async ({ page }) => {
  await openEditor(page)
  await page.waitForTimeout(1500)

  // Текст задаёт сам тест: иначе проверка была бы пустой — если в тексте песни
  // нет ни «все», ни «всё», словарь «все» не подсвечивал бы НИЧЕГО, и
  // утверждение «нет подсветок с «ё»» проходило бы на любом коде.
  await page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let inst = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const t = v.component?.type
      if (v.component && t && (t.name || t.__name) === 'SubsEdit') inst = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    inst.proxy.sourceText = 'Все сказали, что всё хорошо'
  })
  await waitForEditorText(page, 'Все сказали, что всё хорошо')

  await setDictionary(page, ['все'])
  await waitForMarkedWordsAtLeast(page, 1)

  const words = await markedWords(page)
  const withYo = words.filter((w) => w.toLowerCase().includes('ё'))
  console.log('ПОДСВЕЧЕНО ПРИ СЛОВАРЕ «все»:', JSON.stringify(words), '| с «ё»:', withYo.length)
  // Предусловие: словарь «все» что-то подсвечивает — иначе проверка ниже пуста.
  expect(words.length, 'слово «все» должно подсвечиваться').toBeGreaterThan(0)
  expect(withYo, '«всё» не должно подсвечиваться словарным «все»').toEqual([])
})

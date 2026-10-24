// Навигация по маркерам: «[» и «]» переводят playhead на соседний маркер.
//
// ЗАЧЕМ ИНДЕКС ВЫЧИСЛЯЕТСЯ ДИНАМИЧЕСКИ (аудит #336, BR-621). Прежняя версия
// теста брала индекс 100 в списке видимых маркеров живой песни. Это значило:
//   * правка разметки песни (штатная работа админа) ломала тест — «vis[101]»
//     становился undefined, и тест падал с TypeError без отношения к
//     проверяемому поведению;
//   * константа 100 была продублирована в трёх местах, и рассинхрон между
//     ними давал ложные падения.
//
// Теперь опорная точка — середина списка видимых маркеров, а число маркеров
// проверяется явным утверждением с внятным сообщением. Индекс в исходном
// массиве маркеров (currentMarkersIndex считается по sourceMarkers) берётся
// из найденного маркера, а не из позиции в отфильтрованном списке.

import { test, expect } from '@playwright/test'

import { openEditor } from './fixtures/song.js'

test.setTimeout(180000)

// Минимум видимых маркеров, на котором тест имеет смысл.
const MIN_VISIBLE_MARKERS = 4

async function openEditorAndPick(page) {
  await openEditor(page)
  return page.evaluate((min) => {
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
    const p = i.proxy
    const vis = p.sourceMarkers.filter((m) => p.isShowMarkerType(m.markertype))
    if (vis.length < min) {
      return { error: 'видимых маркеров слишком мало: ' + vis.length }
    }
    // Середина списка — опорная точка, не зависящая от числа маркеров.
    const k = Math.floor(vis.length / 2)
    return {
      k,
      visible: vis.length,
      // currentMarkersIndex считается по sourceMarkers, поэтому нужен именно
      // индекс найденного маркера в исходном массиве.
      idxInSource: p.sourceMarkers.indexOf(vis[k]),
      tLeft: vis[k].time,
      tRight: vis[k + 1].time,
    }
  }, MIN_VISIBLE_MARKERS)
}

test('«[» с позиции МЕЖДУ маркерами идёт на маркер слева, а не через один', async ({ page }) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  const setup = await openEditorAndPick(page)
  expect(setup.error, setup.error || '').toBeUndefined()

  // Ставим playhead ровно посередине между двумя соседними видимыми маркерами.
  const at = await page.evaluate(async (t) => {
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
    const p = i.proxy
    p.ws.setTime(t)
    await new Promise((r) => setTimeout(r, 400))
    return { idx: p.currentMarkersIndex, isEditMode: p.isEditMode }
  }, (setup.tLeft + setup.tRight) / 2)

  console.log('СТАРТ:', JSON.stringify({ ...setup, ...at }))
  expect(at.isEditMode, 'редактор должен быть в режиме правки').toBe(true)
  expect(at.idx, 'после setTime индекс должен указывать на левый маркер').toBe(setup.idxInSource)

  // Вызываем функцию НАПРЯМУЮ, без клавиши: у «[» есть автоповтор
  // setInterval(..., 100), и за время удержания он успевает съехать ещё
  // на несколько маркеров назад. Для проверки смещения на единицу нужен
  // ровно один переход.
  const after = await page.evaluate(async () => {
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
    i.proxy.goToPreviousMarker()
    await new Promise((r) => setTimeout(r, 400))
    return { t: i.proxy.currentTime, idx: i.proxy.currentMarkersIndex }
  })
  console.log('ПОСЛЕ [:', JSON.stringify(after), 'ожидаем tLeft=', setup.tLeft)

  expect(
    Math.abs(after.t - setup.tLeft),
    '«[» должна встать на левый маркер, а не через один',
  ).toBeLessThan(0.05)
  expect(errors, 'переход не должен ронять редактор').toEqual([])
})

test('«]» с той же позиции идёт на правый маркер (контроль: правка не задела вперёд)', async ({
  page,
}) => {
  const setup = await openEditorAndPick(page)
  expect(setup.error, setup.error || '').toBeUndefined()

  const at = await page.evaluate(async (t) => {
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
    const p = i.proxy
    p.ws.setTime(t)
    await new Promise((r) => setTimeout(r, 400))
    return { idx: p.currentMarkersIndex }
  }, (setup.tLeft + setup.tRight) / 2)
  expect(at.idx, 'после setTime индекс должен указывать на левый маркер').toBe(setup.idxInSource)

  const after = await page.evaluate(async () => {
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
    i.proxy.goToNextMarker()
    await new Promise((r) => setTimeout(r, 400))
    return { t: i.proxy.currentTime }
  })
  expect(Math.abs(after.t - setup.tRight), '«]» должна встать на правый маркер').toBeLessThan(0.05)
})

test('«[» с позиции РОВНО НА маркере ведёт на один назад, а не на тот же', async ({ page }) => {
  const setup = await openEditorAndPick(page)
  expect(setup.error, setup.error || '').toBeUndefined()

  const at = await page.evaluate(async (t) => {
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
    const p = i.proxy
    p.ws.setTime(t)
    await new Promise((r) => setTimeout(r, 400))
    return { idx: p.currentMarkersIndex }
  }, setup.tLeft)
  expect(at.idx, 'указатель стоит на маркере').toBe(setup.idxInSource)

  const after = await page.evaluate(async () => {
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
    i.proxy.goToPreviousMarker()
    await new Promise((r) => setTimeout(r, 400))
    return { t: i.proxy.currentTime }
  })
  const tPrev = await page.evaluate(async (idx) => {
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
    return i.proxy.sourceMarkers[idx - 1].time
  }, setup.idxInSource)
  console.log('НА МАРКЕРЕ:', JSON.stringify({ on: setup.tLeft, prev: tPrev, got: after.t }))
  expect(
    Math.abs(after.t - tPrev),
    'с позиции на маркере «[» идёт на один назад',
  ).toBeLessThan(0.05)
})

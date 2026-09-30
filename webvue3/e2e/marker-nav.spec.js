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
  await page.waitForTimeout(8000)
}

test('«[» с позиции МЕЖДУ маркерами идёт на маркер слева, а не через один', async ({ page }) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)

  // Ставим playhead ровно посередине между двумя соседними видимыми маркерами.
  const setup = await page.evaluate(async () => {
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
    const k = 100
    const mid = (vis[k].time + vis[k + 1].time) / 2
    p.ws.setTime(mid)
    await new Promise((r) => setTimeout(r, 400))
    return {
      k,
      tLeft: vis[k].time,
      tRight: vis[k + 1].time,
      mid,
      idx: p.currentMarkersIndex,
      isEditMode: p.isEditMode,
    }
  })
  console.log('СТАРТ:', JSON.stringify(setup))
  expect(setup.isEditMode, 'редактор должен быть в режиме правки').toBe(true)
  expect(setup.idx, 'после setTime индекс должен указывать на левый маркер').toBe(setup.k)

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
  await openEditor(page)
  const setup = await page.evaluate(async () => {
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
    const k = 100
    p.ws.setTime((vis[k].time + vis[k + 1].time) / 2)
    await new Promise((r) => setTimeout(r, 400))
    return { tRight: vis[k + 1].time, idx: p.currentMarkersIndex }
  })
  expect(setup.idx).toBe(100)

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
  await openEditor(page)
  const setup = await page.evaluate(async () => {
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
    const k = 100
    p.ws.setTime(vis[k].time)
    await new Promise((r) => setTimeout(r, 400))
    return { tOn: vis[k].time, tPrev: vis[k - 1].time, idx: p.currentMarkersIndex }
  })
  expect(setup.idx, 'указатель стоит на маркере').toBe(100)

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
  console.log('НА МАРКЕРЕ:', JSON.stringify({ on: setup.tOn, prev: setup.tPrev, got: after.t }))
  expect(
    Math.abs(after.t - setup.tPrev),
    'с позиции на маркере «[» идёт на один назад',
  ).toBeLessThan(0.05)
})

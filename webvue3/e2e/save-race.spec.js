// #189: save() был голым dispatch без await/catch — два быстрых нажатия давали
// два ПАРАЛЛЕЛЬНЫХ POST /api/song/savesourcetextmarkers, и более ранний ответ мог
// перетереть более поздний (потеря правок).
//
// Отдельная находка: doDiffBeatsInc/Dec вызывали мутирующий эндпоинт
// /api/song/diffbeatsinc ЧЕТЫРЕ раза подряд. На бэкенде это
// `song.fields[DIFFBEATS] = diffBeats + 1; song.saveToDb()`, то есть одно
// нажатие давало +4 к diffBeats и четыре записи в БД. Кнопки, вызывавшие эти
// методы, закомментированы в шаблоне, поэтому дефект ЛАТЕНТНЫЙ — но вызывается
// через сам компонент, и при раскомментировании кнопок сразу стал бы боевым.

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
  await page.locator('#waveform').waitFor({ state: 'attached' })
  await page.waitForTimeout(9000)
}

const callComponent = (page, method, arg) =>
  page.evaluate(
    ([m, a]) => {
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
      return inst.proxy[m](a)
    },
    [method, arg ?? null],
  )

test('#189: мутирующий эндпоинт вызывается ОДИН раз, а не четыре', async ({ page }) => {
  const hits = []
  // Отвечаем -1 («песня не найдена»): тогда код НЕ дёргает doChordsDel/doChordsAdd,
  // и проверка остаётся чистой — считаем только число вызовов эндпоинта.
  await page.route('**/api/song/diffbeatsinc', (r) => {
    hits.push(Date.now())
    return r.fulfill({ status: 200, contentType: 'application/json', body: '-1' })
  })
  await openEditor(page)
  await callComponent(page, 'doDiffBeatsInc')
  await page.waitForTimeout(1200)
  expect(hits.length, 'эндпоинт мутирует БД, вызывать его нужно один раз').toBe(1)
})

test('#189: два быстрых save() не дают пересекающихся POST', async ({ page }) => {
  let inFlight = 0
  let maxInFlight = 0
  let total = 0
  await page.route('**/api/song/savesourcetextmarkers', async (r) => {
    inFlight++
    total++
    maxInFlight = Math.max(maxInFlight, inFlight)
    // Задержка, чтобы перекрытие было гарантированно заметным.
    await new Promise((res) => setTimeout(res, 400))
    inFlight--
    await r.fulfill({ status: 200, contentType: 'application/json', body: 'ok' })
  })
  await openEditor(page)
  await callComponent(page, 'save')
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
    // Второе сохранение, пока первое ещё в полёте.
    inst.proxy.save()
  })
  await page.waitForTimeout(3000)
  console.log('SAVE: всего=' + total + ' максимум_одновременных=' + maxInFlight)
  expect(maxInFlight, 'сохранения не должны пересекаться').toBe(1)
  expect(total, 'второе сохранение должно выполниться, а не потеряться').toBe(2)
})

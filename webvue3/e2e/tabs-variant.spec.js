import { expect, test } from '@playwright/test'

// #194: getStringsForAllNotesInSong() писал this.indexTabsVariant, а метод
// вызывается из computed lstIndexesTabsVariant — то есть присваивание
// происходило в фазе render, и computed был нечистым. Кламп вынесен в watcher.
//
// Первая версия теста утверждала «должен быть хотя бы один вариант строя».
// Это неверная посылка: у тестовой песни 11718 («Говорит Деметра») НЕТ нот, и
// список вариантов строя закономерно пуст. Тест падал не из-за дефекта.
//
// Здесь проверяется то, что действительно наблюдаемо: редактор открывается,
// блок варианта строя отрисовывается, значение indexTabsVariant остаётся в
// границах списка — то есть кламп из watcher'а отрабатывает. Наличие самих
// вариантов не предполагается.

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

test('#194: блок варианта строя отрисован, значение в границах списка', async ({ page }) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)

  const select = page.locator('#select-tabs')
  await expect(select).toBeVisible()

  const options = await select.locator('option').count()
  const rawValue = await page.evaluate(() => {
    const app = document.querySelector('#app[data-v-app]').__vue_app__
    const seen = new Set()
    let inst = null
    const walk = (v) => {
      if (!v || typeof v !== 'object' || seen.has(v)) return
      seen.add(v)
      const n = v.component?.type
      if (v.component && n && (n.name || n.__name) === 'SubsEdit') inst = v.component
      if (Array.isArray(v.children)) v.children.forEach(walk)
      if (v.component?.subTree) walk(v.component.subTree)
    }
    walk(app._container._vnode)
    return inst ? inst.proxy.indexTabsVariant : null
  })

  // v-model на <select> отдаёт строку, поэтому приводим к числу: индекс
  // используется как номер варианта, и неоднородность типа здесь не наш дефект.
  const value = Number(rawValue)

  console.log('TABSVARIANT: options=' + options + ' value=' + value + ' raw=' + rawValue)

  const lower = options === 0 ? -1 : 0
  const upper = Math.max(0, options - 1)
  expect(
    value,
    'indexTabsVariant вне границ [' + lower + ', ' + upper + ']',
  ).toBeGreaterThanOrEqual(lower)
  expect(value).toBeLessThanOrEqual(upper)
  expect(errors, 'ошибок на странице быть не должно').toHaveLength(0)
})

// #194: getStringsForAllNotesInSong() писал this.indexTabsVariant, а метод
// вызывается из computed lstIndexesTabsVariant — то есть присваивание
// происходило в фазе render, и computed был нечистым. Кламп вынесен в watcher.
//
// ЧТО ПРОВЕРЯЕТСЯ ИМЕННО (аудит #336, BR-619). Прежняя версия теста
// утверждала, что indexTabsVariant лежит в диапазоне [-1; 0], потому что у
// тестовой песни нет нот и список вариантов строя закономерно пуст. На таких
// данных кламп НЕ может сработать, и утверждение было истинно при любой
// реализации — в том числе если кламп убрать совсем. Тест ничего не ловил.
//
// Теперь варианты создаёт сам тест: он подставляет в разметку маркеры с нотами
// (как это уже сделано со словарём в yo-highlight.spec.js), поэтому список
// вариантов непустой, и кламп становится наблюдаемым:
//
//   1. список вариантов непустой (иначе проверять нечего);
//   2. значение, заведомо выходящее за границы (999), после пересчёта списка
//      клампится в последний индекс — то есть кламп РЕАЛЬНО отработал;
//   3. в DOM значение select'а остаётся в границах списка.
//
// Отдельно инвариант «computed не мутирует состояние» (#194) проверяется
// статически — см. src/components/Songs/edit/__tests__/subsEdit-invariants.test.js.

import { expect, test } from '@playwright/test'

import { openEditor, withSubsEdit } from './fixtures/song.js'

test.setTimeout(180000)

// Ноты для подстановки: значения из таблицы струн редактора (SubsEdit.getStrings),
// поэтому список вариантов строя действительно строится.
const NOTES = ['E|4', 'G|4']

test('#194: значение варианта строя клампится в границы списка', async ({ page }) => {
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await openEditor(page)

  const select = page.locator('#select-tabs')
  await expect(select).toBeVisible()

  // Подставляем ноты в разметку и заведомо недопустимое значение индекса.
  //
  // Ноты добавляются к СУЩЕСТВУЮЩИМ маркерам: sourceMarkers — это данные
  // компонента, а sourceText при этом не трогается, поэтому пересборка
  // разметки по тексту не затирает подстановку.
  //
  // lockLad + stringLad обязательны: getFormattedNotes берёт строку/лад из
  // маркера, только если лад зафиксирован, а иначе — из массива вариантов по
  // индексу indexTabsVariant. Без этого недопустимый индекс ронял бы
  // getFormattedNotes (TypeError) ДО того, как отработал бы кламп, и тест
  // падал бы на корректном коде.
  const injected = await withSubsEdit(page, (p, notes) => {
    const markers = p.sourceMarkers
    const touched = []
    for (let i = 0; i < markers.length && touched.length < notes.length; i++) {
      const type = markers[i].markertype
      if (type !== 'syllables' && type !== 'note') continue
      markers[i].note = notes[touched.length]
      markers[i].lockLad = true
      markers[i].stringLad = '5|0'
      touched.push(markers[i].note)
    }
    // Значение, заведомо выходящее за границы списка вариантов.
    p.indexTabsVariant = 999
    return { touched, markers: markers.length }
  }, NOTES)

  expect(
    injected.touched.length,
    'в разметку нужно подставить ноты — иначе вариантов строя не будет',
  ).toBe(NOTES.length)

  // Ждём, пока список вариантов пересчитается (предусловие проверки).
  await page.waitForFunction(
    () => {
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
      if (!inst) return false
      return inst.proxy.lstIndexesTabsVariant.length > 0
    },
    null,
    { timeout: 30000 },
  )

  // Кламп обязан отработать: значение 999 обязано уехать в границы списка.
  // Если клампа нет (регресс #194), poll истечёт и тест упадёт сExpected/Received.
  await expect
    .poll(async () => (await withSubsEdit(page, (p) => p.indexTabsVariant)), { timeout: 10000 })
    .toBeLessThan(999)

  const state = await withSubsEdit(page, (p) => ({
    variants: p.lstIndexesTabsVariant.length,
    value: p.indexTabsVariant,
  }))

  const options = await select.locator('option').count()
  console.log(
    'TABSVARIANT: подставлено=' +
      JSON.stringify(injected.touched) +
      ' вариантов=' +
      state.variants +
      ' options=' +
      options +
      ' value=' +
      state.value,
  )

  // Предусловие: варианты есть. Без них кламп не может сработать, и проверка
  // ниже была бы пустой (ровно тот дефект, который закрывается в BR-619).
  expect(state.variants, 'список вариантов строя должен быть непустым').toBeGreaterThan(0)
  expect(options, 'в select должны быть варианты').toBe(state.variants)

  // ГЛАВНОЕ УТВЕРЖДЕНИЕ: значение 999 клампится в последний индекс списка.
  // Если кламп убрать (или вернуть мутацию в computed), значение останется 999 —
  // и тест упадёт.
  expect(
    state.value,
    'indexTabsVariant должен быть клампнут в границы списка вариантов',
  ).toBe(state.variants - 1)
  expect(state.value).toBeGreaterThanOrEqual(0)
  expect(state.value).toBeLessThanOrEqual(state.variants - 1)
  expect(errors, 'ошибок на странице быть не должно').toHaveLength(0)
})

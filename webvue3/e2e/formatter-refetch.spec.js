// #218: правка текста/аккордов НЕ обновляла форматтеры в SongEdit.
//
// Три билдера на бэкенде (Song.getTextFormatted/getFormattedNotes/getFormattedChords)
// считаются ТОЛЬКО из sourceMarkers. Но watcher `song` в SongEdit срабатывает
// только при смене песни, а сохранение разметки (SubsEdit → saveSourceTextAndMarkers)
// currentSong не трогает. Форматтеры оставались прежними до перезахода на песню.
//
// Тест обязан ПАДАТЬ на старом коде: там после сохранения разметки форматтеры
// не перезапрашиваются (0 запросов), а должны перезапрашиваться.
//
// Отдельно зафиксировано (замером на старом коде), что формулировка «перезапрос
// на каждый автосейв» была неверной: commit('saveSong') меняет только
// snapshotSong, поэтому смена песни даёт 3 запроса, а правка поля + автосейв — 0.

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
  await page.waitForFunction(
    () => {
      const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
      const song = s.getters.getCurrentSong
      return Boolean(song && song.id)
    },
    null,
    { timeout: 30000 },
  )
  await page.waitForTimeout(2500)
}

// Счётчик обращений к трём эндпоинтам форматтеров.
const countFormatterCalls = async (page) => {
  const hits = { text: 0, notes: 0, chords: 0 }
  await page.route('**/api/song/*formatted*', async (route) => {
    const u = route.request().url()
    if (u.includes('textformatted')) hits.text += 1
    else if (u.includes('notesformatted')) hits.notes += 1
    else if (u.includes('chordsformatted')) hits.chords += 1
    await route.fulfill({ status: 200, contentType: 'text/html', body: '' })
  })
  return hits
}

test('#218: сохранение разметки обновляет форматтеры в SongEdit', async ({ page }) => {
  await openEditor(page)

  // Считаем ТОЛЬКО после открытия формы, чтобы не поймать первичную загрузку.
  const hits = await countFormatterCalls(page)
  const before = { ...hits }

  // Сохраняем разметку — так же, как это делает SubsEdit.
  await page.route('**/api/song/savesourcetextmarkers*', (r) =>
    r.fulfill({ status: 200, contentType: 'application/json', body: 'true' }),
  )
  await page.evaluate(async () => {
    const s = document.querySelector('#app[data-v-app]').__vue_app__.config.globalProperties.$store
    await s.dispatch('saveSourceTextAndMarkers', {
      voice: 0,
      sourceText: 'Проверка #218',
      sourceMarkers: '[]',
      indexTabsVariant: 0,
    })
  })
  await page.waitForTimeout(2500)

  const delta = {
    text: hits.text - before.text,
    notes: hits.notes - before.notes,
    chords: hits.chords - before.chords,
  }
  console.log('перезапросов форматтеров после сохранения разметки:', JSON.stringify(delta))

  expect(delta.text, 'textformatted должен обновиться после сохранения разметки').toBe(1)
  expect(delta.notes, 'notesformatted должен обновиться после сохранения разметки').toBe(1)
  expect(delta.chords, 'chordsformatted должен обновиться после сохранения разметки').toBe(1)
})

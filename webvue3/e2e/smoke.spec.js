// Смоук-тест инфраструктуры e2e для webvue3.
//
// Задача НЕ в проверке бизнес-логики, а в том, чтобы убедиться, что:
//   1. Playwright доходит до запущенного контейнера karaoke-webvue3;
//   2. приложение реально отдаёт SPA, а не пустую страницу;
//   3. ошибки в консоли браузера видны тесту (на них будут опираться
//      регрессионные тесты на дефекты #187 и #188 карты #186).
//
// Адрес: E2E_BASE_URL, по умолчанию http://localhost:7906 (контейнер на nsa-i9).

import { expect, test } from '@playwright/test'

test.describe('инфраструктура e2e', () => {
  test('приложение отдаёт SPA и грузится без ошибок в консоли', async ({ page }) => {
    const consoleErrors = []
    const pageErrors = []
    page.on('console', (msg) => {
      if (msg.type() === 'error') consoleErrors.push(msg.text())
    })
    page.on('pageerror', (err) => pageErrors.push(err.message))

    const response = await page.goto('/')
    expect(response, 'главная должна отвечать').not.toBeNull()
    expect(response.status(), 'главная отвечает 200').toBe(200)

    // SPA-обвязка Vite: корневой элемент, в который смонтировано приложение.
    //
    // Селектор уточнён до [data-v-app], и это НЕ косметика: в документе
    // одновременно два элемента с id="app" — внешний контейнер монтирования
    // и внутренний <div v-else id="app"> из App.vue:5. Дубликат ID заведён
    // как отдельная находка (карта #186); здесь берём однозначный корень.
    // Не «чиним» здесь молча: см. [wayfinder:finding] про дубликат id="app".
    await expect(page.locator('#app[data-v-app]')).toHaveCount(1)
    await expect(page).toHaveTitle(/KARAOKE/i)

    // Необработанных исключений в JS быть не должно. Порог 0 строгий намеренно:
    // дефект #187 карты #186 — это как раз unhandled TypeError из осиротевшего
    // обработчика клавиатуры, и он обязан валить тест, а не тихо проходить.
    expect(pageErrors, `pageerror: ${pageErrors.join(' | ')}`).toEqual([])
    expect(consoleErrors, `console.error: ${consoleErrors.join(' | ')}`).toEqual([])
  })

  test('маршрут админки доступен', async ({ page }) => {
    const response = await page.goto('/songs')
    expect(response).not.toBeNull()
    // Не фиксируем конкретный код: SPA может отдать 200 с редиректом на логин
    // либо наоборот отдать 404 и показать свой экран. Проверяем, что сервер
    // ответил и приложение смонтировалось.
    expect(response.status()).toBeLessThan(500)
    await expect(page.locator('#app[data-v-app]')).toHaveCount(1)
  })

  // #199: в DOM было ДВА элемента с id="app" — точка монтирования из index.html
  // и ещё один, который рисовал App.vue внутри неё. Не-scoped правило `#app`
  // (flex-контейнер высотой 100vh) применялось к обоим.
  test('id="app" в DOM ровно один', async ({ page }) => {
    await page.goto('/songs')
    await expect(page.locator('#app[data-v-app]')).toHaveCount(1)
    const count = await page.evaluate(() => document.querySelectorAll('#app').length)
    expect(count, 'точка монтирования и корень приложения не должны делить один id').toBe(1)
  })
})

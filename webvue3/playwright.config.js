// Playwright-конфигурация для e2e-тестов админки (webvue3).
//
// Контекст: усилие «Аудит SubsEdit.vue» (карта OpenProject #186). Инфраструктура
// e2e доводится до рабочего состояния ДО починки дефектов, чтобы каждый S1
// закрывался регрессионным тестом, а не «на глаз».
//
// Прецедент в проекте: Playwright уже используется на бэкенде —
// karaoke-app/build.gradle.kts подключает com.microsoft.playwright:playwright:1.56.0,
// а knowledge/domains/processing/components/playwright-rendering.md фиксирует
// ловушки headless-запуска. Здесь npm-драйвер, версии независимы.
//
// Запуск: npm run test:e2e          (headless, против запущенного контейнера)
//         npm run test:e2e:headed   (то же, но с видимым браузером)
//
// Адрес приложения переопределяется переменной E2E_BASE_URL — это то, что
// понадобится для CI и для прогона против другого окружения.

import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:7906'

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.artifacts',
  // Исключаем артефакты прогона из индекса тестов.
  testIgnore: /.*\.spec\.js\.tmp/,

  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1, // админка мутирует общее состояние песни; параллелизм небезопасен

  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
    // Ловушка из knowledge/domains/processing/components/playwright-rendering.md:
    // headless Chromium требует --no-sandbox, иначе permission denied.
    launchOptions: { args: ['--no-sandbox', '--disable-dev-shm-usage'] },
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1600, height: 1000 } },
    },
  ],
})

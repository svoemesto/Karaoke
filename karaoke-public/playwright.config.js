// Playwright-конфигурация для e2e-тестов публичного сайта (karaoke-public).
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
//
// CI (BR-525, аудит #336, OpenProject #387): джоб `e2e (karaoke-public)` в
// .github/workflows/lint.yml собирает образ karaoke-public из репозитория,
// поднимает его и прогоняет тесты. Ветки process.env.CI ниже перестали быть
// мёртвыми (до BR-525 e2e в CI не запускались вообще):
//   - forbidOnly — в CI запрещено оставлять test.only (иначе прогон «зелёный»
//     не из-за всех тестов);
//   - reporter — в CI отчёт с аннотациями GitHub + HTML-артефакт.
//
// BR-524: проверка «контракт развёртывания» помечена тегом @smoke — она не
// блокирует CI (прогон без неё: `npx playwright test --grep-invert @smoke`).

import { defineConfig, devices } from '@playwright/test'

const baseURL = process.env.E2E_BASE_URL || 'http://localhost:7907'

export default defineConfig({
  testDir: './e2e',
  outputDir: './e2e/.artifacts',
  // Исключаем артефакты прогона из индекса тестов.
  testIgnore: /.*\.spec\.js\.tmp/,

  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  // Ретраи в CI МАСКИРОВАЛИ нестабильность прогона (аудит #336, P-50-03):
  // красный прогон «лечился» повтором и не всплывал. Теперь прогон
  // детерминирован — красное означает красное.
  retries: 0,
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

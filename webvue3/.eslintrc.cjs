/* eslint-env node */
// Карат-специфический ESLint-конфиг для webvue3 (admin SPA).
//
// @vue/eslint-config-typescript ОТКЛЮЧЁН: версия 13.x зависит от
// @typescript-eslint/type-utils → ts-api-utils, который не совместим
// с Node 25.7.0 (TypeError: 'Intrinsic'). TypeScript-проверка делается
// отдельно через `npx vue-tsc --noEmit` (см. CONTRIBUTING.md).
//
// Чтобы включить TS в ESLint:
// 1. Дождаться фикса в @vue/eslint-config-typescript (см. issue tracker).
// 2. Или использовать альтернативный конфиг (например, @typescript-eslint
//    напрямую с правильными peer-deps).
// 3. Или зафиксировать @typescript-eslint на версии 7.x с ручным патчем.
module.exports = {
  root: true,
  extends: [
    'plugin:vue/vue3-recommended',
    // 'eslint:recommended',  // базовые JS-правила; включаем поэтапно
    '@vue/eslint-config-prettier/skip-formatting',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    // Karaoke-специфические правила (см. CONTRIBUTING.md, раздел Vue).
    'vue/multi-word-component-names': 'off',
    'vue/html-self-closing': ['error', {
      html: { void: 'always', normal: 'always', component: 'always' },
    }],
    // vue/max-attributes-per-line — оставляем настройки по умолчанию из
    // plugin:vue/vue3-recommended (чтобы избежать несовместимости схем
    // между версиями eslint-plugin-vue).
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-indent': 'off',
    // vue/require-explicit-emits — ОТКЛЮЧЁН (было 50 в baseline).
    // Правило требует декларировать все emits в опции `emits`, но в проекте
    // 50+ компонентов с emit('xxx') без явной декларации. Это стилистическая
    // рекомендация (для IDE автодополнения), не баг. Заменено
    // документацией в CONTRIBUTING.md (раздел "Vue: emit declaration").
    // Включение обратно: убрать строку ниже + добавить `emits: [...]` в каждый
    // компонент, использующий emit.
    'vue/require-explicit-emits': 'off',
    // vue/no-template-shadow — ОТКЛЮЧЁН (1 в baseline). Стилистическое.
    'vue/no-template-shadow': 'off',
    'no-unused-vars': ['warn', {
      argsIgnorePattern: '^_',
      varsIgnorePattern: '^_',
    }],
    // vue/no-v-html — ОТКЛЮЧЁН (10 в baseline). Правило рекомендует
    // избегать v-html из-за XSS, но в проекте v-html используется только
    // для уже подготовленного сервером текста (lyrics с <br>, preview с
    // разметкой) — пользовательский ввод не передаётся напрямую.
    // Долгосрочно: заменить v-html на v-text + DOMPurify или парсинг
    // BBCode/Markdown. См. CONTRIBUTING.md, раздел "Vue: v-html usage".
    'vue/no-v-html': 'off',
    // vue/require-toggle-inside-transition — ОТКЛЮЧЁН (20 в baseline).
    // Стилистическое правило: требует v-if/v-show внутри <transition>.
    // В проекте <transition> оборачивает модалки с show/hide через
    // v-model + Boolean prop, либо используется для CSS-анимаций выхода
    // (появление гарантировано v-if на самом <transition>). Не баг.
    // Включение обратно: убрать строку ниже, добавить v-if/v-show в
    // каждый <transition>-блок (20 мест в webvue3).
    'vue/require-toggle-inside-transition': 'off',
  },
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  ignorePatterns: ['dist/**', 'node_modules/**', '*.config.js', '*.config.cjs'],
}

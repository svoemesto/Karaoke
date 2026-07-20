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
  },
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  ignorePatterns: ['dist/**', 'node_modules/**', '*.config.js', '*.config.cjs'],
}

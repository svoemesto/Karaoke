/* eslint-env node */
// Карат-специфический ESLint-конфиг для karaoke-public (public SPA).
//
// @vue/eslint-config-typescript ОТКЛЮЧЁН: версия 13.x зависит от
// @typescript-eslint/type-utils → ts-api-utils, который не совместим
// с Node 25.7.0 (TypeError: 'Intrinsic'). TypeScript-проверка делается
// отдельно через `npx vue-tsc --noEmit` (см. CONTRIBUTING.md).
module.exports = {
  root: true,
  extends: [
    'plugin:vue/vue3-recommended',
    '@vue/eslint-config-prettier/skip-formatting',
  ],
  parserOptions: {
    ecmaVersion: 'latest',
    sourceType: 'module',
  },
  rules: {
    'vue/multi-word-component-names': 'off',
    'vue/html-self-closing': ['error', {
      html: { void: 'always', normal: 'always', component: 'always' },
    }],
    // vue/max-attributes-per-line — оставляем настройки по умолчанию.
    'vue/singleline-html-element-content-newline': 'off',
    'vue/html-indent': 'off',
    // vue/require-explicit-emits — ОТКЛЮЧЁН (см. webvue3/.eslintrc.cjs).
    'vue/require-explicit-emits': 'off',
    'no-unused-vars': ['warn', {
      argsIgnorePattern: '^_',
      varsIgnorePattern: '^_',
    }],
    // vue/no-v-html — ОТКЛЮЧЁН (4 в baseline). Правило рекомендует
    // избегать v-html из-за XSS, но в проекте v-html используется только
    // для уже подготовленного сервером текста (lyrics с <br>).
    // См. CONTRIBUTING.md, раздел "Vue: v-html usage".
    'vue/no-v-html': 'off',
  },
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  ignorePatterns: ['dist/**', 'node_modules/**', '*.config.js', '*.config.cjs'],
}

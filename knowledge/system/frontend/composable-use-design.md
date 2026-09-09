# Composable: useDesign

> **Домен**: system (frontend)
> **Компонента**: `karaoke-public/src/composables/useDesign.js` —
> тема сайта (dark/light/system).

## Файл

`karaoke-public/src/composables/useDesign.js` (25 строк, **компактный**)

## Назначение

Управление **темой** сайта (dark/light/system). `system` следует
за `prefers-color-scheme` браузера.

## State

```javascript
const theme = ref(localStorage.getItem('karaoke-theme') || 'system')
```

**`localStorage` ключ**: `karaoke-theme`. Значения: `'dark' | 'light' | 'system'`.

## Логика

```javascript
function applyTheme(val) {
  const sys = window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = val === 'dark' || (val === 'system' && sys)
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
}

watch(theme, (val) => {
  localStorage.setItem('karaoke-theme', val)
  applyTheme(val)
  trackUi('theme', `theme:${val}`)
})

applyTheme(theme.value)
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (theme.value === 'system') applyTheme('system')
})
```

## Hot paths

- Инициализация при загрузке (вызов `applyTheme(theme.value)`).
- `prefers-color-scheme: dark` change — если `theme=system`.

## Связь

- **Tracking** (`useDesign` вызывает `trackUi`) — аналитика.

## Changelog

- **Pass 387** (2026-09-09): Initial. Автор: agent (Karaoke).
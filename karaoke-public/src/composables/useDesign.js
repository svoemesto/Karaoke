import { ref, watch } from 'vue'
import { trackUi } from '../services/tracking'

const theme = ref(localStorage.getItem('karaoke-theme') || 'system')

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

export function useDesign() {
  return { theme, applyTheme }
}

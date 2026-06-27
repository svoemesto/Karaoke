import { ref, watch } from 'vue'

const design = ref(localStorage.getItem('karaoke-design') || 'classic')
const theme  = ref(localStorage.getItem('karaoke-theme')  || 'system')

function applyTheme(val) {
  const sys = window.matchMedia('(prefers-color-scheme: dark)').matches
  const dark = val === 'dark' || (val === 'system' && sys)
  document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
}

watch(design, val => localStorage.setItem('karaoke-design', val))
watch(theme,  val => {
  localStorage.setItem('karaoke-theme', val)
  applyTheme(val)
})

applyTheme(theme.value)
window.matchMedia('(prefers-color-scheme: dark)')
  .addEventListener('change', () => { if (theme.value === 'system') applyTheme('system') })

export function useDesign() {
  return { design, theme, applyTheme }
}

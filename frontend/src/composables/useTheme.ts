import { ref } from 'vue'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'tracing-ats-theme'
const theme = ref<Theme>('light')

function apply(value: Theme) {
  theme.value = value
  document.documentElement.classList.toggle('dark', value === 'dark')
}

function storedTheme(): Theme | null {
  try {
    const value = window.localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : null
  } catch {
    return null
  }
}

export function initTheme(): Theme {
  const value = storedTheme() ?? 'light'
  apply(value)
  return value
}

export function useTheme() {
  function toggleTheme() {
    const next: Theme = theme.value === 'dark' ? 'light' : 'dark'
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch {
      // Приватный режим может запрещать localStorage — тема всё равно применится.
    }
    apply(next)
  }

  return { theme, toggleTheme }
}

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { initTheme } from './composables/useTheme'
import { logger } from './lib/logger'
import './style.css'

initTheme()

const app = createApp(App)

app.config.errorHandler = (error) => {
  logger.error('ui_error', error instanceof Error ? error.message : 'unknown')
}

window.addEventListener('unhandledrejection', (event) => {
  logger.error(
    'ui_unhandled_rejection',
    event.reason instanceof Error ? event.reason.message : 'unknown',
  )
})

app.use(router).mount('#app')

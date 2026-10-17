import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'
import { createVuetify } from 'vuetify'
import { aliases, mdi } from 'vuetify/iconsets/mdi'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import App from './App.vue'

const vuetify = createVuetify({
  components,
  directives,
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: {
      mdi,
    },
  },
  theme: {
    defaultTheme: 'dark',
    themes: {
      dark: {
        dark: true,
        colors: {
          primary: '#06b6d4',
          secondary: '#3b82f6',
          accent: '#10b981',
          background: '#0b1329',
          surface: '#15213b',
          'surface-variant': '#1e293b',
          error: '#ef4444',
          info: '#38bdf8',
          success: '#22c55e',
          warning: '#f59e0b',
        },
      },
    },
  },
})

const app = createApp(App)
app.use(createPinia())
app.use(vuetify)
app.mount('#app')

import { createApp } from 'vue'
import App from './App.vue'
import router from './router/routes.js'
import './shared/styles/app.css'

createApp(App).use(router).mount('#app')

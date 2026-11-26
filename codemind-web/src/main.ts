import 'element-plus/theme-chalk/dark/css-vars.css'
import 'element-plus/dist/index.css'
import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import { router } from './router'

createApp(App)
  .use(router)
  .mount('#app')

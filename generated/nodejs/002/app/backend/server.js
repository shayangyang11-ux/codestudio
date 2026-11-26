import { createApp } from './src/app.js'

const port = Number(process.env.PORT || 3101)
const app = createApp()

app.listen(port, () => {
  console.log(`Generated backend listening on http://127.0.0.1:${port}`)
})

import app from './src/app.js'

const port = Number(process.env.PORT || 3101)

app.listen(port, '0.0.0.0', () => {
  console.log(`[CodeMind] Generated backend listening on http://127.0.0.1:${port}`)
})

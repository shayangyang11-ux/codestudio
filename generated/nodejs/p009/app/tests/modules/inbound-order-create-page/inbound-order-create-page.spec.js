async function main() {
  const list = await fetch('http://127.0.0.1:3101/api/inbound-order-create-page')
  assert(list.ok, 'list endpoint should be ok')

  const created = await fetch('http://127.0.0.1:3101/api/inbound-order-create-page', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title: '入库单创建页 自动化验收记录' }),
  })
  assert(created.ok, 'create endpoint should be ok')

  console.log('Page smoke test passed.')
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

main().catch((error) => {
  console.error(error)
  process.exit(1)
})

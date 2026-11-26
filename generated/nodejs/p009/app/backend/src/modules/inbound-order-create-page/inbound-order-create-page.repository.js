import { readStore, writeStore } from '../../db/localStore.js'

export async function listInboundOrderCreatePage() {
  const store = await readStore()
  return store.records || []
}

export async function insertInboundOrderCreatePage(payload) {
  const store = await readStore()
  const records = store.records || []
  const nextRecord = {
    id: `inbound-order-create-page-${Date.now()}`,
    ...payload,
    createdAt: new Date().toISOString(),
  }

  records.push(nextRecord)
  await writeStore({ ...store, records })
  return nextRecord
}

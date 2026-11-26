import { seedData } from './seedData.js'

const store = new Map()

export function collection(name) {
  if (!store.has(name)) {
    store.set(name, JSON.parse(JSON.stringify(seedData[name] || [])))
  }
  return store.get(name)
}

export function resetStore() {
  store.clear()
}

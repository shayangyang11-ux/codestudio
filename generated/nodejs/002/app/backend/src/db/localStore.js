import { promises as fs } from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { seedData } from './seedData.js'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const dataFile = path.resolve(__dirname, '../../data/local-store.json')

export async function readStore() {
  try {
    const content = await fs.readFile(dataFile, 'utf-8')
    return JSON.parse(content)
  } catch (_error) {
    await writeStore(seedData)
    return structuredClone(seedData)
  }
}

export async function writeStore(nextStore) {
  await fs.mkdir(path.dirname(dataFile), { recursive: true })
  await fs.writeFile(dataFile, JSON.stringify(nextStore, null, 2), 'utf-8')
  return nextStore
}

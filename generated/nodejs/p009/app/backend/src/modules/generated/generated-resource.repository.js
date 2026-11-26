const collections = new Map()

export function getCollection(resource) {
  const key = resource.resourceKey || resource.apiSegment || 'resource'
  if (!collections.has(key)) {
    collections.set(key, {
      nextId: 1000,
      items: structuredCloneSafe(resource.seedRecords || []),
    })
  }
  return collections.get(key)
}

function structuredCloneSafe(value) {
  return JSON.parse(JSON.stringify(value || []))
}

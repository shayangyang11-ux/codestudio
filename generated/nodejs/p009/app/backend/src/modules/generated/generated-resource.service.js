import { getCollection } from './generated-resource.repository.js'
import { validatePayload } from './generated-resource.validation.js'

export function createGeneratedResourceService(resource) {
  const collection = getCollection(resource)

  function list(query = {}) {
    const q = String(query.q || query.keyword || '').trim().toLowerCase()
    let items = collection.items.filter((item) => !item.__deleted)

    if (q) {
      items = items.filter((item) => Object.values(item).some((value) => String(value ?? '').toLowerCase().includes(q)))
    }

    for (const field of resource.fields || []) {
      const value = query[field.key]
      if (value !== undefined && value !== null && String(value).trim() !== '') {
        items = items.filter((item) => String(item[field.key] ?? '') === String(value))
      }
    }

    return {
      items,
      total: items.length,
      resource: {
        resourceKey: resource.resourceKey,
        title: resource.title,
        fields: resource.fields,
      },
    }
  }

  function get(id) {
    return collection.items.find((item) => String(item.id) === String(id) && !item.__deleted) || null
  }

  function create(payload) {
    const errors = validatePayload(resource, payload)
    if (errors.length) {
      const error = new Error('Validation failed: ' + errors.join('; '))
      error.status = 400
      throw error
    }

    const now = new Date().toISOString()
    const record = {
      id: payload.id || `${resource.resourceKey}-${collection.nextId++}`,
      ...payload,
      createdAt: payload.createdAt || now,
      updatedAt: now,
    }
    collection.items.unshift(record)
    return record
  }

  function update(id, payload) {
    const current = get(id)
    if (!current) return null
    Object.assign(current, payload, { updatedAt: new Date().toISOString() })
    return current
  }

  function remove(id) {
    const current = get(id)
    if (!current) return null
    current.__deleted = true
    current.updatedAt = new Date().toISOString()
    return current
  }

  function runAction(actionId, payload) {
    const action = String(actionId || '').toLowerCase()
    if (action.includes('create') || action.includes('save') || action.includes('submit') || action.includes('新增') || action.includes('保存') || action.includes('提交')) {
      const form = payload.form && typeof payload.form === 'object' ? payload.form : payload
      return create(form)
    }
    return {
      actionId,
      payload,
      handledAt: new Date().toISOString(),
      resourceKey: resource.resourceKey,
    }
  }

  return { list, get, create, update, remove, runAction }
}

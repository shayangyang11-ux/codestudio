export function validatePayload(resource, payload = {}) {
  const errors = []

  for (const field of resource.fields || []) {
    if (!field.required) continue
    const value = payload[field.key]
    if (value === undefined || value === null || String(value).trim() === '') {
      errors.push(`${field.label || field.key} 不能为空`)
    }
  }

  return errors
}

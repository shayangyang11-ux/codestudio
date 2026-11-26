function isExpressResponse(value) {
  return value && typeof value.status === 'function' && typeof value.json === 'function'
}

function normalizeError(errorOrMessage) {
  if (errorOrMessage instanceof Error) {
    return {
      message: errorOrMessage.message || 'Internal server error',
      details: errorOrMessage.stack,
    }
  }

  if (typeof errorOrMessage === 'object' && errorOrMessage !== null) {
    return {
      message: errorOrMessage.message || 'Request failed',
      details: errorOrMessage,
    }
  }

  return {
    message: errorOrMessage || 'Request failed',
    details: undefined,
  }
}

export function ok(target = null, data = null, message = 'OK', meta = undefined) {
  const body = {
    success: true,
    code: 0,
    message,
    data: isExpressResponse(target) ? data : target,
  }

  if (meta !== undefined) {
    body.meta = meta
  }

  if (isExpressResponse(target)) {
    return target.json(body)
  }

  return body
}

export function fail(target = null, message = 'Request failed', status = 400, details = undefined) {
  const body = {
    success: false,
    code: status,
    message,
  }

  if (details !== undefined) {
    body.details = details
  }

  if (isExpressResponse(target)) {
    return target.status(status).json(body)
  }

  return body
}

export function page(items = [], total = 0, pageNo = 1, pageSize = 20) {
  return ok({
    items,
    total,
    pageNo,
    pageSize,
  })
}

export function sendSuccess(res, data = null, message = 'OK', status = 200, meta = undefined) {
  const body = {
    success: true,
    code: 0,
    message,
    data,
  }

  if (meta !== undefined) {
    body.meta = meta
  }

  return res.status(status).json(body)
}

export function sendError(res, errorOrMessage = 'Request failed', status = 500, details = undefined) {
  const normalized = normalizeError(errorOrMessage)

  const body = {
    success: false,
    code: status,
    message: normalized.message,
  }

  if (details !== undefined) {
    body.details = details
  } else if (normalized.details !== undefined) {
    body.details = normalized.details
  }

  return res.status(status).json(body)
}

export function sendPaginated(res, items = [], total = 0, pageNo = 1, pageSize = 20, message = 'OK') {
  return sendSuccess(res, {
    items,
    total,
    pageNo,
    pageSize,
  }, message)
}

export function badRequest(res, message = 'Bad request', details = undefined) {
  return sendError(res, message, 400, details)
}

export function notFound(res, message = 'Not found', details = undefined) {
  return sendError(res, message, 404, details)
}

export function serverError(res, error = 'Internal server error', details = undefined) {
  return sendError(res, error, 500, details)
}

export default {
  ok,
  fail,
  page,
  sendSuccess,
  sendError,
  sendPaginated,
  badRequest,
  notFound,
  serverError,
}

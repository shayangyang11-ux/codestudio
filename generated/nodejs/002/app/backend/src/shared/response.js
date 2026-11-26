export function sendOk(res, data = {}, meta = {}) {
  res.json({ success: true, data, meta })
}

export function sendError(res, error) {
  const status = Number(error?.status || 500)
  res.status(status).json({
    success: false,
    error: {
      message: error?.message || 'Internal server error',
    },
  })
}

import { sendOk } from '../../shared/response.js'
import { createInboundOrderCreatePage, listInboundOrderCreatePage, runInboundOrderCreatePage } from './inbound-order-create-page.service.js'

export async function listInboundOrderCreatePageRecords(_req, res, next) {
  try {
    sendOk(res, await listInboundOrderCreatePage())
  } catch (error) {
    next(error)
  }
}

export async function createInboundOrderCreatePageRecord(req, res, next) {
  try {
    sendOk(res, await createInboundOrderCreatePage(req.body || {}))
  } catch (error) {
    next(error)
  }
}

export async function runInboundOrderCreatePageAction(req, res, next) {
  try {
    sendOk(res, await runInboundOrderCreatePage(req.body || {}))
  } catch (error) {
    next(error)
  }
}

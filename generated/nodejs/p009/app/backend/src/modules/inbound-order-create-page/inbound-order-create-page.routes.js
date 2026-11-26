import { Router } from 'express'
import { createInboundOrderCreatePageRecord, listInboundOrderCreatePageRecords, runInboundOrderCreatePageAction } from './inbound-order-create-page.controller.js'

export const router = Router()

router.get('/inbound-order-create-page', listInboundOrderCreatePageRecords)
router.post('/inbound-order-create-page', createInboundOrderCreatePageRecord)
router.post('/inbound-order-create-page/actions/inbound-order-create-page', runInboundOrderCreatePageAction)

import { insertInboundOrderCreatePage, listInboundOrderCreatePage } from './inbound-order-create-page.repository.js'

export async function listInboundOrderCreatePage() {
  return listInboundOrderCreatePage()
}

export async function createInboundOrderCreatePage(payload) {
  if (!payload?.title) {
    const error = new Error('标题不能为空。')
    error.status = 400
    throw error
  }

  return insertInboundOrderCreatePage({
    title: payload.title,
    description: payload.description || '',
    status: 'ACTIVE',
  })
}

export async function runInboundOrderCreatePage(payload) {
  return createInboundOrderCreatePage({
    title: payload.title || '入库单创建页',
    description: payload.description || '功能动作已通过真实后端服务处理。',
  })
}

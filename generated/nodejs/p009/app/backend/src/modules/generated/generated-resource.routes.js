import express from 'express'
import { fail, ok } from '../../shared/response.js'
import { createGeneratedResourceService } from './generated-resource.service.js'

export function createGeneratedResourceRouter(resource) {
  const router = express.Router()
  const service = createGeneratedResourceService(resource)

  router.get('/', (request, response) => {
    response.json(ok(service.list(request.query)))
  })

  router.post('/', (request, response) => {
    const result = service.create(request.body || {})
    response.status(201).json(ok(result, 'Created'))
  })

  router.get('/:id', (request, response) => {
    const result = service.get(request.params.id)
    if (!result) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(result))
  })

  router.put('/:id', (request, response) => {
    const result = service.update(request.params.id, request.body || {})
    if (!result) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(result, 'Updated'))
  })

  router.delete('/:id', (request, response) => {
    const removed = service.remove(request.params.id)
    if (!removed) {
      response.status(404).json(fail('Record not found'))
      return
    }
    response.json(ok(removed, 'Deleted'))
  })

  router.post('/:actionId', (request, response) => {
    response.json(ok(service.runAction(request.params.actionId, request.body || {}), 'Action completed'))
  })

  return router
}

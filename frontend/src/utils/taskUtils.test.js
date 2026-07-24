import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  getTaskDeleteBlockReason,
  TASK_DELETE_ASSIGNED_MESSAGE,
} from './taskUtils.js'

test('getTaskDeleteBlockReason: missing task', () => {
  assert.equal(getTaskDeleteBlockReason(null), 'Task could not be found')
})

test('getTaskDeleteBlockReason: blocked when assigned to a robot', () => {
  assert.equal(
    getTaskDeleteBlockReason({ id: 1, robotId: 5 }),
    TASK_DELETE_ASSIGNED_MESSAGE
  )
})

test('getTaskDeleteBlockReason: blocked by IN_PROGRESS status', () => {
  assert.equal(
    getTaskDeleteBlockReason({ id: 1, status: 'IN_PROGRESS' }),
    TASK_DELETE_ASSIGNED_MESSAGE
  )
})

test('getTaskDeleteBlockReason: blocked when another task depends on it', () => {
  const task = { id: 1, status: 'PENDING_ASSIGNMENT' }
  const tasks = [task, { id: 2, name: 'Deliver', dependencyIds: [1] }]

  assert.equal(getTaskDeleteBlockReason(task, tasks), 'Deliver depends on this task')
})

test('getTaskDeleteBlockReason: deletable when free and undepended', () => {
  const task = { id: 1, status: 'PENDING_ASSIGNMENT' }
  assert.equal(getTaskDeleteBlockReason(task, [task]), '')
})

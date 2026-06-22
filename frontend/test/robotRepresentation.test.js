import test from 'node:test'
import assert from 'node:assert/strict'

import { normaliseRobotFromBackend } from '../src/api/robotApi.js'
import { normaliseTaskFromBackend } from '../src/api/taskApi.js'
import {
  createRobot,
  reconcileRobotTaskIds,
} from '../src/utils/robotUtils.js'

test('createRobot returns only the canonical robot fields', () => {
  const robot = createRobot({
    id: 4,
    name: ' R-004 ',
    type: 'Large',
    position: { latitude: '1.3', longitude: '103.8' },
    taskIds: [7, { id: 8 }, 7],
  })

  assert.deepEqual(robot, {
    id: 4,
    name: 'R-004',
    type: 'LARGE',
    status: 'IDLE',
    speed: 5,
    position: { latitude: 1.3, longitude: 103.8 },
    taskIds: [7, 8],
  })
})

test('createRobot keeps an unknown or zero position as null', () => {
  assert.equal(createRobot({ id: 1, name: 'R-001' }).position, null)
  assert.equal(
    createRobot({
      id: 1,
      name: 'R-001',
      position: { latitude: null, longitude: null },
    }).position,
    null
  )
  assert.equal(
    createRobot({
      id: 1,
      name: 'R-001',
      position: { latitude: 0, longitude: 0 },
    }).position,
    null
  )
})

test('backend robots are converted to the canonical shape', () => {
  const robot = normaliseRobotFromBackend({
    id: 9,
    name: 'L9',
    type: 'LARGE',
    status: 'ASSIGNED',
    speed: 5,
    latitude: 1.31,
    longitude: 103.81,
    tasks: [{ id: 12 }],
    battery: 80,
  })

  assert.deepEqual(robot, {
    id: 9,
    name: 'L9',
    type: 'LARGE',
    status: 'ASSIGNED',
    speed: 5,
    position: { latitude: 1.31, longitude: 103.81 },
    taskIds: [12],
  })
})

test('task ownership is normalized to robotId and reconciled into robots', () => {
  const task = normaliseTaskFromBackend({
    id: 12,
    name: 'Delivery',
    priority: 1,
    startWayPointStr: '1.3,103.8',
    endWayPointStr: '1.4,103.9',
    robotId: 9,
  })
  const robots = [createRobot({ id: 9, name: 'L9', type: 'LARGE' })]

  assert.equal(task.robotId, 9)
  assert.deepEqual(reconcileRobotTaskIds(robots, [task])[0].taskIds, [12])
})

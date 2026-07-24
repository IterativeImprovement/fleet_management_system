import { test } from 'node:test'
import assert from 'node:assert/strict'
import { canDeleteRobot, reconcileRobotTaskIds } from './robotUtils.js'

test('canDeleteRobot: true when no tasks', () => {
  assert.equal(canDeleteRobot({ taskIds: [] }), true)
  assert.equal(canDeleteRobot({}), true)
  assert.equal(canDeleteRobot(null), true)
})

test('canDeleteRobot: false when tasks assigned', () => {
  assert.equal(canDeleteRobot({ taskIds: [1] }), false)
})

test('reconcileRobotTaskIds: folds task.robotId back onto its robot', () => {
  const robots = [
    { id: 1, taskIds: [] },
    { id: 2, taskIds: [] },
  ]
  const tasks = [
    { id: 10, robotId: 1 },
    { id: 11, robotId: 1 },
    { id: 12, robotId: null },
  ]

  const [r1, r2] = reconcileRobotTaskIds(robots, tasks)

  assert.deepEqual(r1.taskIds, [10, 11])
  assert.deepEqual(r2.taskIds, [])
})

test('reconcileRobotTaskIds: merges + dedupes existing taskIds', () => {
  const robots = [{ id: 1, taskIds: [10] }]
  const tasks = [{ id: 10, robotId: 1 }, { id: 11, robotId: 1 }]

  const [r1] = reconcileRobotTaskIds(robots, tasks)

  assert.deepEqual(r1.taskIds, [10, 11])
})

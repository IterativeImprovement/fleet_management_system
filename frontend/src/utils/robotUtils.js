export function getRobotStatusLabel(status) {
  const value = String(status).toUpperCase()

  if (value === 'IDLE') return 'Idle'
  if (value === 'ASSIGNED') return 'Assigned'
  if (value === 'MOVING') return 'Moving'
  if (value === 'MOVING_TO_BASE') return 'Moving to Base'
  if (value === 'MOVING_TO_MAINTENANCE') return 'Moving to Maintenance'
  if (value === 'CHARGING') return 'Charging'
  if (value === 'NEED_MAINTENANCE') return 'Needs Maintenance'
  if (value === 'UNDER_MAINTENANCE') return 'Under Repair'
  if (value === 'BLOCKED') return 'Blocked'
  if (value === 'ERROR') return 'Error'

  return 'Unknown'
}

export function getRobotStatusType(status) {
  const value = String(status).toUpperCase()

  if (value === 'IDLE') return 'idle'
  if (value === 'ASSIGNED') return 'assigned'
  if (value === 'MOVING') return 'to-task-start'
  if (value === 'MOVING_TO_BASE') return 'returning-to-base'
  if (value === 'MOVING_TO_MAINTENANCE') return 'maintenance'
  if (value === 'CHARGING') return 'charging'
  if (value === 'NEED_MAINTENANCE') return 'maintenance'
  if (value === 'UNDER_MAINTENANCE') return 'maintenance'
  if (value === 'BLOCKED') return 'blocked'
  if (value === 'ERROR') return 'error'

  return 'unknown'
}

export function getRobotTypeLabel(type) {
  const value = String(type).toUpperCase()

  if (value === '0' || value === 'STANDARD') return 'Standard'
  if (value === '1' || value === 'LARGE') return 'Large'

  return 'Unknown'
}

export const ROBOT_DELETE_ASSIGNED_TASKS_MESSAGE =
  "Complete or unassign this robot's tasks before deleting it."

export function canDeleteRobot(robot) {
  return (robot?.taskIds?.length ?? 0) === 0
}

export function normaliseRobotType(type) {
  const value = String(type || 'UNINITIALISED').trim().toUpperCase()

  if (value === '0' || value === 'STANDARD') return 'STANDARD'
  if (value === '1' || value === 'LARGE') return 'LARGE'

  return 'UNINITIALISED'
}

export function getRobotSpeed(robot) {
  if (
    robot.speed !== null &&
    robot.speed !== undefined &&
    robot.speed !== '' &&
    Number.isFinite(Number(robot.speed))
  ) {
    return Number(robot.speed)
  }

  const type = normaliseRobotType(robot.type)

  if (type === 'LARGE') return 5
  if (type === 'STANDARD') return 10

  return 0
}

export function normaliseRobotPosition(position) {
  if (!position) return null

  const { latitude, longitude } = position

  if (latitude === null || latitude === undefined) return null
  if (longitude === null || longitude === undefined) return null

  const numericLatitude = Number(latitude)
  const numericLongitude = Number(longitude)

  if (!Number.isFinite(numericLatitude) || !Number.isFinite(numericLongitude)) {
    return null
  }

  if (numericLatitude === 0 && numericLongitude === 0) return null

  return {
    latitude: numericLatitude,
    longitude: numericLongitude,
  }
}

export function normaliseTaskIds(taskIds = []) {
  if (!Array.isArray(taskIds)) return []

  const uniqueTaskIds = new Map()

  taskIds.forEach(task => {
    const taskId = typeof task === 'object' ? task?.id : task

    if (taskId === null || taskId === undefined || taskId === '') return

    uniqueTaskIds.set(String(taskId), taskId)
  })

  return [...uniqueTaskIds.values()]
}

export function createRobot({
  id,
  name = '',
  type = 'STANDARD',
  status = 'IDLE',
  speed,
  position = null,
  taskIds = [],
}) {
  const normalisedType = normaliseRobotType(type)

  return {
    id,
    name: String(name).trim(),
    type: normalisedType,
    status: String(status || 'IDLE').trim().toUpperCase(),
    speed: getRobotSpeed({ speed, type: normalisedType }),
    position: normaliseRobotPosition(position),
    taskIds: normaliseTaskIds(taskIds),
  }
}

export function reconcileRobotTaskIds(robots, tasks) {
  const taskIdsByRobotId = new Map()

  tasks.forEach(task => {
    if (task.robotId === null || task.robotId === undefined) return

    const robotId = String(task.robotId)
    const taskIds = taskIdsByRobotId.get(robotId) || []

    taskIds.push(task.id)
    taskIdsByRobotId.set(robotId, taskIds)
  })

  return robots.map(robot =>
    createRobot({
      ...robot,
      taskIds: [
        ...robot.taskIds,
        ...(taskIdsByRobotId.get(String(robot.id)) || []),
      ],
    })
  )
}

export function getRobotStatusLabel(status) {
  const value = String(status).toUpperCase()

  if (value === 'IDLE') return 'Idle'
  if (value === 'ASSIGNED') return 'Assigned'
  if (value === 'MOVING_TO_BASE') return 'Moving to Base'
  if (value === 'MOVING_TO_MAINTENANCE') return 'Moving to Maintenance'
  if (value === 'CHARGING') return 'Charging'
  if (value === 'NEED_MAINTENANCE') return 'Needs Maintenance'
  if (value === 'ERROR') return 'Error'

  return 'Unknown'
}

export function getRobotStatusType(status) {
  const value = String(status).toUpperCase()

  if (value === 'IDLE') return 'idle'
  if (value === 'ASSIGNED') return 'assigned'
  if (value === 'MOVING_TO_BASE') return 'moving'
  if (value === 'MOVING_TO_MAINTENANCE') return 'maintenance'
  if (value === 'CHARGING') return 'charging'
  if (value === 'NEED_MAINTENANCE') return 'maintenance'
  if (value === 'ERROR') return 'error'

  return 'unknown'
}

export function getRobotTypeLabel(type) {
  const value = String(type).toUpperCase()

  if (value === '0' || value === 'STANDARD') return 'Standard'
  if (value === '1' || value === 'LARGE') return 'Large'

  return 'Unknown'
}

export function getRobotSpeed(robot) {
  if (Number.isFinite(Number(robot.speed))) return Number(robot.speed)
  if (Number.isFinite(Number(robot.SPEED))) return Number(robot.SPEED)

  const type = String(robot.type).toUpperCase()

  if (type === 'LARGE') return 5
  if (type === 'STANDARD' || type === '0') return 10

  return 0
}

export function createRobot({
  id,
  name,
  type = 'STANDARD',
  status = 'IDLE',
  speed = 10.0,
  route = null,
  tasks = [],

  battery = null,
  x = null,
  y = null,
  latitude = null,
  longitude = null,
  path = [],
}) {
  return {
    id,
    name: name.trim(),
    type,
    status,
    speed,
    route,
    tasks,

    battery,
    x,
    y,
    latitude,
    longitude,
    path,
  }
}

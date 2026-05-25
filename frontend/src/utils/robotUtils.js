export function getRobotStatusLabel(status) {
  if (status === 0) return 'Idle'
  if (status === 1) return 'Moving'
  if (status === 2) return 'Executing Task'
  if (status === 5) return 'Low Battery'
  if (status === 9) return 'Broken Down'
  return 'Unknown'
}

export function getRobotStatusType(status) {
  if (status === 0) return 'idle'
  if (status === 1) return 'moving'
  if (status === 2) return 'executing'
  if (status === 5) return 'low-battery'
  if (status === 9) return 'broken-down'
  return 'unknown'
}

export function createRobot({
  id,
  name,
  type = 0,
  status = 0,
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
import { createRobot } from '../utils/robotUtils'

const ROBOT_ENDPOINT = '/robot'

async function getErrorMessage(response) {
  try {
    const data = await response.json()
    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

function normaliseRobotFromBackend(robot) {
  const latitude = Number(robot.latitude)
  const longitude = Number(robot.longitude)

  return createRobot({
    id: robot.id,
    name: robot.name ?? `Robot ${robot.id}`,
    type: robot.type ?? robot.TYPE ?? 'UNINITIALISED',
    status: robot.status ?? 'IDLE',
    speed: robot.speed ?? robot.SPEED,
    latitude: Number.isFinite(latitude) ? latitude : null,
    longitude: Number.isFinite(longitude) ? longitude : null,
    x: Number.isFinite(longitude) ? longitude : null,
    y: Number.isFinite(latitude) ? latitude : null,
    tasks: robot.tasks ?? [],
  })
}

function normaliseRobotTypeForBackend(type) {
  const value = String(type || 'STANDARD').trim().toUpperCase()

  if (value === 'STANDARD') return 'Standard'
  if (value === 'LARGE') return 'Large'

  return type
}

function robotToCreateRequest(robot) {
  return {
    name: robot.name,
    type: normaliseRobotTypeForBackend(robot.type),
    status: robot.status ?? 'IDLE',
    tasksIdsToRemove: [],
    taskIdsToAdd: [],
  }
}

export async function getRobots() {
  const response = await fetch(ROBOT_ENDPOINT, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Robot request failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('Robot request failed: expected an array of robots')
  }

  return data.map(normaliseRobotFromBackend)
}

export async function createRobotInBackend(robot) {
  const response = await fetch(ROBOT_ENDPOINT, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(robotToCreateRequest(robot)),
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Create robot failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  return normaliseRobotFromBackend(data)
}

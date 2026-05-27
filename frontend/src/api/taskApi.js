import { createTask } from '../utils/taskUtils'

const TASK_ENDPOINT = '/task'

async function getErrorMessage(response) {
  try {
    const data = await response.json()
    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

function normaliseTaskFromBackend(task) {
  return createTask({
    id: task.id,
    priority: Number(task.priority),
    name: task.name ?? '',
    description: task.description ?? '',
    type: task.type ?? 'STANDARD',
    status: task.status ?? 'PENDING_ASSIGNMENT',
    startDateTime: task.startDateTime ?? '',
    completionDateTime: task.completionDateTime ?? '',
    startWayPoint: task.startWayPoint ?? null,
    endWayPoint: task.endWayPoint ?? null,
    robot: task.robot ?? null,
    dependencies: task.dependencies ?? task.tasks ?? [],
  })
}

export async function getTasks() {
  const response = await fetch(TASK_ENDPOINT, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Task request failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('Task request failed: expected an array of tasks')
  }

  return data.map(normaliseTaskFromBackend)
}

function wayPointToString(wayPoint) {
  if (!wayPoint) {
    throw new Error('Task waypoint is missing')
  }

  const latitude = Number(wayPoint.latitude)
  const longitude = Number(wayPoint.longitude)

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    throw new Error('Task waypoint must contain valid latitude and longitude')
  }

  return `${latitude},${longitude}`
}

function getDependencyIds(task) {
  const dependencies = task.dependencies ?? task.tasks ?? []

  return dependencies
    .map(dependency =>
      typeof dependency === 'object' ? dependency.id : dependency
    )
    .filter(id => id !== null && id !== undefined && id !== '')
    .map(Number)
    .filter(Number.isFinite)
}

function normaliseTaskTypeForBackend(type) {
  const value = String(type || 'STANDARD').trim().toUpperCase()

  if (value === 'STANDARDTRANSPORT') return 'STANDARD'
  if (value === 'STANDARD') return 'STANDARD'
  if (value === 'LARGE') return 'LARGE'

  return value
}

function taskToCreateRequest(task) {
  return {
    name: task.name,
    description: task.description,
    type: normaliseTaskTypeForBackend(task.type),
    priority: task.priority,
    startDateTime: task.startDateTime,
    completionDateTime: task.completionDateTime,
    startWayPointStr: wayPointToString(task.startWayPoint),
    endWayPointStr: wayPointToString(task.endWayPoint),
    dependencyIds: getDependencyIds(task),
  }
}

export async function createTaskInBackend(task) {
  const response = await fetch(TASK_ENDPOINT, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(taskToCreateRequest(task)),
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Create task failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  return normaliseTaskFromBackend(data)
}

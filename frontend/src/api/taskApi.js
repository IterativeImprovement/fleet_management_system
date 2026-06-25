import { createTask } from '../utils/taskUtils.js'

const TASK_ENDPOINT = '/task'

async function getErrorMessage(response) {
  try {
    const data = await response.json()
    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

function parseWaypoint(wayPoint) {
  if (!wayPoint) return null

  if (typeof wayPoint === 'object') {
    const latitude = Number(wayPoint.latitude)
    const longitude = Number(wayPoint.longitude)

    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null

    return {
      latitude,
      longitude,
    }
  }

  const parts = wayPoint.split(',')
  if (parts.length !== 2) return null

  const latitude = Number(parts[0].trim())
  const longitude = Number(parts[1].trim())

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null

  return {
    latitude,
    longitude,
  }
}

function parseLocation(location) {
  if (!location) return null

  const latitude = Number(location.latitude)
  const longitude = Number(location.longitude)

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null

  return {
    id: location.id,
    name: location.name ?? '',
    address: location.address ?? '',
    postalCode: location.postalCode ?? '',
    latitude,
    longitude,
    source: location.source ?? '',
  }
}

export function normaliseTaskFromBackend(task) {
  return createTask({
    id: task.id,
    priority: Number(task.priority),
    name: task.name ?? '',
    description: task.description ?? '',
    type: task.type ?? 'STANDARD',
    status: task.status ?? 'PENDING_ASSIGNMENT',
    startDateTime: task.startDateTime ?? '',
    completionDateTime: task.completionDateTime ?? '',
    startLocationId: task.startLocationId ?? task.startLocation?.id ?? null,
    endLocationId: task.endLocationId ?? task.endLocation?.id ?? null,
    startLocation: parseLocation(task.startLocation),
    endLocation: parseLocation(task.endLocation),
    startWayPoint: parseWaypoint(task.startWayPointStr || task.startWayPoint),
    endWayPoint: parseWaypoint(task.endWayPointStr || task.endWayPoint),
    robotId: task.robotId ?? task.robot?.id ?? null,
    dependencyIds: task.dependencyIds ?? task.dependencies ?? task.tasks ?? [],
    routeGeometry: task.routeGeometry ?? task.route?.routeGeo ?? '',
    routeDistance: task.routeDistance ?? task.route?.totalDistance ?? null,
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
  const dependencyIds = task.dependencyIds ?? []

  return dependencyIds
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

function getLocationId(locationId, location) {
  const id = locationId ?? location?.id
  const numericId = Number(id)

  return Number.isFinite(numericId) ? numericId : null
}

function taskToCreateRequest(task) {
  const request = {
    name: task.name,
    description: task.description,
    type: normaliseTaskTypeForBackend(task.type),
    priority: task.priority,
    startDateTime: task.startDateTime,
    completionDateTime: task.completionDateTime,
    dependencyIds: getDependencyIds(task),
  }
  const startLocationId = getLocationId(task.startLocationId, task.startLocation)
  const endLocationId = getLocationId(task.endLocationId, task.endLocation)

  if (startLocationId !== null) {
    request.startLocationId = startLocationId
  } else {
    request.startWayPointStr = wayPointToString(task.startWayPoint)
  }

  if (endLocationId !== null) {
    request.endLocationId = endLocationId
  } else {
    request.endWayPointStr = wayPointToString(task.endWayPoint)
  }

  return request
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

export async function deleteTaskInBackend(taskId) {
  const response = await fetch(`${TASK_ENDPOINT}/${taskId}`, {
    method: 'DELETE',
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Delete task failed (${response.status}): ${message}`)
  }
}

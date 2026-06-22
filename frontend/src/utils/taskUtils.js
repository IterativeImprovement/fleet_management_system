export function getPriorityType(priority) {
  if (priority === 1) return 'high'
  if (priority === 2) return 'medium'
  if (priority === 3) return 'low'

  return 'unknown'
}

export function getTaskStatusLabel(status) {
  const value = String(status || 'PENDING_ASSIGNMENT').toUpperCase()

  if (value === 'PENDING_ASSIGNMENT') return 'Pending Assignment'
  if (value === 'ASSIGNED') return 'Assigned'
  if (value === 'WAITING_FOR_DEPENDENCIES') return 'Waiting for Dependencies'
  if (value === 'IN_PROGRESS') return 'In Progress'
  if (value === 'COMPLETED') return 'Completed'
  if (value === 'ERROR') return 'Error'

  return 'Unknown'
}

export function getTaskStatusType(status) {
  const value = String(status || 'PENDING_ASSIGNMENT').toUpperCase()

  if (value === 'PENDING_ASSIGNMENT') return 'pending'
  if (value === 'ASSIGNED') return 'assigned'
  if (value === 'WAITING_FOR_DEPENDENCIES') return 'waiting'
  if (value === 'IN_PROGRESS') return 'in-progress'
  if (value === 'COMPLETED') return 'completed'
  if (value === 'ERROR') return 'error'

  return 'unknown'
}

export const TASK_DELETE_ASSIGNED_MESSAGE =
  'Cannot delete a task that is assigned or in progress'

function normaliseWayPoint(wayPoint) {
  if (!wayPoint) return null

  const latitude = Number(wayPoint.latitude)
  const longitude = Number(wayPoint.longitude)

  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null

  return {
    ...wayPoint,
    latitude,
    longitude,
  }
}

function normaliseTaskType(type) {
  const value = String(type || 'STANDARD').trim().toUpperCase()

  return value === 'STANDARDTRANSPORT' ? 'STANDARD' : value
}

export function normaliseDependencyIds(dependencyIds = []) {
  if (!Array.isArray(dependencyIds)) return []

  const uniqueIds = new Map()

  dependencyIds.forEach(dependency => {
    const id = typeof dependency === 'object' ? dependency?.id : dependency
    const numericId = Number(id)

    if (
      id === null ||
      id === undefined ||
      id === '' ||
      !Number.isFinite(numericId)
    ) return

    uniqueIds.set(String(numericId), numericId)
  })

  return [...uniqueIds.values()]
}

export function getTaskDeleteBlockReason(task, tasks = []) {
  if (!task) return 'Task could not be found'

  const status = String(task?.status || '').toUpperCase()

  if (
    (task.robotId !== null && task.robotId !== undefined) ||
    status === 'ASSIGNED' ||
    status === 'IN_PROGRESS'
  ) {
    return TASK_DELETE_ASSIGNED_MESSAGE
  }

  const dependentTask = tasks.find(candidate =>
    String(candidate.id) !== String(task?.id) &&
    normaliseDependencyIds(candidate.dependencyIds).some(
      dependencyId => String(dependencyId) === String(task?.id)
    )
  )

  if (dependentTask) {
    return `${dependentTask.name || `Task ${dependentTask.id}`} depends on this task`
  }

  return ''
}

export function createTask({
  id,
  priority,
  name,
  description = '',
  type = 'STANDARD',
  status = 'PENDING_ASSIGNMENT',
  startDateTime = '',
  completionDateTime = '',
  startWayPoint,
  endWayPoint,
  robotId = null,
  dependencyIds = [],
  routeGeometry = '',
  routeDistance = null,
}) {
  const numericPriority = Number(priority)
  const numericRouteDistance = routeDistance === null || routeDistance === undefined
    ? null
    : Number(routeDistance)

  return {
    id,
    priority: numericPriority,

    name: String(name || '').trim(),
    description: String(description || '').trim(),
    type: normaliseTaskType(type),
    status: String(status || 'PENDING_ASSIGNMENT').trim().toUpperCase(),

    startDateTime,
    completionDateTime,

    startWayPoint: normaliseWayPoint(startWayPoint),
    endWayPoint: normaliseWayPoint(endWayPoint),

    robotId,
    dependencyIds: normaliseDependencyIds(dependencyIds),
    routeGeometry: String(routeGeometry || ''),
    routeDistance: Number.isFinite(numericRouteDistance) ? numericRouteDistance : null,
  }
}

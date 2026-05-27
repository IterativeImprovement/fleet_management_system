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
  robot = null,
  tasks = [],
  dependencies = tasks,
}) {
  return {
    id,
    priority,
    priorityType: getPriorityType(priority),

    name: name.trim(),
    description: description.trim(),
    type,
    status,

    startDateTime,
    completionDateTime,

    startWayPoint,
    endWayPoint,

    robot,
    tasks: dependencies,
    dependencies,
  }
}

export function getPriorityType(priority) {
  return priority.toLowerCase()
}

export function createTask({
  id,
  priority,
  start,
  destination,
  requiredCompletionTime = '',
  dependencies = [],
  assignedRobotId = null,
  status = 'Pending',
  eta = '-',
  route = [],
}) {
  return {
    id: id.trim(),
    priority,
    priorityType: getPriorityType(priority),

    start: start.trim(),
    destination: destination.trim(),
    requiredCompletionTime: requiredCompletionTime.trim() || '-',
    dependencies,

    status,
    assignedRobotId,
    eta,
    route,
  }
}
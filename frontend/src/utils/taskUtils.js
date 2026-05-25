export function getPriorityType(priority) {
  if (priority === 1) return 'high'
  if (priority === 2) return 'medium'
  if (priority === 3) return 'low'

  return 'unknown'
}

export function createTask({
  id,
  priority,
  name,
  description = '',
  type = 'StandardTransport',
  startDateTime = '',
  completionDateTime = '',
  startWayPoint,
  endWayPoint,
  robot = null,
  tasks = [],
}) {
  return {
    id,
    priority,
    priorityType: getPriorityType(priority),

    name: name.trim(),
    description: description.trim(),
    type,

    startDateTime,
    completionDateTime,

    startWayPoint,
    endWayPoint,

    robot,
    tasks,
  }
}
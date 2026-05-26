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
    type: task.type ?? 'StandardTransport',
    startDateTime: task.startDateTime ?? '',
    completionDateTime: task.completionDateTime ?? '',
    startWayPoint: task.startWayPoint ?? null,
    endWayPoint: task.endWayPoint ?? null,
    robot: task.robot ?? null,
    tasks: task.tasks ?? [],
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
async function getErrorMessage(response) {
  try {
    const data = await response.json()
    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

export async function generateSimulation(config) {
  const response = await fetch('/simulation/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config ?? {}),
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Simulation generate failed (${response.status}): ${message}`)
  }

  return response.json()
}

export async function resetSimulationRun(simulationId) {
  const response = await fetch(`/simulation/${simulationId}`, { method: 'DELETE' })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Simulation reset failed (${response.status}): ${message}`)
  }
}

export async function getRoad(roadId) {
  const response = await fetch(`/road/${roadId}`)

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Get road failed (${response.status}): ${message}`)
  }

  return response.json()
}

// Startup/reconnect snapshot of every robot's current dispatch leg for a run.
export async function getDispatchSnapshot(simulationId) {
  const response = await fetch(`/simulation/${simulationId}/dispatches`, {
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) return []
  return response.json()
}

// Tell the backend a robot finished the leg it was animating (revision-gated).
export async function postDispatchArrival(robotId, revision) {
  await fetch(`/robot/${robotId}/dispatch/${revision}/arrive`, { method: 'POST' })
}

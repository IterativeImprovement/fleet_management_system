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

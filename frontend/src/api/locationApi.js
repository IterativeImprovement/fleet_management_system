const LOCATION_ENDPOINT = '/locations'

async function getErrorMessage(response) {
  try {
    const data = await response.json()
    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

function normaliseLocation(location) {
  const latitude = Number(location.latitude)
  const longitude = Number(location.longitude)

  return {
    id: location.id,
    name: location.name ?? '',
    address: location.address ?? '',
    postalCode: location.postalCode ?? '',
    latitude,
    longitude,
    source: location.source ?? 'CUSTOM',
    externalId: location.externalId ?? '',
  }
}

export async function searchLocations(query) {
  const trimmedQuery = String(query || '').trim()
  if (!trimmedQuery) return []

  const response = await fetch(
    `${LOCATION_ENDPOINT}/search?query=${encodeURIComponent(trimmedQuery)}`,
    {
      method: 'GET',
      headers: {
        Accept: 'application/json',
      },
    }
  )

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Location search failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('Location search failed: expected an array of locations')
  }

  return data.map(normaliseLocation)
}

export async function createCustomLocation(location) {
  const response = await fetch(LOCATION_ENDPOINT, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      name: location.name,
      address: location.address || '',
      postalCode: location.postalCode || '',
      latitude: Number(location.latitude),
      longitude: Number(location.longitude),
    }),
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Create location failed (${response.status}): ${message}`)
  }

  const data = await response.json()
  return normaliseLocation(data)
}

export async function saveSelectedOneMapLocation(location) {
  const response = await fetch(`${LOCATION_ENDPOINT}/onemap`, {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      name: location.name,
      address: location.address || '',
      postalCode: location.postalCode || '',
      latitude: Number(location.latitude),
      longitude: Number(location.longitude),
      externalId: location.externalId || '',
    }),
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Save OneMap location failed (${response.status}): ${message}`)
  }

  const data = await response.json()
  return normaliseLocation(data)
}

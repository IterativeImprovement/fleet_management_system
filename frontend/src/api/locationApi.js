const LOCATION_ENDPOINT = '/locations'

export class CustomLocationConflictError extends Error {
  constructor(message, existingLocation, proposedName) {
    super(message)
    this.name = 'CustomLocationConflictError'
    this.existingLocation = existingLocation
    this.proposedName = proposedName
  }
}

async function getErrorData(response) {
  try {
    return await response.json()
  } catch {
    return null
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

function getErrorMessage(response, data) {
  if (!data) return response.statusText || 'Unknown error'

  return data.message || data.error || JSON.stringify(data)
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
    const data = await getErrorData(response)
    const message = getErrorMessage(response, data)
    throw new Error(`Location search failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('Location search failed: expected an array of locations')
  }

  return data.map(normaliseLocation)
}

export async function createCustomLocation(location, options = {}) {
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
      confirmRename: Boolean(options.confirmRename),
    }),
  })

  if (!response.ok) {
    const data = await getErrorData(response)
    const message = getErrorMessage(response, data)

    if (response.status === 409 && data?.existingLocation) {
      throw new CustomLocationConflictError(
        message,
        normaliseLocation(data.existingLocation),
        data.proposedName || location.name
      )
    }

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
    const data = await getErrorData(response)
    const message = getErrorMessage(response, data)
    throw new Error(`Save OneMap location failed (${response.status}): ${message}`)
  }

  const data = await response.json()
  return normaliseLocation(data)
}

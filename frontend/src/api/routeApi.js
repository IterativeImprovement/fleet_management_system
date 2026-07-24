// /route/coords is now the graph A* router (needs robotType, returns routeGeo).
// This fallback stays on OneMap: it always answers, even when A* has no graph or no path.
const ROUTE_ENDPOINT = '/route/onemap/coords'

async function getErrorMessage(response) {
  try {
    const data = await response.json()

    return data.message || data.error || JSON.stringify(data)
  } catch {
    return response.statusText || 'Unknown error'
  }
}

export async function getRouteGeometry(start, end) {
  if (!start || !end) {
    throw new Error('Start and end coordinates are required')
  }

  const params = new URLSearchParams({
    start,
    end,
  })

  const response = await fetch(`${ROUTE_ENDPOINT}?${params.toString()}`, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Route request failed (${response.status}): ${message}`)
  }

  const data = await response.json()

  return {
    routeGeometry: data.route_geometry ?? data.routeGeometry ?? '',
    routeSummary: data.route_summary ?? data.routeSummary ?? null,
    routeName: data.route_name ?? data.routeName ?? [],
    raw: data,
  }
}

export async function getColoredRouteSegments(start, end) {
  if (!start || !end) {
    throw new Error('Start and end coordinates are required')
  }

  const params = new URLSearchParams({ start, end })
  const response = await fetch(`/route/colored-coords?${params.toString()}`, {
    method: 'GET',
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    const message = await getErrorMessage(response)
    throw new Error(`Colored route request failed (${response.status}): ${message}`)
  }

  return response.json()
}